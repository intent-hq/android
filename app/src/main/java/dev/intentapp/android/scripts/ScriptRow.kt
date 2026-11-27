package dev.intentapp.android.scripts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing
import kotlin.math.roundToInt

/** iOS WorkspaceScriptRow status text. */
internal fun scriptStatusLabel(row: WorkspaceScriptRow, state: WorkspaceScriptsState): String =
    when {
        !state.connected -> "Disconnected"
        state.isUpdating(row.id) -> "Updating"
        state.isLaunching(row) -> "Starting"
        else -> when (row.status) {
            "running" -> "Running"
            "starting" -> "Starting"
            "restarting" -> "Restarting"
            "exited" -> if (row.runtime?.error == null) "Finished" else "Failed"
            else -> "Ready"
        }
    }

private data class ScriptMenuAction(
    val label: String,
    val destructive: Boolean,
    val run: () -> Unit
)

/** iOS WorkspaceScriptRow: tap launches; swipe or long press offers Stop and Delete. */
@Composable
internal fun ScriptRow(
    row: WorkspaceScriptRow,
    state: WorkspaceScriptsState,
    revealed: Boolean,
    onReveal: (Boolean) -> Unit,
    actions: WorkspaceScriptsActions
) {
    val canUpdate = state.connected && !state.isUpdating(row.id)
    val width = with(LocalDensity.current) { revealWidth(row).toPx() }
    var drag by remember(row.id, revealed) { mutableFloatStateOf(0f) }
    var menu by remember(row.id) { mutableStateOf(false) }
    val menuActions = scriptMenuActions(row, actions)
    val select: (ScriptMenuAction) -> Unit = {
        menu = false
        onReveal(false)
        it.run()
    }
    val swipeEnabled = canUpdate && row.registered
    val offset = ((if (revealed) -width else 0f) + drag).coerceIn(-width, 0f)
    val progress = if (width > 0f) (-offset / width).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        if (progress > 0f) {
            SwipeActions(row, revealed, progress) { label ->
                menuActions.firstOrNull { it.label == label }?.let(select)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .testTag("workspace.script.${row.id}")
                .scriptSwipe(swipeEnabled, revealed, width, onReveal) { drag = it }
                .combinedClickable(
                    enabled = canUpdate,
                    onClickLabel = if (row.browserUrl != null) "Open preview" else "Run script",
                    onClick = {
                        if (revealed) {
                            onReveal(false)
                        } else if (!state.isLaunching(row)) {
                            actions.launch(row.id)
                        }
                    },
                    onLongClickLabel = "Script actions",
                    onLongClick = { if (menuActions.isNotEmpty()) menu = true }
                )
                .semantics(mergeDescendants = true) {
                    if (canUpdate) {
                        customActions = menuActions.map {
                            CustomAccessibilityAction(it.label) {
                                select(it)
                                true
                            }
                        }
                    }
                }
        ) {
            ScriptRowContent(row, state, progress)
        }
        ScriptRowMenu(menu, menuActions, canUpdate, { menu = false }, select)
    }
}

@Composable
private fun ScriptRowMenu(
    expanded: Boolean,
    actions: List<ScriptMenuAction>,
    enabled: Boolean,
    dismiss: () -> Unit,
    select: (ScriptMenuAction) -> Unit
) {
    DropdownMenu(expanded, onDismissRequest = dismiss) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = {
                    val color = if (action.destructive) IntentScreen.error else IntentScreen.text
                    Text(action.label, color = color)
                },
                enabled = enabled,
                onClick = { select(action) }
            )
        }
    }
}

@Composable
private fun ScriptRowContent(
    row: WorkspaceScriptRow,
    state: WorkspaceScriptsState,
    progress: Float
) {
    val secondary = IntentScreen.secondaryText
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(start = IntentSpacing.page, end = IntentSpacing.page + TRAILING_ICON_INSET),
        horizontalArrangement = Arrangement.spacedBy(AVATAR_SPACING),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(AVATAR_SIZE), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_terminal),
                contentDescription = null,
                tint = secondary,
                modifier = Modifier.size(ICON_SIZE)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TITLE_SPACING)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium, color = IntentScreen.text)
            Text(
                row.command,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.sp
                ),
                color = secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                row.runtime?.error ?: scriptStatusLabel(row, state),
                style = MaterialTheme.typography.bodyMedium,
                color = secondary
            )
        }
        ScriptRowIndicator(row, state, progress)
    }
}

