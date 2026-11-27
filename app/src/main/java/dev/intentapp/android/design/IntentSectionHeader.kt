package dev.intentapp.android.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.intentapp.android.R

/** Uppercase eyebrow heading that expands and collapses the section below it. */
@Composable
fun IntentCollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    count: Int? = null,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(
        if (expanded) EXPANDED_ROTATION else 0f,
        label = "sectionChevron"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onToggle
            )
            .semantics {
                heading()
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (if (count == null) title else "$title ($count)").uppercase(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.24.sp),
            color = IntentScreen.secondaryText
        )
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = IntentScreen.secondaryText,
            modifier = Modifier.size(16.dp).rotate(rotation)
        )
    }
}

private const val EXPANDED_ROTATION = 90f
