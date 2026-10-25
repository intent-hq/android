package dev.intent.app.conversation

/** Link labels determine chip length; pipes inside links never delimit a legacy label. */
internal object PromptLinks {
    private val link =
        Regex(
            "(?<!!)\\[([^\\[\\]\\n]+)]\\(((?i:https?|intent)://[^\\s<>]*(?:\\([^\\s<>]*\\)[^\\s<>]*)?)\\)"
        )

    fun visible(text: String): String = link.replace(text) { it.groupValues[1] }

    fun afterLabel(text: String): String {
        val links = link.findAll(text).map { it.range }.toList()
        val pipe = text.indices.firstOrNull {
            text[it] == '|' && links.none { range -> it in range }
        }
        return if (pipe == null) text else text.substring(pipe + 1).trim()
    }
}
