package dev.intent.app.workspaces

import dev.intent.core.protocol.models.Workspace
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

enum class WorkspaceAction(val label: String, val method: String) {
    Rename("Rename", "workspace.update"),
    Archive("Archive", "workspace.archive"),
    Delete("Delete", "workspace.delete")
}

internal data class WorkspaceActionState(
    val workspace: Workspace? = null,
    val action: WorkspaceAction? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val removedId: String? = null
)

/** Mutations require an explicit matching server confirmation and never retry automatically. */
internal class WorkspaceActionStore(
    private val workspaces: WorkspaceStore,
    private val request: (String, JsonObject) -> CompletableFuture<JsonElement>
) : Closeable {
    private val mutableState = MutableStateFlow(WorkspaceActionState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var closed = false

    @Synchronized
    fun begin(workspace: Workspace, action: WorkspaceAction) {
        if (closed || state.value.busy) return
        generation++
        mutableState.value = WorkspaceActionState(workspace, action)
    }

    @Synchronized
    fun cancel() {
        if (state.value.busy) return
        generation++
        mutableState.value = WorkspaceActionState()
    }

    @Synchronized
    fun submit(draft: String = "") {
        val current = state.value
        val hasTarget = current.workspace != null && current.action != null
        if (closed || current.busy || !hasTarget) return
        val workspace = requireNotNull(current.workspace)
        val action = requireNotNull(current.action)
        val title = draft.trim()
        if (action == WorkspaceAction.Rename &&
            (title.isEmpty() || title == workspace.title.trim())
        ) {
            if (title.isEmpty()) {
                mutableState.value = current.copy(error = "Enter a workspace name.")
            } else {
                cancel()
            }
            return
        }
        val token = ++generation
        mutableState.value = current.copy(busy = true, error = null)
        val params = buildJsonObject {
            put("workspaceId", workspace.id)
            if (action == WorkspaceAction.Rename) put("title", title)
        }
        request(action.method, params).whenComplete { result, error ->
            finish(token, current, title, result, error)
        }
    }

    @Synchronized
    private fun finish(
        token: Long,
        current: WorkspaceActionState,
        title: String,
        result: JsonElement?,
        error: Throwable?
    ) {
        if (closed || token != generation) return
        val workspace = requireNotNull(current.workspace)
        val action = requireNotNull(current.action)
        if (error != null || !confirmed(result, action, workspace.id, title)) {
            mutableState.value = current.copy(
                error = error?.cause?.message ?: error?.message
                    ?: "The server did not confirm the workspace change. Try again."
            )
            return
        }
        workspaces.confirmChange(
            workspace.id,
            title = title.takeIf { action == WorkspaceAction.Rename },
            archived = action == WorkspaceAction.Archive
        )
        mutableState.value = WorkspaceActionState(
            removedId = workspace.id.takeIf { action != WorkspaceAction.Rename }
        )
    }

    @Synchronized
    override fun close() {
        closed = true
        generation++
    }
}

private fun confirmed(
    result: JsonElement?,
    action: WorkspaceAction,
    id: String,
    title: String
): Boolean {
    val response = result as? JsonObject
    val workspace = response?.get("workspace") as? JsonObject
    val matches = (workspace?.get("id") as? JsonPrimitive)?.contentOrNull == id
    val confirmedTitle = (workspace?.get("title") as? JsonPrimitive)?.contentOrNull
    val confirmedStatus = (workspace?.get("status") as? JsonPrimitive)?.contentOrNull
    val success = (response?.get("success") as? JsonPrimitive)?.booleanOrNull
    return when (action) {
        WorkspaceAction.Rename -> matches && confirmedTitle == title
        WorkspaceAction.Archive -> matches && confirmedStatus.equals("archived", ignoreCase = true)
        WorkspaceAction.Delete -> success == true
    }
}
