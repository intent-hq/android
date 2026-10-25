package dev.intent.app.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

val checkboxStatuses = listOf("todo", "in-progress", "done")
val taskNoteStatuses = listOf(
    "not_started",
    "waiting",
    "discussion_needed",
    "blocked",
    "in_progress",
    "review_required",
    "complete",
    "cancelled"
)

@Composable
fun TaskStatusMenu(
    status: String,
    choices: List<String>,
    enabled: Boolean,
    onStatus: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            enabled = enabled,
            label = { Text(status.replace('_', ' ').replace('-', ' ')) }
        )
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.replace('_', ' ').replace('-', ' ')) },
                    onClick = {
                        expanded = false
                        onStatus(choice)
                    }
                )
            }
        }
    }
}
