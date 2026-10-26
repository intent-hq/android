package dev.intent.app.drawers

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentScreen
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

class DrawerPanels(
    val connections: @Composable (() -> Unit) -> Unit,
    val history: @Composable (() -> Unit) -> Unit
)

val LocalDrawerPanels = staticCompositionLocalOf<DrawerPanels?> { null }
val LocalDrawerButtons = staticCompositionLocalOf<DrawerButtons?> { null }
class DrawerButtons(val connections: () -> Unit, val history: () -> Unit)

/** Physical left/right panels. Child row gestures win through normal pointer consumption. */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun WorkspaceDrawers(content: @Composable () -> Unit) {
    val panels = LocalDrawerPanels.current
    if (panels == null) {
        content()
        return
    }
    var side by rememberSaveable { mutableStateOf<String?>(null) }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    val close = {
        side = null
        drag = 0f
    }
    val view = LocalView.current
    var announced by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(side) {
        if (side != null || announced != null) {
            view.announceForAccessibility(
                side?.let { "$it opened" } ?: "${announced.orEmpty()} closed"
            )
        }
        announced = side
    }
    PredictiveBackHandler(enabled = side != null) { progress ->
        try {
            progress.collect { backProgress = it.progress }
            close()
        } catch (_: CancellationException) {
            // A cancelled system Back gesture leaves the panel open.
        } finally {
            backProgress = 0f
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().testTag("workspace.drawers")) {
        val density = LocalDensity.current
        val reveal = minOf(320.dp, (maxWidth - 82.dp).coerceAtLeast(0.dp))
        val width = with(density) { reveal.toPx() }
        val edgeMargin = with(density) { 24.dp.roundToPx() }
        val leftInset = WindowInsets.systemGestures.getLeft(
            density,
            androidx.compose.ui.unit.LayoutDirection.Ltr
        ).coerceAtLeast(edgeMargin)
        val rightInset = WindowInsets.systemGestures.getRight(
            density,
            androidx.compose.ui.unit.LayoutDirection.Ltr
        ).coerceAtLeast(edgeMargin)
        val base = when (side) {
            "Connections" -> width
            "Agent history" -> -width
            else -> 0f
        }
        val offset by animateFloatAsState(
            (base + drag).coerceIn(-width, width) * (1f - backProgress),
            animationSpec = if (dragging ||
                backProgress > 0f
            ) {
                snap()
            } else {
                tween(DRAWER_ANIMATION_MS)
            },
            label = "Drawer page offset"
        )
        val visible =
            side
                ?: when {
                    offset > 0 -> "Connections"
                    offset < 0 -> "Agent history"
                    else -> null
                }
        Box(
            Modifier.fillMaxSize().background(
                IntentScreen.surface
            ).pointerInput(side, width, leftInset, rightInset) {
                drawerGestures(
                    leftInset,
                    rightInset,
                    onCancel = {
                        drag = 0f
                        dragging = false
                    },
                    onEnd = {
                        dragging = false
                        val end = (base + drag).coerceIn(-width, width)
                        side = if (abs(end) > width / 2) {
                            if (end > 0) "Connections" else "Agent history"
                        } else {
                            null
                        }
                        drag = 0f
                    },
                    onDrag = { amount ->
                        dragging = true
                        drag = when (side) {
                            "Connections" -> (drag + amount).coerceIn(-width, 0f)
                            "Agent history" -> (drag + amount).coerceIn(0f, width)
                            else -> (drag + amount).coerceIn(-width, width)
                        }
                    }
                )
            }
        ) {
            if (visible != null) {
                Box(
                    Modifier.align(
                        if (visible ==
                            "Connections"
                        ) {
                            AbsoluteAlignment.CenterLeft
                        } else {
                            AbsoluteAlignment.CenterRight
                        }
                    )
                        .width(reveal).semantics { paneTitle = visible }
                        .testTag("drawer.$visible")
                ) {
                    if (visible ==
                        "Connections"
                    ) {
                        panels.connections(close)
                    } else {
                        panels.history(close)
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().absoluteOffset { IntOffset(offset.roundToInt(), 0) }
                    .clip(RoundedCornerShape(if (visible != null) 20.dp else 0.dp))
                    .background(IntentScreen.canvas)
            ) {
                Box(if (visible != null) Modifier.clearAndSetSemantics {} else Modifier) {
                    CompositionLocalProvider(
                        LocalDrawerButtons provides DrawerButtons(
                            { side = "Connections" },
                            { side = "Agent history" }
                        )
                    ) { content() }
                }
                if (visible != null) {
                    Box(
                        Modifier.fillMaxSize().clickable(onClick = close).semantics {
                            contentDescription = "Close $visible"
                        }
                    )
                }
            }
        }
    }
}

private const val DRAWER_ANIMATION_MS = 180
