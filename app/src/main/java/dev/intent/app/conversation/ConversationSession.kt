package dev.intent.app.conversation

import dev.intent.core.protocol.models.ChatPush
import dev.intent.core.protocol.models.ConversationPage
import dev.intent.core.protocol.models.MessageBlockResult
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface ConversationSession {
    fun history(token: String): CompletableFuture<ConversationPage>
    fun hydrate(messageId: String, blockId: String): CompletableFuture<MessageBlockResult>
    fun subscribe(
        onPush: (ChatPush) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable
}

class RpcConversationSession(
    private val session: RpcSession,
    private val workspaceId: String,
    private val agentId: String
) : ConversationSession {
    private fun params(extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) =
        buildJsonObject {
            put("workspaceId", workspaceId)
            put("agentId", agentId)
            extra()
        }

    override fun history(token: String): CompletableFuture<ConversationPage> = session.request(
        "agent.getConversation",
        params {
            put("nextToken", token)
            put("limit", PAGE_SIZE)
            put("projection", "slim")
        },
        ConversationPage.serializer()
    )

    override fun hydrate(
        messageId: String,
        blockId: String
    ): CompletableFuture<MessageBlockResult> = session.request(
        "agent.getMessageBlock",
        params {
            put("messageId", messageId)
            put("blockId", blockId)
        },
        MessageBlockResult.serializer()
    )

    override fun subscribe(
        onPush: (ChatPush) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable = session.subscribe(
        SubscriptionSpec(
            "chat.subscribe",
            params {
                put("limit", PAGE_SIZE)
                put("projection", "slim")
                put("historyDelivery", "atomic")
            }
        ),
        onMessage = { payload ->
            runCatching { WireJson.decodeFromJsonElement(ChatPush.serializer(), payload) }
                .onSuccess(onPush).onFailure(onError)
        },
        onSubscribed = { onSubscribed() },
        onError = onError
    )
}

private const val PAGE_SIZE = 20
