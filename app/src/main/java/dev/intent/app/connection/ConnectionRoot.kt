package dev.intent.app.connection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.intent.app.attachments.AttachmentMedia
import dev.intent.app.attachments.LocalAttachmentMedia
import dev.intent.app.cache.RoomOfflineCache
import dev.intent.app.drawers.AgentHistoryDrawer
import dev.intent.app.drawers.ConnectionsDrawer
import dev.intent.app.drawers.DrawerPanels
import dev.intent.app.drawers.LocalDrawerPanels
import dev.intent.app.history.AgentHistoryStore
import dev.intent.app.history.AgentVisit
import dev.intent.app.history.HistoryNavigation
import dev.intent.app.history.LocalHistoryNavigation
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.app.navigation.IntentNavigation
import dev.intent.app.navigation.LocalChromeActions
import dev.intent.app.navigation.WorkspaceContent
import dev.intent.app.notifications.LocalNotifications
import dev.intent.app.settings.AppPreferences
import dev.intent.core.protocol.attachments.RpcAttachmentClient
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
    val actions = buildList {
        add(
            ChromeAction("Settings") {
                utilities = utilities.openUtility("Settings")
            }
        )
        add(
            ChromeAction("Connections") {
                utilities = utilities.openUtility("Connections")
            }
        )
        if (state.activeId != null) add(ChromeAction("Disconnect", run = controller::disconnect))
    }
    CompositionLocalProvider(
        LocalChromeActions provides actions,
        LocalHistoryNavigation provides
            HistoryNavigation(history, state.activeId, historyTarget) { historyTarget = null },
        LocalDrawerPanels provides DrawerPanels(
            connections = { close ->
                ConnectionsDrawer(state, onSelect = {
                    close()
                    historyTarget = null
                    if (it != state.activeId) controller.select(it)
                }, onManage = {
                    close()
                    utilities = utilities.openUtility("Connections")
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
            state.error?.let { Text(it) }
            val utilityTitle = when {
                utility == "Settings" -> "Settings"
                utility == "Pair connection" || state.connections.isEmpty() -> "Pair connection"
                utility == "Connections" || state.session == null -> "Connections"
                else -> null
            }
            if (utilityTitle != null) {
                CompactAppBar(
                    utilityTitle,
                    onBack = if (utilities.isNotEmpty()) back else null
                )
            }
            when {
                utility == "Settings" -> ConnectionSettings(
                    controller.diagnostics,
                    Modifier.weight(1f),
                    preferences
                ) {
                    utilities = utilities.openUtility("Connections")
                }
                utility == "Pair connection" || state.connections.isEmpty() -> PairingScreen(
                    state.busy,
                    onScan,
                    controller::prepareManual,
                    Modifier.weight(1f)
                )
                utility == "Connections" || state.session == null -> ConnectionScreen(
                    state,
                    onSelect = {
                        controller.select(it)
                        utilities = emptyList()
                    },
                    onForget = controller::forget,
                    onAdd = { utilities = utilities.openUtility("Pair connection") },
                    modifier = Modifier.weight(1f)
                )
                else -> key(state.session, state.cacheScope) {
                    contentState.SaveableStateProvider(state.cacheScope ?: "connected") {
                        ConnectedContent(state, Modifier.weight(1f), notificationWorkspace)
                    }
                }
            }
        }
    }
    state.pending?.let { payload ->
        PairingConfirmation(payload, state.busy, controller::probeCertificate, {
            controller.confirmPairing()
            utilities = emptyList()
        }, controller::cancelPairing)
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
