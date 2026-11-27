package dev.intentapp.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalWorkspaceSectionHeader =
    staticCompositionLocalOf<(@Composable (List<ChromeAction>) -> Unit)?> { null }

@Composable
internal fun WorkspaceSectionAppBar(title: String, actions: List<ChromeAction> = emptyList()) {
    val header = LocalWorkspaceSectionHeader.current
    val menu = actions + LocalChromeActions.current
    if (header == null) CompactAppBar(title, actions = menu) else header(menu)
}
