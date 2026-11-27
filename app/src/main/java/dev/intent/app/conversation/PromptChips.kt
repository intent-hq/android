package dev.intent.app.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.intent.app.R
import dev.intent.app.design.IntentSpacing

@Composable
internal fun PromptChips(
    prompts: List<String>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
    ) {
        prompts.forEach { prompt ->
            OutlinedButton(
                onClick = { onSend(prompt) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = CHIP_MIN_HEIGHT),
                shape = RoundedCornerShape(CHIP_RADIUS),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(
                    horizontal = CHIP_HORIZONTAL_PADDING,
                    vertical = CHIP_VERTICAL_PADDING
                )
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CHIP_ICON_SPACING)
                ) {
                    Text(
                        PromptLinks.visible(prompt),
                        Modifier.weight(1f).alignByBaseline(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Icon(
                        painterResource(R.drawable.ic_arrow_right),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterVertically).size(16.dp)
                    )
                }
            }
        }
    }
}

// iOS SuggestedPrompt: 20dp label inset, 12dp vertical padding, 56dp minimum height.
private val CHIP_MIN_HEIGHT = 56.dp
private val CHIP_RADIUS = 20.dp
private val CHIP_HORIZONTAL_PADDING = 20.dp
private val CHIP_VERTICAL_PADDING = 12.dp
private val CHIP_ICON_SPACING = 12.dp
