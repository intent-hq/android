@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

/** Optional fields distinguish an absent live field from an authoritative empty value. */
@Serializable
data class ContentBlock(
    val type: String,
    val id: String? = null,
    @JsonNames("content") val text: String? = null,
    val textDelta: String? = null,
    val language: String? = null,
    @JsonNames("toolName") val name: String? = null,
    val input: JsonElement? = null,
    val toolCallId: String? = null,
    @JsonNames("tool_use_id") val toolUseId: String? = null,
    val output: JsonElement? = null,
    @JsonNames("is_error") val isError: Boolean? = null,
    val data: String? = null,
    val mimeType: String? = null,
    val fileName: String? = null,
    val attachmentId: String? = null,
    val size: Long? = null,
    val resource: ResourceContents? = null,
    @JsonNames("meta") val metadata: JsonObject? = null,
    val messageMetadata: JsonObject? = null,
    val inputTruncated: Boolean? = null,
    val inputBytes: Long? = null,
    val inputPruned: Boolean? = null,
    val outputTruncated: Boolean? = null,
    val outputBytes: Long? = null,
    val outputPruned: Boolean? = null,
    val dataTruncated: Boolean? = null,
    val dataIsThumbnail: Boolean? = null,
    val dataBytes: Long? = null,
    val width: Long? = null,
    val height: Long? = null,
    val media: Map<String, MediaDimensions>? = null
)

@Serializable
data class ResourceContents(
    val uri: String? = null,
    val mimeType: String? = null,
    val text: String? = null
)

@Serializable
data class MediaDimensions(val width: Long, val height: Long)

@Serializable
data class ConversationMessage(
    val id: String,
    val role: String,
    val contentBlocks: List<ContentBlock> = emptyList(),
    val timestamp: String = "",
    val agentId: String? = null,
    val seq: Long? = null,
    val appMessageId: String? = null,
    val metadata: JsonObject? = null,
    val error: String? = null,
    val isStreaming: Boolean? = null
)

@Serializable
data class ConversationParams(
    val agentId: String,
    val workspaceId: String? = null,
    val limit: Int? = null,
    val nextToken: String? = null,
    val aroundMessageId: String? = null,
    val aroundIndex: Long? = null,
    val projection: String? = null
)

@Serializable
data class ConversationPage(
    val agentId: String,
    val messages: List<ConversationMessage>,
    val truncated: Boolean = false,
    val totalMessages: Long = 0,
    val nextToken: String? = null,
    val prevToken: String? = null,
    val turnInFlight: Boolean = false,
    val lastStreamActivityAt: String? = null,
    val historyDelivery: String? = null,
    val initialHistory: ChatHistory? = null,
    val isResponding: Boolean = false
)

@Serializable
data class MessageBlockParams(
    val agentId: String,
    val messageId: String,
    val blockId: String,
    val workspaceId: String? = null
)

@Serializable
data class MessageBlockResult(val block: ContentBlock)
