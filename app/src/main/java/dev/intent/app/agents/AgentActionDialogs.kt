package dev.intent.app.agents

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import dev.intent.app.design.IntentScreen
import dev.intent.core.protocol.models.Agent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// UI action boundary reports RPC failures.
@Suppress("LongMethod", "LongParameterList", "TooGenericExceptionCaught")
@Composable
fun AgentActionDialog(
    action: String,
    agent: Agent,
    controls: AgentControls,
    onChanged: (Agent) -> Unit,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(agent.id) { mutableStateOf(agent.name) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val deleting = action == "Delete agent"
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text(if (deleting) "Delete agent?" else "Rename agent") },
        text = {
            Column {
                if (deleting) {
                    Text("Delete ${agent.name} and its conversation? This cannot be undone.")
                } else {
                    OutlinedTextField(name, {
                        name = it
                    }, label = { Text("Agent name") }, enabled = !busy)
                }
                error?.let { Text(it, color = IntentScreen.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (deleting || name.isNotBlank()), onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        if (deleting) {
                            withContext(Dispatchers.IO) { controls.delete(agent.id) }
                            onDeleted()
                        } else {
                            val updated =
                                withContext(Dispatchers.IO) { controls.rename(agent, name) }
                            onChanged(updated)
                        }
                        onDismiss()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: "The change was not confirmed. Try again."
                    } finally {
                        busy = false
                    }
                }
            }) {
                Text(
                    if (busy) {
                        "Saving…"
                    } else if (deleting) {
                        "Delete"
                    } else {
                        "Save"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
        }
    )
}
