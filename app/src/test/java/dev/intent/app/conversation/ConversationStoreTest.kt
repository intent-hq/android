package dev.intent.app.conversation

import dev.intent.core.protocol.models.ChatEntity
import dev.intent.core.protocol.models.ChatPush
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.ConversationPage
import dev.intent.core.protocol.models.EntityDelta
import dev.intent.core.protocol.models.MessageBlockResult
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStoreTest {
    private val session = FakeConversationSession()
    private val store = ConversationStore(session, "agent")
    private fun row(id: String = "m", text: String = "Hello") = ConversationMessage(
        id,
        "assistant",
        listOf(ContentBlock("text", "$id:0", text)),
        seq = 2
    )
    private fun snapshot(
        messages: List<ConversationMessage> = listOf(row()),
        token: String? = "older"
    ) {
        session.push(
            ChatPush(
                "s",
                "snapshot",
                0,
                snapshot = ConversationPage("agent", messages, nextToken = token)
            )
        )
    }
    private fun delta(text: String, terminal: Boolean = false) {
        session.push(
            ChatPush(
                "s",
                "delta",
                1,
                delta = EntityDelta(
                    updated = listOf(
                        ChatEntity(
                            "agent",
                            "m",
                            "assistant",
                            ContentBlock("text", "m:0", text),
                            streamingComplete = terminal
                        )
                    )
                )
            )
        )
    }
    private fun start() {
        store.start()
        session.admit()
        snapshot()
    }

    @Test
    fun snapshotLoadsHistoryAndPagingPreservesLiveOverlap() {
        start()
        assertFalse(store.state.value.isLoading)
        store.loadOlder()
        delta("Hello world")
        session.pages.single().complete(
            ConversationPage("agent", listOf(row("old"), row(text = "stale")))
        )
        assertEquals(listOf("old", "m"), store.state.value.messages.map { it.id })
        assertEquals("Hello world", store.state.value.messages.last().contentBlocks.single().text)
        assertEquals(null, store.state.value.nextToken)
    }

    @Test
    fun manualRefreshKeepsLoadedPrefixAndCursorButUpdatesSnapshotTail() {
        start()
        store.loadOlder()
        session.pages.single().complete(
            ConversationPage("agent", listOf(row("old")), nextToken = null)
        )
        store.refresh()
        session.admit()
        snapshot(listOf(row(text = "Refreshed"), row("new")))
        assertEquals(listOf("old", "m", "new"), store.state.value.messages.map { it.id })
        assertEquals("Refreshed", store.state.value.messages[1].contentBlocks.single().text)
        assertEquals(null, store.state.value.nextToken)
        // A later server snapshot is still authoritative; retention is one-shot.
        snapshot(listOf(row("replacement")), token = null)
        assertEquals(listOf("replacement"), store.state.value.messages.map { it.id })
    }

    @Test
    fun exhaustedRefreshAndReconnectBeforeSnapshotDoNotKeepStaleHistory() {
        start()
        store.refresh()
        session.admit()
        snapshot(listOf(row("replacement")), token = null)
        assertEquals(listOf("replacement"), store.state.value.messages.map { it.id })
        store.refresh()
        session.admit()
        session.admit()
        snapshot(listOf(row("reconnected")))
        assertEquals(listOf("reconnected"), store.state.value.messages.map { it.id })
    }

    @Test
    fun fullDeltasReplaceAndTerminalSettles() {
        start()
        delta("Hello world")
        delta("Hello world")
        assertEquals("Hello world", store.state.value.messages.single().contentBlocks.single().text)
        assertTrue(store.state.value.messages.single().isStreaming == true)
        delta("Final", true)
        assertFalse(store.state.value.messages.single().isStreaming == true)
    }

    @Test
    fun reconnectSnapshotDiscardsOldPagesAndPendingReplies() {
        start()
        store.loadOlder()
        store.hydrate("m", "m:0")
        session.admit()
        snapshot(listOf(row("new")))
        session.pages.single().complete(ConversationPage("agent", listOf(row("obsolete"))))
        session.blocks.single().complete(
            MessageBlockResult(ContentBlock("text", "m:0", "obsolete"))
        )
        assertEquals(listOf("new"), store.state.value.messages.map { it.id })
        assertTrue(store.state.value.hydrating.isEmpty())
    }

    @Test
    fun hydrationExpandsBlockButCannotEraseLiveText() {
        start()
        store.hydrate("m", "m:0")
        session.blocks.last().complete(
            MessageBlockResult(ContentBlock("text", "m:0", "Full content"))
        )
        assertEquals(
            "Full content",
            store.state.value.messages.single().contentBlocks.single().text
        )
        store.hydrate("m", "m:0")
        delta("Newest live content")
        session.blocks.last().complete(
            MessageBlockResult(ContentBlock("text", "m:0", "Old full content"))
        )
        assertEquals(
            "Newest live content",
            store.state.value.messages.single().contentBlocks.single().text
        )
    }

    @Test
    fun removalsAndForeignEntitiesAreHandled() {
        start()
        session.push(
            ChatPush(
                "s",
                "delta",
                1,
                delta = EntityDelta(
                    added = listOf(
                        ChatEntity(
                            "foreign",
                            "bad",
                            "assistant",
                            ContentBlock("text", "bad:0", "bad")
                        )
                    ),
                    removedIds = listOf("m:0")
                )
            )
        )
        assertEquals(1, store.state.value.messages.size)
        assertTrue(store.state.value.messages.single().contentBlocks.isEmpty())
    }

    @Test
    fun closedStoreRejectsCallbacksAndReleasesLease() {
        start()
        store.close()
        snapshot(listOf(row("wrong")))
        assertEquals("m", store.state.value.messages.single().id)
        assertEquals(1, session.closed)
    }

    @Test
    fun pageFailureKeepsHistoryAndAllowsRetry() {
        start()
        store.loadOlder()
        session.pages.last().completeExceptionally(IllegalStateException("private server details"))
        assertEquals("m", store.state.value.messages.single().id)
        assertFalse(store.state.value.loadingOlder)
        assertFalse(store.state.value.error.orEmpty().contains("private"))
        store.loadOlder()
        assertEquals(2, session.pages.size)
    }
}

internal class FakeConversationSession : ConversationSession {
    lateinit var push: (ChatPush) -> Unit
    lateinit var admit: () -> Unit
    val pages = mutableListOf<CompletableFuture<ConversationPage>>()
    val blocks = mutableListOf<CompletableFuture<MessageBlockResult>>()
    var closed = 0
    override fun history(token: String) =
        CompletableFuture<ConversationPage>().also { pages.add(it) }
    override fun hydrate(messageId: String, blockId: String) =
        CompletableFuture<MessageBlockResult>().also { blocks.add(it) }
    override fun subscribe(
        onPush: (ChatPush) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        push = onPush
        admit = onSubscribed
        return Closeable { closed++ }
    }
}
