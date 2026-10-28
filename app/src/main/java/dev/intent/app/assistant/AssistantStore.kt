package dev.intent.app.assistant

import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.AgentListResult
import dev.intent.core.protocol.models.AgentResult
import dev.intent.core.protocol.models.WireJson
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

const val ASSISTANT_WORKSPACE = "__chief__"

typealias AssistantRequest = (String, JsonObject) -> CompletableFuture<JsonElement>

internal fun JsonElement?.objectValue(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.text(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

/** Authority is read from the current socket, never from a saved connection. */
fun assistantOwner(principal: JsonElement, capabilities: JsonObject): Boolean =
    principalRole(principal, capabilities) == "owner"

@Suppress("CyclomaticComplexMethod") // Fail closed for each malformed authority field.
internal fun principalRole(principal: JsonElement, capabilities: JsonObject): String? {
    val row = principal.objectValue()
    val administrator = (row["isAdministrator"] as? JsonPrimitive)?.takeUnless {
        it.isString
    }?.booleanOrNull
    val membership =
        (capabilities["hostMembership"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ==
            1
    val role = row.text("hostRole") ?: if (!membership) {
        if (administrator == true) "owner" else "guest"
    } else {
        null
    }
    val revision = (row["hostMembershipRevision"] as? JsonPrimitive)?.takeUnless {
        it.isString
    }?.doubleOrNull
    val validRevision = !membership ||
        (
            revision != null &&
                revision.isFinite() &&
                revision >= 0 &&
                revision <= MAX_MEMBERSHIP_REVISION &&
                revision % 1.0 == 0.0
            )
    val validRole =
        role in setOf("owner", "member", "guest") &&
            (role == "owner") == administrator &&
            (role != "member" || membership)
    return role.takeIf {
        !row.text("id").isNullOrBlank() &&
            administrator != null &&
            validRole &&
            validRevision
    }
}

data class AssistantState(
    val threads: List<Agent> = emptyList(),
    val selectedId: String? = null,
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val owner: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)

class AssistantStore(
    private val request: AssistantRequest,
    private val capabilities: () -> JsonObject = { JsonObject(emptyMap()) }
) : Closeable {
    private val mutable = MutableStateFlow(AssistantState())
    val state = mutable.asStateFlow()
    private var closed = false
    private var revision = 0L
    private var creationKey: String? = null
    private var principalId: String? = null

    @Synchronized
    fun refresh() {
        if (closed || state.value.busy) return
        val read = ++revision
        mutable.value = state.value.copy(loading = true, error = null)
        authorized().thenCompose {
            request("agent.list", scope())
        }.whenComplete { result, failure ->
            synchronized(this) {
                if (!closed && read == revision) {
                    runCatching {
                        if (failure != null) throw failure
                        val rows = WireJson.decodeFromJsonElement(
                            AgentListResult.serializer(),
                            result
                        ).agents
                        val threads = rows.filter {
                            (it.workspaceId == null || it.workspaceId == ASSISTANT_WORKSPACE) &&
                                it.createdByAgentId == null &&
                                it.status != "deleted"
                        }.sortedByDescending {
                            it.lastActivity ?: it.updatedAt
                                ?: it.createdAt.orEmpty()
                        }
                        mutable.value = state.value.copy(
                            threads = threads,
                            selectedId =
                            state.value.selectedId?.takeIf { id -> threads.any { it.id == id } }
                                ?: threads.firstOrNull()?.id,
                            loading = false,
                            loaded = true,
                            owner = true,
                            error = null
                        )
                    }.onFailure { fail(it) }
                }
            }
        }
    }

    @Synchronized
    fun select(id: String) {
        if (!closed &&
            state.value.threads.any { it.id == id }
        ) {
            mutable.value =
                state.value.copy(selectedId = id)
        }
    }

    @Synchronized
    fun create() {
        if (closed || state.value.busy) return
        if (!state.value.loaded || !state.value.owner) return
        val key = creationKey ?: UUID.randomUUID().toString().also { creationKey = it }
        mutate(
            "agent.create",
            scope {
                put("specialistId", "chief-of-staff")
                put("name", "Assistant")
                put("nameExplicitlySet", false)
                put("idempotencyKey", key)
            }
        ) { result ->
            val agent = WireJson.decodeFromJsonElement(AgentResult.serializer(), result).agent
            require(
                agent.id.isNotBlank() &&
                    (agent.workspaceId == null || agent.workspaceId == ASSISTANT_WORKSPACE)
            )
            creationKey = null
            mutable.value = state.value.copy(
                threads = listOf(agent) + state.value.threads.filterNot { it.id == agent.id },
                selectedId = agent.id
            )
        }
    }

    @Synchronized
    fun delete(id: String) {
        if (closed || state.value.busy || state.value.threads.none { it.id == id }) return
        mutate("agent.delete", scope { put("agentId", id) }) { result ->
            require((result.objectValue()["success"] as? JsonPrimitive)?.booleanOrNull == true)
            val rows = state.value.threads.filterNot { it.id == id }
            mutable.value = state.value.copy(
                threads = rows,
                selectedId =
                state.value.selectedId?.takeUnless { it == id } ?: rows.firstOrNull()?.id
            )
        }
    }

    private fun mutate(method: String, params: JsonObject, apply: (JsonElement) -> Unit) {
        ++revision
        mutable.value = state.value.copy(busy = true, loading = false, error = null)
        authorized().thenCompose { request(method, params) }.whenComplete { result, failure ->
            synchronized(this) {
                if (!closed) {
                    ++revision
                    runCatching {
                        if (failure != null) throw failure
                        apply(result)
                        mutable.value = state.value.copy(busy = false)
                    }.onFailure { fail(it) }
                }
            }
        }
    }

    private fun authorized(): CompletableFuture<Unit> =
        request("principal.me", JsonObject(emptyMap())).thenApply {
            synchronized(this) {
                check(!closed) { "Assistant closed." }
                val id = it.objectValue().text("id")
                if (!assistantOwner(it, capabilities()) ||
                    (principalId != null && principalId != id)
                ) {
                    mutable.value =
                        AssistantState(
                            loading = false,
                            error = "Assistant is available only to the host owner."
                        )
                    error("Assistant is available only to the host owner.")
                }
                principalId = id
            }
        }

    private fun fail(error: Throwable) {
        mutable.value = state.value.copy(
            loading = false,
            busy = false,
            error = error.cause?.message ?: error.message ?: "Cannot load Assistant."
        )
    }

    @Synchronized
    override fun close() {
        closed = true
        ++revision
    }
}

internal fun scope(extra: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
    put("workspaceId", ASSISTANT_WORKSPACE)
    extra()
}

private const val MAX_MEMBERSHIP_REVISION = 9_007_199_254_740_991.0
