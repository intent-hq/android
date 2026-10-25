package dev.intent.app.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.core.protocol.models.QueuedMessage

@Composable
internal fun QueuedConversationMessages(state: ConversationSendState, onRetract: (String) -> Unit) {
    val (background, human) = state.queue.partition { ConversationNoise.label(it) != null }
    var expanded by remember { mutableStateOf(false) }
    human.forEach { QueuedMessageRow(it, it.id !in state.retracting, onRetract) }
    if (background.isNotEmpty()) {
        Column {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    "${if (expanded) "▾" else "▸"} ${background.size} queued background updates",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                background.forEach { QueuedMessageRow(it, it.id !in state.retracting, onRetract) }
            }
        }
    }
}

@Composable
private fun QueuedMessageRow(
    message: QueuedMessage,
    enabled: Boolean,
    onRetract: (String) -> Unit
) {
    Row {
        Text("Queued: ${message.content}", Modifier.weight(1f))
        TextButton(onClick = { onRetract(message.id) }, enabled = enabled) { Text("Retract") }
    }
}
