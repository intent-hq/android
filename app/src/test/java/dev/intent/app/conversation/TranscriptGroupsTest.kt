package dev.intent.app.conversation

import dev.intent.core.protocol.models.ContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptGroupsTest {
    @Test
    fun groupsKeepSurroundingProseAndBothClosingForms() {
        val sections =
            parse("Before<group:Setup>First</group:Setup><group:Work>Second</group>After")
        assertEquals(listOf(null, "Setup", "Work", null), sections.map { it.name })
        assertEquals(
            listOf("Before", "First", "Second", "After"),
            sections.map {
                it.blocks.single().text
            }
        )
    }

    @Test
    fun groupsSpanBlocksAndKeepToolsAndHydrationIds() {
        val tool = ContentBlock("tool_use", id = "tool", inputTruncated = true)
        val sections = transcriptSections(
            listOf(text("<group:Work>Start", "first"), tool, text("End</group>Done", "last"))
        )
        assertEquals("Work", sections.first().name)
        assertEquals(listOf("first", "tool", "last"), sections.first().blocks.map { it.id })
        assertEquals(tool, sections.first().blocks[1])
        assertEquals("Done", sections.last().blocks.single().text)
    }

    @Test
    fun codeExamplesRemainLiteral() {
        val code = "Use `<group:Example>` here.\n```xml\n<group:Code>example</group>\n```\n" +
            "~~~\n<group:Other>literal</group>\n~~~\n"
        val sections = parse(code)
        assertEquals(code, sections.single().blocks.single().text)
        assertNull(sections.single().name)
        assertEquals(
            code,
            transcriptSections(listOf(text(code)), true).single().blocks.single().text
        )
    }

    @Test
    fun streamingTagsStayHiddenAndGroupIdentitySurvivesGrowth() {
        val partial = transcriptSections(listOf(text("<group:Work>Start</gro")), true)
        val complete = parse("<group:Work>Start and finish</group>")
        assertEquals("Start", partial.single().blocks.single().text)
        assertEquals(partial.single().id, complete.single().id)
        assertEquals(
            emptyList<TranscriptSection>(),
            transcriptSections(listOf(text("<group:Wor")), true)
        )
    }

    @Test
    fun unclosedAndAdjacentGroupsRetainAllContent() {
        val sections = parse("<group:One>A<group:Two>B")
        assertEquals(listOf("One", "Two"), sections.map { it.name })
        assertEquals(listOf("A", "B"), sections.map { it.blocks.single().text })
    }

    @Test
    fun malformedHistoricalWrappersKeepTheirTitles() {
        listOf("Delegating walkthrough", "Closing hotfix").forEach { title ->
            val source = "<group:$title</group:>"
            listOf(false, true).forEach { streaming ->
                val sections = transcriptSections(listOf(text(source)), streaming)
                assertEquals(title, sections.single().name)
                assertEquals(emptyList<ContentBlock>(), sections.single().blocks)
            }
        }
    }

    @Test
    fun emptyAndStrayClosersDoNotLeakMarkupOrRemoveProse() {
        val sections = parse("</group:>Before<group:Work>Body</group:>After</group:Missing>")
        assertEquals(listOf(null, "Work", null), sections.map { it.name })
        assertEquals(listOf("Before", "Body", "After"), sections.map { it.blocks.single().text })
    }

    @Test
    fun incompleteOpenersKeepTitlesAndFollowingLines() {
        val sections = parse("<group:Setup\nBody</group:><group:Closing hotfix")
        assertEquals(listOf("Setup", "Closing hotfix"), sections.map { it.name })
        assertEquals("\nBody", sections.first().blocks.single().text)
        assertEquals(emptyList<ContentBlock>(), sections.last().blocks)
    }

    @Test
    fun malformedCodeExamplesRemainLiteral() {
        val source = "`<group:Delegating walkthrough</group:>`\n" +
            "```\n<group:Closing hotfix</group:>\n</group:>\n```\n"
        assertEquals(source, parse(source).single().blocks.single().text)
    }

    private fun parse(value: String) = transcriptSections(listOf(text(value)))
    private fun text(value: String, id: String = "text") =
        ContentBlock("text", id = id, text = value)
}
