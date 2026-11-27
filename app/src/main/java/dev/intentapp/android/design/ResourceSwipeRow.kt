package dev.intentapp.android.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
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
import dev.intentapp.android.R
import kotlin.math.roundToInt

/**
 * iOS WorkspaceSwipeContainer with one action: the row slides left to reveal a red Delete
 * button on the page inset. Long press opens the iOS context menu; TalkBack gets each action.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun ResourceSwipeRow(
    id: String,
    noun: String,
    revealed: Boolean,
    enabled: Boolean,
    onReveal: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    menuActions: List<ResourceMenuAction> = emptyList(),
    content: @Composable (Float) -> Unit
) {
    val width = with(LocalDensity.current) { REVEAL_WIDTH.toPx() }
    var drag by remember(id, revealed) { mutableFloatStateOf(0f) }
    var menu by remember(id) { mutableStateOf(false) }
    val offset = ((if (revealed) -width else 0f) + drag).coerceIn(-width, 0f)
    val progress = (-offset / width).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        if (progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = IntentSpacing.page)
                    .graphicsLayer {
                        alpha = progress
                        transformOrigin = TransformOrigin(1f, CENTER)
                    }
                    .width(BUTTON_WIDTH)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(BUTTON_RADIUS))
                    .background(IntentScreen.error)
                    .clickable(
                        enabled = enabled && revealed,
                        role = Role.Button,
                        onClick = onDelete
                    )
                    .semantics { contentDescription = "Delete $noun" }
                    .testTag("$noun.delete.$id"),
                contentAlignment = Alignment.Center
            ) {
                Text("Delete", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .testTag("$noun.row.$id")
                .pointerInput(id, enabled, revealed) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (enabled) {
                                onReveal(
                                    if (revealed) {
                                        drag <= width * REVEAL_THRESHOLD
                                    } else {
                                        drag < -width * REVEAL_THRESHOLD
                                    }
                                )
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
                    onClick = onOpen,
                    onLongClickLabel = "${noun.replaceFirstChar { it.uppercase() }} actions",
                    onLongClick = { menu = true }
                )
                .semantics(mergeDescendants = true) {
                    if (enabled) {
                        customActions = menuActions.map { action ->
                            CustomAccessibilityAction(action.label) {
                                action.run()
                                true
                            }
                        } + CustomAccessibilityAction("Delete $noun") {
                            onDelete()
                            true
                        }
                    }
                }
                .padding(horizontal = IntentSpacing.page)
        ) { content(progress) }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            menuActions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(painterResource(action.iconRes), null) },
                    onClick = {
                        menu = false
                        action.run()
                    },
                    enabled = enabled
                )
            }
            DropdownMenuItem(
                text = { Text("Delete $noun", color = IntentScreen.error) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        null,
                        tint = IntentScreen.error
                    )
                },
                onClick = {
                    menu = false
                    onDelete()
                },
                enabled = enabled
            )
        }
    }
}

// iOS WorkspaceSwipeStyle: one 88dp button with an 8dp radius on the 24dp page inset.
private val BUTTON_WIDTH = 88.dp
private val BUTTON_RADIUS = 8.dp
private val REVEAL_WIDTH = BUTTON_WIDTH + IntentSpacing.page + IntentSpacing.medium
private const val REVEAL_THRESHOLD = 0.333f
private const val CENTER = 0.5f
