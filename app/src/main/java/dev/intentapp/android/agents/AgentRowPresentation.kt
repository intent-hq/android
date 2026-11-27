package dev.intentapp.android.agents

import dev.intent.core.protocol.models.Agent
import dev.intentapp.android.design.AgentAvatarState
import dev.intentapp.android.workspaces.formatRelativeTime
import java.time.Instant

internal fun Agent.rowStatus(): String = when (val state = avatarState()) {
    AgentAvatarState.Waiting -> "Waiting for agents"
    else -> state.label
}

internal fun Agent.rowPreview(): String? = (
    if (lastMessageRole == "user") lastUserMessage else digest ?: lastAgentResponse
    )?.let(::agentPreviewText)?.takeIf { it.isNotEmpty() }

internal fun agentRelativeTime(agent: Agent, now: Instant = Instant.now()): String? =
    listOf(agent.lastActivity, agent.updatedAt, agent.createdAt)
        .firstNotNullOfOrNull { raw ->
            raw?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?.takeIf { it > Instant.EPOCH }
        }
        ?.let { formatRelativeTime(it, now) }

internal fun Agent.muteActionLabel(): String =
    if (notificationsMuted) "Unmute notifications" else "Mute notifications"
