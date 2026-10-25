@file:Suppress("MatchingDeclarationName")

package dev.intent.app.notifications

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

data class LocalAlert(
    val channel: String,
    val workspaceId: String,
    val subjectId: String,
    val title: String
)

internal fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.flag(key: String): Boolean =
    (get(key) as? JsonPrimitive)?.booleanOrNull == true

@Suppress("ReturnCount")
fun notificationTrigger(payload: JsonObject): LocalAlert? {
    val event = payload["event"] as? JsonObject ?: payload
    val data = event["data"] as? JsonObject ?: return null
    val workspace = event.text("workspaceId") ?: data.text("workspaceId") ?: return null
    if (workspace.isBlank() || data.suppressed()) return null
    return data.trigger(event.text("type"), workspace)
}

private fun JsonObject.suppressed(): Boolean =
    flag("isBackground") || flag("notificationsMuted") || !text("parentAgentId").isNullOrBlank()

private fun JsonObject.trigger(type: String?, workspace: String): LocalAlert? {
    val agent = text("agentId")
    return when (type) {
        "agent:attention-requested" -> agent?.let {
            LocalAlert("attention", workspace, it, "Agent needs attention")
        }
        "agent:updated" -> agent?.takeIf {
            !text("pendingQuestionsMessageId").isNullOrBlank()
        }?.let {
            LocalAlert("attention", workspace, it, "Agent has a question")
        }
        "agent:idle" -> agent?.takeUnless { waiting() }?.let {
            LocalAlert("completion", workspace, it, "Agent finished")
        }
        "task:status-changed" -> text("noteId")?.takeIf {
            text("newStatus") == "complete" && text("previousStatus") != "complete"
        }?.let { LocalAlert("completion", workspace, it, "Task completed") }
        else -> null
    }
}

private fun JsonObject.waiting(): Boolean = flag("isWaitingForOtherAgents") ||
    (get("waitingForAgentIds") as? JsonArray)?.isNotEmpty() == true
