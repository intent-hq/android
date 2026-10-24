package dev.intent.app.notes

/** Deliberately small read-only Markdown subset. Tables and Mermaid are future work. */
data class MarkdownBlock(val text: String, val kind: String = "text", val level: Int = 0)

fun markdownBlocks(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var fence: String? = null
    val code = mutableListOf<String>()
    content.lines().forEach { line ->
        val trimmed = line.trimStart()
        val marker = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }
        when {
            fence != null && marker == fence -> {
                blocks.add(MarkdownBlock(code.joinToString("\n"), "code"))
                code.clear()
                fence = null
            }
            fence != null -> code.add(line)
            marker != null -> fence = marker
            else -> blocks.add(markdownLine(line))
        }
    }
    if (fence != null) blocks.add(MarkdownBlock(code.joinToString("\n"), "code"))
    return blocks
}

private fun markdownLine(line: String): MarkdownBlock {
    val heading = Regex("^(#{1,6}) +(.+)$").find(line)
    val bullet = Regex("^\\s*[-*+] +(.+)$").find(line)
    return when {
        heading != null -> MarkdownBlock(
            heading.groupValues[2],
            "heading",
            heading.groupValues[1].length
        )
        bullet != null -> MarkdownBlock("• ${bullet.groupValues[1]}", "list")
        Regex("^\\s*\\d+[.)] +").containsMatchIn(line) -> MarkdownBlock(line.trimStart(), "list")
        else -> MarkdownBlock(line)
    }
}
