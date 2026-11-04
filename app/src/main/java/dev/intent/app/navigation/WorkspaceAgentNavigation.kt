package dev.intent.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf

val LocalOpenWorkspaceAgent = staticCompositionLocalOf<((String, String) -> Unit)?> { null }

val LocalConnectionReady = staticCompositionLocalOf { true }

val LocalConnectionName = staticCompositionLocalOf { "Connected host" }
