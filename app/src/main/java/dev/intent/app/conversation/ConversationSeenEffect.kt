package dev.intent.app.conversation

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.Closeable

internal fun scheduleSeen(action: () -> Unit): Closeable {
    val handler = Handler(Looper.getMainLooper())
    val runnable = Runnable(action)
    handler.postDelayed(runnable, SEEN_DEBOUNCE_MS)
    return Closeable { handler.removeCallbacks(runnable) }
}

@Composable
internal fun ConversationSeenEffect(
    seen: ConversationSeen?,
    state: ConversationState,
    knownMarker: String?
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current = rememberUpdatedState(state)
    val marker = rememberUpdatedState(knownMarker)
    SideEffect {
        seen?.observe(state, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), knownMarker)
    }
    DisposableEffect(seen, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            seen?.observe(
                current.value,
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                marker.value
            )
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            seen?.leave()
        }
    }
}

private const val SEEN_DEBOUNCE_MS = 1000L
