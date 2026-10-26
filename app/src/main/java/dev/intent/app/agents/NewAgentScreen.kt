package dev.intent.app.agents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.intentPageInsets
import dev.intent.app.navigation.CompactAppBar
import dev.intent.app.navigation.LocalChromeActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class NewAgentForm {
    var draft by mutableStateOf(NewAgentDraft())
    var nameEdited by mutableStateOf(false)
    var providerStep by mutableStateOf(false)
    var options by mutableStateOf<NewAgentOptions?>(null)
    var models by mutableStateOf(emptyList<AgentModelChoice>())
    var loading by mutableStateOf(true)
    var loadingModels by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var modelError by mutableStateOf<String?>(null)
    var retry by mutableStateOf(0)
    var modelRetry by mutableStateOf(0)

    // Failed mutations stay visible and never retry automatically.
    @Suppress("TooGenericExceptionCaught")
    suspend fun create(
        store: NewAgentOptionsStore,
        workspaceId: String,
        onCreated: (String) -> Unit
    ) {
        if (busy) return
        busy = true
        error = null
        try {
            val id = withContext(Dispatchers.IO) {
                store.create(workspaceId, draft, requireNotNull(options), models)
            }
            onCreated(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Creation was not confirmed. Check the agent list before retrying."
        } finally {
            busy = false
        }
    }

    fun selectSpecialist(id: String) {
        val specialist = options?.specialists?.firstOrNull { it.id == id } ?: return
        draft = draft.copy(specialist = id, name = if (nameEdited) draft.name else specialist.name)
    }

    fun selectProvider(id: String) {
        if (id == draft.provider) return
        models = emptyList()
        loadingModels = true
        draft = draft.copy(provider = id, model = "", reasoning = "")
    }
}

// One form owns its single creation request.
@Suppress("LongMethod")
@Composable
fun NewAgentScreen(
    store: NewAgentOptionsStore,
    workspaceId: String,
    onBack: () -> Unit,
    onCreated: (String) -> Unit
) {
    val form = remember(store, workspaceId) { NewAgentForm() }
    val scope = rememberCoroutineScope()
    LoadNewAgentOptions(store, form)
    val back = {
        if (!form.busy) {
            if (form.providerStep) form.providerStep = false else onBack()
        }
    }
    BackHandler { back() }
    CompositionLocalProvider(LocalChromeActions provides emptyList()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            CompactAppBar(
                if (form.providerStep) "Provider & Model" else "New agent",
                if (form.busy) null else back
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).intentPageInsets(),
                verticalArrangement = Arrangement.spacedBy(IntentScreen.sectionSpacing)
            ) {
                when {
                    form.loading -> CircularProgressIndicator()
                    form.options == null -> TextButton(onClick = {
                        form.retry++
                    }) { Text("Retry choices") }
                    form.providerStep -> ProviderStep(form)
                    else -> SpecialistStep(form)
                }
            }
            Column(Modifier.fillMaxWidth().intentPageInsets()) {
                form.error?.let { Text(it, color = IntentScreen.error) }
                Button(
                    onClick = {
                        if (!form.providerStep) {
                            form.providerStep = true
                        } else {
                            scope.launch { form.create(store, workspaceId, onCreated) }
                        }
                    },
                    enabled = canContinue(form),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (form.busy) {
                            "Creating…"
                        } else if (form.providerStep) {
                            "Create agent"
                        } else {
                            "Next"
                        }
                    )
                }
            }
        }
    }
}

private fun canContinue(form: NewAgentForm): Boolean = !form.busy &&
    !form.loading &&
    form.options?.specialists?.any { it.id == form.draft.specialist } == true &&
    (
        !form.providerStep ||
            (
                !form.loadingModels &&
                    form.options?.providers?.any {
                        it.id == form.draft.provider && it.selectable
                    } == true
                )
        )

