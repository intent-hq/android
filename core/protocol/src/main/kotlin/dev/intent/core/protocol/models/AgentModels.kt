package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Status strings stay open so a newer daemon cannot break the list. */
@Serializable
data class Agent(
    val id: String,
    val name: String,
    val workspaceId: String? = null,
    val status: String = "idle",
    val parentAgentId: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val reasoningEffort: String? = null,
    val isActive: Boolean = false,
    val isStreaming: Boolean = false,
    val isProcessing: Boolean = false,
    val isResponding: Boolean = false,
    val isWaitingForOtherAgents: Boolean = false,
    val waitingForAgentIds: List<String> = emptyList(),
    val turnInFlight: Boolean = false,
    val lastStreamActivityAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastActivity: String? = null,
    val lastAgentResponse: String? = null,
    val lastUserMessage: String? = null,
    val lastMessageRole: String? = null,
    val lastMessageId: String? = null,
    val digest: String? = null,
    val lastToolUse: JsonObject? = null,
    val lastAgentResponseTruncated: Boolean? = null,
    val lastUserMessageTruncated: Boolean? = null,
    val digestTruncated: Boolean? = null,
    val messageCount: Long = 0,
    val notificationsMuted: Boolean = false,
    val retiredAt: String? = null,
    val pendingDeleteAt: String? = null,
    val stopReason: String? = null,
    val stopReasonTimestamp: String? = null,
    val sessionCorrupted: Boolean? = null,
    val harnessVersion: String? = null,
    val metadata: JsonObject? = null
)

@Serializable
data class AgentListParams(val workspaceId: String)

@Serializable
data class AgentListResult(
    val agents: List<Agent>,
    val retiredCount: Long = 0,
    val scopeCounts: JsonObject? = null,
    val delegatedCounts: JsonObject? = null
)

@Serializable
data class AgentResult(val agent: Agent)

@Serializable
data class AgentParams(val agentId: String, val workspaceId: String? = null)

@Serializable
data class AgentStatusData(
    val agentId: String,
    val status: String? = null,
    val isActive: Boolean? = null,
    val stopReason: String? = null,
    val stopReasonTimestamp: String? = null,
    val sessionCorrupted: Boolean? = null
)
