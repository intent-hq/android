package dev.intent.core.protocol.session

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.RpcError
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.reconnectDelayMillis
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

class RpcException(val error: RpcError) : IOException(error.message)
class SessionDisconnectedException : IOException("RPC connection is not ready")

enum class SessionState { DISCONNECTED, CONNECTING, HANDSHAKING, READY, CLOSED }

/**
 * All state and callbacks run on one owned executor. Callbacks must not block or wait on requests.
 * Mutations are never replayed. Reconnect restores subscriptions only after a successful hello.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class RpcSession(
    private val transportFactory: (WssTransport.Listener) -> SessionTransport,
    private val helloParams: JsonObject,
    private val requestTimeoutMillis: Long = 30_000,
    private val reconnectDelay: (Int) -> Long = ::reconnectDelayMillis,
    private val onState: (SessionState) -> Unit = {},
    private val verifyPrincipal: Boolean = false,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) : Closeable {
    private val responseJson = Json { ignoreUnknownKeys = true }

    @Volatile
    var principalId: String? = null
        private set
    private val closed = AtomicBoolean()
    private var state = SessionState.DISCONNECTED
    private var transport: SessionTransport? = null
    private var generation = 0L
    private var nextId = 0L
    private var reconnectAttempt = 0
    private val pending = mutableMapOf<JsonElement, CompletableFuture<JsonObject>>()
    private val subscriptions = mutableSetOf<SubscriptionEntry>()

    init {
        require(requestTimeoutMillis > 0)
    }

    fun connect() = execute {
        if (state == SessionState.DISCONNECTED) open()
    }

    @Synchronized
    fun request(
        method: String,
        params: JsonElement? = null,
        timeoutMillis: Long = requestTimeoutMillis
    ): CompletableFuture<JsonObject> {
        require(timeoutMillis > 0)
        val result = CompletableFuture<JsonObject>()
        if (closed.get()) {
            result.completeExceptionally(SessionDisconnectedException())
        } else {
            execute {
                if (state != SessionState.READY) {
                    result.completeExceptionally(SessionDisconnectedException())
                } else {
                    send(method, params, timeoutMillis, result)
                }
            }
        }
        return result
    }

    fun <T> request(
        method: String,
        params: JsonElement?,
        deserializer: DeserializationStrategy<T>
    ): CompletableFuture<T> = request(method, params).thenApply {
        responseJson.decodeFromJsonElement(deserializer, it)
    }

    @Synchronized
    fun subscribe(
        spec: SubscriptionSpec,
        onMessage: (JsonObject) -> Unit,
        onSubscribed: (String) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): SessionSubscription {
        check(!closed.get()) { "Session is closed" }
        val entry = SubscriptionEntry(spec, onMessage, onSubscribed, onError)
        execute {
            subscriptions.add(entry)
            if (state == SessionState.READY) admit(entry)
        }
        return object : SessionSubscription {
            override val ready = entry.ready
            override fun close() = execute { retire(entry) }
        }
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.execute {
            generation++
            transport?.close()
            transport = null
            failPending(SessionDisconnectedException())
            subscriptions.forEach { it.ready.completeExceptionally(SessionDisconnectedException()) }
            subscriptions.clear()
            changeState(SessionState.CLOSED)
            executor.shutdownNow()
        }
    }

    @Synchronized
    private fun execute(action: () -> Unit) {
        if (!closed.get()) executor.execute(action)
    }

    private fun open() {
        val current = ++generation
        changeState(SessionState.CONNECTING)
        val listener = object : WssTransport.Listener {
            override fun onOpen() = dispatch(current) { handshake() }
            override fun onMessage(message: JsonRpcMessage) = dispatch(current) { receive(message) }
            override fun onClosed(code: Int, reason: String) = dispatch(current) { disconnected() }
            override fun onFailure(error: Throwable) = dispatch(current) { disconnected() }
        }
        try {
            transport = transportFactory(listener)
            transport?.connect()
        } catch (_: Exception) {
            disconnected()
        }
    }

    private fun dispatch(current: Long, action: () -> Unit) = execute {
        if (current == generation) action()
    }

    private fun handshake() {
        changeState(SessionState.HANDSHAKING)
        val reply = CompletableFuture<JsonObject>()
        val current = generation
        reply.whenComplete { _, error ->
            if (current == generation) {
                if (error != null) {
                    // Includes startup-not-ready: never hydrate or replay while hello is gated.
                    disconnected()
                } else {
                    if (verifyPrincipal) verifyIdentity(current) else ready()
                }
            }
        }
        send("client.hello", helloParams, requestTimeoutMillis, reply)
    }

    private fun ready() {
        reconnectAttempt = 0
        changeState(SessionState.READY)
        subscriptions.toList().forEach { admit(it) }
    }

    /** Check identity before replaying subscriptions or exposing a retained cache. */
    private fun verifyIdentity(current: Long) {
        val reply = CompletableFuture<JsonObject>()
        reply.whenComplete { result, error ->
            if (current == generation) {
                val id = result?.string("id")?.takeIf { it.isNotBlank() }
                val samePrincipal = principalId == null || principalId == id
                if (error != null || id == null || !samePrincipal) {
                    close()
                } else {
                    principalId = id
                    ready()
                }
            }
        }
        send("principal.me", null, requestTimeoutMillis, reply)
    }

    private fun send(
        method: String,
        params: JsonElement?,
        timeoutMillis: Long,
        result: CompletableFuture<JsonObject>
    ) {
        val id = JsonPrimitive((++nextId).toString())
        val message = try {
            JsonRpcMessage.Request(id, method, params)
        } catch (error: IllegalArgumentException) {
            result.completeExceptionally(error)
            return
        }
        pending[id] = result
        val timeout = executor.schedule({
            pending.remove(id)?.completeExceptionally(TimeoutException("RPC request timed out"))
        }, timeoutMillis, TimeUnit.MILLISECONDS)
        result.whenComplete { _, _ ->
            timeout.cancel(false)
            execute { pending.remove(id) }
        }
        try {
            if (transport?.send(message) != true) disconnected()
        } catch (_: Exception) {
            disconnected()
        }
    }

    private fun receive(message: JsonRpcMessage) {
        when (message) {
            is JsonRpcMessage.Success -> pending.remove(message.id)?.complete(message.result)
            is JsonRpcMessage.Failure -> pending.remove(
                message.id
            )?.completeExceptionally(RpcException(message.error))
            is JsonRpcMessage.Notification -> route(message)
            is JsonRpcMessage.Request -> Unit
        }
    }

    private fun disconnected() {
        generation++
        transport?.close()
        transport = null
        subscriptions.forEach {
            it.serverId = null
            it.sequence = -1
        }
        failPending(SessionDisconnectedException())
        changeState(SessionState.DISCONNECTED)
        val current = generation
        executor.schedule({
            if (!closed.get() && current == generation) open()
        }, reconnectDelay(reconnectAttempt++), TimeUnit.MILLISECONDS)
    }

    private fun failPending(error: Throwable) {
        val requests = pending.values.toList()
        pending.clear()
        requests.forEach { it.completeExceptionally(error) }
    }

    private fun admit(entry: SubscriptionEntry) {
        val current = generation
        val reply = CompletableFuture<JsonObject>()
        reply.whenComplete { result, error ->
            if (current == generation) {
                if (error != null) {
                    entry.ready.completeExceptionally(error)
                    safely { entry.onError(error) }
                    if (error is TimeoutException) disconnected()
                } else {
                    val id = result.string("subscriptionId")
                    if (id == null) {
                        val malformed = IOException("Subscription reply has no subscriptionId")
                        entry.ready.completeExceptionally(malformed)
                        safely { entry.onError(malformed) }
                        disconnected()
                    } else {
                        entry.serverId = id
                        if (!entry.active) {
                            unsubscribe(entry)
                        } else {
                            entry.ready.complete(id)
                            safely { entry.onSubscribed(id) }
                        }
                    }
                }
            }
        }
        send(entry.spec.method, entry.spec.params, requestTimeoutMillis, reply)
    }

    private fun retire(entry: SubscriptionEntry) {
        entry.active = false
        subscriptions.remove(entry)
        entry.ready.cancel(false)
        unsubscribe(entry)
    }

    private fun unsubscribe(entry: SubscriptionEntry) {
        val id = entry.serverId ?: return
        entry.serverId = null
        val params = mutableMapOf<String, JsonElement>("subscriptionId" to JsonPrimitive(id))
        entry.spec.params["workspaceId"]?.let { params["workspaceId"] = it }
        send(
            entry.spec.unsubscribeMethod,
            JsonObject(params),
            requestTimeoutMillis,
            CompletableFuture()
        )
    }

    @Suppress("ReturnCount")
    private fun route(message: JsonRpcMessage.Notification) {
        val params = message.params as? JsonObject ?: return
        val id = params.string("subscriptionId") ?: return
        val entry = subscriptions.firstOrNull {
            it.active && it.serverId == id && it.spec.notificationMethod == message.method
        } ?: return
        if (message.method != "subscription.push" || acceptSequence(entry, params)) {
            safely { entry.onMessage(params) }
        }
    }

    @Suppress("ReturnCount")
    private fun acceptSequence(entry: SubscriptionEntry, params: JsonObject): Boolean {
        val value = params["seq"] as? JsonPrimitive ?: return false
        val seq = value.takeUnless { it.isString }?.longOrNull ?: return false
        if (seq < 0 || seq <= entry.sequence) return false
        val kind = params.string("kind")
        if (kind !in setOf("snapshot", "delta", "history")) return false
        if (kind != "snapshot" && (seq != entry.sequence + 1 || entry.sequence < 0)) {
            unsubscribe(entry)
            entry.sequence = -1
            admit(entry)
            return false
        }
        entry.sequence = seq
        return true
    }

    private fun changeState(value: SessionState) {
        state = value
        safely { onState(value) }
    }

    private fun safely(action: () -> Unit) {
        try {
            action()
        } catch (_: Exception) { /* A consumer cannot stop the receive pump. */ }
    }
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }?.content
