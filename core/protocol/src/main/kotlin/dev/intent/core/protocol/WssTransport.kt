package dev.intent.core.protocol

import java.io.Closeable
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/** One pinned endpoint. Reconnect callers must authenticate and restore subscriptions each time. */
class WssTransport(
    endpoint: String,
    token: String,
    expectedFingerprint: String,
    private val listener: Listener,
    heartbeatMillis: Long = 30_000
) : Closeable {
    interface Listener {
        fun onOpen() {}
        fun onMessage(message: JsonRpcMessage) {}
        fun onClosed(code: Int, reason: String) {}
        fun onFailure(error: Throwable) {}
    }

    private val pin = CertificateFingerprint.normalize(expectedFingerprint)
    private val request = Request.Builder().url(
        endpoint
    ).header("Authorization", "Bearer $token").build()
    private val client: OkHttpClient
    private var socket: WebSocket? = null
    private var generation = 0L
    private var disposed = false

    init {
        require(endpoint.startsWith("wss://")) { "Only pinned WSS endpoints are supported" }
        require(request.url.encodedUsername.isEmpty() && request.url.encodedPassword.isEmpty())
        require(request.url.query == null && request.url.fragment == null)
        require(token.isNotBlank()) { "A bearer token is required" }
        require(heartbeatMillis > 0)
        val trust = LeafTrustManager {
            if (CertificateFingerprint.of(it) !=
                pin
            ) {
                throw CertificateException("Certificate fingerprint mismatch")
            }
        }
        client = OkHttpClient.Builder()
            .sslSocketFactory(tlsContext(trust).socketFactory, trust)
            // The confirmed certificate is the identity, including for tunnel loopback endpoints.
            .hostnameVerifier { _, session ->
                val leaf = session.peerCertificates.firstOrNull() as? X509Certificate
                leaf != null && CertificateFingerprint.of(leaf) == pin
            }
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .pingInterval(heartbeatMillis, TimeUnit.MILLISECONDS)
            .build()
    }

    @Synchronized
    fun connect() {
        check(!disposed) { "Transport is closed" }
        check(socket == null) { "Transport is already connected or connecting" }
        val current = ++generation
        socket = client.newWebSocket(request, callbacks(current))
    }

    @Synchronized
    fun send(message: JsonRpcMessage): Boolean = socket?.send(JsonRpc.encode(message)) ?: false

    @Synchronized
    fun disconnect() {
        generation++
        socket?.cancel()
        socket = null
    }

    @Synchronized
    override fun close() {
        disconnect()
        disposed = true
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    private fun callbacks(current: Long): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) =
            active(current) { listener.onOpen() }

        override fun onMessage(webSocket: WebSocket, text: String) = active(current) {
            val message = try {
                JsonRpc.decode(text)
            } catch (error: kotlinx.serialization.SerializationException) {
                fail(error)
                return@active
            }
            listener.onMessage(message)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = active(current) {
            fail(IllegalArgumentException("JSON-RPC requires a text frame"))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = active(current) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = active(current) {
            socket = null
            generation++
            listener.onClosed(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
            active(current) {
                fail(t)
            }
    }

    @Synchronized
    private fun active(current: Long, action: () -> Unit) {
        if (current == generation && !disposed) action()
    }

    private fun fail(error: Throwable) {
        disconnect()
        listener.onFailure(error)
    }
}

/** Delay hook for the session owner. A successful connection resets its attempt counter. */
fun reconnectDelayMillis(attempt: Int): Long {
    require(attempt >= 0)
    return minOf(
        MAX_RECONNECT_DELAY,
        INITIAL_RECONNECT_DELAY shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT)
    )
}

private const val MAX_RECONNECT_DELAY = 30_000L
private const val INITIAL_RECONNECT_DELAY = 1000L
private const val MAX_BACKOFF_SHIFT = 5
