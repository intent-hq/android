package dev.intent.app.assistant

import dev.intent.core.protocol.session.RpcException
import dev.intent.core.protocol.session.RpcSession
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class ProposalState(
    val pending: Map<String, String> = emptyMap(),
    val resolutions: Map<String, String> = emptyMap(),
    val completed: Map<String, Set<String>> = emptyMap(),
    val created: Map<String, String> = emptyMap(),
    val attempts: Set<String> = emptySet(),
    val busy: Boolean = false,
    val canCreate: Boolean = false,
    val error: String? = null
)

/** One instance per conversation. Successful mutations survive retries until the route closes. */
@Suppress("TooManyFunctions") // One scoped proposal state machine.
class ProposalStore(
    private val request: AssistantRequest,
    private val workspaceId: String,
    private val agentId: String,
    private val capabilities: () -> JsonObject = { JsonObject(emptyMap()) }
) : Closeable {
    private val mutable = MutableStateFlow(ProposalState())
    val state = mutable.asStateFlow()
    private var closed = false
    private val creationRequests = mutableMapOf<String, JsonObject>()

    fun key(proposal: ActionProposal, message: String): String = "$message:${proposal.id}"

    @Synchronized
    fun refresh() {
        if (closed || state.value.busy) return
        mutable.value = state.value.copy(busy = true)
        load().whenComplete { _, error ->
            synchronized(this) {
                if (!closed) {
                    mutable.value = state.value.copy(busy = false)
                    if (error != null) fail(error)
                }
            }
        }
    }

    private fun load(): CompletableFuture<Unit> =
        request("principal.me", JsonObject(emptyMap())).thenCompose { principal ->
            synchronized(this) {
                check(!closed) { "Conversation closed." }
                mutable.value =
                    state.value.copy(
                        canCreate =
                        principalRole(principal, capabilities()) in setOf("owner", "member")
                    )
            }
            request("agent.get", params())
        }.thenApply { response ->
            synchronized(this) {
                if (!closed) {
                    val agent =
                        response.objectValue()["agent"] as? JsonObject
                            ?: error("Incomplete proposal response.")
                    val metadata = agent["metadata"].objectValue()
                    val references = (metadata["pendingProposals"] as? JsonArray).orEmpty()
                    val pending = references.mapNotNull {
                        val row = it.objectValue()
                        val id = row.text("proposalId")
                        val message = row.text("messageId")
                        if (id == null || message == null) null else id to message
                    }.toMap()
                    val outcomes = metadata["proposalResolutions"].objectValue()
                    val resolutions = outcomes.mapNotNull { (id, value) ->
                        (value as? JsonPrimitive)?.contentOrNull?.let { id to it }
                    }.toMap()
                    mutable.value =
                        state.value.copy(pending = pending, resolutions = resolutions, error = null)
                }
            }
        }

    @Synchronized
    @Suppress("ReturnCount") // Reject invalid actions before starting a request.
    fun apply(
        proposal: ActionProposal,
        message: String,
        selected: Set<String>,
        title: String = proposal.creationTitle,
        prompt: String = proposal.prompt
    ) {
        if (closed || state.value.busy) return
        if (proposal.method == null && !proposal.creation) return
        val ids = proposal.items.filter { it.id in selected && !it.disabled }.map { it.id }
        if (!proposal.creation && ids.isEmpty()) return
        operate(proposal, message) {
            if (proposal.creation) {
                check(state.value.canCreate) {
                    "Workspace creation is unavailable for this account."
                }
                create(proposal, message, title, prompt)
            } else {
                val receipt = key(proposal, message)
                var chain = CompletableFuture.completedFuture(Unit)
                ids.forEach { id ->
                    chain = chain.thenCompose {
                        if (closed ||
                            id in state.value.completed[receipt].orEmpty()
                        ) {
                            CompletableFuture.completedFuture(Unit)
                        } else {
                            request(
                                proposal.method!!,
                                buildJsonObject {
                                    put("workspaceId", id)
                                }
                            ).thenApply { result ->
                                require(success(result)) {
                                    "The server did not confirm the workspace change."
                                }
                                synchronized(this) {
                                    mutable.value = state.value.copy(
                                        completed = state.value.completed +
                                            (
                                                receipt to
                                                    (state.value.completed[receipt].orEmpty() + id)
                                                )
                                    )
                                }
                            }
                        }
                    }
                }
                chain
            }
        }
    }

    private fun create(
        proposal: ActionProposal,
        message: String,
        title: String,
        prompt: String
    ): CompletableFuture<Unit> {
        val receipt = key(proposal, message)
        if (state.value.created[receipt] != null) return CompletableFuture.completedFuture(Unit)
        val requestParams = creationRequests.getOrPut(receipt) {
            proposal.creationRequest(
                title,
                prompt,
                "android-proposal:$workspaceId:$agentId:$receipt"
            )
        }
        mutable.value = state.value.copy(attempts = state.value.attempts + receipt)
        return request("workspace.create", requestParams).whenComplete { _, error ->
            val cause = generateSequence(error) {
                it.cause
            }.filterIsInstance<RpcException>().firstOrNull()
            if (cause?.error?.code == INVALID_PARAMETERS) {
                synchronized(this) {
                    creationRequests.remove(receipt)
                    mutable.value = state.value.copy(attempts = state.value.attempts - receipt)
                }
            }
        }.thenApply { response ->
            val id = response.objectValue()["workspace"].objectValue().text("id")
            require(!id.isNullOrBlank()) {
                "The server did not confirm workspace creation. Retry with the same request."
            }
            synchronized(this) {
                mutable.value =
                    state.value.copy(created = state.value.created + (receipt to id))
            }
        }
    }

    @Synchronized
    fun dismiss(proposal: ActionProposal, message: String) {
        if (closed || state.value.busy || key(proposal, message) in state.value.attempts) return
        operate(proposal, message, "dismissed") { CompletableFuture.completedFuture(Unit) }
    }

    private fun operate(
        proposal: ActionProposal,
        message: String,
        outcome: String = "applied",
        action: () -> CompletableFuture<Unit>
    ) {
        mutable.value = state.value.copy(busy = true, error = null)
        load().thenCompose {
            if (closed ||
                state.value.pending[proposal.id] != message
            ) {
                CompletableFuture.completedFuture(Unit)
            } else {
                action().thenCompose {
                    if (closed) {
                        CompletableFuture.completedFuture(
                            Unit
                        )
                    } else {
                        resolve(proposal, outcome)
                    }
                }
            }
        }.whenComplete { _, error ->
            synchronized(this) {
                if (!closed) {
                    mutable.value = state.value.copy(busy = false)
                    if (error != null) fail(error)
                }
            }
        }
    }

    private fun resolve(proposal: ActionProposal, outcome: String) = request(
        "agent.resolveProposal",
        params {
            put("proposalId", proposal.id)
            put("outcome", outcome)
        }
    ).thenApply { response ->
        val result = response.objectValue()
        val recorded = result.text("outcome")
        require(
            (result["success"] as? JsonPrimitive)?.booleanOrNull == true &&
                recorded in setOf("applied", "dismissed")
        ) {
            "Could not save proposal confirmation. Retry to confirm."
        }
        synchronized(this) {
            mutable.value = state.value.copy(
                pending = state.value.pending - proposal.id,
                resolutions = state.value.resolutions + (proposal.id to recorded!!)
            )
        }
    }

    private fun params(extra: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("workspaceId", workspaceId)
        put("agentId", agentId)
        extra()
    }
    private fun fail(error: Throwable) {
        mutable.value =
            state.value.copy(
                error =
                error.cause?.message ?: error.message ?: "Proposal action failed."
            )
    }

    @Synchronized
    override fun close() {
        closed = true
    }
}

private fun success(value: JsonElement): Boolean = value.objectValue().let {
    (it["success"] as? JsonPrimitive)?.booleanOrNull == true || it["workspace"] is JsonObject
}

internal fun proposalRequest(session: RpcSession): AssistantRequest = { method, params ->
    session.request(
        method,
        params,
        timeoutMillis = if (method ==
            "workspace.create"
        ) {
            CREATE_TIMEOUT
        } else {
            DEFAULT_TIMEOUT
        }
    )
}

private const val CREATE_TIMEOUT = 120_000L
private const val DEFAULT_TIMEOUT = 30_000L
private const val INVALID_PARAMETERS = -32602
