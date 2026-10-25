package dev.intent.app.conversation

import dev.intent.core.protocol.models.ContentBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

// Canonical fixture: intent-hq/intent docs/protocol/fixtures/suggested-prompts/v1/cases.json
// Source revision: eec94e3674f33e9e80d2937a452741959bdd0871.
class SuggestedPromptsTest {
    @Test
    fun canonicalCasesMatchForPromptsAndCleanedText() {
        val text = requireNotNull(javaClass.getResource("/suggested-prompts/cases.json")).readText()
        val cases = Json.parseToJsonElement(text).jsonObject.getValue("cases").jsonArray
        cases.forEach { entry ->
            val case = entry.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val input = case.getValue("input").jsonPrimitive.content
            val expected = case.getValue("expect").jsonObject
            val result = SuggestedPrompts.parse(listOf(ContentBlock("text", text = input)))
            assertEquals(
                id,
                expected.getValue("prompts").jsonArray.map {
                    it.jsonPrimitive.content
                },
                result.prompts
            )
            assertEquals(
                id,
                expected.getValue("cleaned").jsonPrimitive.content,
                normalize(result.blocks.single().text.orEmpty())
            )
        }
    }

    @Test
    fun markersAcrossTextBlocksStripTogetherAndToolsStayUnchanged() {
        val tool = ContentBlock("tool_use", text = "<!-- suggested-prompts\nTool content\n-->")
        val result = SuggestedPrompts.parse(
            listOf(
                ContentBlock("text", text = "Done.\n<!-- suggested-"),
                tool,
                ContentBlock("text", text = "prompts\nRun tests\n-->")
            )
        )
        assertEquals(listOf("Run tests"), result.prompts)
        assertEquals("Done.\n", result.blocks.first().text)
        assertEquals(tool, result.blocks[1])
        assertEquals("", result.blocks.last().text)
    }

    @Test
    fun incompleteStreamingBlockIsHiddenUntilItFinishes() {
        val blocks = listOf(ContentBlock("text", text = "Done.\n<!-- suggested-prompts\nRun tests"))
        assertEquals(
            "Done.\n",
            SuggestedPrompts.parse(blocks, streaming = true).blocks.single().text
        )
        assertEquals(emptyList<String>(), SuggestedPrompts.parse(blocks, streaming = true).prompts)
        assertEquals(blocks, SuggestedPrompts.parse(blocks).blocks)
    }

    private fun normalize(text: String) =
        text.replace("\r\n", "\n").trim().replace(Regex("\n{3,}"), "\n\n")
}
