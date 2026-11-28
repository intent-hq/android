package dev.intentapp.android.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.intentapp.android.R
import dev.intentapp.android.mermaid.MermaidFence
import kotlinx.coroutines.delay
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

/** Notes can replace a list item in place; ordinary Markdown remains read-only. */
val LocalMarkdownListItemRenderer =
    staticCompositionLocalOf<(@Composable (ListItem) -> Boolean)?> { null }

@Composable
fun MarkdownContent(content: String, modifier: Modifier = Modifier) {
    val document = remember(content) { parseMarkdown(content) }
    SelectionContainer(modifier) { MarkdownNodes(document.children()) }
}

@Composable
private fun MarkdownNodes(nodes: List<Node>) {
    // iOS CompactParagraphStyle leaves one line (the 16sp body size) under each block.
    Column(verticalArrangement = Arrangement.spacedBy(BLOCK_SPACING)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(LIST_ITEM_SPACING)) {
        node.children().filterIsInstance<ListItem>().forEachIndexed { index, item ->
            if (LocalMarkdownListItemRenderer.current?.invoke(item) == true) {
                return@forEachIndexed
            }
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
    val scroll = rememberScrollState()
    Column {
        if (scroll.maxValue > 0) {
            Text(
                "↔ Scroll horizontally for more columns",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Column(Modifier.horizontalScroll(scroll)) {
            node.children().flatMap { it.children() }.forEachIndexed { rowIndex, row ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    row.children().filterIsInstance<TableCell>().forEachIndexed {
                            columnIndex,
                            cell
                        ->
                        Surface(
                            color = if (cell.isHeader) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier.width(180.dp)
                                .fillMaxHeight()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                .testTag("markdown-cell-$rowIndex-$columnIndex")
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
}

/** iOS CodeBlockView: a rounded card with the language, a Copy action, and scrolling code. */
@Composable
private fun MarkdownCode(code: String, language: String) {
    val tokens = remember(code, language) { codeTokens(code, language) }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceVariant)
    ) {
        DisableSelection { CodeHeader(code, language) }
        Text(
            buildAnnotatedString {
                tokens.forEach { token ->
                    withStyle(
                        SpanStyle(
                            color = when (token.kind) {
                                "keyword" -> colors.onSurface
                                "string" -> colors.onTertiaryContainer
                                "number" -> colors.secondary
                                "comment" -> colors.onSurfaceVariant
                                else -> colors.onSurface
                            }
                        )
                    ) { append(token.text) }
                }
            },
            Modifier.horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            softWrap = false
        )
    }
}

@Composable
private fun CodeHeader(code: String, language: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPY_CONFIRMATION_MILLIS)
            copied = false
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            language,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.heightIn(min = 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                }
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(if (copied) R.drawable.ic_check else R.drawable.ic_copy),
                null,
                Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (copied) "Copied" else "Copy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val COPY_CONFIRMATION_MILLIS = 2_000L

private val BLOCK_SPACING = 16.dp

// iOS Markdown task list spacing: 2 above and 4 below each item.
private val LIST_ITEM_SPACING = 6.dp
