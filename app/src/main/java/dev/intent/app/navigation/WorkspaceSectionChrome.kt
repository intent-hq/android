package dev.intent.app.navigation

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.intent.app.design.IntentTextButton as TextButton

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
internal fun WorkspaceResources(
    open: (WorkspaceDestination) -> Unit,
    onCreateTerminal: () -> Unit
) {
    FlowRow {
        TextButton(onClick = { open(WorkspaceDestination.Browser) }) { Text("Browser preview") }
        TextButton(onClick = { open(WorkspaceDestination.Terminals) }) { Text("Terminals") }
        TextButton(onClick = onCreateTerminal) { Text("New terminal") }
    }
}
