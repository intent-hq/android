package dev.intent.app.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.IntentTextField
import dev.intent.core.protocol.models.QueuedMessage

@Composable
internal fun QueuedConversationMessages(
    state: ConversationSendState,
    onRetract: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onSendNow: (String) -> Unit
) {
    val (background, human) = state.queue.partition { ConversationNoise.label(it) != null }
    var expanded by remember { mutableStateOf(false) }
    if (human.isNotEmpty()) {
        Text("Queued messages · ${human.size}", style = MaterialTheme.typography.labelSmall)
    }
    human.forEach { QueuedMessageRow(it, it.id !in state.retracting, onRetract, onEdit, onSendNow) }
    if (background.isNotEmpty()) {
        Column {
            CompactDisclosure("${background.size} queued background updates", expanded) {
                expanded = !expanded
            }
            if (expanded) {
                background.forEach {
                    QueuedMessageRow(it, it.id !in state.retracting, onRetract, onEdit, onSendNow)
                }
            }
        }
    }
}

@Composable
private fun QueuedMessageRow(
    message: QueuedMessage,
    enabled: Boolean,
    onRetract: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onSendNow: (String) -> Unit
) {
    var editing by remember(message.id) { mutableStateOf(false) }
    var actions by remember(message.id) { mutableStateOf(false) }
    var text by remember(message.id, message.content) { mutableStateOf(message.content) }
    Column {
        if (editing) {
            IntentTextField(text, { text = it }, label = { Text("Edit queued message") })
            Row {
                TextButton(onClick = {
                    onEdit(message.id, text)
                    editing = false
                }, enabled = enabled && text.isNotBlank()) { Text("Save") }
                TextButton(onClick = { editing = false }) { Text("Cancel edit") }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.content.ifBlank { "Queued message" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    IconButton(onClick = { actions = true }, enabled = enabled) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Queued message actions")
                    }
                    DropdownMenu(expanded = actions, onDismissRequest = { actions = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = {
                            actions = false
                            editing = true
                        }, enabled = enabled)
                        DropdownMenuItem(text = { Text("Send now") }, onClick = {
                            actions = false
                            onSendNow(message.id)
                        }, enabled = enabled)
                        DropdownMenuItem(text = { Text("Retract") }, onClick = {
                            actions = false
                            onRetract(message.id)
                        }, enabled = enabled)
                    }
                }
            }
        }
    }
}
