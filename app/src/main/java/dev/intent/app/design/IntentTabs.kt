package dev.intent.app.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TrackRadius))
            .background(colors.surface)
            .padding(Inset)
    ) {
        val labelStyle = if (maxWidth < 300.dp) {
            MaterialTheme.typography.labelMedium
        } else {
            MaterialTheme.typography.labelLarge
        }
        val tabWidth = maxWidth / titles.size.coerceAtLeast(1)
        val offset by animateDpAsState(
            tabWidth * selectedIndex.coerceAtLeast(0),
            spring(dampingRatio = 0.85f, stiffness = 500f),
            label = "tabSelection"
        )
        if (selectedIndex in titles.indices) {
            Box(
                Modifier
                    .offset(x = offset)
                    .width(tabWidth)
                    .height(TabHeight)
                    .clip(RoundedCornerShape(PillRadius))
                    .background(colors.onSurface)
            )
        }
        Row(Modifier.fillMaxWidth().height(TabHeight).selectableGroup()) {
            titles.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) }
                        )
                        .padding(horizontal = IntentSpacing.small),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        style = labelStyle,
                        color = if (selected) colors.surface else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private val Inset = 4.dp
private val TabHeight = 48.dp
private val TrackRadius = 20.dp
private val PillRadius = 16.dp
