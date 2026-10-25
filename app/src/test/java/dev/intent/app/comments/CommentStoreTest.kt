package dev.intent.app.comments

import dev.intent.core.protocol.models.Comment
import dev.intent.core.protocol.models.CommentReplyResult
import dev.intent.core.protocol.models.CommentReplyThread
import dev.intent.core.protocol.models.CommentThreadSummary
import dev.intent.core.protocol.models.EntityDelta
import dev.intent.core.protocol.models.SubscriptionPush
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentStoreTest {
    @Test
    fun loadAndLiveRowsIncludeThreadRepliesAndRemoval() {
        val source = FakeComments()
        val store = CommentStore(source, "note")
        store.start()
        source.refresh()
        source.reads.last().complete(listOf(thread()))
        assertEquals("Root", store.state.value.threads.single().comments!!.single().content)
        source.push(
            SubscriptionPush(
                "s",
                "delta",
                1,
                delta = EntityDelta(updated = listOf(thread(listOf(comment(), comment("reply")))))
            )
        )
        assertEquals(2, store.state.value.threads.single().comments!!.size)
        source.push(
            SubscriptionPush("s", "delta", 2, delta = EntityDelta(removedIds = listOf("thread")))
        )
        assertTrue(store.state.value.threads.isEmpty())
    }

    @Test
    fun replyClearsOnlyAcknowledgedDraftAndRefreshes() {
        val source = FakeComments()
        val store = ready(source)
        store.draft("thread", "Hello")
        store.reply("thread")
        store.reply("thread")
        assertEquals(listOf("thread" to "Hello"), source.sent)
        assertTrue("thread" in store.state.value.sending)
        source.replies.single().complete(
            CommentReplyResult(true, "ok", comment("reply"), CommentReplyThread("thread", 2))
        )
        assertFalse("thread" in store.state.value.sending)
        assertEquals(null, store.state.value.drafts["thread"])
        source.reads.last().complete(listOf(thread(listOf(comment(), comment("reply")))))
        assertEquals(2, store.state.value.threads.single().comments!!.size)
    }

    @Test
    fun failureKeepsDraftAndReconnectDoesNotReplay() {
        val source = FakeComments()
        val store = ready(source)
        store.draft("thread", "Hello")
        store.reply("thread")
        source.replies.single().completeExceptionally(IllegalStateException("Disconnected"))
        source.refresh()
        assertEquals("Hello", store.state.value.drafts["thread"])
        assertTrue(store.state.value.replyErrors.containsKey("thread"))
        assertEquals(1, source.sent.size)
    }

    @Test
    fun newDraftSurvivesOldAcknowledgment() {
        val source = FakeComments()
        val store = ready(source)
        store.draft("thread", "First")
        store.reply("thread")
        store.draft("thread", "Second")
        source.replies.single().complete(
            CommentReplyResult(true, "ok", comment("reply"), CommentReplyThread("thread", 2))
        )
        assertEquals("Second", store.state.value.drafts["thread"])
    }

    @Test
    fun liveSnapshotAndReconnectFenceOlderReads() {
        val source = FakeComments()
        val store = ready(source)
        store.refresh()
        val stale = source.reads.last()
        source.push(SubscriptionPush("s", "snapshot", 0, snapshot = emptyList()))
        stale.complete(listOf(thread()))
        assertTrue(store.state.value.threads.isEmpty())
        source.refresh()
        val oldConnection = source.reads.last()
        source.refresh()
        source.reads.last().complete(emptyList())
        oldConnection.complete(listOf(thread()))
        assertTrue(store.state.value.threads.isEmpty())
    }

    @Test
    fun closeFencesRepliesAndReleasesLease() {
        val source = FakeComments()
        val store = ready(source)
        store.draft("thread", "Hello")
        store.reply("thread")
        store.close()
        val state = store.state.value
        source.replies.single().complete(
            CommentReplyResult(true, "ok", comment("reply"), CommentReplyThread("thread", 2))
        )
        source.push(SubscriptionPush("s", "snapshot", 1, snapshot = emptyList()))
        assertEquals(state, store.state.value)
        assertTrue(source.closed)
    }

    @Test
    fun ignoresOtherNotesBlankRepliesAndMissingThreads() {
        val source = FakeComments()
        val store = ready(source)
        source.push(
            SubscriptionPush("s", "snapshot", 1, snapshot = listOf(thread().copy(noteId = "other")))
        )
        store.draft("thread", "Hello")
        store.reply("thread")
        assertTrue(store.state.value.threads.isEmpty())
        assertTrue(source.sent.isEmpty())
    }

    private fun ready(source: FakeComments): CommentStore = CommentStore(source, "note").also {
        it.start()
        source.refresh()
        source.reads.last().complete(listOf(thread()))
    }
}

private fun comment(id: String = "root") =
    Comment(id, "thread", "comment", "Root", "Sam", "user", "open", "now", "now")
private fun thread(comments: List<Comment> = listOf(comment())) = CommentThreadSummary(
    "thread", "note", "open", "now", "now", "Sam", "user", "now", comments.size,
    comments = comments
)

private class FakeComments : CommentSession {
    val reads = mutableListOf<CompletableFuture<List<CommentThreadSummary>>>()
    val replies = mutableListOf<CompletableFuture<CommentReplyResult>>()
    val sent = mutableListOf<Pair<String, String>>()
    lateinit var refresh: () -> Unit
    lateinit var push: (SubscriptionPush<CommentThreadSummary>) -> Unit
    var closed = false
    override fun list() = CompletableFuture<List<CommentThreadSummary>>().also(reads::add)
    override fun reply(threadId: String, text: String): CompletableFuture<CommentReplyResult> {
        sent += threadId to text
        return CompletableFuture<CommentReplyResult>().also(replies::add)
    }
    override fun subscribe(
        onPush: (SubscriptionPush<CommentThreadSummary>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        push = onPush
        refresh = onSubscribed
        return Closeable { closed = true }
    }
}
