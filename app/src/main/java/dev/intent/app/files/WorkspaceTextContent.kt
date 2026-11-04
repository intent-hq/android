package dev.intent.app.files

import dev.intent.app.markdown.CodeToken
import dev.intent.app.markdown.codeTokens

internal data class WorkspaceTextContent(val text: String, val truncated: Boolean) {
    val lineStarts: List<Int> = buildList {
        add(0)
        text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
    }
}

/** Bound native layout as well as downloads; never cut a UTF-16 surrogate pair. */
internal fun boundedWorkspaceText(text: String): WorkspaceTextContent {
    var end = minOf(text.length, TEXT_LAYOUT_CHAR_LIMIT)
    var lines = 1
    for (index in 0 until end) {
        if (text[index] == '\n' && ++lines > TEXT_LAYOUT_LINE_LIMIT) {
            end = index
            break
        }
    }
    if (end < text.length && end > 0 && text[end - 1].isHighSurrogate()) end--
    return WorkspaceTextContent(text.substring(0, end), end < text.length)
}

internal fun workspaceCodeTokens(text: String, path: String): List<CodeToken> =
    if (text.length <= TEXT_HIGHLIGHT_CHAR_LIMIT) {
        codeTokens(text, path.substringAfterLast('.', ""))
    } else {
        listOf(CodeToken(text))
    }

internal const val TEXT_LAYOUT_CHAR_LIMIT = 200_000
internal const val TEXT_LAYOUT_LINE_LIMIT = 4_000
internal const val TEXT_HIGHLIGHT_CHAR_LIMIT = 20_000
