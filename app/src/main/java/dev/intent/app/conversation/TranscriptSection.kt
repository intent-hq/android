package dev.intent.app.conversation

import dev.intent.core.protocol.models.ContentBlock

internal data class TranscriptSection(
    val id: String,
    val name: String? = null,
    val blocks: List<ContentBlock>
)

/** Group across text and tool blocks, keeping original block ids for hydration. */
internal fun transcriptSections(
    blocks: List<ContentBlock>,
    streaming: Boolean = false
): List<TranscriptSection> = GroupParser(streaming).parse(blocks)

private class GroupParser(private val streaming: Boolean) {
    private val sections = mutableListOf<TranscriptSection>()
    private var group: TranscriptSection? = null
    private val tokenPattern = (
        "(?m)^ {0,3}(`{3,}|~{3,})[^\\n]*\\n[\\s\\S]*?(?:^ {0,3}\\1[ \\t]*(?:\\n|$)|\\z)" +
            "|(`+)[^`]*?\\2|</group(?::[^<>\\n]+)?>|<group:(?<name>[^<>\\n]+)>"
        )

    private val tokens = Regex(
        tokenPattern + if (streaming) "|</?(?:g(?:r(?:o(?:u(?:p(?::[^<>\\n]*)?)?)?)?)?)?$" else ""
    )

    fun parse(blocks: List<ContentBlock>): List<TranscriptSection> {
        blocks.forEachIndexed { index, block ->
            if (block.type == "text") parseText(block, index) else append(block, "block:$index")
        }
        flush()
        return sections
    }

    private fun parseText(block: ContentBlock, index: Int) {
        val text = block.text.orEmpty()
        var cursor = 0
        var ordinal = 0
        tokens.findAll(text).forEach { token ->
            if (token.value.startsWith("<")) {
                appendText(block, text.substring(cursor, token.range.first), "$index:$ordinal:text")
                if (token.value.startsWith("<group:") && token.value.endsWith(">")) {
                    flush()
                    group =
                        TranscriptSection(
                            "$index:$ordinal:group",
                            token.groups["name"]?.value.orEmpty().trim(),
                            emptyList()
                        )
                } else if (token.value.endsWith(">")) {
                    flush()
                }
                ordinal++
                cursor = token.range.last + 1
            }
        }
        appendText(block, text.substring(cursor), "$index:$ordinal:tail")
    }

    private fun appendText(block: ContentBlock, text: String, id: String) {
        if (text.isNotBlank()) append(block.copy(text = text), id)
    }

    private fun append(block: ContentBlock, id: String) {
        val current = group
        if (current == null) {
            sections += TranscriptSection(id, blocks = listOf(block))
        } else {
            group = current.copy(blocks = current.blocks + block)
        }
    }

    private fun flush() {
        group?.let { sections += it }
        group = null
    }
}
