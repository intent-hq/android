package dev.intent.app.connection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton

internal val ConnectionState.isRecovering: Boolean
    get() = session != null && status != "Connected"

@Composable
internal fun ConnectionRecoveryBanner(state: ConnectionState, onConnections: () -> Unit) {
    if (!state.isRecovering) return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            Modifier.fillMaxWidth().padding(IntentScreen.itemSpacing)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Text("Reconnecting…", style = MaterialTheme.typography.titleSmall)
            Text(
                "Connection lost. Your screen is kept while we retry. Check your network and desktop.",
                style = MaterialTheme.typography.bodySmall
            )
            IntentTextButton(onClick = onConnections) { Text("Saved connections") }
        }
    }
}
