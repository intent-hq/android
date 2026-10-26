package dev.intent.app.conversation

import dev.intent.core.protocol.models.ChatEntity
import dev.intent.core.protocol.models.ChatPush
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.ConversationPage
import dev.intent.core.protocol.models.EntityDelta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRefreshGapTest {
    private val session = FakeConversationSession()
    private val store = ConversationStore(session, "agent")
    private fun page(vararg ids: String, token: String? = null) = ConversationPage(
        "agent",
        ids.map { ConversationMessage(it, "assistant", emptyList()) },
        nextToken = token
    )
    private fun snapshot(page: ConversationPage) {
        session.push(ChatPush("s", "snapshot", 0, snapshot = page))
    }
    private fun refresh() {
        store.start()
        session.admit()
        snapshot(page("old", "anchor", token = "retained"))
        store.refresh()
        session.admit()
        snapshot(page("new", token = "gap"))
    }
    private fun ids() = store.state.value.messages.map { it.id }

    @Test
    fun onePageBridgesGapAndKeepsLoadedPrefixAndCursor() {
        refresh()
        assertEquals(listOf("old", "anchor"), ids())
        assertTrue(store.state.value.loadingOlder)
        store.loadOlder()
        assertEquals(1, session.pages.size)
        session.pages.last().complete(page("anchor", "between", "new", token = "page-cursor"))
        assertEquals(listOf("old", "anchor", "between", "new"), ids())
        assertEquals("retained", store.state.value.nextToken)
        assertFalse(store.state.value.loadingOlder)
    }

    @Test
    fun severalPagesBridgeGapWithoutPublishingPartialHistory() {
        refresh()
        session.pages.last().complete(page("middle2", token = "gap2"))
        session.pages.last().complete(page("middle1", token = "gap3"))
        assertEquals(listOf("old", "anchor"), ids())
        session.pages.last().complete(page("anchor", token = "gap4"))
        assertEquals(listOf("old", "anchor", "middle1", "middle2", "new"), ids())
        assertEquals(3, session.pages.size)
        assertEquals("retained", store.state.value.nextToken)
    }

    @Test
    fun capReplacesHistoryWithContiguousNewestPages() {
        refresh()
        repeat(10) { index ->
            session.pages.last().complete(page("gap-$index", token = "cursor-$index"))
        }
        assertEquals(10, session.pages.size)
        assertEquals((9 downTo 0).map { "gap-$it" } + "new", ids())
        assertEquals("cursor-9", store.state.value.nextToken)
        assertFalse(store.state.value.isLoading)
        assertFalse(store.state.value.loadingOlder)
    }

    @Test
    fun pageErrorKeepsLoadedHistoryAndCanRetryRefresh() {
        refresh()
        session.pages.last().complete(page("between", token = "gap2"))
        session.pages.last().completeExceptionally(IllegalStateException("private detail"))
        assertEquals(listOf("old", "anchor"), ids())
        assertEquals("retained", store.state.value.nextToken)
        assertNotNull(store.state.value.error)
        assertFalse(store.state.value.error.orEmpty().contains("private"))
        assertFalse(store.state.value.loadingOlder)
        store.refresh()
        session.admit()
        snapshot(page("new", token = "retry"))
        session.pages.last().complete(page("anchor", "between"))
        assertEquals(listOf("old", "anchor", "between", "new"), ids())
    }

    @Test
    fun pageCrossingLoadedStartKeepsServerCursor() {
        refresh()
        session.pages.last().complete(page("earliest", "old", "anchor", token = "earlier"))
        assertEquals(listOf("earliest", "old", "anchor", "new"), ids())
        assertEquals("earlier", store.state.value.nextToken)
    }

    @Test
    fun repeatedCursorStopsPaging() {
        refresh()
        session.pages.last().complete(page("between", token = "gap"))
        assertEquals(1, session.pages.size)
        assertEquals(listOf("between", "new"), ids())
        assertFalse(store.state.value.loadingOlder)
    }

    @Test
    fun liveUpdatesSurviveGapMergeInPrefixAndTail() {
        refresh()
        session.push(
            ChatPush(
                "s",
                "delta",
                1,
                delta = EntityDelta(
                    updated = listOf("old", "new").map {
                        ChatEntity("agent", it, "assistant", ContentBlock("text", "$it:0", "live"))
                    }
                )
            )
        )
        session.pages.last().complete(page("anchor", "new"))
        assertEquals(listOf("old", "anchor", "new"), ids())
        listOf("old", "new").forEach { id ->
            assertEquals(
                "live",
                store.state.value.messages.first {
                    it.id == id
                }.contentBlocks.single().text
            )
        }
    }

    @Test
    fun reconnectFencesPendingGapPage() {
        refresh()
        session.admit()
        snapshot(page("replacement"))
        session.pages.last().complete(page("anchor", "between"))
        assertEquals(listOf("replacement"), ids())
    }

    @Test
    fun laterSnapshotFencesPendingGapPage() {
        refresh()
        snapshot(page("replacement"))
        session.pages.last().complete(page("anchor", "between"))
        assertEquals(listOf("replacement"), ids())
    }

    @Test
    fun closeFencesPendingGapPage() {
        refresh()
        store.close()
        session.pages.last().complete(page("anchor", "between"))
        assertEquals(listOf("old", "anchor"), ids())
    }
}
