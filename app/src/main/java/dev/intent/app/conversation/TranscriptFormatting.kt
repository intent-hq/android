package dev.intent.app.conversation

import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage

internal data class TranscriptRow(val messages: List<ConversationMessage>) {
    val id: String get() = messages.first().id
}

/** Group adjacent machine notices without merging across a human or assistant reply. */
internal fun transcriptRows(messages: List<ConversationMessage>): List<TranscriptRow> {
    val rows = mutableListOf<TranscriptRow>()
    messages.forEach { message ->
        val label = ConversationNoise.label(message)
        val previous = rows.lastOrNull()
        if (label != null &&
            previous != null &&
            ConversationNoise.label(previous.messages.first()) == label
        ) {
            rows[rows.lastIndex] = TranscriptRow(previous.messages + message)
        } else {
            rows += TranscriptRow(listOf(message))
        }
    }
    return rows
}

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
        "tool_use" -> block.input?.toString().orEmpty()
        else -> block.output?.toString() ?: block.text.orEmpty()
    }
    val title = block.name ?: return body
    val first = body.lineSequence().firstOrNull().orEmpty().trim()
    return if (first == title || first == "**$title**" || first == "### $title") {
        body.substringAfter('\n', "").trimStart()
    } else {
        body
    }
}
