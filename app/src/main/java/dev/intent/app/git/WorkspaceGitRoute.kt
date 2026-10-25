package dev.intent.app.git

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.intent.app.navigation.LocalChromeBack
import dev.intent.core.protocol.session.RpcSession

@Composable
fun WorkspaceGitRoute(session: RpcSession, workspaceId: String, onBack: () -> Unit) {
    val store = remember(session, workspaceId) { GitStore(RpcGitSession(session, workspaceId)) }
    val state by store.state.collectAsState()
    DisposableEffect(store) {
        store.start()
        onDispose { store.close() }
    }
    val back = { if (state.selected == null) onBack() else store.select(null) }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalChromeBack provides back) {
            if (state.selected == null) {
                GitScreen(state, store::select, store::refresh, store::loadMore)
            } else {
                GitDiffScreen(state) { store.select(state.selected) }
            }
        }
    }
}
