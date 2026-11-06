package dev.intent.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal fun WorkspaceActionLauncher(actions: LauncherActions, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val transition = updateTransition(open, label = "Intent launcher")
    val progress by transition.animateFloat(
        transitionSpec = {
            spring(dampingRatio = LAUNCHER_DAMPING, stiffness = LAUNCHER_STIFFNESS)
        },
        label = "Intent button morph"
    ) { if (it) 1f else 0f }
    val modal = transition.currentState || transition.targetState
    BackHandler(open) { open = false }
    Box(
        modifier.size(80.dp).onGloballyPositioned { anchor = it.boundsInWindow() }
            .graphicsLayer { alpha = if (modal) 0f else 1f }
            .then(if (modal) Modifier.clearAndSetSemantics {} else Modifier)
    ) {
        IntentLauncherButton(0f, false) { open = true }
    }
    val bounds = anchor
    if (modal && bounds != null) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            LauncherOverlay(actions, bounds, progress, open) { open = it }
        }
    }
}

@Composable
private fun LauncherOverlay(
    actions: LauncherActions,
    anchor: Rect,
    progress: Float,
    open: Boolean,
    onOpen: (Boolean) -> Unit
) {
    val close = { onOpen(false) }
    val window = (LocalView.current.parent as DialogWindowProvider).window
    SideEffect {
        window.setDimAmount(0f)
        window.setWindowAnimations(0)
    }
    val density = LocalDensity.current
    val fraction = progress.coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bottom = (maxHeight - with(density) { anchor.bottom.toDp() }).coerceAtLeast(0.dp)
        val end = (maxWidth - with(density) { anchor.right.toDp() }).coerceAtLeast(0.dp)
        Box(
            Modifier.matchParentSize().graphicsLayer { alpha = LAUNCHER_SHADE * fraction }
                .background(Color.Black).clickable(onClick = close)
                .semantics { contentDescription = "Dismiss app launcher" }
        )
        Column(
            Modifier.align(Alignment.BottomEnd).padding(bottom = bottom + 8.dp * fraction),
            verticalArrangement = Arrangement.spacedBy(23.dp),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                Modifier.graphicsLayer {
                    alpha = fraction
                    translationY = with(density) { 20.dp.toPx() } * (1 - fraction)
                }
            ) { LauncherTiles(actions, open, close) }
            Box(Modifier.padding(end = end)) {
                IntentLauncherButton(progress, open) { onOpen(!open) }
            }
        }
    }
}

@Composable
private fun LauncherTiles(actions: LauncherActions, enabled: Boolean, close: () -> Unit) {
    val tiles = buildList {
        actions.onAssistant?.let { add(ChromeAction("Assistant", run = it)) }
        actions.onCreate?.let { add(ChromeAction("New workspace", run = it)) }
        add(ChromeAction("Connections: ${actions.connectionCount}", run = actions.onConnections))
        add(ChromeAction("Appearance: ${actions.appearance.name}", run = actions.onAppearance))
        add(ChromeAction("Settings", run = actions.onSettings))
    }
    LazyRow(
        modifier = Modifier.testTag("launcher.tiles"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)
    ) {
        items(tiles, key = { it.label.substringBefore(':') }) { tile ->
            Card(
                enabled = enabled,
                onClick = {
                    if (!tile.label.startsWith("Appearance:")) close()
                    tile.run()
                },
                modifier = Modifier.width(192.dp).height(240.dp)
                    .semantics { contentDescription = tile.label }
            ) {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    val icon = when (tile.label.substringBefore(':')) {
                        "Connections" -> Icons.Default.AccountBox
                        "Appearance" -> Icons.Default.Refresh
                        "Settings" -> Icons.Default.Settings
                        else -> Icons.Default.Add
                    }
                    if (tile.label == "Assistant") {
                        Icon(
                            androidx.compose.ui.res.painterResource(
                                dev.intent.app.R.drawable.avatar_chief_of_staff
                            ),
                            null,
                            tint = androidx.compose.ui.graphics.Color.Unspecified
                        )
                    } else {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(tile.label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private const val LAUNCHER_DAMPING = 0.85f
private const val LAUNCHER_STIFFNESS = 322f
private const val LAUNCHER_SHADE = 0.32f
