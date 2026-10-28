package dev.intent.app.conversation

import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.AgentResult
import dev.intent.core.protocol.models.CommandResult
import dev.intent.core.protocol.models.ConversationPage
import dev.intent.core.protocol.models.QueueResult
import dev.intent.core.protocol.models.SendResult
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Suppress("TooManyFunctions") // One scoped command surface for the conversation.
interface ConversationCommands {
    fun send(id: String, content: String, metadata: JsonObject?): CompletableFuture<SendResult>
    fun sendAttachments(
        id: String,
        content: String,
        metadata: JsonObject?,
        images: List<JsonObject>,
        files: List<JsonObject>
    ): CompletableFuture<SendResult> {
        require(images.isEmpty() && files.isEmpty())
        return send(id, content, metadata)
    }
    fun stopReply(): CompletableFuture<CommandResult> = unsupportedControl()
    fun editQueued(id: String, content: String): CompletableFuture<CommandResult> =
        unsupportedControl()
    fun sendNow(id: String): CompletableFuture<SendResult> = unsupportedControl()
    fun dismissQuestions(id: String): CompletableFuture<CommandResult> = unsupportedControl()
    fun queue(): CompletableFuture<QueueResult>
    fun retract(id: String): CompletableFuture<CommandResult>
    fun agent(): CompletableFuture<Agent>
    fun history(aroundId: String? = null): CompletableFuture<ConversationPage>
    fun watch(onRefresh: () -> Unit, onError: (Throwable) -> Unit): Closeable
}

@Suppress("TooManyFunctions") // Implements the conversation command surface.
class RpcConversationCommands(
    private val session: RpcSession,
    private val workspaceId: String,
    private val agentId: String
) : ConversationCommands {
    private fun params(extra: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("workspaceId", workspaceId)
        put("agentId", agentId)
        extra()
    }

    override fun send(id: String, content: String, metadata: JsonObject?) =
        sendAttachments(id, content, metadata, emptyList(), emptyList())

    override fun sendAttachments(
        id: String,
        content: String,
        metadata: JsonObject?,
        images: List<JsonObject>,
        files: List<JsonObject>
    ) = session.request(
        "agent.sendMessage",
        params {
            put("messageId", id)
            put("userAppMessageId", id)
            put("content", content)
            if (images.isNotEmpty()) put("imageBlocks", JsonArray(images))
            if (files.isNotEmpty()) put("fileBlocks", JsonArray(files))
            metadata?.let { put("messageMetadata", it) }
        },
        SendResult.serializer()
    )

    override fun stopReply() = session.request("agent.stop", params(), CommandResult.serializer())
    override fun editQueued(id: String, content: String) = session.request(
        "agent.editQueuedMessage",
        params {
            put("messageId", id)
            put("content", content)
        },
        CommandResult.serializer()
    )
    override fun sendNow(id: String) = session.request(
        "agent.sendQueuedMessageNow",
        params { put("messageId", id) },
        SendResult.serializer()
    )
    override fun dismissQuestions(id: String) = session.request(
        "agent.dismissQuestions",
        params { put("messageId", id) },
        CommandResult.serializer()
    )

    override fun queue() = session.request("agent.getQueue", params(), QueueResult.serializer())
    override fun retract(id: String) = session.request(
        "agent.removeQueuedMessage",
        params { put("messageId", id) },
        CommandResult.serializer()
    )
    override fun agent() = session.request("agent.get", params(), AgentResult.serializer())
        .thenApply { it.agent }
    override fun history(aroundId: String?) = session.request(
        "agent.getConversation",
        params {
            put("limit", COMMAND_HISTORY_LIMIT)
            aroundId?.let { put("aroundMessageId", it) }
        },
        ConversationPage.serializer()
    )

    override fun watch(onRefresh: () -> Unit, onError: (Throwable) -> Unit): Closeable =
        session.subscribe(
            SubscriptionSpec(
                "events.subscribe",
                buildJsonObject {
                    put("workspaceId", workspaceId)
                    put(
                        "eventTypes",
                        buildJsonArray {
                            listOf(
                                "agent:queue:*",
                                "agent:message",
                                "agent:updated",
                                "agent:stream:end"
                            )
                                .forEach { add(JsonPrimitive(it)) }
                        }
                    )
                },
                notificationMethod = "events.event"
            ),
            onMessage = { payload ->
                val event = payload["event"] as? JsonObject ?: payload
                val data = event["data"] as? JsonObject
                if ((data?.get("agentId") as? JsonPrimitive)?.content == agentId) onRefresh()
            },
            onSubscribed = { onRefresh() },
            onError = onError
        )
}

private const val COMMAND_HISTORY_LIMIT = 20

private fun <T> unsupportedControl(): CompletableFuture<T> = CompletableFuture<T>().apply {
    completeExceptionally(UnsupportedOperationException("Conversation control unavailable"))
}
