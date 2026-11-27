package dev.intent.app.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.intent.app.R

@Composable
internal fun DisclosureChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = IntentScreen.secondaryText,
    size: Dp = 16.dp
) {
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size).rotate(if (expanded) EXPANDED_ROTATION else 0f)
    )
}

@Composable
internal fun DisclosureLabel(
    label: String,
    expanded: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.semantics(mergeDescendants = true) {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DisclosureChevron(expanded, tint = color, size = 12.dp)
        Text(
            label,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val EXPANDED_ROTATION = 90f
