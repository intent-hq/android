package dev.intent.app.status

import dev.intent.app.agents.avatarState
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.WireJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class WatchedAgent(
    val agent: Agent,
    val completed: Boolean = false,
    val deleted: Boolean = false
) {
    val label: String get() = when {
        deleted -> "Deleted"
        completed -> "Completed"
        agent.status == "unknown" -> "Status unavailable"
        agent.status == "responding" -> "Running"
        else -> agent.avatarState().label
    }
}

data class AgentWatches(
    val rows: List<WatchedAgent> = emptyList(),
    val completed: Int = 0,
    val total: Int? = null,
    val error: String? = null
) {
    val header: String get() = total?.let { "Waiting for all ($completed/$it)" }
        ?: "Waiting for ${rows.size} agent${if (rows.size == 1) "" else "s"}"
}

/** Both modern flattened subscriptions and the older nested filter are supported by iOS. */
internal fun parseAgentWatches(body: JsonObject): AgentWatches {
    val subscriptions = body.objects("subscriptions")
    val groups = body.objects("delegationGroups")
    val completed = groups.flatMap { it.ids("completedAgentIds") }.toSet()
    val deleted = groups.flatMap { it.ids("deletedAgentIds") }.toSet()
    val ids = (
        groups.flatMap {
            it.ids("expectedAgentIds") + it.ids("completedAgentIds") + it.ids("deletedAgentIds")
        } + subscriptions.flatMap {
            if (it["actorIds"] is JsonArray) {
                it.ids(
                    "actorIds"
                )
            } else {
                it.objectValue("filter")?.ids("actorIds").orEmpty()
            }
        }
        ).distinct().sorted()
    val agents = body.objects("agents").associateBy { it.text("id") }
    val statuses = body.objectValue("agentStatuses")
    return AgentWatches(
        rows = ids.map { id ->
            val raw = agents[id].orEmpty() + mapOf(
                "id" to JsonPrimitive(id),
                "name" to JsonPrimitive(agents[id]?.text("name") ?: id),
                "status" to
                    JsonPrimitive(statuses?.text(id) ?: agents[id]?.text("status") ?: "unknown")
            )
            WatchedAgent(
                WireJson.decodeFromJsonElement(Agent.serializer(), JsonObject(raw)),
                id in completed,
                id in deleted || agents[id]?.text("status") == "deleted"
            )
        },
        completed = groups.sumOf {
            (it.ids("completedAgentIds") + it.ids("deletedAgentIds")).toSet().size
        },
        total = groups.takeIf { it.isNotEmpty() }?.sumOf { it.ids("expectedAgentIds").toSet().size }
    )
}

private fun JsonObject.objects(key: String): List<JsonObject> =
    (get(key) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
private fun JsonObject.ids(key: String): List<String> =
    (get(key) as? JsonArray).orEmpty().mapNotNull {
        (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
    }
