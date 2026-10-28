package dev.intent.app.browser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.intent.core.protocol.session.RpcSession

val LocalWorkspaceBrowserLink = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** Keep the originating note or conversation mounted while the preview is open. */
@Composable
fun WorkspaceBrowserLinks(
    session: RpcSession,
    workspaceId: String,
    content: @Composable () -> Unit
) {
    var target by rememberSaveable(session, workspaceId) { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalWorkspaceBrowserLink provides { target = it }) { content() }
    target?.let { url ->
        Dialog(
            onDismissRequest = { target = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize()) {
                WorkspaceBrowser(session, workspaceId, { target = null }, initialUrl = url)
            }
        }
    }
}
