package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatSubscribeParams(
    val agentId: String,
    val workspaceId: String? = null,
    val limit: Int? = null,
    val sinceMessageId: String? = null,
    val projection: String? = null,
    val deltaEncoding: String? = null,
    val historyDelivery: String? = null
)

/** Chat snapshots are pages, unlike collection snapshots, which are arrays. */
@Serializable
data class ChatPush(
    val subscriptionId: String,
    val kind: String,
    val seq: Long,
    val snapshot: ConversationPage? = null,
    val delta: EntityDelta<ChatEntity>? = null,
    val history: ChatHistory? = null
)

@Serializable
data class ChatHistory(
    val target: Long,
    val received: Long,
    val complete: Boolean,
    val message: ConversationMessage? = null,
    val nextToken: String? = null,
    val truncated: Boolean? = null,
    val totalMessages: Long? = null
)

@Serializable
data class ChatEntity(
    val agentId: String,
    val messageId: String,
    val role: String,
    val block: ContentBlock,
    val messageSeq: Long? = null,
    val appMessageId: String? = null,
    val timestamp: String? = null,
    val streamingComplete: Boolean? = null,
    val metadata: JsonObject? = null
)

@Serializable
data class StreamStartData(val agentId: String, val messageId: String, val reason: String? = null)

/** A liveness preview, never a transcript delta. */
@Serializable
data class StreamActivityData(
    val agentId: String,
    val messageId: String,
    val lastAgentResponse: String? = null,
    val digest: String? = null,
    val lastToolUse: JsonObject? = null
)

@Serializable
data class StreamEndData(
    val agentId: String,
    val messageId: String? = null,
    val turnId: String? = null,
    val trailingBlocks: List<ContentBlock>? = null,
    val stopReason: String? = null,
    val interruptReason: String? = null,
    val interruptedBy: JsonObject? = null,
    val finishReason: String? = null,
    val lastAgentResponse: String? = null,
    val digest: String? = null
)

@Serializable
data class StreamStatusData(
    val agentId: String,
    val phase: String,
    val message: String? = null,
    val silentMs: Long? = null,
    val workspaceId: String? = null,
    val level: String? = null,
    val timestamp: Long? = null
)

@Serializable
data class AgentFailedData(
    val agentId: String,
    val error: String,
    val turnId: String? = null,
    val parentAgentId: String? = null,
    val errorCode: String? = null,
    val providerId: String? = null
)

@Serializable
data class AgentToolCallData(
    val agentId: String,
    val toolName: String,
    val toolCallId: String,
    val status: String,
    val title: String? = null,
    val toolKind: String? = null,
    val input: JsonElement? = null,
    val output: JsonElement? = null,
    val messageId: String? = null,
    val blockIndex: Long? = null,
    val blockId: String? = null,
    val resultBlockIndex: Long? = null,
    val resultBlockId: String? = null,
    val proposalBlockIds: List<String>? = null,
    val registeredAttachments: List<JsonObject>? = null
)
