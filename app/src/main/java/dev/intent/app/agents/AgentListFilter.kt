package dev.intent.app.agents

import dev.intent.app.design.AgentAvatarState
import dev.intent.app.lists.retainMatchingBranches
import dev.intent.core.protocol.models.Agent

internal fun filterAgents(agents: List<Agent>, query: String, currentOnly: Boolean): List<Agent> {
    val search = query.trim()
    return retainMatchingBranches(agents, { it.id }, { it.parentAgentId }) {
        val current = it.avatarState() !in setOf(AgentAvatarState.Idle, AgentAvatarState.Completed)
        (!currentOnly || current) && it.name.contains(search, ignoreCase = true)
    }
}
