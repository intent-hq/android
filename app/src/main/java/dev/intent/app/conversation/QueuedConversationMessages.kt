package dev.intent.app.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            Text("Queued: ${message.content}")
            Row {
                TextButton(onClick = { editing = true }, enabled = enabled) { Text("Edit") }
                TextButton(onClick = {
                    onSendNow(message.id)
                }, enabled = enabled) { Text("Send now") }
                TextButton(onClick = {
                    onRetract(message.id)
                }, enabled = enabled) { Text("Retract") }
            }
        }
    }
}
