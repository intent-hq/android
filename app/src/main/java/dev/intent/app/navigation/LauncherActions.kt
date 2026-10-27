package dev.intent.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import dev.intent.app.settings.Appearance

internal data class LauncherActions(
    val connectionCount: Int,
    val appearance: Appearance,
    val onAppearance: () -> Unit,
    val onConnections: () -> Unit,
    val onSettings: () -> Unit,
    val onCreate: (() -> Unit)? = null
)

internal val LocalLauncherActions = staticCompositionLocalOf<LauncherActions?> { null }
