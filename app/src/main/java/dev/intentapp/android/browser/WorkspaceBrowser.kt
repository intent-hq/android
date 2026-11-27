package dev.intentapp.android.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.intent.core.protocol.session.RpcSession
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing
import dev.intentapp.android.navigation.LocalConnectionReady

/** iOS WorkspaceBrowserView: title header, page content, and the address bar at the bottom. */
@Suppress("LongParameterList") // Session, page callbacks and connection state.
@Composable
fun WorkspaceBrowser(
    session: RpcSession,
    workspaceId: String,
    onBack: () -> Unit,
    initialUrl: String? = null,
    connected: Boolean = LocalConnectionReady.current,
    onPageChanged: (String, String?) -> Unit = { _, _ -> }
) {
    val store = remember(session, workspaceId) { BrowserStore(session) }
    val context = LocalContext.current
    val page = remember(store, context) { BrowserPage(context, store) }
    DisposableEffect(page) {
        onDispose {
            page.close()
            store.close()
        }
    }
    LaunchedEffect(store, initialUrl) { initialUrl?.let(store::open) }
    val state by store.state.collectAsState()
    val pageChanged by rememberUpdatedState(onPageChanged)
    LaunchedEffect(state.requestedUrl, state.title, state.loading, state.error) {
        if (!state.loading && state.error == null && state.url != null) {
            state.requestedUrl?.let { pageChanged(it, state.title) }
        }
    }
    val retry = { (state.requestedUrl ?: initialUrl)?.let(store::open) }
    // iOS reattaches the page when the connection returns.
    LaunchedEffect(connected) { if (connected && state.error != null) retry() }
    BrowserScreen(
        state,
        onBack,
        store::open,
        initialAddress = initialUrl ?: "",
        onPageBack = page::back,
        onPageForward = page::forward,
        onReload = {
            if (state.error == null && state.url != null) page.reload() else retry()
        },
        connected = connected
    ) {
        if (state.url != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { page.view() },
                update = { page.load(state) }
            )
        }
    }
}

@Suppress("LongParameterList") // Browser chrome actions stay separate from the page content.
@Composable
internal fun BrowserScreen(
    state: BrowserState,
    onBack: () -> Unit,
    open: (String) -> Unit,
    initialAddress: String = "",
    onPageBack: () -> Unit = {},
    onPageForward: () -> Unit = {},
    onReload: () -> Unit = {},
    connected: Boolean = true,
    page: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().background(IntentScreen.canvas)) {
        BrowserHeader(state.title?.takeIf { it.isNotBlank() } ?: "Browser", onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !connected -> PageUnavailable(
                    "Reconnect to your machine to open this browser.",
                    retryEnabled = false,
                    onReload
                )
                state.error != null -> PageUnavailable(state.error, retryEnabled = true, onReload)
                else -> page()
            }
        }
        BrowserAddressBar(
            state,
            initialAddress,
            connected,
            open,
            onPageBack,
            onPageForward,
            onReload
        )
    }
}

/** iOS WorkspaceNavigationHeader in conversation layout with the Close icon. */
@Composable
private fun BrowserHeader(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(
            horizontal = IntentSpacing.page,
            vertical = IntentSpacing.medium
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(CONTROL_SIZE)) {
            Icon(painterResource(R.drawable.intent_close), "Close browser")
        }
        Text(
            title,
            Modifier.weight(1f).padding(horizontal = IntentSpacing.small).semantics { heading() },
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.size(CONTROL_SIZE))
    }
}

/** iOS ContentUnavailableView: globe, Page unavailable, the reason, and a Retry button. */
@Composable
private fun PageUnavailable(message: String, retryEnabled: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = IntentSpacing.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.row, Alignment.CenterVertically)
    ) {
        Icon(
            painterResource(R.drawable.ic_globe),
            null,
            Modifier.size(48.dp),
            tint = IntentScreen.secondaryText
        )
        Text(
            "Page unavailable",
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Text(
            message,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(BODY_WEIGHT)),
            color = IntentScreen.secondaryText,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(IntentSpacing.small))
        BrowserSecondaryButton("Retry", enabled = retryEnabled, onClick = onRetry)
    }
}

private val CONTROL_SIZE = 48.dp
private const val BODY_WEIGHT = 450
