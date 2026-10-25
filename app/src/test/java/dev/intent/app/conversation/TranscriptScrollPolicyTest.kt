package dev.intent.app.conversation

import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptScrollPolicyTest {
    @Test
    fun manualScrollPausesAndNewMessagesStayPendingUntilBottom() {
        val first = message("first")
        val paused = TranscriptScrollPolicy().contentChanged(first).pause()
            .contentChanged(message("second")).observe(viewport())
        assertFalse(paused.following)
        assertTrue(paused.hasNewMessages)
        assertFalse(paused.observe(viewport(atBottom = true)).following)
        val resumed = paused.observe(viewport(atBottom = true, scrolling = true))
        assertTrue(resumed.following)
        assertFalse(resumed.hasNewMessages)
    }

    @Test
    fun streamingGrowthShowsNewMessagesWithoutResuming() {
        val paused = TranscriptScrollPolicy().contentChanged(message("same")).pause()
            .contentChanged(message("same", "more text"))
        assertTrue(paused.hasNewMessages)
        assertFalse(paused.following)
    }

    @Test
    fun reachingTopLoadsEachPageOnceAndLeavingAllowsRetry() {
        val policy = TranscriptScrollPolicy()
        val top = viewport(atTop = true)
        assertTrue(policy.shouldLoad(top))
        val requested = policy.copy(requestedToken = "page")
        assertFalse(requested.shouldLoad(top))
        assertTrue(requested.shouldLoad(top.copy(nextToken = "next")))
        assertTrue(requested.observe(viewport()).shouldLoad(top))
        assertFalse(policy.shouldLoad(top.copy(loading = true)))
        assertFalse(policy.shouldLoad(top.copy(nextToken = null)))
        assertFalse(policy.shouldLoad(viewport()))
    }

    private fun message(id: String, text: String = "text") =
        ConversationMessage(id, "assistant", listOf(ContentBlock("text", text = text)))

    private fun viewport(
        atBottom: Boolean = false,
        atTop: Boolean = false,
        scrolling: Boolean = false
    ) = TranscriptViewport(atBottom, atTop, scrolling, "page", false)
}
