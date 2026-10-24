package dev.intent.app.markdown

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BlockQuote
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.OrderedList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun `headings quotes nested lists and ordered starts retain structure`() {
        val tree = parseMarkdown("# Title\n\n> **Quote**\n>\n> - Nested\n\n3. Third\n4. Fourth")
        assertEquals(1, (tree.firstChild as Heading).level)
        assertTrue(tree.children()[1] is BlockQuote)
        assertEquals(3, (tree.children()[2] as OrderedList).markerStartNumber)
        assertTrue(markdownRuns(tree.children()[1]).first().bold)
    }

    @Test
    fun `tables preserve alignment escaped pipes and rich cells`() {
        val table = parseMarkdown("| Name | Value |\n| :--- | ---: |\n| **A** | a\\|b |").firstChild
        assertTrue(table is TableBlock)
        val rows = table.children().flatMap { it.children() }
        val header = rows.first().children().map { it as TableCell }
        assertTrue(header.all { it.isHeader })
        assertEquals(TableCell.Alignment.RIGHT, header[1].alignment)
        val cells = rows.last().children()
        assertTrue(markdownRuns(cells[0]).single().bold)
        assertEquals("a|b", markdownRuns(cells[1]).single().text)
    }

    @Test
    fun `task markers are read only and nested tasks retain checked state`() {
        val list = parseMarkdown("- [x] Done\n- [ ] Pending\n  - [X] Nested").firstChild
        val items = list.children()
        assertTrue((items[0].firstChild as TaskListItemMarker).isChecked)
        assertFalse((items[1].firstChild as TaskListItemMarker).isChecked)
        val nested = items[1].lastChild.firstChild.firstChild as TaskListItemMarker
        assertTrue(nested.isChecked)
        assertEquals("Done", markdownRuns(items[0]).single().text)
    }

    @Test
    fun `fences preserve language literal syntax and unfinished streaming content`() {
        val code = parseMarkdown(
            "````kotlin\nval x = \"**raw**\"\n```\n````"
        ).firstChild as FencedCodeBlock
        assertEquals("kotlin", code.info)
        assertEquals("val x = \"**raw**\"\n```\n", code.literal)
        val partial = parseMarkdown("~~~mermaid\ngraph TD\nA --> B").firstChild as FencedCodeBlock
        assertEquals("mermaid", partial.info)
        assertEquals("graph TD\nA --> B\n", partial.literal)
    }

    @Test
    fun `syntax highlighting retains all bytes and separates comments and strings`() {
        val source = "val answer = 42 // true\nval text = \"false\"\n"
        val tokens = codeTokens(source, "kotlin")
        assertEquals(source, tokens.joinToString("") { it.text })
        assertEquals("keyword", tokens.first().kind)
        assertEquals("number", tokens.first { it.text == "42" }.kind)
        assertEquals("comment", tokens.first { it.text == "// true" }.kind)
        assertEquals("string", tokens.first { it.text == "\"false\"" }.kind)
        assertEquals(listOf(CodeToken(source)), codeTokens(source, "unknown"))
    }

    @Test
    fun `inline emphasis code escapes reference links and balanced URLs survive parsing`() {
        val runs = markdownRuns(
            parseMarkdown(
                "**bold *both*** `**literal**` \\*plain\\* [site][ref]\n\n[ref]: https://example.com/a_(b)"
            )
        )
        assertTrue(runs.any { it.bold && it.italic && it.text == "both" })
        assertTrue(runs.any { it.code && it.text == "**literal**" })
        assertTrue(runs.any { it.text.contains("*plain*") })
        assertEquals("https://example.com/a_(b)", runs.last().url)
    }

    @Test
    fun `images and file links use the scoped attachment loader references`() {
        val runs = markdownRuns(
            parseMarkdown(
                "Before ![Photo](intent://local/file/a%20b.png) after [PDF](intent://local/file/report.pdf)"
            )
        )
        val media = runs.mapNotNull { it.media }
        assertEquals(listOf("a b.png", "report.pdf"), media.map { it.path })
        assertEquals(listOf(true, false), media.map { it.image })
        assertEquals("Before ", runs.first().text)
        assertNull(
            markdownRuns(parseMarkdown("![bad](intent://local/file/../secret)")).single().media
        )
    }

    @Test
    fun `links route only local notes tasks and web URLs`() {
        assertEquals("spec", markdownNoteId("intent://local/note/spec"))
        assertEquals("task-id", markdownNoteId("intent://local/task/task-id"))
        listOf(
            "intent://other/note/spec",
            "intent://local/note/a%2fb",
            "intent://local/note/..",
            "intent://local/note/spec?x=1",
            "intent://local/file/a"
        ).forEach {
            assertNull(markdownNoteId(it))
        }
        assertTrue(isMarkdownWebLink("https://example.com"))
        assertFalse(isMarkdownWebLink("javascript:alert(1)"))
        assertFalse(isMarkdownWebLink("file:///private"))
    }
}
