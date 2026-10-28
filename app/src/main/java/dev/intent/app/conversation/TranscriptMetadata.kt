package dev.intent.app.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.intent.app.agents.LocalConversationAgent
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.core.protocol.models.ConversationMessage

@Composable
internal fun TranscriptMetadata(message: ConversationMessage) {
    var details by remember(message.id) { mutableStateOf(false) }
    val agent = LocalConversationAgent.current?.takeIf {
        message.agentId == null || message.agentId == it.id
    }
    val sent = message.role == "user" &&
        ConversationNoise.label(message) == null &&
        !message.isToolActivity()
    val author = if (message.role == "assistant") agent?.name ?: "Assistant" else message.role
    val label = if (message.role == "assistant" && agent != null) {
        transcriptTimestamp(message.timestamp)
    } else {
        "${if (sent) "Sent" else author} · ${transcriptTimestamp(message.timestamp)}"
    }
    Text(
        label,
        modifier = Modifier.clickable(
            role = Role.Button,
            onClickLabel = "Show message details"
        ) { details = true }.padding(vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (details) {
        AlertDialog(
            onDismissRequest = { details = false },
            title = { Text("Message details") },
            text = { Text("$author\n${message.timestamp.ifBlank { "Time unavailable" }}") },
            confirmButton = { TextButton(onClick = { details = false }) { Text("Close") } }
        )
    }
}
