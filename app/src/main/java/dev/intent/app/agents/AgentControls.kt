package dev.intent.app.agents

import dev.intent.app.workspaces.WorkspaceOperations
import dev.intent.core.protocol.models.Agent
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Same mutations as the iOS conversation actions. Call on a worker dispatcher. */
class AgentControls(private val operations: WorkspaceOperations, private val workspaceId: String) {
    fun rename(agent: Agent, name: String): Agent {
        require(name.isNotBlank()) { "Enter an agent name" }
        operations.request("agent.rename", params(agent.id) { put("name", name.trim()) })
        return agent.copy(name = name.trim())
    }

    fun delete(agentId: String) {
        operations.request("agent.delete", params(agentId))
    }

    fun mute(agent: Agent): Agent {
        update(agent.id, buildJsonObject { put("notificationsMuted", !agent.notificationsMuted) })
        return agent.copy(notificationsMuted = !agent.notificationsMuted)
    }

    fun model(agent: Agent, provider: String, model: String): Agent {
        operations.request(
            "agent.setModel",
            params(agent.id) {
                put("providerId", provider)
                put("modelId", model)
            }
        )
        return agent.copy(provider = provider, model = model)
    }

    fun reasoning(agent: Agent, effort: String?): Agent {
        update(
            agent.id,
            buildJsonObject {
                put("reasoningEffort", effort?.let(::JsonPrimitive) ?: JsonNull)
            }
        )
        return agent.copy(reasoningEffort = effort)
    }

    private fun update(agentId: String, changes: JsonObject) {
        operations.request("agent.update", params(agentId) { put("changes", changes) })
    }

    private fun params(
        agentId: String,
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {
        }
    ) = buildJsonObject {
        put("workspaceId", workspaceId)
        put("agentId", agentId)
        extra()
    }
}
