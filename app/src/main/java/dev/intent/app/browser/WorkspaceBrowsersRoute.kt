package dev.intent.app.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentTextButton
import dev.intent.core.protocol.session.RpcSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A blank connectionId uses session-local memory; otherwise use the stable saved-connection ID. */
@Composable
fun WorkspaceBrowsersRoute(
    session: RpcSession,
    connectionId: String,
    workspaceId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val store = remember(session, connectionId, workspaceId) {
        SavedBrowserStore(
            browserPersistence(connectionId, workspaceId) {
                SavedBrowserPreferences(context, connectionId, workspaceId)
            }
        )
    }
    val scope = rememberCoroutineScope()
    val state by store.state.collectAsState()
    var target by rememberSaveable(session, connectionId, workspaceId) { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable(session, connectionId, workspaceId) { mutableStateOf<String?>(null) }
    LaunchedEffect(store) { withContext(Dispatchers.IO) { store.load() } }
    BackHandler(target != null) { target = null }
    if (target != null && !state.loading) {
        WorkspaceBrowser(
            session,
            workspaceId,
            onBack = { target = null },
            initialUrl = target?.takeIf { it.isNotEmpty() },
            onPageChanged = { url, title ->
                val pageTarget = target
                val pageId = selectedId
                scope.launch {
                    val savedId = withContext(Dispatchers.IO) { store.savePage(pageId, url, title) }
                    if (target == pageTarget) selectedId = savedId ?: selectedId
                }
            }
        )
    } else {
        WorkspaceBrowsersScreen(
            state,
            onBack,
            onNew = {
                selectedId = null
                target = ""
            },
            onOpen = {
                selectedId = it.id
                target = it.url
            },
            onRemove = { id -> scope.launch(Dispatchers.IO) { store.remove(id) } },
            onRetry = { scope.launch(Dispatchers.IO) { store.load() } }
        )
    }
}

@Suppress("LongParameterList") // Pure screen callbacks allow host-free screenshots.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceBrowsersScreen(
    state: SavedBrowsersState,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (SavedBrowser) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = IntentScreen.pageInsets,
        verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
    ) {
        item {
            Column {
                Text("Browsers", style = MaterialTheme.typography.headlineSmall)
                Text("Under development", color = IntentScreen.secondaryText)
                FlowRow {
                    IntentTextButton(onClick = onBack) { Text("Back") }
                    IntentTextButton(onClick = onNew, enabled = !state.loading && state.error == null) {
                        Text("New browser")
                    }
                }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message ->
            item {
                Text(message, color = IntentScreen.error)
                IntentTextButton(onClick = onRetry, enabled = !state.loading) { Text("Retry") }
            }
        }
        if (!state.loading && state.browsers.isEmpty() && state.error == null) {
            item {
                IntentSection("No saved browsers") {
                    Text("Open a workspace preview to save its title and URL here.")
                }
            }
        }
        items(state.browsers, key = { it.id }) { browser ->
            IntentSection(browser.title, onClick = { onOpen(browser) }) {
                Text(browser.url, maxLines = 2, overflow = TextOverflow.Ellipsis, color = IntentScreen.secondaryText)
                IntentTextButton(onClick = { onRemove(browser.id) }, enabled = !state.loading) {
                    Text("Remove saved browser")
                }
            }
        }
    }
}
