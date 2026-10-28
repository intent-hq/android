package dev.intent.app.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.workspaces.CreationChoice
import dev.intent.app.workspaces.WorkspaceOperations
import dev.intent.core.protocol.models.Agent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val scroll = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = { if (!selection.busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { !selection.busy }
        )
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(
                max = (
                    LocalConfiguration.current.screenHeightDp *
                        0.85f
                    ).dp
            )
                .padding(horizontal = IntentScreen.sectionInsets),
            verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
        ) {
            Text("Model settings", style = MaterialTheme.typography.titleLarge)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(scroll),
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
                if (selection.error !=
                    null
                ) {
                    TextButton(enabled = !selection.busy && !selection.loading, onClick = {
                        scope.launch { selection.load() }
                    }) { Text("Retry providers") }
                }
                val models = selection.models.map { CreationChoice(it.id, it.name) }.toMutableList()
                if (selection.model != "default" && models.none { it.id == selection.model }) {
                    models.add(CreationChoice(selection.model, selection.model))
                }
                ModelChoices(
                    "Model",
                    selection.model,
                    listOf(CreationChoice("default", "Provider default")) + models,
                    !selection.busy && !selection.loadingModels,
                    selection::selectModel,
                    compact = true
                )
                if (selection.loadingModels) Text("Loading models…")
                selection.modelError?.let { Text(it, color = IntentScreen.error) }
                if (selection.modelError != null) {
                    TextButton(
                        enabled =
                        !selection.busy &&
                            !selection.loadingModels &&
                            selection.provider.isNotBlank(),
                        onClick = {
                            scope.launch { selection.loadModels() }
                        }
                    ) { Text("Retry models") }
                }
                ModelChoices(
                    "Reasoning",
                    selection.reasoning.orEmpty(),
                    listOf(CreationChoice("", "Auto")) +
                        selection.efforts.map { CreationChoice(it, it) },
                    !selection.busy && !selection.loadingModels,
                    onSelect = { selection.reasoning = it.takeIf(String::isNotBlank) }
                )
                selection.error?.let { Text(it, color = IntentScreen.error) }
            }
            if (scroll.canScrollForward) {
                Text(
                    "Scroll for more settings ↓",
                    style = MaterialTheme.typography.labelSmall,
                    color = IntentScreen.secondaryText
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !selection.busy, onClick = onDismiss) { Text("Cancel") }
                TextButton(enabled = selection.canSave, onClick = {
                    scope.launch { if (selection.save()) onDismiss() }
                }) { Text(if (selection.busy) "Saving…" else "Save") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongParameterList") // Choice content, selection, interaction, and compact layout.
@Composable
private fun ModelChoices(
    title: String,
    selected: String,
    choices: List<CreationChoice>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    compact: Boolean = false
) {
    var expanded by remember(title) { mutableStateOf(false) }
    val visible = if (compact && !expanded) choices.filter { it.id == selected } else choices
    IntentSection(title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)) {
            visible.forEach { choice ->
                FilterChip(
                    selected = choice.id == selected,
                    onClick = {
                        onSelect(choice.id)
                        expanded = false
                    },
                    enabled = enabled && choice.selectable,
                    label = { Text(choice.name) }
                )
            }
        }
        if (compact) {
            TextButton(enabled = enabled, onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide models" else "Show models")
            }
        }
    }
}
