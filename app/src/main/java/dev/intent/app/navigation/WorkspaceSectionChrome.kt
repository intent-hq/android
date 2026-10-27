package dev.intent.app.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalWorkspaceSectionHeader =
    staticCompositionLocalOf<(@Composable (List<ChromeAction>) -> Unit)?> { null }
internal val LocalWorkspaceResources = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
internal fun WorkspaceSectionAppBar(title: String, actions: List<ChromeAction> = emptyList()) {
    val header = LocalWorkspaceSectionHeader.current
    val menu = actions + LocalChromeActions.current
    if (header == null) CompactAppBar(title, actions = menu) else header(menu)
}

@Composable
internal fun WorkspaceResources(open: (WorkspaceDestination) -> Unit) {
    Row {
        TextButton(onClick = { open(WorkspaceDestination.Browser) }) { Text("Browser preview") }
        TextButton(onClick = { open(WorkspaceDestination.Terminals) }) { Text("Terminals") }
    }
}
