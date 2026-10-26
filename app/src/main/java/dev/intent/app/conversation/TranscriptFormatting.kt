package dev.intent.app.conversation

import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal data class TranscriptRow(
    val messages: List<ConversationMessage>,
    val id: String = messages.first().id
)

/** Retain a loaded row's identity when older or live activity extends its group. */
internal class TranscriptRowGrouping {
    private var previous = emptyList<TranscriptRow>()

    fun rows(messages: List<ConversationMessage>): List<TranscriptRow> {
        val keys = previous.map { it.id }.toSet()
        previous = transcriptRows(messages).map { row ->
            row.copy(id = row.messages.firstOrNull { it.id in keys }?.id ?: row.id)
        }
        return previous
    }
}

/** Group adjacent machine notices without merging across a human or assistant reply. */
internal fun transcriptRows(messages: List<ConversationMessage>): List<TranscriptRow> {
    val rows = mutableListOf<TranscriptRow>()
    messages.forEach { message ->
        val label = activityLabel(message)
        val previous = rows.lastOrNull()
        if (label != null &&
            previous != null &&
            activityLabel(previous.messages.first()) == label
        ) {
            rows[rows.lastIndex] = TranscriptRow(previous.messages + message)
        } else {
            rows += TranscriptRow(listOf(message))
        }
    }
    return rows
}

internal fun ContentBlock.isToolActivity(): Boolean =
    type in setOf("thinking", "tool_use", "tool_result") && !isQuestion()

internal fun ConversationMessage.isToolActivity(): Boolean =
    role in setOf("assistant", "tool", "user") &&
        error == null &&
        metadata?.get("interruptReason") == null &&
        metadata?.get("finishReason") == null &&
        contentBlocks.isNotEmpty() &&
        contentBlocks.all { it.isToolActivity() }

private fun activityLabel(message: ConversationMessage): String? =
    if (message.isToolActivity()) "Tool activity" else ConversationNoise.label(message)

internal data class TranscriptTextPart(val text: String, val toolMarkup: Boolean = false)

/** Keep surrounding prose visible, but disclose raw tool protocol text on demand. */
internal fun transcriptTextParts(text: String): List<TranscriptTextPart> {
    val opener =
        Regex("<(?:(?:[\\w.-]+):)?(invoke|function_calls|tool_call|tool_result|parameter)\\b[^>]*>")
    val parts = mutableListOf<TranscriptTextPart>()
    var cursor = 0
    while (cursor < text.length) {
        val match = opener.find(text, cursor) ?: break
        if (match.range.first > cursor) {
            parts += TranscriptTextPart(text.substring(cursor, match.range.first))
        }
        val tag = match.value.substringAfter('<').takeWhile {
            !it.isWhitespace() &&
                it != '>' &&
                it != '/'
        }
        val close = "</$tag>"
        val end = if (match.value.endsWith("/>")) {
            match.range.last + 1
        } else {
            text.indexOf(close, match.range.last + 1).takeIf { it >= 0 }
                ?.plus(close.length) ?: text.length
        }
        parts += TranscriptTextPart(text.substring(match.range.first, end), true)
        cursor = end
    }
    if (cursor < text.length) parts += TranscriptTextPart(text.substring(cursor))
    return parts
}

internal fun toolBody(block: ContentBlock): String {
    val body = when (block.type) {
        "tool_use" -> block.input?.displayText().orEmpty()
        else -> block.output?.displayText() ?: block.text.orEmpty()
    }
    val title = block.name ?: return body
    val first = body.lineSequence().firstOrNull().orEmpty().trim()
    return if (first == title || first == "**$title**" || first == "### $title") {
        body.substringAfter('\n', "").trimStart()
    } else {
        body
    }
}

private fun JsonElement.displayText(): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: toString()
