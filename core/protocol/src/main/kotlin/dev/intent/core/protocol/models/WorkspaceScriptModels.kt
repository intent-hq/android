package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceScriptResource(
    val id: String,
    val workspaceId: String,
    val name: String,
    val command: String,
    val mode: String,
    val runtime: WorkspaceScriptRuntime? = null
)

@Serializable
data class WorkspaceScriptRuntime(
    val status: String,
    val restartCount: Int = 0,
    val exitCode: Int? = null,
    val error: String? = null,
    val detectedUrl: String? = null,
    val previouslyRunning: Boolean = false
) {
    // Unknown future transitions remain live, as required by §5.8.
    val isLive: Boolean get() = status != "idle" && status != "exited"
}

@Serializable
data class WorkspaceScriptList(val scripts: List<WorkspaceScriptResource>)

@Serializable
data class WorkspaceScriptActionResult(val ok: Boolean, val scriptId: String)
