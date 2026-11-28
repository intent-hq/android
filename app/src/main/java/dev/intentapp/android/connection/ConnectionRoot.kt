package dev.intentapp.android.connection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.attachments.RpcAttachmentClient
import dev.intentapp.android.attachments.AttachmentMedia
import dev.intentapp.android.attachments.LocalAttachmentMedia
import dev.intentapp.android.cache.RoomOfflineCache
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.drawers.AgentHistoryDrawer
import dev.intentapp.android.drawers.ConnectionsDrawer
import dev.intentapp.android.drawers.DrawerPanels
import dev.intentapp.android.drawers.LocalDrawerPanels
import dev.intentapp.android.history.AgentHistoryStore
import dev.intentapp.android.history.AgentVisit
import dev.intentapp.android.history.HistoryNavigation
import dev.intentapp.android.history.LocalHistoryNavigation
import dev.intentapp.android.navigation.CompactAppBar
import dev.intentapp.android.navigation.IntentNavigation
import dev.intentapp.android.navigation.LocalConnectionReady
import dev.intentapp.android.navigation.WorkspaceContent
import dev.intentapp.android.notifications.LocalNotifications
import dev.intentapp.android.settings.AppPreferences
import java.io.File

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ConnectionRoot(
    controller: ConnectionController,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: AppPreferences = rememberPreferences(),
    notificationWorkspace: Pair<String, String>? = null
) {
    val state by controller.state.collectAsState()
    val notifications by preferences.notifications.collectAsState()
    val context = LocalContext.current.applicationContext
    val history = remember(context) { AgentHistoryStore.open(context) }
    var historyTarget by remember { mutableStateOf<AgentVisit?>(null) }
    DisposableEffect(state.session, notifications) {
        val lease = if (notifications) {
            state.session?.let {
                LocalNotifications(context).observe(it, state.activeId.orEmpty())
            }
        } else {
            null
        }
        onDispose { lease?.close() }
    }
    var utilities by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val utility = utilities.lastOrNull()
    val back = { utilities = utilities.dropLast(1) }
    val contentState = rememberSaveableStateHolder()
    BackHandler(utilities.isNotEmpty(), onBack = back)
    val appearance by preferences.appearance.collectAsState()
    val launcher = dev.intentapp.android.navigation.LauncherActions(
        state.connections.size,
        appearance,
        onAppearance = {
            val modes = dev.intentapp.android.settings.Appearance.entries
            preferences.setAppearance(modes[(appearance.ordinal + 1) % modes.size])
        },
        onConnections = { utilities = utilities.openUtility("Connections") },
        onSettings = { utilities = utilities.openUtility("Settings") }
    )
    CompositionLocalProvider(
        LocalConnectionReady provides (state.status == "Connected"),
        dev.intentapp.android.navigation.LocalConnectionName provides
            (
                state.connections.firstOrNull { it.id == state.activeId }?.displayName
                    ?: "Connected host"
                ),
        dev.intentapp.android.navigation.LocalLauncherActions provides launcher,
        LocalHistoryNavigation provides
            HistoryNavigation(history, state.activeId, historyTarget) { historyTarget = null },
        LocalDrawerPanels provides DrawerPanels(
            connections = { close ->
                ConnectionsDrawer(state, onSelect = {
                    close()
                    historyTarget = null
                    if (it != state.activeId) controller.select(it)
                })
            },
            history = { close ->
                AgentHistoryDrawer(history, state) {
                    close()
                    historyTarget = it
                    if (it.connectionId != state.activeId) controller.select(it.connectionId)
                }
            }
        )
    ) {
        Column(modifier) {
            val launching = utility == null &&
                state.session == null &&
                state.connections.isNotEmpty() &&
                state.status == "Connecting"
            val utilityTitle = when {
                launching -> null
                utility == "Settings" -> "Settings"
                utility == "Acknowledgements" -> "Acknowledgements"
                utility == "Pair connection" -> "Pair connection"
                state.connections.isEmpty() -> null
                utility == "Connections" || state.session == null -> "Connections"
                else -> null
            }
            if (utilityTitle != null) {
                CompactAppBar(
                    utilityTitle,
                    onBack = if (utilities.isNotEmpty()) back else null
                )
            }
            state.error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(IntentScreen.pageInsets),
                    color = IntentScreen.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            when {
                utility == "Settings" -> ConnectionSettings(
                    controller.diagnostics,
                    Modifier.weight(1f),
                    preferences,
                    state,
                    onDisconnect = controller::disconnect,
                    onPair = { utilities = utilities.openUtility("Pair connection") },
                    onAcknowledgements = { utilities = utilities.openUtility("Acknowledgements") }
                ) {
                    utilities = utilities.openUtility("Connections")
                }
                utility == "Acknowledgements" -> AcknowledgementsPage(Modifier.weight(1f))
                utility == "Pair connection" || state.connections.isEmpty() -> PairingScreen(
                    state.busy,
                    onScan,
                    controller::prepareManual,
                    Modifier.weight(1f),
                    tunnelAvailable = controller.tunnelAvailable,
                    error = state.error,
                    onDismissError = controller::cancelPairing
                )
                launching -> LaunchSplash(Modifier.weight(1f))
                utility == "Connections" || state.session == null -> ConnectionScreen(
                    state,
                    onSelect = {
                        if (it != state.activeId || state.session == null) controller.select(it)
                        utilities = emptyList()
                    },
                    onForget = controller::forget,
                    onAdd = { utilities = utilities.openUtility("Pair connection") },
                    modifier = Modifier.weight(1f)
                )
                else -> key(state.session, state.cacheScope) {
                    contentState.SaveableStateProvider(state.cacheScope ?: "connected") {
                        Box(Modifier.weight(1f)) {
                            ConnectedContent(state, Modifier.fillMaxSize(), notificationWorkspace)
                            // Clears the 80dp Intent launcher and its 16dp inset, like iOS chrome clearance.
                            ConnectionRecoveryBanner(
                                state,
                                onHome = { utilities = utilities.openUtility("Connections") },
                                modifier = Modifier.align(
                                    Alignment.BottomCenter
                                ).padding(bottom = 104.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    state.pending?.let { payload ->
        PairingConfirmation(payload, state.busy, controller::probeCertificate, {
            controller.confirmPairing()
            utilities = emptyList()
        }, controller::cancelPairing, controller.tunnelAvailable)
    }
}

@Composable
private fun ConnectedContent(
    state: ConnectionState,
    modifier: Modifier,
    notificationWorkspace: Pair<String, String>?
) {
    val session = state.session ?: return
    val scope = state.cacheScope ?: return
    val context = LocalContext.current.applicationContext
    key(session, scope) {
        val cache = remember { RoomOfflineCache.open(context, scope) }
        DisposableEffect(cache) { onDispose { cache.close() } }
        IntentNavigation(
            session = session,
            cache = cache,
            connectionScope = scope,
            modifier = modifier,
            initialWorkspace = notificationWorkspace?.takeIf { it.first == state.activeId }?.second,
            destinations = state.connections.filter {
                it.id != state.activeId &&
                    it.payload.certFingerprint !=
                    state.connections.firstOrNull { row ->
                        row.id == state.activeId
                    }?.payload?.certFingerprint
            }
        ) {
                workspaceId,
                back
            ->
            val media = remember(session, scope, workspaceId) {
                AttachmentMedia(
                    RpcAttachmentClient(session),
                    workspaceId,
                    File(context.cacheDir, "attachments"),
                    scope
                )
            }
            CompositionLocalProvider(LocalAttachmentMedia provides media) {
                WorkspaceContent(session, workspaceId, cache, back, sectionScope = scope)
            }
        }
    }
}

@Composable
private fun rememberPreferences(): AppPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { AppPreferences(context) }
}
