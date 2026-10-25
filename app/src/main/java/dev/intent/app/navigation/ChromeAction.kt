package dev.intent.app.navigation

data class ChromeAction(val label: String, val enabled: Boolean = true, val run: () -> Unit)
