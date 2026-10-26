package dev.intent.app.agents

import dev.intent.app.workspaces.CreationChoice
import dev.intent.app.workspaces.CreationOptionsStore
import dev.intent.app.workspaces.WorkspaceOperations
import dev.intent.app.workspaces.obj
import dev.intent.app.workspaces.rows
import dev.intent.app.workspaces.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class AgentSpecialist(
    val id: String,
    val name: String,
    val description: String = "",
    val role: String? = null,
    val resolvedModel: String? = null,
    val resolvedProvider: String? = null
) {
    fun defaultLabel(provider: String): String = if (resolvedProvider == provider &&
        resolvedModel != null
    ) {
        "Default ($resolvedModel)"
    } else {
        "Host default"
    }
}

data class AgentModelChoice(
    val id: String,
    val name: String,
    val provider: String,
    val effortLevels: List<String> = emptyList()
)

data class NewAgentOptions(
    val specialists: List<AgentSpecialist>,
    val providers: List<CreationChoice>
) {
    val defaultSpecialist: AgentSpecialist?
        get() = specialists.filter { it.role == "orchestrator" }.minByOrNull { it.id }
            ?: specialists.firstOrNull()
    val defaultProvider: CreationChoice?
        get() = providers.firstOrNull { it.id == "auggie" && it.selectable }
            ?: providers.firstOrNull { it.selectable }
}

data class NewAgentDraft(
    val specialist: String = "",
    val name: String = "",
    val provider: String = "",
    val model: String = "",
    val reasoning: String = ""
) {
    fun params(
        workspaceId: String,
        options: NewAgentOptions,
        models: List<AgentModelChoice>
    ): JsonObject {
        require(options.specialists.any { it.id == specialist }) { "Choose a specialist" }
        require(
            options.providers.any {
                it.id == provider && it.selectable
            }
        ) { "Choose a signed-in provider" }
        val selectedModel = models.firstOrNull { it.id == model && it.provider == provider }
        return buildJsonObject {
            put("workspaceId", workspaceId)
            put("specialistId", specialist)
            if (name.isNotEmpty()) put("name", name)
            if (provider != "auggie") put("provider", provider)
            selectedModel?.let {
                put("model", it.id)
                if (reasoning in it.effortLevels) put("reasoningEffort", reasoning)
            }
        }
    }
}

/** Uses the workspace provider policy without changing the Create workspace form. */
class NewAgentOptionsStore(private val operations: WorkspaceOperations) {
    fun load(): NewAgentOptions = NewAgentOptions(
        specialists = operations.request("specialist.list").rows("specialists")
            .filter { it["hidden"] != JsonPrimitive(true) && it.text("role") != "internal" }
            .map {
                AgentSpecialist(
                    it.string("id"),
                    it.string("name"),
                    it.text("description").orEmpty(),
                    it.text("role"),
                    it.text("resolvedModel"),
                    it.text("resolvedProvider")
                )
            },
        providers = CreationOptionsStore(operations).loadProviders()
    )

    fun models(provider: String): List<AgentModelChoice> = operations.request(
        "models.list",
        buildJsonObject { put("providerId", provider) }
    ).rows("models").filter { it.text("provider") == provider }.map {
        AgentModelChoice(
            it.string("id"),
            it.text("name") ?: it.string("id"),
            provider,
            (it["effortLevels"] as? JsonArray).orEmpty()
                .mapNotNull { level -> (level as? JsonPrimitive)?.contentOrNull }
                .filter(String::isNotBlank).distinct()
        )
    }

    fun create(
        workspaceId: String,
        draft: NewAgentDraft,
        options: NewAgentOptions,
        models: List<AgentModelChoice>
    ): String = operations.request(
        "agent.create",
        draft.params(workspaceId, options, models)
    ).obj("agent").string("id")
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
