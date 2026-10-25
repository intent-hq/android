package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,
    val title: String,
    val workspaceId: String? = null,
    val content: String? = null,
    val contentPreview: String? = null,
    val contentLength: Long? = null,
    val contentType: String? = null,
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isDefault: Boolean = false,
    val parentId: String? = null,
    val visibility: String? = null,
    val metadata: NoteMetadata? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val rev: Long = 0,
    val specLinked: Boolean? = null,
    val contentTruncated: Boolean = false,
    val contentBytes: Long? = null
)

@Serializable
data class NoteMetadata(val task: TaskMetadata? = null)

@Serializable
data class TaskMetadata(
    val status: String,
    val assignedAgentIds: List<String> = emptyList(),
    val acceptanceCriteria: List<String> = emptyList(),
    val estimatedEffort: String? = null,
    val actualEffort: String? = null,
    val blockedReason: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val peerOrder: Long? = null,
    val dependsOn: List<String> = emptyList(),
    val conflictsWith: List<String> = emptyList(),
    val unmetDependsOn: List<String> = emptyList()
)

@Serializable
data class NoteListParams(val workspaceId: String, val projection: String? = null)

@Serializable
data class NoteGetParams(
    val workspaceId: String,
    val noteId: String,
    val maxContentBytes: Long? = null
)

@Serializable
data class NoteParams(val workspaceId: String, val noteId: String)

@Serializable
data class NoteListResult(val notes: List<Note>)

@Serializable
data class NoteResult(val note: Note)

@Serializable
data class NoteTaskRow(
    val lineNumber: Int,
    val text: String,
    val status: String,
    val taskNoteId: String? = null,
    val linkedTaskNoteId: String? = null,
    val dependsOn: List<String> = emptyList(),
    val conflictsWith: List<String> = emptyList(),
    val unmetDependsOn: List<String> = emptyList()
)

@Serializable
data class WorkspaceTask(
    val id: String,
    val title: String,
    val status: String,
    val updatedAt: String,
    val specLinked: Boolean = false,
    val parentId: String? = null,
    val dependsOn: List<String> = emptyList(),
    val conflictsWith: List<String> = emptyList(),
    val unmetDependsOn: List<String> = emptyList()
)

@Serializable
data class TaskListParams(val workspaceId: String, val status: String? = null)

@Serializable
data class TaskGetParams(val workspaceId: String, val taskNoteId: String)

@Serializable
data class TaskListResult(val tasks: List<WorkspaceTask>, val stats: WorkspaceTaskStats)

@Serializable
data class TaskResult(val task: WorkspaceTask)

@Serializable
data class TaskUpdateNoteStatusParams(
    val workspaceId: String,
    val noteId: String,
    val status: String,
    val expectedVersion: Long? = null
)

@Serializable
data class TaskUpdateNoteStatusResult(
    val ok: Boolean,
    val noteId: String,
    val status: String,
    val note: Note,
    val advisory: String? = null
)

@Serializable
data class TaskUpdateStatusParams(
    val workspaceId: String,
    val noteId: String,
    val taskText: String,
    val status: String
)

@Serializable
data class TaskUpdateStatusResult(
    val ok: Boolean,
    val noteId: String,
    val taskText: String,
    val status: String
)
