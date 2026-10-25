package dev.intent.app.conversation

import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.QueuedMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** iOS attribution order: row metadata, text-block metadata, then legacy prefixes. */
internal object ConversationNoise {
    private val types = mapOf(
        "agent_message" to "Agent message",
        "event_notification" to "Workspace update",
        "hook_wake" to "Background update",
        "pr_monitor_wake" to "PR update",
        "script_monitor_wake" to "Script update",
        "agent_watch" to "Agent update"
    )
    private val prefixes = mapOf(
        "[MESSAGE FROM AGENT" to "Agent message",
        "[WORKSPACE EVENTS]" to "Workspace update",
        "[SYSTEM NOTE]" to "System detail",
        "[Role Reminder:" to "System detail",
        "<system>" to "System detail",
        "<system-reminder>" to "System detail",
        "<supervisor>" to "System detail"
    )

    fun label(message: ConversationMessage): String? = when (message.role) {
        "system" -> "System detail"
        "user" -> userLabel(message)
        else -> null
    }

    private fun userLabel(message: ConversationMessage): String? {
        val metadata = listOfNotNull(message.metadata) + message.contentBlocks
            .filter { it.type == "text" }.mapNotNull { it.messageMetadata }
        return metadata.firstNotNullOfOrNull(::fromMetadata) ?: fromText(
            message.contentBlocks.firstOrNull { it.type == "text" }?.text.orEmpty()
        )
    }

    fun label(message: QueuedMessage): String? =
        message.messageMetadata?.let(::fromMetadata) ?: fromText(message.content)

    private fun fromMetadata(metadata: JsonObject): String? {
        val type =
            (metadata["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return if (type == "agent_message" &&
            (metadata["fromAgentId"] as? JsonPrimitive)?.takeIf {
                it.isString
            }?.content.isNullOrBlank()
        ) {
            null
        } else {
            types[type]
        }
    }

    private fun fromText(text: String): String? {
        val trimmed = text.trimStart()
        return prefixes.entries.firstOrNull { trimmed.startsWith(it.key) }?.value
            ?: "Agent update".takeIf {
                Regex("[^\\n]+ sent a message(?: and finished)?[.!]?").matches(trimmed.trim())
            }
    }
}
