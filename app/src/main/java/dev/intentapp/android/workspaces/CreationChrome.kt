@file:Suppress("MagicNumber") // iOS CreateWorkspaceFormStyle colors.

package dev.intentapp.android.workspaces

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing

/** iOS CreateWorkspaceSelectionPage heading: 28sp title with trailing 48dp actions. */
@Composable
internal fun CreationHeading(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineMedium
        )
        actions()
    }
}

@Composable
internal fun CreationIconAction(
    icon: Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(CONTROL_HEIGHT)
            .clip(RoundedCornerShape(CORNER_RADIUS))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), null, Modifier.size(24.dp))
    }
}

@Composable
internal fun CreationSectionLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.semantics { heading() },
        style = MaterialTheme.typography.labelSmall,
        color = IntentScreen.secondaryText
    )
}

/** iOS selection row: 24dp padding, 80dp minimum, card fill and a green check when selected. */
@Suppress("LongParameterList") // Row content, selection and divider state.
@Composable
internal fun CreationChoiceRow(
    title: String,
    selected: Boolean,
    showsDivider: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_MIN_HEIGHT)
                .clip(RoundedCornerShape(CORNER_RADIUS))
                .background(if (selected) IntentScreen.surface else Color.Transparent)
                .semantics { this.selected = selected }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(IntentSpacing.page),
            horizontalArrangement = Arrangement.spacedBy(IntentSpacing.page),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.invoke()
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentScreen.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis
                    )
                }
            }
            trailing?.invoke()
            if (selected) CreationSelectionMark()
        }
        if (showsDivider) {
            HorizontalDivider(
                Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
internal fun CreationSelectionMark() {
    Box(
        Modifier.size(24.dp).padding(1.5.dp).background(Connected, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(R.drawable.ic_check), null, Modifier.size(14.dp), tint = Color.White)
    }
}

/** iOS bottom controls: Close or Back on the left, the lime Next or Create button on the right. */
@Composable
internal fun CreationBottomBar(back: CreationNext?, backIsClose: Boolean, next: CreationNext?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = IntentSpacing.page,
                end = IntentSpacing.page,
                top = IntentSpacing.medium,
                bottom = IntentSpacing.page
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        back?.let { CreationBackButton(it, backIsClose) }
        Box(Modifier.weight(1f))
        next?.let { CreationPrimaryButton(it) }
    }
}

@Composable
private fun CreationBackButton(action: CreationNext, close: Boolean) {
    Box(
        Modifier
            .size(CONTROL_HEIGHT)
            .clip(RoundedCornerShape(CORNER_RADIUS))
            .background(IntentScreen.surface)
            .clickable(enabled = action.enabled, role = Role.Button, onClick = action.run)
            .semantics { contentDescription = action.label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(if (close) R.drawable.intent_close else R.drawable.ic_arrow_left),
            null,
            Modifier.size(24.dp)
        )
    }
}

@Composable
private fun CreationPrimaryButton(action: CreationNext) {
    val muted = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .heightIn(min = CONTROL_HEIGHT)
            .clip(RoundedCornerShape(CORNER_RADIUS))
            .background(if (action.enabled) Primary else muted.copy(alpha = 0.1f))
            .clickable(enabled = action.enabled, role = Role.Button, onClick = action.run)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            action.label,
            style = MaterialTheme.typography.titleSmall,
            color = if (action.enabled) Ink else muted.copy(alpha = 0.3f)
        )
    }
}

private val CONTROL_HEIGHT = 48.dp
private val CORNER_RADIUS = 20.dp
private val ROW_MIN_HEIGHT = 80.dp
private val Primary = Color(0xFFDDEC61)
private val Ink = Color(0xFF080808)
private val Connected = Color(0xFF6FA356)
