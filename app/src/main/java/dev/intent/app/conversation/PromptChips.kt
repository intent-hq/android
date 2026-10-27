package dev.intent.app.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

@Composable
internal fun PromptChips(prompts: List<String>, enabled: Boolean, onSend: (String) -> Unit) {
    prompts.forEach { prompt ->
        SuggestionChip(
            onClick = { onSend(prompt) },
            enabled = enabled,
            label = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(PromptLinks.visible(prompt))
                    Text("→", Modifier.clearAndSetSemantics {})
                }
            }
        )
    }
}
