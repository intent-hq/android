package dev.intent.core.protocol.models

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentModelsTest {
    private val payloads = fixture("payloads.json").jsonObject

    @Test
    fun agentListAcceptsUnknownStatusAndMetadata() {
        val result = decode<AgentListResult>("agents")
        val agent = result.agents.single()
        assertEquals("future-status", agent.status)
        assertTrue(agent.notificationsMuted)
        assertTrue(agent.turnInFlight)
        assertEquals("", agent.metadata?.get("pendingQuestionsMessageId")?.jsonPrimitive?.content)
        assertEquals(1L, result.retiredCount)
        assertEquals(
            result,
            WireJson.decodeFromString<AgentListResult>(WireJson.encodeToString(result))
        )
    }

    @Test
    fun historyPreservesCursorsBlockIdentityAndSlimFlags() {
        val page = decode<ConversationPage>("conversation")
        assertEquals("older", page.nextToken)
        assertEquals("newer", page.prevToken)
        assertNull(page.lastStreamActivityAt)
        val message = page.messages.single()
        assertEquals(7L, message.seq)
        assertEquals("user_stop", message.metadata?.get("interruptReason")?.jsonPrimitive?.content)
        val blocks = message.contentBlocks
        assertEquals("m:0", blocks[0].id)
        assertEquals(3000L, blocks[0].inputBytes)
        assertEquals(true, blocks[0].inputTruncated)
        assertEquals("call-1", blocks[1].toolUseId)
        assertEquals(false, blocks[1].isError)
        assertEquals(true, blocks[2].dataTruncated)
        assertNull(blocks[2].data)
        assertEquals("application/vnd.intent.question+json", blocks[3].resource?.mimeType)
        assertEquals("future-block", blocks[4].type)
        assertEquals(
            page,
            WireJson.decodeFromString<ConversationPage>(WireJson.encodeToString(page))
        )
    }

    @Test
    fun canonicalChatSnapshotAndDeltaExamplesDecode() {
        val snapshot = chat("chat-2.json").snapshot!!
        assertEquals("0190a1b2-user:0", snapshot.messages.single().contentBlocks.single().id)
        assertFalse(snapshot.truncated)
        assertEquals(1L, snapshot.totalMessages)
        val delta = chat("chat-5.json")
        assertEquals(6L, delta.seq)
        val entity = delta.delta!!.updated.single()
        assertEquals("0190a200-asst", entity.messageId)
        assertEquals("Let me check the logs first.", entity.block.text)
        assertNull(entity.streamingComplete)
    }

    @Test
    fun progressiveHistoryFramesKeepProgressAndTranscriptSequenceSeparate() {
        val frame = chat("chat-3.json")
        assertEquals(1L, frame.seq)
        val history = frame.history!!
        assertEquals(0L, history.message!!.seq)
        assertEquals(20L, history.target)
        assertFalse(history.complete)
        val complete = chat("chat-4.json").history!!
        assertTrue(complete.complete)
        assertEquals(2L, complete.totalMessages)
        assertNull(complete.message)
    }

    @Test
    fun incrementalTextAndTerminalMetadataRemainDistinct() {
        val incremental = decode<ChatPush>("incremental").delta!!.updated.single()
        assertEquals(" fragment", incremental.block.textDelta)
        assertNull(incremental.block.text)
        assertNull(incremental.streamingComplete)
        val terminal = decode<ChatPush>("terminal")
        val entity = terminal.delta!!.updated.single()
        assertEquals(true, entity.streamingComplete)
        assertEquals(7L, entity.messageSeq)
        assertEquals("max_tokens", entity.metadata?.get("finishReason")?.jsonPrimitive?.content)
        assertEquals("Complete", entity.block.text)
        assertNull(entity.block.textDelta)
        assertEquals(listOf("m:orphan"), terminal.delta.removedIds)
    }

    @Test
    fun streamEventsKeepStopAttributionAndToolIdentity() {
        val end = decode<StreamEndData>("end")
        assertEquals("preempted_by_message", end.interruptReason)
        assertEquals("agent-two", end.interruptedBy?.get("agentId")?.jsonPrimitive?.content)
        assertNull(end.trailingBlocks)
        val status = decode<StreamStatusData>("status")
        assertEquals(300000L, status.silentMs)
        assertEquals(1792868400000L, status.timestamp)
        val tool = decode<AgentToolCallData>("tool")
        assertEquals("m:2", tool.resultBlockId)
        assertEquals("call-1", tool.toolCallId)
        val activity = WireJson.decodeFromString<StreamActivityData>(
            """{"agentId":"a","messageId":"m","futureField":true}"""
        )
        assertNull(activity.lastAgentResponse)
        assertNull(activity.digest)
    }

    private inline fun <reified T> decode(key: String): T =
        WireJson.decodeFromJsonElement(payloads.getValue(key))

    private fun chat(name: String): ChatPush =
        WireJson.decodeFromJsonElement(fixture(name).jsonObject.getValue("params"))

    private fun fixture(name: String): JsonElement = WireJson.parseToJsonElement(
        requireNotNull(javaClass.getResource("/agents/$name")).readText()
    )
}
