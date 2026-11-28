package dev.intentapp.android.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.intentapp.android.design.IntentScreen
import kotlinx.coroutines.delay

internal val ConnectionState.isRecovering: Boolean
    get() = session != null && status != "Connected"

/** iOS ReadOnlyBanner: a floating status pill that expands into connection details. */
@Composable
internal fun ConnectionRecoveryBanner(
    state: ConnectionState,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = state.isRecovering && !rememberBackgroundRecoveryGrace(state.isRecovering)
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) expanded = false }
    AnimatedVisibility(
        visible,
        modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        val machine = state.connections.firstOrNull { it.id == state.activeId }
            ?.displayName?.takeIf(String::isNotBlank)
        val title = if (expanded) {
            machine?.let { "Reconnecting to $it… · Read-only" } ?: "Reconnecting… · Read-only"
        } else {
            "Reconnecting…"
        }
        Surface(
            Modifier.padding(horizontal = 8.dp)
                .widthIn(max = if (expanded) ExpandedMaxWidth else CollapsedMaxWidth)
                .animateContentSize(),
            shape = RoundedCornerShape(if (expanded) 22.dp else 100.dp),
            color = IntentScreen.surface,
            shadowElevation = 6.dp
        ) {
            Column(
                Modifier.padding(
                    horizontal = 16.dp,
                    vertical = if (expanded) 16.dp else 11.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.clickable(
                        onClickLabel = if (expanded) "Hide details" else "Show details"
                    ) { expanded = !expanded }
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        Modifier.size(14.dp),
                        color = IntentScreen.secondaryText,
                        strokeWidth = 2.dp
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (expanded) RecoveryDetails(onHome)
            }
        }
    }
}

@Composable
private fun RecoveryDetails(onHome: () -> Unit) {
    Text(
        "You can keep reading. Changes are turned off until the connection comes back.",
        style = MaterialTheme.typography.bodySmall,
        color = IntentScreen.secondaryText
    )
    Box(
        Modifier.fillMaxWidth().heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onHome),
        contentAlignment = Alignment.Center
    ) {
        Text("Back to Home", style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * iOS ConnectionLossPresentation: after the app returns to the foreground, hide the pill for
 * a short grace period so a quick reconnect after unlocking shows nothing.
 */
@Composable
private fun rememberBackgroundRecoveryGrace(recovering: Boolean): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentRecovering by rememberUpdatedState(recovering)
    var backgrounded by remember { mutableStateOf(false) }
    var deferred by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgrounded = !currentRecovering
                Lifecycle.Event.ON_START -> if (backgrounded) {
                    backgrounded = false
                    deferred = true
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(deferred) {
        if (deferred) {
            delay(BACKGROUND_RECOVERY_GRACE_MILLIS)
            deferred = false
        }
    }
    return deferred || backgrounded
}

private val CollapsedMaxWidth = 300.dp
private val ExpandedMaxWidth = 360.dp
private const val BACKGROUND_RECOVERY_GRACE_MILLIS = 1_000L
