package dev.intent.app.markdown

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.intent.app.mermaid.MermaidFence
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.ThematicBreak

/** Return true after rendering a recognized fence, false to use the plain code fallback. */
typealias MarkdownFenceRenderer = @Composable (language: String, code: String) -> Boolean
val LocalMarkdownFenceRenderer =
    staticCompositionLocalOf<MarkdownFenceRenderer> {
        { language, code -> MermaidFence(language, code) }
    }

@Composable
fun MarkdownContent(content: String, modifier: Modifier = Modifier) {
    val document = remember(content) { parseMarkdown(content) }
    SelectionContainer(modifier) { MarkdownNodes(document.children()) }
}

@Composable
private fun MarkdownNodes(nodes: List<Node>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nodes.forEach { MarkdownNode(it) }
    }
}

@Suppress("CyclomaticComplexMethod") // CommonMark block node dispatch.
@Composable
fun MarkdownNode(node: Node) {
    when (node) {
        is Heading -> MarkdownInline(
            node,
            when (node.level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.headlineSmall
                else -> MaterialTheme.typography.titleLarge
            }
        )
        is Paragraph -> MarkdownInline(node)
        is FencedCodeBlock -> {
            val language = node.info.trim().substringBefore(' ').lowercase()
            if (!LocalMarkdownFenceRenderer.current(language, node.literal)) {
                MarkdownCode(node.literal, language)
            }
        }
        is IndentedCodeBlock -> MarkdownCode(node.literal, "")
        is BlockQuote -> Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)) {
                MarkdownNodes(node.children())
            }
        }
        is BulletList -> MarkdownList(node, null)
        is OrderedList -> MarkdownList(node, node.markerStartNumber ?: 1)
        is TableBlock -> MarkdownTable(node)
        is ThematicBreak -> HorizontalDivider()
        is HtmlBlock -> Text(node.literal)
        else -> MarkdownNodes(node.children())
    }
}

@Composable
private fun MarkdownList(node: Node, start: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        node.children().filterIsInstance<ListItem>().forEachIndexed { index, item ->
            val marker = item.children().filterIsInstance<TaskListItemMarker>().firstOrNull()
            Row {
                if (marker != null) {
                    Checkbox(marker.isChecked, onCheckedChange = null)
                } else {
                    Text(start?.let { "${it + index}." } ?: "•", Modifier.padding(end = 8.dp))
                }
                Column(Modifier.weight(1f)) {
                    MarkdownNodes(item.children().filterNot { it is TaskListItemMarker })
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(node: TableBlock) {
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        node.children().flatMap { it.children() }.forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                row.children().filterIsInstance<TableCell>().forEach { cell ->
                    Surface(
                        color = if (cell.isHeader) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier.width(
                            180.dp
                        ).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            MarkdownInline(
                                cell,
                                MaterialTheme.typography.bodyMedium.copy(
                                    textAlign = when (cell.alignment) {
                                        TableCell.Alignment.CENTER -> TextAlign.Center
                                        TableCell.Alignment.RIGHT -> TextAlign.End
                                        else -> TextAlign.Start
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownCode(code: String, language: String) {
    val tokens = remember(code, language) { codeTokens(code, language) }
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            if (language.isNotEmpty()) Text(language, style = MaterialTheme.typography.labelSmall)
            Text(
                buildAnnotatedString {
                    tokens.forEach { token ->
                        withStyle(
                            SpanStyle(
                                color = when (token.kind) {
                                    "keyword" -> colors.primary
                                    "string" -> colors.tertiary
                                    "number" -> colors.secondary
                                    "comment" -> colors.onSurfaceVariant
                                    else -> colors.onSurface
                                }
                            )
                        ) { append(token.text) }
                    }
                },
                Modifier.horizontalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
