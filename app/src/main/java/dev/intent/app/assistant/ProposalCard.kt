package dev.intent.app.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentButton as Button
import dev.intent.app.design.IntentTextButton as TextButton

val LocalProposalStore = staticCompositionLocalOf<ProposalStore?> { null }
val LocalProposalMessage = staticCompositionLocalOf { "" }
val LocalOpenProposalWorkspace = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // Proposal states.
fun ProposalCard(proposal: ActionProposal) {
    val store = LocalProposalStore.current
    val message = LocalProposalMessage.current
    val state = store?.state?.collectAsState()?.value ?: ProposalState()
    var selection by remember(proposal.id, message) { mutableStateOf(proposal.initialSelection) }
    var title by rememberSaveable(proposal.id, message) { mutableStateOf(proposal.creationTitle) }
    var prompt by rememberSaveable(proposal.id, message) { mutableStateOf(proposal.prompt) }
    var dismiss by remember { mutableStateOf(false) }
    val receipt = store?.key(proposal, message).orEmpty()
    val pending = state.pending[proposal.id] == message
    val attempted = receipt in state.attempts
    LaunchedEffect(store, proposal.id, message) { store?.refresh() }
    Card(Modifier.fillMaxWidth().testTag("proposal-${proposal.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(proposal.title, style = MaterialTheme.typography.titleMedium)
            proposal.summary?.let { Text(it) }
            proposal.warnings.forEach { Text(it) }
            proposal.items.forEach { item ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(
                        item.id in selection,
                        { checked ->
                            selection = if (checked) selection + item.id else selection - item.id
                        },
                        enabled =
                        pending &&
                            !state.busy &&
                            !item.disabled &&
                            item.id !in state.completed[receipt].orEmpty()
                    )
                    Column {
                        Text(item.title)
                        if (item.id in state.completed[receipt].orEmpty()) Text("Applied")
                    }
                }
            }
            if (proposal.creation && pending) {
                Text("Repository: ${proposal.repository}")
                OutlinedTextField(
                    title,
                    {
                        title = it
                    },
                    label = { Text("Workspace title") },
                    enabled =
                    !attempted && !state.busy
                )
                OutlinedTextField(
                    prompt,
                    {
                        prompt = it
                    },
                    label = { Text("Instructions") },
                    enabled =
                    !attempted && !state.busy
                )
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    store?.refresh()
                }, enabled = !state.busy) { Text("Refresh proposal") }
            }
            when {
                state.resolutions[proposal.id] != null -> Text(
                    if (state.resolutions[proposal.id] ==
                        "applied"
                    ) {
                        "Applied"
                    } else {
                        "Dismissed"
                    }
                )
                pending -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        dismiss = true
                    }, enabled = !state.busy && !attempted) { Text("Dismiss") }
                    if (proposal.method != null || proposal.creation) {
                        Button(
                            onClick = { store?.apply(proposal, message, selection, title, prompt) },
                            enabled =
                            !state.busy &&
                                (if (proposal.creation) state.canCreate else selection.isNotEmpty())
                        ) {
                            Text(
                                if (state.busy) {
                                    "Applying…"
                                } else if (attempted) {
                                    "Retry"
                                } else {
                                    "Apply"
                                }
                            )
                        }
                    }
                }
                else -> Text("Waiting for confirmation availability…")
            }
            state.created[receipt]?.let { id ->
                val open = LocalOpenProposalWorkspace.current
                TextButton(onClick = { open(id) }) { Text("Open workspace") }
            }
        }
    }
    if (dismiss) {
        AlertDialog(
            onDismissRequest = { dismiss = false },
            title = { Text("Dismiss this proposal?") },
            text = { Text("The agent will be told that you dismissed it.") },
            confirmButton = {
                TextButton(onClick = {
                    dismiss = false
                    store?.dismiss(proposal, message)
                }) { Text("Dismiss proposal") }
            },
            dismissButton = { TextButton(onClick = { dismiss = false }) { Text("Cancel") } }
        )
    }
}
