package dev.intentapp.android.agents

import dev.intent.core.protocol.models.Agent
import dev.intentapp.android.design.AgentAvatarState
import dev.intentapp.android.lists.retainMatchingBranches

internal fun filterAgents(agents: List<Agent>, query: String, currentOnly: Boolean): List<Agent> {
    val search = query.trim()
    return retainMatchingBranches(agents, { it.id }, { it.delegationParent() }) {
        val current = it.avatarState() !in setOf(AgentAvatarState.Idle, AgentAvatarState.Completed)
        (!currentOnly || current) && it.name.contains(search, ignoreCase = true)
    }
}
