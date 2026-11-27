package dev.intentapp.android.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Apply inside the screen's scroll container; Scaffold owns system-bar insets. */
object IntentScreen {
    val pageInsets =
        PaddingValues(horizontal = IntentSpacing.page, vertical = IntentSpacing.content)
    val sectionSpacing = IntentSpacing.page
    val itemSpacing = IntentSpacing.row
    val sectionInsets = IntentSpacing.page

    val canvas: Color @Composable get() = MaterialTheme.colorScheme.background
    val surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val text: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val secondaryText: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val error: Color @Composable get() = MaterialTheme.colorScheme.error
    val warningSurface: Color
        @Composable get() = if (LocalIntentDarkTheme.current) {
            IntentColors.agentAvatarSurfaceAttentionDark
        } else {
            IntentColors.agentAvatarSurfaceAttentionLight
        }
    val onWarningSurface: Color @Composable get() = IntentColors.agentAvatarGlyphLight
}

fun Modifier.intentPageInsets(): Modifier = padding(IntentScreen.pageInsets)

/** Neutral content surface. Accent colors identify controls, not ordinary body text. */
@Composable
fun IntentSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    IntentCard(modifier) { SectionContent(title, content) }
}

/** Clickable sections share the same filled surface as static sections. */
@Composable
fun IntentSection(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = IntentScreen.surface,
        contentColor = IntentScreen.text
    ) { SectionContent(title, content) }
}

@Composable
private fun IntentCard(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = IntentScreen.surface,
        contentColor = IntentScreen.text,
        content = content
    )
}

@Composable
private fun SectionContent(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(IntentScreen.sectionInsets),
        verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
fun IntentWarning(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = IntentScreen.warningSurface,
        contentColor = IntentScreen.onWarningSurface
    ) {
        Column(
            Modifier.padding(IntentScreen.sectionInsets),
            verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing),
            content = content
        )
    }
}

/** Extends an item past its list's horizontal content padding so it can pad and swipe itself. */
fun Modifier.horizontalBleed(inset: Dp): Modifier = layout { measurable, constraints ->
    val extra = (inset * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) {
                constraints.maxWidth + extra
            } else {
                constraints.maxWidth
            }
        )
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Shortens a list item's reported height so the next item sits [amount] closer. */
fun Modifier.pullNextItem(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height - amount.roundToPx()).coerceAtLeast(0)
    layout(placeable.width, height) { placeable.place(0, 0) }
}

/** iOS WorkspaceDetailStyle: 24dp from a section heading to its first 36dp-spaced row. */
val SectionHeadingPull = 12.dp
