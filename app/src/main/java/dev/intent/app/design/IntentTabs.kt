package dev.intent.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun IntentTabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelStyle = if (maxWidth < 320.dp) {
            MaterialTheme.typography.labelMedium
        } else {
            MaterialTheme.typography.labelLarge
        }
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(colors.surface)
                .padding(IntentSpacing.small).selectableGroup()
        ) {
            titles.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Box(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                        .background(if (selected) colors.inverseSurface else Color.Transparent)
                        .selectable(selected, role = Role.Tab, onClick = { onSelect(index) })
                        .heightIn(min = 48.dp)
                        .padding(horizontal = IntentSpacing.small, vertical = IntentSpacing.row),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        style = labelStyle,
                        color = if (selected) colors.inverseOnSurface else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
