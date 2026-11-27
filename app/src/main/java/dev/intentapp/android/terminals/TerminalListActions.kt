package dev.intentapp.android.terminals

internal data class TerminalListActions(
    val create: () -> Unit = {},
    val delete: (String) -> Unit = {},
    val rename: (String, String) -> Unit = { _, _ -> }
)
