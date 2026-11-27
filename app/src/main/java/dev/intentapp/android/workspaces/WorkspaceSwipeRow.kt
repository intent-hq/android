package dev.intentapp.android.workspaces

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.models.Workspace
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing
import kotlin.math.roundToInt

/** Row horizontal gestures consume movement before a surrounding drawer can claim it. */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun WorkspaceSwipeRow(
    workspace: Workspace,
    enabled: Boolean,
    revealed: Boolean,
    onReveal: (Boolean) -> Unit,
    onClick: () -> Unit,
    onOpenRecent: () -> Unit,
    onAction: (WorkspaceAction) -> Unit,
    content: @Composable () -> Unit
) {
    val width = with(LocalDensity.current) { REVEAL_WIDTH.toPx() }
    var drag by remember(workspace.id, revealed) { mutableFloatStateOf(0f) }
    var menu by remember(workspace.id) { mutableStateOf(false) }
    val select: (WorkspaceAction) -> Unit = {
        menu = false
        onReveal(false)
        onAction(it)
    }
    val offset = ((if (revealed) -width else 0f) + drag).coerceIn(-width, width)
    val progress = (-offset / width).coerceIn(0f, 1f)
    // iOS WorkspaceSwipeRow: the row slides to the screen edge; icon actions sit on the page margin.
    Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).clipToBounds()) {
        if (progress > 0f) {
            Row(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = IntentSpacing.page)
                    .graphicsLayer {
                        scaleX = progress
                        scaleY = progress
                        transformOrigin = TransformOrigin(1f, CENTER)
                    },
                horizontalArrangement = Arrangement.spacedBy(ACTION_SPACING)
            ) {
                SwipeActionButton(WorkspaceAction.Archive, enabled && revealed) { select(it) }
                SwipeActionButton(WorkspaceAction.Delete, enabled && revealed) { select(it) }
            }
        }
        if (drag > 0) {
            Text(
                "Open recent agent",
                Modifier.align(Alignment.CenterStart).padding(start = IntentSpacing.page)
            )
        }
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .testTag("workspace.row.${workspace.id}")
                .pointerInput(workspace.id, enabled, revealed) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                !enabled -> Unit
                                revealed -> onReveal(drag <= width * REVEAL_THRESHOLD)
                                drag > width / 2 -> {
                                    onReveal(false)
                                    onOpenRecent()
                                }
                                drag < -width * REVEAL_THRESHOLD -> onReveal(true)
                                drag > width * REVEAL_THRESHOLD -> onReveal(false)
                            }
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            if (enabled) drag = (drag + amount).coerceIn(-width, width)
                        }
                    )
                }.combinedClickable(
                    enabled = enabled,
                    onClick = { if (revealed) onReveal(false) else onClick() },
                    onLongClickLabel = "Workspace actions",
                    onLongClick = { menu = true }
                ).semantics(mergeDescendants = true) {
                    if (enabled) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Open most recent agent") {
                                    onOpenRecent()
                                    true
                                },
                                CustomAccessibilityAction("Archive workspace") {
                                    select(WorkspaceAction.Archive)
                                    true
                                },
                                CustomAccessibilityAction("Delete workspace") {
                                    select(WorkspaceAction.Delete)
                                    true
                                }
                            )
                    }
                }
        ) {
            Box(Modifier.padding(horizontal = IntentSpacing.page)) { content() }
        }
        HorizontalDivider(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = IntentSpacing.page)
                .graphicsLayer { alpha = 1f - minOf(progress / DIVIDER_FADE, 1f) },
            color = MaterialTheme.colorScheme.outlineVariant
        )
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Open most recent agent") },
                onClick = {
                    menu = false
                    onReveal(false)
                    onOpenRecent()
                },
                enabled = enabled
            )
            listOf(WorkspaceAction.Archive, WorkspaceAction.Delete).forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${action.label} workspace",
                            color =
                            if (action ==
                                WorkspaceAction.Delete
                            ) {
                                IntentScreen.error
                            } else {
                                IntentScreen.text
                            }
                        )
                    },
                    onClick = { select(action) },
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
private fun SwipeActionButton(
    action: WorkspaceAction,
    enabled: Boolean,
    onClick: (WorkspaceAction) -> Unit
) {
    val delete = action == WorkspaceAction.Delete
    Box(
        Modifier
            .size(ACTION_SIZE)
            .clip(RoundedCornerShape(ACTION_RADIUS))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, role = Role.Button) { onClick(action) }
            .semantics { contentDescription = "${action.label} workspace" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(if (delete) R.drawable.ic_delete else R.drawable.ic_archive),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (delete) IntentScreen.error else IntentScreen.text
        )
    }
}

// iOS Constants.WorkspaceList swipe geometry: 48dp buttons 8dp apart, 32dp from the row.
private val ACTION_SIZE = 48.dp
private val ACTION_RADIUS = 20.dp
private val ACTION_SPACING = 8.dp
private val REVEAL_WIDTH = 32.dp + ACTION_SIZE * 2 + ACTION_SPACING + 24.dp
private const val DIVIDER_FADE = 0.15f

private const val REVEAL_THRESHOLD = 0.333f
private const val CENTER = 0.5f
