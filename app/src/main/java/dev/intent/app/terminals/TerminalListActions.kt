package dev.intent.app.terminals

internal data class TerminalListActions(
    val create: () -> Unit = {},
    val delete: (String) -> Unit = {},
    val rename: (String, String) -> Unit = { _, _ -> }
)
