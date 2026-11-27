package dev.intentapp.android.terminals

import dev.intent.core.protocol.models.TerminalInfo

/** Terminal list state and actions for the Agents page section. */
class WorkspaceTerminalsHandle internal constructor(
    val state: TerminalState,
    val open: (String) -> Unit,
    val rename: (TerminalInfo) -> Unit,
    val delete: (String) -> Unit,
    val refresh: () -> Unit
)
