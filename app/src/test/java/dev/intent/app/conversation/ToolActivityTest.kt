package dev.intent.app.conversation

import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.ResourceContents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ToolActivityTest {
    @Test
    fun adjacentThinkingCallsAndResultsGroupWithoutHidingProse() {
        val rows = transcriptRows(
            listOf(
                activity("think", "thinking"),
                activity("call"),
                activity("result", "tool_result"),
                prose("answer")
            )
        )
        assertEquals(listOf(3, 1), rows.map { it.messages.size })
        assertEquals(listOf("think", "answer"), rows.map { it.id })
        assertEquals(listOf("think", "call", "result"), rows.first().messages.map { it.id })
    }

    @Test
    fun proseAttachmentsErrorsAndQuestionsRemainBoundaries() {
        val boundaries = listOf(
            prose("human").copy(role = "user"),
            prose("answer"),
            activity("failed").copy(error = "Cannot run"),
            activity("image").copy(contentBlocks = listOf(ContentBlock("image"))),
            activity("empty").copy(contentBlocks = emptyList()),
            activity("question").copy(
                contentBlocks = listOf(
                    ContentBlock("tool_use", resource = ResourceContents(mimeType = QUESTION_MIME))
                )
            ),
            activity(
                "mixed"
            ).copy(
                contentBlocks = listOf(
                    ContentBlock("thinking"),
                    ContentBlock("text", text = "Answer")
                )
            )
        )
        boundaries.forEach { boundary ->
            assertFalse(boundary.isToolActivity())
            assertEquals(3, transcriptRows(listOf(activity("a"), boundary, activity("b"))).size)
        }
    }

    @Test
    fun loadedKeysSurvivePrependingAppendingAndHydratingActivity() {
        val grouping = TranscriptRowGrouping()
        val loaded = listOf(activity("call"), activity("result", "tool_result"), prose("answer"))
        val keys = grouping.rows(loaded).map { it.id }
        val older = listOf(activity("older", "thinking")) + loaded
        assertEquals(keys, grouping.rows(older).map { it.id })
        val growing = older.dropLast(1) + activity("next") + older.last()
        assertEquals(keys, grouping.rows(growing).map { it.id })
        val hydrated = growing.map { message ->
            message.copy(
                contentBlocks = message.contentBlocks.map {
                    it.copy(outputTruncated = false)
                }
            )
        }
        assertEquals(keys, grouping.rows(hydrated).map { it.id })
        assertEquals(keys + "new-prose", grouping.rows(hydrated + prose("new-prose")).map { it.id })
    }

    @Test
    fun liveActivityKeepsItsKeyWhenAResultArrives() {
        val grouping = TranscriptRowGrouping()
        val initial = listOf(prose("intro"), activity("call"))
        val keys = grouping.rows(initial).map { it.id }
        assertEquals(keys, grouping.rows(initial + activity("result", "tool_result")).map { it.id })
    }

    @Test
    fun mixedMessagesAndNamedGroupsRetainProseBoundariesAndBlockIds() {
        val blocks = listOf(
            ContentBlock("thinking", id = "thought"),
            ContentBlock("tool_use", id = "call"),
            ContentBlock("text", id = "answer", text = "Answer"),
            ContentBlock("text", text = "<group:Checks>"),
            ContentBlock("tool_result", id = "result"),
            ContentBlock("text", text = "</group>"),
            ContentBlock("tool_use", id = "next")
        )
        val sections = groupToolSections(transcriptSections(blocks))
        assertEquals(listOf(2, 1, 1, 1), sections.map { it.blocks.size })
        assertEquals("Checks", sections[2].name)
        assertEquals(
            listOf("thought", "call", "answer", "result", "next"),
            sections.flatMap {
                it.blocks
            }.map { it.id }
        )
    }

    @Test
    fun summaryExposesToolFailures() {
        assertEquals(
            "Tool activity · 2 items · 1 failed",
            toolActivityLabel(
                listOf(
                    ContentBlock("tool_use"),
                    ContentBlock("tool_result", isError = true)
                )
            )
        )
    }

    private fun activity(id: String, type: String = "tool_use") =
        ConversationMessage(id, "assistant", listOf(ContentBlock(type, id = "$id:block")))

    private fun prose(id: String) =
        ConversationMessage(id, "assistant", listOf(ContentBlock("text", text = id)))
}
