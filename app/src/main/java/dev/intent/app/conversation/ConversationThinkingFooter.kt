package dev.intent.app.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentSpacing

@Composable
internal fun ConversationThinkingFooter() {
    Row(
        Modifier.testTag("conversation-thinking").padding(vertical = IntentSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IntentSpacing.small)
    ) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(
            "Thinking...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
