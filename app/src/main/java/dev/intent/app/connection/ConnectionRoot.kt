package dev.intent.app.connection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.intent.app.cache.RoomOfflineCache
import dev.intent.app.conversation.WorkspaceConversations
import dev.intent.app.navigation.IntentNavigation

@Composable
fun ConnectionRoot(
    controller: ConnectionController,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsState()
    var showConnections by remember { mutableStateOf(false) }
    var showPairing by remember { mutableStateOf(false) }
    BackHandler(showConnections || showPairing) {
        showPairing = false
        showConnections = false
    }
    Column(modifier) {
        Text(state.status)
        state.error?.let { Text(it) }
        Row {
            TextButton(onClick = {
                showConnections = !showConnections
                showPairing = false
            }) {
                Text(if (showConnections) "Back" else "Connections")
            }
            if (state.activeId != null) {
                TextButton(onClick = controller::disconnect) { Text("Disconnect") }
            }
        }
        when {
            showPairing || state.connections.isEmpty() -> PairingScreen(
                state.busy,
                onScan,
                controller::prepareManual,
                Modifier.weight(1f)
            )
            showConnections || state.session == null -> ConnectionScreen(
                state,
                onSelect = {
                    controller.select(it)
                    showConnections = false
                },
                onForget = controller::forget,
                onAdd = { showPairing = true },
                modifier = Modifier.weight(1f)
            )
            else -> ConnectedContent(state, Modifier.weight(1f))
        }
    }
    state.pending?.let { payload ->
        PairingConfirmation(payload, state.busy, controller::probeCertificate, {
            controller.confirmPairing()
            showPairing = false
            showConnections = false
        }, controller::cancelPairing)
    }
}

@Composable
private fun ConnectedContent(state: ConnectionState, modifier: Modifier) {
    val session = state.session ?: return
    val scope = state.cacheScope ?: return
    val context = LocalContext.current.applicationContext
    key(session, scope) {
        val cache = remember { RoomOfflineCache.open(context, scope) }
        DisposableEffect(cache) { onDispose { cache.close() } }
        IntentNavigation(session = session, cache = cache, modifier = modifier) {
                workspaceId,
                back
            ->
            WorkspaceConversations(session, workspaceId, back)
        }
    }
}
