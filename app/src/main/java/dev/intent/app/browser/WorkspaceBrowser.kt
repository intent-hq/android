package dev.intent.app.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.intentPageInsets
import dev.intent.core.protocol.session.RpcSession

@Composable
fun WorkspaceBrowser(
    session: RpcSession,
    workspaceId: String,
    onBack: () -> Unit,
    initialUrl: String? = null,
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
    BrowserScreen(
        state,
        onBack,
        store::open,
        initialAddress = initialUrl ?: "",
        onPageBack = page::back,
        onPageForward = page::forward,
        onReload = {
            if (state.error == null &&
                state.url != null
            ) {
                page.reload()
            } else {
                state.requestedUrl?.let(store::open)
            }
        }
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
    page: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        BrowserAddressBar(state, initialAddress, onBack, open, onPageBack, onPageForward, onReload)
        Box(Modifier.weight(1f)) {
            when {
                state.error != null -> Column(Modifier.intentPageInsets()) {
                    IntentSection("Preview unavailable") {
                        Text(state.error, color = IntentScreen.error)
                    }
                }
                state.url == null && !state.loading -> Column(Modifier.intentPageInsets()) {
                    IntentSection("Open a workspace preview") {
                        Text("Enter the server URL, such as localhost:3000, then press Go.")
                        Text(
                            "Start the server in this workspace on the host.",
                            color = IntentScreen.secondaryText
                        )
                    }
                }
                else -> page()
            }
        }
    }
}
