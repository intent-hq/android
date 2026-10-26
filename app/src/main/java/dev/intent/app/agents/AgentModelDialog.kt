package dev.intent.app.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import dev.intent.app.design.IntentScreen
import dev.intent.app.workspaces.CreationChoice
import dev.intent.app.workspaces.WorkspaceOperations
import dev.intent.core.protocol.models.Agent
import kotlinx.coroutines.launch

@Suppress("LongMethod", "CyclomaticComplexMethod") // Declarative loading and selection states.
@Composable
fun AgentModelDialog(
    agent: Agent,
    operations: WorkspaceOperations,
    controls: AgentControls,
    onChanged: (Agent) -> Unit,
    onDismiss: () -> Unit
) {
    val selection =
        remember(agent.id) { AgentModelSelection(agent, operations, controls, onChanged) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selection) { selection.load() }
    LaunchedEffect(selection.provider) { selection.loadModels() }
    AlertDialog(
        onDismissRequest = { if (!selection.busy) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !selection.busy,
            dismissOnClickOutside = !selection.busy
        ),
        title = { Text("Model settings") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
            ) {
                if (selection.loading) Text("Loading providers…")
                ModelChoices(
                    "Provider",
                    selection.provider,
                    selection.providers,
                    !selection.busy,
                    selection::selectProvider
                )
                TextButton(enabled = !selection.busy && !selection.loading, onClick = {
                    scope.launch { selection.load() }
                }) { Text("Retry providers") }
                val models = selection.models.map { CreationChoice(it.id, it.name) }.toMutableList()
                if (selection.model != "default" && models.none { it.id == selection.model }) {
                    models.add(CreationChoice(selection.model, selection.model))
                }
                ModelChoices(
                    "Model",
                    selection.model,
                    listOf(CreationChoice("default", "Provider default")) + models,
                    !selection.busy && !selection.loadingModels,
                    selection::selectModel
                )
                if (selection.loadingModels) Text("Loading models…")
                selection.modelError?.let { Text(it, color = IntentScreen.error) }
                TextButton(
                    enabled =
                    !selection.busy && !selection.loadingModels && selection.provider.isNotBlank(),
                    onClick = {
                        scope.launch { selection.loadModels() }
                    }
                ) { Text("Retry models") }
                ModelChoices(
                    "Reasoning",
                    selection.reasoning.orEmpty(),
                    listOf(CreationChoice("", "Auto")) +
                        selection.efforts.map { CreationChoice(it, it) },
                    !selection.busy && !selection.loadingModels
                ) { selection.reasoning = it.takeIf(String::isNotBlank) }
                selection.error?.let { Text(it, color = IntentScreen.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = selection.canSave, onClick = {
                scope.launch { if (selection.save()) onDismiss() }
            }) { Text(if (selection.busy) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(enabled = !selection.busy, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelChoices(
    title: String,
    selected: String,
    choices: List<CreationChoice>,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Text(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)) {
        choices.forEach { choice ->
            FilterChip(
                selected = choice.id == selected,
                onClick = { onSelect(choice.id) },
                enabled = enabled && choice.selectable,
                label = { Text(choice.name) }
            )
        }
    }
}
