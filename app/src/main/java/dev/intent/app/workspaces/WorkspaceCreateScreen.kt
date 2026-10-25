package dev.intent.app.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("LongMethod")
@Composable
fun WorkspaceCreateScreen(
    store: CreationOptionsStore,
    onBusy: (Boolean) -> Unit,
    onCreated: (String) -> Unit
) {
    var draft by remember(store) { mutableStateOf(WorkspaceDraft("")) }
    var options by remember(store) { mutableStateOf<CreationOptions?>(null) }
    var models by remember(store) { mutableStateOf(emptyList<CreationChoice>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(store) {
        runCatching { withContext(Dispatchers.IO) { store.load() } }
            .onSuccess { options = it }.onFailure {
                error =
                    "Creation choices could not load. Reopen this screen to retry."
            }
    }
    LaunchedEffect(draft.provider) {
        models = emptyList()
        if (draft.provider.isNotBlank()) {
            runCatching { withContext(Dispatchers.IO) { store.models(draft.provider) } }
                .onSuccess { models = it }.onFailure {
                    error =
                        "Models could not load. You can use the host default."
                }
        }
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).intentPageInsets(),
        verticalArrangement = Arrangement.spacedBy(IntentScreen.sectionSpacing)
    ) {
        Text(
            "Workspace creation is under development.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentScreen.secondaryText
        )
        IntentSection("Workspace") {
            OutlinedTextField(draft.title, {
                draft = draft.copy(title = it)
            }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, enabled = !busy)
            OutlinedTextField(draft.repositoryPath, {
                draft = draft.copy(repositoryPath = it)
            }, modifier = Modifier.fillMaxWidth(), label = {
                Text("Repository path on host (optional)")
            }, enabled = !busy)
            OutlinedTextField(draft.baseRef, {
                draft = draft.copy(baseRef = it)
            }, modifier = Modifier.fillMaxWidth(), label = {
                Text("Base branch (optional)")
            }, enabled = !busy)
        }
        IntentSection("Initial prompt") {
            OutlinedTextField(draft.prompt, {
                draft = draft.copy(prompt = it)
            }, modifier = Modifier.fillMaxWidth(), label = {
                Text("Prompt (optional)")
            }, enabled = !busy)
        }
        CreationChoices("Provider", draft.provider, options?.providers.orEmpty(), !busy) {
            draft = draft.copy(provider = it, model = "")
        }
        CreationChoices("Model", draft.model, models, !busy) { draft = draft.copy(model = it) }
        CreationChoices("Specialist", draft.specialist, options?.specialists.orEmpty(), !busy) {
            draft = draft.copy(specialist = it)
        }
        error?.let { Text(it, color = IntentScreen.error) }
        Button(onClick = {
            busy = true
            onBusy(true)
            error = null
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { store.create(draft) } }
                    .onSuccess(onCreated)
                    .onFailure {
                        error =
                            "Creation was not confirmed. Check the workspace list before retrying."
                    }
                busy = false
                onBusy(false)
            }
        }, enabled = !busy && draft.title.isNotBlank()) {
            Text(if (busy) "Creating…" else "Create workspace")
        }
    }
}

@Composable
private fun CreationChoices(
    title: String,
    selected: String,
    choices: List<CreationChoice>,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    IntentSection(title) {
        (listOf(CreationChoice("", "Host default")) + choices).forEach { choice ->
            FilterChip(
                selected = selected == choice.id,
                onClick = { onSelect(choice.id) },
                label = { Text(choice.name) },
                enabled = enabled
            )
        }
    }
}
