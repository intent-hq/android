package dev.intent.app.conversation

import dev.intent.core.protocol.models.ContentBlock

internal data class PromptPresentation(val blocks: List<ContentBlock>, val prompts: List<String>)

/** Shared behavior: docs/protocol/suggested-prompts.md and its canonical v1 fixtures. */
internal object SuggestedPrompts {
    private val opener = Regex("(<|&lt;)!--[ \\t]*suggested-prompts[ \\t]*$")
    private val fenceLine = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
    private val bodyLine = Regex("^(#{1,6}(\\s|$)|`{3,}|~{3,}|\\||<!--)|\\S[ \\t]*--+>")
    private val delay = Regex("^delay:[0-9]+\\|(.*)$", RegexOption.IGNORE_CASE)
    private val entities =
        Regex("&(?:(lt|gt|quot|apos|amp)|#([0-9]{1,7})|#[xX]([0-9a-fA-F]{1,6}));")
    private val named = mapOf("lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "amp" to "&")
    private const val MAX_PROMPTS = 4
    private const val MAX_LENGTH = 200
    private const val HEX = 16
    private const val MAX_SCALAR = 0x10ffff
    private const val SURROGATE_START = 0xd800
    private const val SURROGATE_END = 0xdfff
    private const val HEX_GROUP = 3
    private val surrogates = SURROGATE_START..SURROGATE_END

    private data class Block(val start: Int, val end: Int, val prompts: List<String>)

    fun parse(blocks: List<ContentBlock>, streaming: Boolean = false): PromptPresentation {
        val text = blocks.filter { it.type == "text" }.joinToString("") { it.text.orEmpty() }
        val found = scan(text, streaming)
        var offset = 0
        val cleaned = blocks.map { block ->
            if (block.type != "text") return@map block
            val content = block.text.orEmpty()
            val start = offset
            offset += content.length
            var cursor = 0
            val kept = buildString {
                found.forEach { range ->
                    val left = (range.start - start).coerceIn(0, content.length)
                    val right = (range.end - start).coerceIn(0, content.length)
                    if (right > cursor) {
                        append(content.substring(cursor, left.coerceAtLeast(cursor)))
                        cursor = right
                    }
                }
                append(content.substring(cursor))
            }
            block.copy(text = kept)
        }
        return PromptPresentation(
            cleaned,
            if (streaming) emptyList() else found.lastOrNull()?.prompts.orEmpty()
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod") // Fence/block state machine.
    private fun scan(text: String, streaming: Boolean): List<Block> {
        val lines = text.split('\n')
        val offsets = lines.runningFold(0) { offset, line -> offset + line.length + 1 }
        val found = mutableListOf<Block>()
        var fence: String? = null
        var opening = -1
        var start = 0
        var escaped = false
        lines.forEachIndexed { index, raw ->
            val line = raw.trimEnd()
            val marker = fenceLine.matchEntire(line)
            if (marker != null) {
                val token = marker.groupValues[1]
                val active = fence
                if (active == null) {
                    fence = token
                } else if (token.first() == active.first() &&
                    token.length >= active.length &&
                    marker.groupValues[2].isBlank()
                ) {
                    fence = null
                }
                return@forEachIndexed
            }
            if (fence != null) return@forEachIndexed
            val open = opener.find(line)
            if (opening >= 0) {
                val closer = if (escaped) "--&gt;" else "-->"
                val remainder = line.removeSuffix(closer).trimEnd()
                val closes = line.endsWith(closer) && opener.find(remainder) == null
                val ambiguous = streaming && index == lines.lastIndex && remainder.isNotBlank()
                if (closes && !ambiguous) {
                    val body = lines.subList(opening + 1, index) + remainder
                    found +=
                        Block(start, minOf(offsets[index + 1], text.length), prompts(body, escaped))
                    opening = -1
                    return@forEachIndexed
                }
            }
            if (open != null) {
                val duplicate =
                    opening >= 0 && lines.subList(opening + 1, index).all { it.isBlank() }
                if (!duplicate) start = offsets[index] + open.range.first
                opening = index
                escaped = open.groupValues[1] == "&lt;"
            }
        }
        if (streaming && opening >= 0) found += Block(start, text.length, emptyList())
        return found
    }

    private fun prompts(body: List<String>, escaped: Boolean): List<String> {
        val lines = body.map { trim(if (escaped) decode(it) else it) }.filter { it.isNotEmpty() }
        if (lines.any { bodyLine.containsMatchIn(it) }) return emptyList()
        return lines.mapNotNull { line ->
            val fullDelay = delay.matchEntire(line)
            val withoutLabel = if (fullDelay != null) {
                fullDelay.groupValues[1]
            } else {
                PromptLinks.afterLabel(line)
            }
            val value = trim(
                if (fullDelay !=
                    null
                ) {
                    withoutLabel
                } else {
                    delay.matchEntire(withoutLabel)?.groupValues?.get(1)
                        ?: withoutLabel
                }
            )
            value.takeIf { it.isNotEmpty() && PromptLinks.visible(it).length <= MAX_LENGTH }
        }.take(MAX_PROMPTS)
    }

    private fun trim(text: String) = text.trim { it.isWhitespace() || it == '\uFEFF' }

    private fun decode(text: String) = entities.replace(text) { match ->
        named[match.groupValues[1]] ?: run {
            val decimal = match.groupValues[2]
            val value = if (decimal.isNotEmpty()) {
                decimal.toInt()
            } else {
                match.groupValues[HEX_GROUP].toInt(
                    HEX
                )
            }
            if (value in 1..MAX_SCALAR && value !in surrogates) {
                String(Character.toChars(value))
            } else {
                match.value
            }
        }
    }
}
