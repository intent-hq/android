package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class QueuedMessage(
    val id: String,
    val content: String,
    val queuedAt: String,
    val position: Int,
    val messageMetadata: JsonObject? = null
)

@Serializable
data class QueueResult(val success: Boolean, val queue: List<QueuedMessage>)

@Serializable
data class SendResult(
    val success: Boolean,
    val queued: Boolean,
    val messageId: String? = null,
    val queuedMessage: QueuedMessage? = null
)

@Serializable
data class CommandResult(val success: Boolean)

@Serializable
data class QuestionOption(val label: String, val description: String? = null)

@Serializable
data class Question(
    val attachmentId: String,
    val header: String,
    val question: String,
    val options: List<QuestionOption>,
    val explanation: String? = null,
    val multiSelect: Boolean = false
)
