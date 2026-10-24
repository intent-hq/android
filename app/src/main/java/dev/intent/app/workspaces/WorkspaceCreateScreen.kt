package dev.intent.app.workspaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Create workspace — under development")
        OutlinedTextField(draft.title, {
            draft = draft.copy(title = it)
        }, label = { Text("Title") }, enabled = !busy)
        OutlinedTextField(draft.repositoryPath, {
            draft = draft.copy(repositoryPath = it)
        }, label = { Text("Repository path on host (optional)") }, enabled = !busy)
        OutlinedTextField(draft.baseRef, {
            draft = draft.copy(baseRef = it)
        }, label = { Text("Base branch (optional)") }, enabled = !busy)
        OutlinedTextField(draft.prompt, {
            draft = draft.copy(prompt = it)
        }, label = { Text("Initial prompt (optional)") }, enabled = !busy)
        Text("Provider: ${draft.provider.ifBlank { "Host default" }}")
        TextButton(onClick = {
            draft = draft.copy(provider = "", model = "")
        }, enabled = !busy) { Text("Use host default") }
        options?.providers?.forEach { choice ->
            TextButton(onClick = {
                draft = draft.copy(provider = choice.id, model = "")
            }, enabled = !busy) { Text(choice.name) }
        }
        Text("Model: ${draft.model.ifBlank { "Host default" }}")
        models.forEach { choice ->
            TextButton(onClick = {
                draft = draft.copy(model = choice.id)
            }, enabled = !busy) { Text(choice.name) }
        }
        Text("Specialist: ${draft.specialist.ifBlank { "Host default" }}")
        options?.specialists?.forEach { choice ->
            TextButton(onClick = {
                draft = draft.copy(specialist = choice.id)
            }, enabled = !busy) { Text(choice.name) }
        }
        error?.let { Text(it) }
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
