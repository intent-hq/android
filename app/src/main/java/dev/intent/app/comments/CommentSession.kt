package dev.intent.app.comments

import dev.intent.core.protocol.models.CommentListResult
import dev.intent.core.protocol.models.CommentReplyResult
import dev.intent.core.protocol.models.CommentThreadSummary
import dev.intent.core.protocol.models.SubscriptionPush
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface CommentSession {
    fun list(): CompletableFuture<List<CommentThreadSummary>>
    fun reply(threadId: String, text: String): CompletableFuture<CommentReplyResult>
    fun subscribe(
        onPush: (SubscriptionPush<CommentThreadSummary>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable
}

class RpcCommentSession(
    private val session: RpcSession,
    private val workspaceId: String,
    private val noteId: String
) : CommentSession {
    override fun list(): CompletableFuture<List<CommentThreadSummary>> = session.request(
        "comment.list",
        buildJsonObject {
            put("workspaceId", workspaceId)
            put("noteId", noteId)
            put("includeComments", true)
        },
        CommentListResult.serializer()
    ).thenApply { it.threads }

    override fun reply(threadId: String, text: String): CompletableFuture<CommentReplyResult> =
        session.request(
            "comment.respond",
            buildJsonObject {
                put("workspaceId", workspaceId)
                put("noteId", noteId)
                put("threadId", threadId)
                put("comment", text)
                put("authorType", "user")
                put("type", "comment")
            },
            CommentReplyResult.serializer()
        )

    override fun subscribe(
        onPush: (SubscriptionPush<CommentThreadSummary>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable = session.subscribe(
        SubscriptionSpec(
            "comment.subscribe",
            buildJsonObject {
                put("workspaceId", workspaceId)
                put("noteId", noteId)
            },
            unsubscribeMethod = "comment.unsubscribe"
        ),
        onMessage = { payload ->
            runCatching {
                WireJson.decodeFromJsonElement(
                    SubscriptionPush.serializer(CommentThreadSummary.serializer()),
                    payload
                )
            }.onSuccess(onPush).onFailure(onError)
        },
        onSubscribed = { onSubscribed() },
        onError = onError
    )
}
