package dev.intent.app.conversation

import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptFormattingTest {
    @Test
    fun rawToolMarkupPreservesProseAndStreamingFragments() {
        val parts = transcriptTextParts("Before\n<invoke name=\"read\">payload</invoke>\nAfter")
        assertEquals(listOf(false, true, false), parts.map { it.toolMarkup })
        assertEquals("Before\n", parts.first().text)
        assertEquals("\nAfter", parts.last().text)
        assertTrue(transcriptTextParts("<invoke name=\"read\">partial").single().toolMarkup)
        assertFalse(transcriptTextParts("Use <strong>bold</strong>.").single().toolMarkup)
    }

    @Test
    fun adjacentLifecycleNoticesGroupWithoutCrossingAReply() {
        val notices = listOf(notice("1"), notice("2"))
        val answer =
            ConversationMessage("3", "assistant", listOf(ContentBlock("text", text = "Answer")))
        val rows = transcriptRows(notices + answer + notice("4"))
        assertEquals(listOf(2, 1, 1), rows.map { it.messages.size })
        assertEquals("1", rows.first().id)
    }

    @Test
    fun toolBodyRemovesOnlyAnExactLeadingTitle() {
        val block = ContentBlock("tool_result", name = "Read files", text = "Read files\nResult")
        assertEquals("Result", toolBody(block))
        assertEquals(
            "Read files carefully\nResult",
            toolBody(block.copy(text = "Read files carefully\nResult"))
        )
        assertEquals("Result\nRead files", toolBody(block.copy(text = "Result\nRead files")))
    }

    @Test
    fun wireStringInputAndOutputRemoveRepeatedTitle() {
        val value = JsonPrimitive("Read files\nResult")
        assertEquals(
            "Result",
            toolBody(ContentBlock("tool_use", name = "Read files", input = value))
        )
        assertEquals(
            "Result",
            toolBody(ContentBlock("tool_result", name = "Read files", output = value))
        )
    }

    @Test
    fun structuredWireValuesKeepJsonRepresentation() {
        val value = JsonObject(mapOf("title" to JsonPrimitive("Read files")))
        assertEquals(
            value.toString(),
            toolBody(ContentBlock("tool_use", name = "Read files", input = value))
        )
        assertEquals(
            value.toString(),
            toolBody(ContentBlock("tool_result", name = "Read files", output = value))
        )
    }

    private fun notice(id: String) = ConversationMessage(
        id,
        "user",
        listOf(ContentBlock("text", text = "Worker sent a message and finished"))
    )
}
