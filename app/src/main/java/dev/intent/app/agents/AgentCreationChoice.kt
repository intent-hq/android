package dev.intent.app.agents

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun AgentCreationChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilterChip(selected, onClick, {
        Text(label)
    }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
}
