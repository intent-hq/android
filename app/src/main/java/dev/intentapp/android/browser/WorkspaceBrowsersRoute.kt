package dev.intentapp.android.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.session.RpcSession
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing
import dev.intentapp.android.design.SectionHeadingPull
import dev.intentapp.android.design.pullNextItem
import dev.intentapp.android.navigation.LocalConnectionReady
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WorkspaceBrowsersRoute(
    session: RpcSession,
    connectionId: String,
    workspaceId: String,
    onBack: () -> Unit
) {
    WorkspaceBrowsersHost(session, connectionId, workspaceId) { browsers ->
        WorkspaceBrowsersScreen(
            browsers.state,
            onBack,
            onNew = browsers.new,
            onOpen = browsers.open,
            onRemove = browsers.remove,
            onRetry = browsers.retry,
            connected = browsers.connected
        )
    }
}

/**
 * Owns one saved-browser store. An open browser page replaces [content] until it closes.
 * A blank connectionId uses session-local memory; otherwise use the stable saved-connection ID.
 */
@Suppress("LongMethod") // List, new-browser sheet and browser page share one saved-browser store.
@Composable
fun WorkspaceBrowsersHost(
    session: RpcSession,
    connectionId: String,
    workspaceId: String,
    content: @Composable (WorkspaceBrowsersHandle) -> Unit
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
    val connected = LocalConnectionReady.current
    var target by rememberSaveable(session, connectionId, workspaceId) {
        mutableStateOf<String?>(null)
    }
    var selectedId by rememberSaveable(session, connectionId, workspaceId) {
        mutableStateOf<String?>(null)
    }
    var creating by rememberSaveable(session, connectionId, workspaceId) { mutableStateOf(false) }
    LaunchedEffect(store) { withContext(Dispatchers.IO) { store.load() } }
    BackHandler(target != null) { target = null }
    val open = { browser: SavedBrowser ->
        selectedId = browser.id
        target = browser.url
    }
    if (target != null && !state.loading) {
        WorkspaceBrowser(
            session,
            workspaceId,
            onBack = { target = null },
            initialUrl = target,
            connected = connected,
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
        content(
            WorkspaceBrowsersHandle(
                state,
                connected,
                open = open,
                remove = { id -> scope.launch(Dispatchers.IO) { store.remove(id) } },
                retry = { scope.launch(Dispatchers.IO) { store.load() } },
                new = { creating = true }
            )
        )
    }
    if (creating) {
        NewBrowserSheet(
            onDismiss = { creating = false },
            onCreate = { address ->
                store.create(address).map {
                    creating = false
                    // Storage drops the query and fragment; the first load keeps the typed address.
                    open(it.copy(url = normalizeBrowserUrl(address) ?: it.url))
                }
            }
        )
    }
}

/** iOS WorkspaceDetailResources: saved browsers listed below the workspace agents. */
fun LazyListScope.workspaceBrowsersSection(
    browsers: WorkspaceBrowsersHandle,
    revealed: String?,
    onReveal: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = browsers.state
    if (state.browsers.isEmpty() && state.error == null) return
    item(key = "browsers.heading") {
        Box(modifier.pullNextItem(SectionHeadingPull)) { BrowsersEyebrow() }
    }
    items(state.browsers, key = { "browser.${it.id}" }) { browser ->
        Box(modifier) {
            BrowserSwipeRow(
                browser,
                revealed = revealed == browser.id,
                enabled = !state.loading,
                onReveal = { onReveal(if (it) browser.id else null) },
                onOpen = { if (revealed != null) onReveal(null) else browsers.open(browser) },
                onDelete = {
                    onReveal(null)
                    browsers.remove(browser.id)
                }
            ) { progress -> BrowserRow(browser, browsers.connected, progress) }
        }
    }
    state.error?.let { message ->
        item(key = "browsers.error") {
            Box(modifier) { BrowsersError(message, !state.loading, browsers.retry) }
        }
    }
}

@Composable
private fun BrowsersEyebrow() {
    Text(
        "BROWSERS",
        Modifier.padding(horizontal = IntentSpacing.page).semantics { heading() },
        style = MaterialTheme.typography.labelSmall,
        color = IntentScreen.secondaryText
    )
}

@Composable
private fun BrowsersError(message: String, enabled: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.padding(horizontal = IntentSpacing.page),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentScreen.secondaryText
        )
        BrowserSecondaryButton("Retry", enabled = enabled, onClick = onRetry)
    }
}

