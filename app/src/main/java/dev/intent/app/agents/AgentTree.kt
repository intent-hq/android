package dev.intent.app.agents

import dev.intent.core.protocol.models.Agent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class AgentTreeRow(val agent: Agent, val depth: Int, val childCount: Int)

/** Missing parents become roots. A visited set keeps malformed cycles visible and finite. */
class AgentTree(agents: List<Agent>) {
    private val ordered = agents.distinctBy { it.id }.sortedWith(
        compareByDescending<Agent> { it.isCoordinator() }
            .thenByDescending {
                it.isActive ||
                    it.isResponding ||
                    it.turnInFlight ||
                    it.status == "active"
            }
            .thenByDescending { it.lastActivity ?: it.updatedAt }
            .thenBy { it.id }
    )
    private val ids = ordered.map { it.id }.toSet()
    private val children = ordered.groupBy { it.parentAgentId }

    fun rows(collapsed: Set<String> = emptySet()): List<AgentTreeRow> {
        val result = mutableListOf<AgentTreeRow>()
        val visited = mutableSetOf<String>()
        fun visit(root: Agent) {
            val pending = ArrayDeque<Pair<Agent, Int>>()
            pending.addLast(root to 0)
            while (pending.isNotEmpty()) {
                val (agent, depth) = pending.removeLast()
                if (!visited.add(agent.id)) continue
                val descendants = children[agent.id].orEmpty().filter { it.id != agent.id }
                if (depth >= 0) result.add(AgentTreeRow(agent, depth, descendants.size))
                val nextDepth = if (depth < 0 || agent.id in collapsed) -1 else depth + 1
                descendants.asReversed().forEach { pending.addLast(it to nextDepth) }
            }
        }
        ordered.filter { it.parentAgentId !in ids || it.parentAgentId == it.id }.forEach(::visit)
        ordered.filter { it.id !in visited }.forEach(::visit)
        return result
    }
}

fun Agent.isCoordinator(): Boolean =
    (metadata?.get("specialist") as? JsonPrimitive)?.contentOrNull in
        setOf("spec-writer", "coordinator")
