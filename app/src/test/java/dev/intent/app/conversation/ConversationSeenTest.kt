package dev.intent.app.conversation

import dev.intent.core.protocol.models.ConversationMessage
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSeenTest {
    private val calls = mutableListOf<Pair<String, CompletableFuture<String?>>>()
    private var scheduled: (() -> Unit)? = null
    private val seen = ConversationSeen({ id ->
        CompletableFuture<String?>().also { calls += id to it }
    }, { action ->
        scheduled = action
        Closeable { scheduled = null }
    })

    @Test fun entryDoesNotMarkAndTurnCompletionDebouncesAndDeduplicates() {
        seen.observe(state("first"), true)
        assertTrue(calls.isEmpty())
        assertEquals(null, scheduled)
        seen.observe(state("reply", streaming = true), true)
        seen.observe(state("reply"), true)
        assertTrue(calls.isEmpty())
        scheduled?.invoke()
        assertEquals(listOf("reply"), calls.map { it.first })
        seen.sentMessage()
        scheduled?.invoke()
        seen.leave()
        assertEquals(1, calls.size)
    }

    @Test fun departureFlushesOnceAndDoesNotIncludeBackgroundMessages() {
        seen.observe(state("visible"), true)
        seen.sentMessage()
        seen.observe(state("background"), false)
        assertEquals(null, scheduled)
        assertEquals(listOf("visible"), calls.map { it.first })
        seen.observe(state("new-background"), false)
        seen.leave()
        assertEquals(1, calls.size)
    }

    @Test fun failuresCanRetryAndServerMarkersDeduplicate() {
        seen.observe(state("reply"), true)
        seen.sentMessage()
        scheduled?.invoke()
        calls.single().second.completeExceptionally(IllegalStateException("offline"))
        seen.sentMessage()
        scheduled?.invoke()
        assertEquals(2, calls.size)
        calls.last().second.complete("newer-server-marker")
        seen.observe(state("newer-server-marker"), true)
        seen.leave()
        assertEquals(2, calls.size)
    }

    @Test fun streamingRowsAndUnloadedSnapshotsAreNotMarked() {
        seen.observe(state("stream", true), true)
        seen.leave()
        assertTrue(calls.isEmpty())
        seen.observe(state("unloaded").copy(isLoading = true), true)
        seen.leave()
        assertTrue(calls.isEmpty())
        seen.observe(state("known"), true, "known")
        seen.leave()
        assertTrue(calls.isEmpty())
    }

    private fun state(id: String, streaming: Boolean = false) = ConversationState(
        messages = listOf(ConversationMessage(id, "assistant", isStreaming = streaming)),
        isLoading = false,
        isStreaming = streaming
    )
}
