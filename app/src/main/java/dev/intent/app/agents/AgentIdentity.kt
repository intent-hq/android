package dev.intent.app.agents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.intent.app.design.AgentAvatar
import dev.intent.app.design.AgentAvatarState
import dev.intent.core.protocol.models.Agent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

val LocalConversationAgent = staticCompositionLocalOf<Agent?> { null }

fun Agent.avatarState(): AgentAvatarState = when {
    metadataText("attentionRequestKind") == "blocker" -> AgentAvatarState.AttentionBlocker
    metadataText("attentionRequestKind") == "discussion" -> AgentAvatarState.AttentionDiscussion
    !metadataText("pendingQuestionsMessageId").isNullOrBlank() -> AgentAvatarState.Question
    isWaitingForOtherAgents || waitingForAgentIds.isNotEmpty() -> AgentAvatarState.Waiting
    status in setOf("error", "failed") -> AgentAvatarState.Failed
    status in setOf("complete", "completed") -> AgentAvatarState.Completed
    running() -> AgentAvatarState.Running
    else -> AgentAvatarState.Idle
}

private fun Agent.running(): Boolean =
    isActive || isStreaming || isProcessing || isResponding || turnInFlight || status == "active"

private fun Agent.metadataText(key: String): String? =
    (metadata?.get(key) as? JsonPrimitive)?.contentOrNull

@Composable
fun AgentIdentityAvatar(agent: Agent) {
    AgentAvatar(
        agent.id,
        specialist = (agent.metadata?.get("specialist") as? JsonPrimitive)?.contentOrNull,
        icon = (agent.metadata?.get("icon") as? JsonPrimitive)?.contentOrNull,
        state = agent.avatarState()
    )
}
