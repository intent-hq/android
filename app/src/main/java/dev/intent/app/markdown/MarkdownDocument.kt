package dev.intent.app.markdown

import java.net.URI
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser

private val parser = Parser.builder().extensions(
    listOf(TablesExtension.create(), TaskListItemsExtension.create())
).build()

/** Each render owns its tree, including partial fences received during streaming. */
fun parseMarkdown(source: String): Node = parser.parse(source)

fun Node.children(): List<Node> = generateSequence(firstChild) { it.next }.toList()

/** Only local note/task routes are consumed internally; file links use AttachmentMedia. */
fun markdownNoteId(url: String): String? = runCatching {
    val uri = URI(url)
    if (uri.scheme != "intent" || uri.host != "local") return null
    if (uri.rawQuery != null || uri.rawFragment != null) return null
    val parts = uri.path.split('/')
    parts.takeIf { it.size == NOTE_PATH_SEGMENTS && it[1] in setOf("note", "task") }
        ?.get(2)?.takeIf { it.isNotBlank() && it != "." && it != ".." }
}.getOrNull()

fun isMarkdownWebLink(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

private const val NOTE_PATH_SEGMENTS = 3
