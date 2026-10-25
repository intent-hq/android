package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable

@Serializable
data class TerminalInfo(
    val id: String,
    val name: String = "Terminal",
    val cwd: String = "",
    val isExecutingCommand: Boolean = false
)

@Serializable
data class TerminalListResult(val terminals: List<TerminalInfo>, val daemonBootId: String)

@Serializable
data class TerminalBuffer(
    val terminalId: String,
    val data: String,
    val daemonBootId: String? = null,
    val startOffset: String? = null,
    val endOffset: String? = null
)

@Serializable
data class TerminalData(
    val terminalId: String,
    val chunk: String,
    val daemonBootId: String? = null,
    val startOffset: String? = null,
    val endOffset: String? = null
)

@Serializable
data class TerminalAck(val ok: Boolean)