@Composable
private fun ScriptRowIndicator(
    row: WorkspaceScriptRow,
    state: WorkspaceScriptsState,
    progress: Float
) {
    val secondary = IntentScreen.secondaryText
    val busy = state.isUpdating(row.id) || state.isLaunching(row) || row.status in TRANSITIONS
    val icon = if (row.runtime?.detectedUrl == null) R.drawable.ic_play else R.drawable.ic_globe
    Box(Modifier.size(AVATAR_SIZE), contentAlignment = Alignment.Center) {
        if (busy) {
            CircularProgressIndicator(
                Modifier.size(20.dp).semantics { contentDescription = "Working" },
                color = secondary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = secondary,
                modifier = Modifier.size(ICON_SIZE).alpha(1f - progress)
            )
        }
    }
}

private fun scriptMenuActions(row: WorkspaceScriptRow, actions: WorkspaceScriptsActions) =
    buildList {
        if (row.registered) {
            add(ScriptMenuAction("Show output", false) { actions.showOutput(row.id) })
        }
        if (row.active) add(ScriptMenuAction("Stop script", false) { actions.stop(row.id) })
        if (row.registered) {
            add(ScriptMenuAction("Delete script", true) { actions.remove(row.id) })
        }
    }

/** iOS swipe actions: Stop for an active script, then Delete, scaling in with the swipe. */
@Composable
private fun BoxScope.SwipeActions(
    row: WorkspaceScriptRow,
    revealed: Boolean,
    progress: Float,
    select: (String) -> Unit
) {
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
        if (row.active) {
            SwipeAction(R.drawable.ic_stop_fill, "Stop script", false, revealed) {
                select("Stop script")
            }
        }
        SwipeAction(R.drawable.ic_delete, "Delete script", true, revealed) {
            select("Delete script")
        }
    }
}

/** Row drags consume horizontal movement so the drawer does not claim a row swipe. */
private fun Modifier.scriptSwipe(
    enabled: Boolean,
    revealed: Boolean,
    width: Float,
    onReveal: (Boolean) -> Unit,
    onDrag: (Float) -> Unit
): Modifier = pointerInput(enabled, revealed, width) {
    var drag = 0f
    detectHorizontalDragGestures(
        onDragEnd = {
            if (enabled) {
                val threshold = width * REVEAL_THRESHOLD
                onReveal(if (revealed) drag <= threshold else drag < -threshold)
            }
            drag = 0f
            onDrag(0f)
        },
        onDragCancel = {
            drag = 0f
            onDrag(0f)
        },
        onHorizontalDrag = { change, amount ->
            if (enabled) {
                change.consume()
                drag = (drag + amount).coerceIn(-width, width)
                onDrag(drag)
            }
        }
    )
}

@Composable
private fun SwipeAction(
    icon: Int,
    label: String,
    destructive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(ACTION_SIZE)
            .clip(RoundedCornerShape(ACTION_RADIUS))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
            tint = if (destructive) IntentScreen.error else IntentScreen.text
        )
    }
}

private fun revealWidth(row: WorkspaceScriptRow) =
    SWIPE_LEADING + ACTION_SIZE * (if (row.active) 2 else 1) +
        (if (row.active) ACTION_SPACING else 0.dp) + IntentSpacing.page

// iOS WorkspaceDetailStyle row geometry and Constants.WorkspaceList swipe actions.
private val AVATAR_SIZE = 32.dp
private val ICON_SIZE = 24.dp
private val AVATAR_SPACING = 24.dp
private val TITLE_SPACING = 8.dp
private val TRAILING_ICON_INSET = 12.dp
private val ACTION_SIZE = 48.dp
private val ACTION_RADIUS = 20.dp
private val ACTION_SPACING = 8.dp
private val SWIPE_LEADING = 32.dp
private const val REVEAL_THRESHOLD = 0.333f
private const val CENTER = 0.5f
private val TRANSITIONS = setOf("starting", "restarting")
