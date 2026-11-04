package dev.intent.app.agents

import dev.intent.app.design.AgentAvatarState
import dev.intent.core.protocol.models.Agent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal enum class AgentGroup(val title: String) {
    FOREGROUND("Agents"),
    BACKGROUND("Background agents"),
    ORPHANED("Orphaned delegated agents"),
    RETIRED("Retired agents")
}

internal data class AgentGroupSection(val group: AgentGroup, val agents: List<Agent>) {
    val running: Int get() = agents.count { it.avatarState() == AgentAvatarState.Running }
}

internal fun Agent.delegationParent(): String? =
    parentAgentId?.takeIf(String::isNotBlank)
        ?: createdByAgentId?.takeIf(String::isNotBlank)
        ?: (metadata?.get("createdByAgentId") as? JsonPrimitive)?.contentOrNull
            ?.takeIf(String::isNotBlank)

internal fun Agent.isRetired(): Boolean = retiredAt != null || status == "retired"

/** Classify complete trees before search so filtering never invents an orphan. */
internal fun agentGroups(agents: List<Agent>): List<AgentGroupSection> {
    val groups = AgentGroup.entries.associateWith { mutableListOf<Agent>() }
    val active = agents.filterNot { it.isRetired() }
    var group = AgentGroup.FOREGROUND
    AgentTree(active).rows().forEach { row ->
        if (row.depth == 0) {
            group = when {
                row.agent.delegationParent() != null -> AgentGroup.ORPHANED
                (row.agent.metadata?.get("isBackground") as? JsonPrimitive)
                    ?.contentOrNull == "true" -> AgentGroup.BACKGROUND
                else -> AgentGroup.FOREGROUND
            }
        }
        groups.getValue(group).add(row.agent)
    }
    groups.getValue(AgentGroup.RETIRED).addAll(agents.filter { it.isRetired() })
    return groups.map { (key, rows) -> AgentGroupSection(key, rows) }.filter { it.agents.isNotEmpty() }
}
