package dev.intent.app.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import dev.intent.app.attachments.MediaPreview
import dev.intent.app.attachments.MediaReference
import dev.intent.app.attachments.noteMediaReference
import dev.intent.app.browser.LocalWorkspaceBrowserLink
import dev.intent.core.protocol.browser.PreviewUrl
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownText

val LocalMarkdownNoteLink = staticCompositionLocalOf<((String) -> Unit)?> { null }

data class MarkdownRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
    val media: MediaReference? = null
)

@Suppress("CyclomaticComplexMethod") // CommonMark inline node variants.
fun markdownRuns(node: Node, style: MarkdownRun = MarkdownRun("")): List<MarkdownRun> =
    when (node) {
        is MarkdownText -> listOf(style.copy(text = node.literal))
        is Code -> listOf(style.copy(text = node.literal, code = true))
        is SoftLineBreak -> listOf(style.copy(text = " "))
        is HardLineBreak -> listOf(style.copy(text = "\n"))
        is HtmlInline -> listOf(style.copy(text = node.literal))
        is StrongEmphasis -> node.children().flatMap { markdownRuns(it, style.copy(bold = true)) }
        is Emphasis -> node.children().flatMap { markdownRuns(it, style.copy(italic = true)) }
        is Image -> {
            val label = node.children().flatMap { markdownRuns(it) }.joinToString("") { it.text }
            listOf(
                style.copy(
                    text = label.ifEmpty { "Image" },
                    media = noteMediaReference(node.destination, label, true),
                    url = node.destination
                )
            )
        }
        is Link -> {
            val label = node.children().flatMap { markdownRuns(it) }.joinToString("") { it.text }
            val media = noteMediaReference(node.destination, label, false)
            if (media != null) {
                listOf(style.copy(text = label, media = media))
            } else {
                node.children().flatMap { markdownRuns(it, style.copy(url = node.destination)) }
            }
        }
        else -> node.children().flatMap { markdownRuns(it, style) }
    }

@Composable
fun MarkdownInline(node: Node, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val runs = markdownRuns(node)
    var start = 0
    runs.forEachIndexed { index, run ->
        if (run.media != null) {
            if (start < index) MarkdownTextRuns(runs.subList(start, index), style)
            MediaPreview(run.media)
            start = index + 1
        }
    }
    if (start < runs.size) MarkdownTextRuns(runs.subList(start, runs.size), style)
}

@Composable
private fun MarkdownTextRuns(runs: List<MarkdownRun>, style: TextStyle) {
    val uriHandler = LocalUriHandler.current
    val onNote = LocalMarkdownNoteLink.current
    val onBrowser = LocalWorkspaceBrowserLink.current
    val linkStyle = TextLinkStyles(
        SpanStyle(
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = TextDecoration.Underline
        )
    )
    val text = buildAnnotatedString {
        runs.forEach { run ->
            withStyle(
                SpanStyle(
                    fontWeight = if (run.bold) FontWeight.Bold else null,
                    fontStyle = if (run.italic) FontStyle.Italic else null,
                    fontFamily = if (run.code) FontFamily.Monospace else null
                )
            ) {
                val url = run.url
                val note = url?.let(::markdownNoteId)
                val canOpenNote = note != null && onNote != null
                if (url != null && (isMarkdownWebLink(url) || canOpenNote)) {
                    withLink(
                        LinkAnnotation.Clickable(url, linkStyle) {
                            when {
                                note != null -> onNote?.invoke(note)
                                PreviewUrl.isHostLocal(url) -> onBrowser?.invoke(url)
                                else -> runCatching { uriHandler.openUri(url) }
                            }
                        }
                    ) { append(run.text) }
                } else {
                    append(run.text)
                }
            }
        }
    }
    Text(text, modifier = Modifier.fillMaxWidth(), style = style)
}
