package dev.intent.app.agents

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.intent.app.workspaces.CreationChoice
import dev.intent.app.workspaces.CreationOptionsStore
import dev.intent.app.workspaces.WorkspaceOperations
import dev.intent.core.protocol.models.Agent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("TooGenericExceptionCaught") // Present all RPC failures while preserving cancellation.
class AgentModelSelection(
    agent: Agent,
    private val operations: WorkspaceOperations,
    private val controls: AgentControls,
    private val onChanged: (Agent) -> Unit
) {
    private var saved = agent
    var provider by mutableStateOf(agent.provider.orEmpty())
        private set
    var model by mutableStateOf(agent.model ?: "default")
        private set
    var reasoning by mutableStateOf(agent.reasoningEffort)
    var providers by mutableStateOf(emptyList<CreationChoice>())
        private set
    var models by mutableStateOf(emptyList<AgentModelChoice>())
        private set
    var loading by mutableStateOf(true)
        private set
    var loadingModels by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var modelError by mutableStateOf<String?>(null)
        private set
    private var catalogReady by mutableStateOf(false)
    val efforts: List<String> get() = models.firstOrNull { it.id == model }?.effortLevels.orEmpty()
    val canSave: Boolean get() = !loading &&
        !loadingModels &&
        !busy &&
        catalogReady &&
        providers.any { it.id == provider && it.selectable } &&
        (
            provider != saved.provider ||
                model != (saved.model ?: "default") ||
                reasoning != saved.reasoningEffort
            )

    suspend fun load() {
        loading = true
        error = null
        try {
            val loaded =
                withContext(Dispatchers.IO) { CreationOptionsStore(operations).loadProviders() }
            // iOS keeps the agent's current provider selectable even if hidden from creation.
            providers =
                if (saved.provider ==
                    null
                ) {
                    loaded
                } else {
                    loaded.filterNot { it.id == saved.provider } +
                        CreationChoice(saved.provider!!, saved.provider!!)
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "Providers could not load. Retry to continue. ${failure.message.orEmpty()}"
        } finally {
            loading = false
        }
    }

    fun selectProvider(id: String) {
        if (id == provider) return
        provider = id
        model = "default"
        reasoning = null
        models = emptyList()
        catalogReady = false
    }

    fun selectModel(id: String) {
        model = id
        if (reasoning !in efforts) reasoning = null
    }

    suspend fun loadModels() {
        if (provider.isBlank()) return
        val requested = provider
        loadingModels = true
        modelError = null
        try {
            val loaded =
                withContext(Dispatchers.IO) { NewAgentOptionsStore(operations).models(requested) }
            if (requested == provider) models = loaded
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (requested == provider) {
                models = emptyList()
                modelError =
                    "Models could not load. Retry or use Provider default. ${failure.message.orEmpty()}"
            }
        } finally {
            if (requested == provider) {
                loadingModels = false
                catalogReady = true
            }
        }
    }

    suspend fun save(): Boolean {
        if (!canSave) return false
        busy = true
        error = null
        var modelSaved = false
        return try {
            if (provider != saved.provider || model != (saved.model ?: "default")) {
                saved = withContext(Dispatchers.IO) { controls.model(saved, provider, model) }
                modelSaved = true
                onChanged(saved)
            }
            if (reasoning != saved.reasoningEffort) {
                saved = withContext(Dispatchers.IO) { controls.reasoning(saved, reasoning) }
                onChanged(saved)
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = if (modelSaved) {
                "Provider and model were saved, but reasoning was not saved. Try Save again."
            } else {
                failure.message ?: "Model settings were not saved. Try again."
            }
            false
        } finally {
            busy = false
        }
    }
}