/** iOS WorkspaceBrowsersView: an uppercase BROWSERS eyebrow over globe rows that swipe to delete. */
@Suppress("LongParameterList", "LongMethod") // Pure screen callbacks allow host-free screenshots.
@Composable
fun WorkspaceBrowsersScreen(
    state: SavedBrowsersState,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (SavedBrowser) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: () -> Unit,
    connected: Boolean = true
) {
    var revealed by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        BrowsersHeader(onBack, onNew, enabled = !state.loading && state.error == null)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = IntentSpacing.page,
                bottom = IntentSpacing.page
            ),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
        ) {
            if (state.browsers.isNotEmpty() || state.error != null) {
                item { BrowsersEyebrow() }
            }
            items(state.browsers, key = { it.id }) { browser ->
                BrowserSwipeRow(
                    browser,
                    revealed = revealed == browser.id,
                    enabled = !state.loading,
                    onReveal = { revealed = if (it) browser.id else null },
                    onOpen = {
                        if (revealed != null) revealed = null else onOpen(browser)
                    },
                    onDelete = {
                        revealed = null
                        onRemove(browser.id)
                    }
                ) { progress -> BrowserRow(browser, connected, progress) }
            }
            state.error?.let { message ->
                item { BrowsersError(message, !state.loading, onRetry) }
            }
            if (!state.loading && state.browsers.isEmpty() && state.error == null) {
                item { BrowsersEmptyState(onNew) }
            }
        }
    }
}

@Composable
private fun BrowsersHeader(onBack: () -> Unit, onNew: () -> Unit, enabled: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(
            horizontal = IntentSpacing.page,
            vertical = IntentSpacing.medium
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(CONTROL_SIZE)) {
            Icon(painterResource(R.drawable.ic_arrow_left), "Back")
        }
        Text(
            "Browsers",
            Modifier.weight(1f).padding(horizontal = IntentSpacing.small).semantics { heading() },
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        IconButton(onClick = onNew, enabled = enabled, modifier = Modifier.size(CONTROL_SIZE)) {
            Icon(painterResource(R.drawable.ic_add), "New browser")
        }
    }
}

/** iOS WorkspaceBrowserRow: 32dp globe tile, 24dp gap, title, address, and a fading chevron. */
@Composable
private fun BrowserRow(browser: SavedBrowser, connected: Boolean, swipeProgress: Float) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(IntentSpacing.page)
    ) {
        Box(Modifier.size(AVATAR_SIZE), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_globe),
                null,
                Modifier.size(ICON_SIZE),
                tint = IntentScreen.secondaryText
            )
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
        ) {
            Text(
                browser.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(BODY)),
                color = IntentScreen.text
            )
            Text(
                browser.url,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentScreen.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!connected) {
                Text(
                    "Disconnected",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentScreen.secondaryText
                )
            }
        }
        Box(
            Modifier.size(height = AVATAR_SIZE, width = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                null,
                Modifier.size(12.dp).alpha(1f - swipeProgress),
                tint = IntentScreen.secondaryText
            )
        }
    }
}

/** Android-only empty state; iOS hides the section and opens New browser from the workspace menu. */
@Composable
private fun BrowsersEmptyState(onNew: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = IntentSpacing.page, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.row)
    ) {
        Icon(
            painterResource(R.drawable.ic_globe),
            null,
            Modifier.size(48.dp),
            tint = IntentScreen.secondaryText
        )
        Text(
            "No browsers",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "Use localhost and the server port to open a server running on your connected machine.",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(BODY)),
            color = IntentScreen.secondaryText,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(IntentSpacing.small))
        BrowserSecondaryButton("New browser", onClick = onNew)
    }
}

private val ROW_SPACING = 36.dp
private val AVATAR_SIZE = 32.dp
private val ICON_SIZE = 24.dp
private val CONTROL_SIZE = 48.dp
private const val BODY = 450
