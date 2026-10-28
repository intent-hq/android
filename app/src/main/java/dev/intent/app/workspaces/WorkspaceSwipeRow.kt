package dev.intent.app.workspaces

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.core.protocol.models.Workspace
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
    val width = with(LocalDensity.current) { ACTION_WIDTH.dp.toPx() }
    var drag by remember(workspace.id, revealed) { mutableFloatStateOf(0f) }
    var menu by remember(workspace.id) { mutableStateOf(false) }
    val select: (WorkspaceAction) -> Unit = {
        menu = false
        onReveal(false)
        onAction(it)
    }
    Box(Modifier.clipToBounds()) {
        if (revealed || drag < 0) {
            Row(Modifier.align(Alignment.CenterEnd).width(ACTION_WIDTH.dp)) {
                listOf(WorkspaceAction.Archive, WorkspaceAction.Delete).forEach { action ->
                    TextButton(onClick = {
                        select(action)
                    }, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Text(action.label)
                    }
                }
            }
        }
        if (drag > 0) Text("Open recent agent", Modifier.align(Alignment.CenterStart))
        Box(
            Modifier.offset { IntOffset(((if (revealed) -width else 0f) + drag).roundToInt(), 0) }
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
                }
                .combinedClickable(
                    enabled = enabled,
                    onClick = { if (revealed) onReveal(false) else onClick() },
                    onLongClickLabel = "Workspace actions",
                    onLongClick = { menu = true }
                )
                .semantics {
                    if (enabled) {
                        customActions = listOf(
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
        ) { content() }
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
                    text = { Text("${action.label} workspace") },
                    onClick = { select(action) },
                    enabled = enabled
                )
            }
        }
    }
}

private const val ACTION_WIDTH = 192

private const val REVEAL_THRESHOLD = 0.333f
