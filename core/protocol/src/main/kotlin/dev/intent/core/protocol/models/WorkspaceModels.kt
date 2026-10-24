package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Workspace(
    val id: String,
    val title: String,
    val branch: String = "",
    val status: String = "Active",
    val activity: String = "idle",
    val attention: String = "none",
    val createdAt: String = "",
    val updatedAt: String = "",
    val tags: List<String> = emptyList(),
    val statusMessage: String? = null,
    val statusImageAssetId: String? = null,
    val baseRef: String? = null,
    val baseCommitSha: String? = null,
    val lastActivity: String? = null,
    val lastContentActivity: String? = null,
    val path: String? = null,
    val repositoryPath: String? = null,
    val repositoryOwner: String? = null,
    val repositoryName: String? = null,
    val worktreePath: String? = null,
    val archived: Boolean = false,
    val archivedAt: String? = null,
    val isRemote: Boolean = false,
    val skipWorktree: Boolean = false,
    val displayStatus: String? = null,
    val waiting: Boolean = false,
    val scriptsRunning: Boolean = false,
    val pendingDeleteAt: String? = null,
    val taskStats: WorkspaceTaskStats? = null,
    val agentSummary: WorkspaceAgentSummary? = null,
    val prNumber: Long? = null,
    val prUrl: String? = null,
    val prStatus: String? = null,
    val activePullRequest: JsonObject? = null,
    val pullRequests: List<JsonObject>? = null,
    val pullRequestsTotal: Int? = null,
    val myRole: String? = null,
    val memberCount: Int? = null,
    val openInviteCount: Int? = null
)

@Serializable
data class WorkspaceTaskStats(val total: Int, val completed: Int, val inProgress: Int)

@Serializable
data class WorkspaceAgentSummary(
    val count: Int,
    val agents: List<WorkspaceAgentInfo> = emptyList(),
    val agentIds: List<String> = emptyList()
)

@Serializable
data class WorkspaceAgentInfo(
    val id: String,
    val name: String,
    val status: String,
    val specialist: String? = null,
    val lastActivity: String? = null,
    val isStreaming: Boolean = false,
    val isResponding: Boolean = false,
    val parentAgentId: String? = null,
    val isBackground: Boolean = false,
    val notificationsMuted: Boolean = false
)

@Serializable
data class WorkspaceListParams(
    val includeArchived: Boolean? = null,
    val includeRetired: Boolean? = null
)

@Serializable
data class WorkspaceParams(val workspaceId: String)

@Serializable
data class WorkspaceListResult(val workspaces: List<Workspace>)

@Serializable
data class WorkspaceResult(val workspace: Workspace)

@Serializable
data class WorkspaceDeleteParams(val workspaceId: String, val undoDelayMs: Long? = null)

@Serializable
data class WorkspaceCancelDeleteResult(val cancelled: Boolean)

@Serializable
data class SuccessResult(val success: Boolean)

@Serializable
data class WorkspaceCreateParams(
    val title: String? = null,
    val repositoryPath: String? = null,
    val baseRef: String? = null,
    val branch: String? = null,
    val remote: Boolean? = null,
    val skipIsolation: Boolean? = null,
    val githubUrl: String? = null,
    val clonePath: String? = null,
    val isNewRepo: Boolean? = null,
    val progressId: String? = null,
    val tags: List<String>? = null,
    val idempotencyKey: String? = null
)

@Serializable
data class WorkspaceUpdateParams(
    val workspaceId: String,
    val title: String? = null,
    val tags: List<String>? = null,
    val statusMessage: String? = null
)
