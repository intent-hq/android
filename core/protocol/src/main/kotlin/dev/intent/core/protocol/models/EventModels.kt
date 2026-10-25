package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class NoteChangedData(
    val noteId: String,
    val title: String,
    val action: String,
    val note: Note? = null
)

@Serializable
data class WorkspaceCreatedData(
    val workspaceId: String,
    val workspace: Workspace,
    val origin: JsonObject? = null
)

@Serializable
data class WorkspaceUpdatedData(val workspaceId: String, val changes: JsonObject)

@Serializable
data class WorkspaceDeletedData(val workspaceId: String)

@Serializable
data class WorkspaceDeleteScheduledData(val workspaceId: String, val deleteAt: String)

@Serializable
data class WorkspaceActivityData(val workspaceId: String, val activity: String)

@Serializable
data class WorkspaceAttentionData(val workspaceId: String, val attention: String)

@Serializable
data class WorkspaceDisplayStatusData(val workspaceId: String, val displayStatus: String)

@Serializable
data class WorkspaceWaitingData(val workspaceId: String, val waiting: Boolean)

@Serializable
data class WorkspaceScriptsRunningData(val workspaceId: String, val scriptsRunning: Boolean)

@Serializable
data class WorkspaceTaskStatsData(val workspaceId: String, val taskStats: WorkspaceTaskStats)

@Serializable
data class WorkspaceContextData(val workspaceId: String, val items: List<JsonObject>)

@Serializable
data class WorkspaceSetupCompletedData(
    val workspaceId: String,
    val ranScript: Boolean,
    val exitCode: Long? = null
)

@Serializable
data class TaskCreatedData(
    val noteId: String,
    val noteTitle: String,
    val status: String,
    val createdAt: String,
    val agentId: String? = null,
    val note: Note? = null
)

@Serializable
data class TaskStatusChangedData(
    val noteId: String,
    val noteTitle: String,
    val changedAt: String,
    val previousStatus: String,
    val newStatus: String,
    val agentId: String? = null,
    val note: Note? = null
)

@Serializable
data class TaskReadyData(
    val readyTaskIds: List<String>,
    val triggeredBy: TaskReadyTrigger,
    val computedAt: String
)

@Serializable
data class TaskReadyTrigger(
    val noteId: String,
    val previousStatus: String? = null,
    val newStatus: String? = null,
    val reason: String? = null
)

@Serializable
data class TasksUpdatedData(
    val workspaceId: String,
    val tasks: List<WorkspaceTask>,
    val removedIds: List<String>
)

@Serializable
data class TaskAgentLinkData(
    val workspaceId: String,
    val noteId: String,
    val taskKey: String,
    val link: JsonObject? = null
)

@Serializable
data class CommentAddedData(val noteId: String, val commentId: String)

@Serializable
data class CommentDeletedData(val noteId: String, val commentId: String, val threadId: String)

@Serializable
data class CommentResolvedData(val noteId: String, val threadId: String, val resolved: Boolean)
