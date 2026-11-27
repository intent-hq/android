package dev.intentapp.android.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.models.ContentBlock
import dev.intentapp.android.design.DisclosureLabel
import dev.intentapp.android.design.IntentTextButton as TextButton

@Composable
internal fun TranscriptSectionContent(
    section: TranscriptSection,
    last: Boolean,
    hydrating: Set<String>,
    onHydrate: (ContentBlock) -> Unit
) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(section.id) {
        mutableStateOf(last)
    }
    Column {
        section.name?.let { name ->
            TextButton(onClick = { expanded = !expanded }) {
                DisclosureLabel(
                    name,
                    expanded,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (section.name == null || expanded) {
            Column(Modifier.padding(start = if (section.name == null) 0.dp else 12.dp)) {
                TranscriptSectionBlocks(section.blocks, hydrating, onHydrate)
            }
        }
    }
}
