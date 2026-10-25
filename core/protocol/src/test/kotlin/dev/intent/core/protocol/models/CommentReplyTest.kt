package dev.intent.core.protocol.models

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentReplyTest {
    @Test
    fun replyFixturePreservesAttributionAndOmitsAnchor() {
        val fixture = javaClass.getResource("/comments/reply.json")!!.readText()
        val result = WireJson.decodeFromString<CommentReplyResult>(fixture)
        assertEquals("thread", result.thread.threadId)
        assertEquals("principal", result.comment.authorPrincipalId)
        assertNull(result.comment.anchor)
        assertNull(result.comment.anchorText)
        assertEquals(
            result,
            WireJson.decodeFromString<CommentReplyResult>(WireJson.encodeToString(result))
        )
    }
}