@Suppress("TooGenericExceptionCaught") // Show recovery guidance for failed host reads.
@Composable
private fun LoadNewAgentOptions(store: NewAgentOptionsStore, form: NewAgentForm) {
    LaunchedEffect(store, form.retry) {
        form.loading = true
        form.options = null
        form.models = emptyList()
        form.error = null
        try {
            val options = withContext(Dispatchers.IO) { store.load() }
            form.options = options
            val specialist =
                options.specialists.firstOrNull { it.id == form.draft.specialist }
                    ?: options.defaultSpecialist
            specialist?.let { form.selectSpecialist(it.id) }
            val provider =
                options.providers.firstOrNull { it.id == form.draft.provider && it.selectable }
                    ?: options.defaultProvider
            form.draft =
                form.draft.copy(provider = provider?.id.orEmpty(), model = "", reasoning = "")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            form.error =
                "Choices could not load from this host. Retry or check the host connection."
        } finally {
            form.loading = false
        }
    }
    LaunchedEffect(store, form.draft.provider, form.options, form.modelRetry) {
        form.models = emptyList()
        form.modelError = null
        val provider = form.draft.provider
        if (form.options == null || provider.isEmpty()) return@LaunchedEffect
        form.loadingModels = true
        try {
            form.models = withContext(Dispatchers.IO) { store.models(provider) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            form.modelError = "Models could not load. Use the host default or retry."
        } finally {
            form.loadingModels = false
        }
    }
}

@Composable
private fun SpecialistStep(form: NewAgentForm) {
    IntentSection("Name") {
        OutlinedTextField(form.draft.name, {
            form.nameEdited = true
            form.draft = form.draft.copy(name = it)
        }, label = { Text("Agent name") }, modifier = Modifier.fillMaxWidth())
    }
    IntentSection("Specialist") {
        if (form.options?.specialists.isNullOrEmpty()) {
            Text(
                "No specialists are available on this host."
            )
        }
        form.options?.specialists?.forEach { specialist ->
            AgentCreationChoice(specialist.name, specialist.id == form.draft.specialist, true) {
                form.selectSpecialist(specialist.id)
            }
            if (specialist.id == form.draft.specialist &&
                specialist.description.isNotEmpty()
            ) {
                Text(specialist.description)
            }
        }
    }
}

@Composable
private fun ProviderStep(form: NewAgentForm) {
    IntentSection("Provider") {
        if (form.options?.providers?.none { it.selectable } != false) {
            Text("Enable and sign in to a provider in the Intent desktop app, then retry.")
        }
        form.options?.providers?.forEach { provider ->
            AgentCreationChoice(
                provider.name,
                provider.id == form.draft.provider,
                !form.busy && provider.selectable
            ) {
                form.selectProvider(provider.id)
            }
        }
        TextButton(onClick = { form.retry++ }, enabled = !form.busy) { Text("Reload providers") }
    }
    if (form.draft.provider.isNotEmpty()) ModelStep(form)
}

@Composable
private fun ModelStep(form: NewAgentForm) {
    val specialist = form.options?.specialists?.firstOrNull { it.id == form.draft.specialist }
    IntentSection("Model") {
        if (form.loadingModels) {
            CircularProgressIndicator()
        } else if (form.models.isEmpty() && form.modelError == null) {
            Text("The host default model will be used.")
        } else {
            AgentCreationChoice(
                specialist?.defaultLabel(form.draft.provider) ?: "Host default",
                form.draft.model.isEmpty(),
                !form.busy
            ) {
                form.draft = form.draft.copy(model = "", reasoning = "")
            }
            form.models.forEach { model ->
                AgentCreationChoice(model.name, model.id == form.draft.model, !form.busy) {
                    form.draft = form.draft.copy(model = model.id, reasoning = "")
                }
            }
            form.modelError?.let {
                Text(it, color = IntentScreen.error)
                TextButton(onClick = {
                    form.modelRetry++
                }, enabled = !form.busy) { Text("Retry models") }
            }
        }
    }
    val levels = form.models.firstOrNull { it.id == form.draft.model }?.effortLevels.orEmpty()
    if (levels.isNotEmpty() && !form.loadingModels) {
        IntentSection("Reasoning") {
            (listOf("") + levels).forEach { level ->
                AgentCreationChoice(
                    level.ifEmpty {
                        "Auto"
                    },
                    level == form.draft.reasoning,
                    !form.busy
                ) {
                    form.draft = form.draft.copy(reasoning = level)
                }
            }
        }
    }
}
