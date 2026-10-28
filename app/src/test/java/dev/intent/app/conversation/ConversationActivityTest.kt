package dev.intent.app.conversation

import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.ChatEntity
import dev.intent.core.protocol.models.ChatPush
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.ConversationPage
import dev.intent.core.protocol.models.EntityDelta
import dev.intent.core.protocol.models.WireJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationActivityTest {
    private val session = FakeConversationSession()
    private val store = ConversationStore(session, "agent").apply { start() }

    @Test fun headlessSnapshotShowsActivityBeforeAnyAssistantBlock() {
        snapshot(ConversationPage("agent", emptyList(), turnInFlight = true))
        assertTrue(store.state.value.isStreaming)
        snapshot(ConversationPage("agent", emptyList(), isResponding = true))
        assertTrue(store.state.value.isStreaming)
        snapshot(
            ConversationPage(
                "agent",
                listOf(ConversationMessage("m", "assistant", isStreaming = true))
            )
        )
        assertTrue(store.state.value.isStreaming)
        snapshot(ConversationPage("agent", emptyList()))
        assertFalse(store.state.value.isStreaming)
    }

    @Test fun liveAssistantStartsAndEndsButUserEchoCannotEndOrStartTurn() {
        snapshot(ConversationPage("agent", emptyList()))
        delta("user", true)
        assertFalse(store.state.value.isStreaming)
        delta("assistant", false)
        assertTrue(store.state.value.isStreaming)
        delta("user", true)
        assertTrue(store.state.value.isStreaming)
        delta("assistant", true)
        assertFalse(store.state.value.isStreaming)
        delta("assistant", false, "other")
        assertFalse(store.state.value.isStreaming)
    }

    @Test fun firehoseIsFallbackUntilSnapshotButStatusBoundariesRemainAuthoritative() {
        session.activity(ConversationActivity.STREAM_START)
        assertTrue(store.state.value.isStreaming)
        session.activity(ConversationActivity.STREAM_END)
        assertFalse(store.state.value.isStreaming)
        snapshot(ConversationPage("agent", emptyList(), turnInFlight = true))
        session.activity(ConversationActivity.STREAM_END)
        assertTrue(store.state.value.isStreaming)
        session.activity(ConversationActivity.IDLE)
        assertFalse(store.state.value.isStreaming)
        session.activity(ConversationActivity.RUNNING)
        assertTrue(store.state.value.isStreaming)
        store.close()
        session.activity(ConversationActivity.IDLE)
        assertTrue(store.state.value.isStreaming)
    }

    @Test fun statusNamesAndScopeAreDecodedWithoutUsingQueueAsActivity() {
        listOf("responding", "streaming", "processing").forEach {
            assertTrue(
                event("agent:status-changed", "\"newStatus\":\"$it\"") ==
                    ConversationActivity.RUNNING
            )
            assertTrue(Agent("agent", "Agent", status = it).conversationRunning())
        }
        listOf("idle", "completed", "failed", "error").forEach {
            assertTrue(
                event("agent:status-changed", "\"status\":\"$it\"") == ConversationActivity.IDLE
            )
        }
        assertTrue(event("agent:idle") == ConversationActivity.IDLE)
        assertTrue(event("agent:stream:start") == ConversationActivity.STREAM_START)
        assertTrue(event("agent:stream:activity") == ConversationActivity.STREAM_START)
        assertTrue(event("agent:stream:end") == ConversationActivity.STREAM_END)
        assertNull(event("agent:queue:updated"))
        assertNull(event("agent:status-changed", "\"status\":\"processing\"", agent = "other"))
        assertNull(event("agent:idle", workspace = "other"))
        assertFalse(Agent("agent", "Agent", isActive = true).conversationRunning())
        assertFalse(
            Agent("agent", "Agent", waitingForAgentIds = listOf("child")).conversationRunning()
        )
        assertTrue(Agent("agent", "Agent", turnInFlight = true).conversationRunning())
    }

    private fun event(
        type: String,
        extra: String = "\"unused\":true",
        agent: String = "agent",
        workspace: String = "ws"
    ) = conversationActivity(
        WireJson.parseToJsonElement(
            """{"event":{"type":"$type","data":{"agentId":"$agent","workspaceId":"$workspace",$extra}}}"""
        ).jsonObject,
        "ws",
        "agent"
    )

    private fun snapshot(page: ConversationPage) =
        session.push(ChatPush("s", "snapshot", 0, snapshot = page))
    private fun delta(role: String, complete: Boolean, agent: String = "agent") = session.push(
        ChatPush(
            "s",
            "delta",
            1,
            delta = EntityDelta(
                updated = listOf(
                    ChatEntity(
                        agent,
                        "m-$role",
                        role,
                        ContentBlock("text", "b-$role", "Text"),
                        streamingComplete = complete
                    )
                )
            )
        )
    )
}
