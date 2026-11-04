package dev.intent.app.workspaces

import dev.intent.app.attachments.MediaReference
import dev.intent.app.attachments.WorkspaceAsset
import dev.intent.core.protocol.models.Workspace
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal enum class WorkspaceGrouping(val label: String) {
    Recent("Recent"),
    Repo("Repo"),
    Status("Status")
}

/** Presentation order only. The daemon owns status derivation (protocol §5.1). */
internal enum class WorkspaceDisplayStatus(val wire: String, val label: String) {
    Failed("failed", "Failed"),
    Blocked("blocked", "Blocked"),
    NeedsAttention("needs_attention", "Needs attention"),
    Idle("idle", "Idle"),
    NotStarted("not_started", "No code changes"),
    InProgress("in_progress", "In progress"),
    Complete("complete", "Complete"),
    PrOpen("pr_open", "PR open"),
    PrReady("pr_ready", "PR mergeable"),
    PrQueued("pr_queued", "PR queued"),
    PrMerged("pr_merged", "PR merged"),
    Unknown("", "Unknown status")
}

internal fun workspaceDisplayStatus(workspace: Workspace): WorkspaceDisplayStatus =
    when (val status = workspace.displayStatus) {
        null -> WorkspaceDisplayStatus.NotStarted
        "unread" -> WorkspaceDisplayStatus.Idle // Retired wire value from older daemons.
        else ->
            WorkspaceDisplayStatus.entries.firstOrNull { it.wire == status && status.isNotEmpty() }
                ?: WorkspaceDisplayStatus.Unknown
    }

internal fun workspaceRepository(workspace: Workspace): String? {
    val name = workspace.repositoryName?.takeIf { it.isNotBlank() } ?: return null
    val owner = workspace.repositoryOwner?.takeIf { it.isNotBlank() }
    return if (owner == null) name else "$owner/$name"
}

internal data class WorkspaceGroup(val title: String?, val workspaces: List<Workspace>)

internal fun workspaceGroups(
    workspaces: List<Workspace>,
    query: String,
    showArchived: Boolean,
    grouping: WorkspaceGrouping
): List<WorkspaceGroup> {
    val search = query.trim()
    val rows =
        workspaces
            .filter { workspace ->
                !workspace.status.equals("Deleted", true) &&
                    (
                        showArchived ||
                            !(workspace.archived || workspace.status.equals("Archived", true))
                        ) &&
                    listOfNotNull(
                        workspace.title,
                        workspace.statusMessage,
                        workspaceRepository(workspace)
                    )
                        .any { it.contains(search, ignoreCase = true) }
            }.sortedByDescending(::workspaceActivityTime)
    return when (grouping) {
        WorkspaceGrouping.Recent -> if (rows.isEmpty()) {
            emptyList()
        } else {
            listOf(
                WorkspaceGroup(null, rows)
            )
        }
        WorkspaceGrouping.Repo ->
            rows
                .groupBy { workspaceRepository(it) ?: "No repository" }
                .map { (title, members) -> WorkspaceGroup(title, members) }
        WorkspaceGrouping.Status ->
            rows
                .groupBy(::workspaceDisplayStatus)
                .toSortedMap()
                .map { (status, members) -> WorkspaceGroup(status.label, members) }
    }
}

/** Metadata writes must not reorder workspaces ahead of actual activity or creation. */
internal fun workspaceActivityTime(workspace: Workspace): Instant =
    listOf(workspace.lastActivity, workspace.createdAt, workspace.updatedAt)
        .firstNotNullOfOrNull { raw ->
            raw?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            }?.takeIf { it > Instant.EPOCH }
        } ?: Instant.EPOCH

internal fun workspaceActivityLabel(workspace: Workspace): String? =
    workspaceActivityTime(workspace).takeIf { it != Instant.EPOCH }?.let {
        val formatted =
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
                .format(it)
        "Activity · $formatted"
    }

internal fun workspaceSignals(workspace: Workspace): List<String> = buildList {
    if (workspace.activity == "agent_running") add("Agent running")
    when (workspace.attention) {
        "unread" -> add("Unread updates")
        "review_required" -> add("Review required")
    }
    if (workspace.waiting) add("Waiting")
    if (workspace.scriptsRunning) add("Scripts running")
}

internal fun workspacePrLabel(workspace: Workspace): String? = workspace.prNumber
    ?.takeIf { it > 0 }
    ?.let { number ->
        val status =
            workspace.prStatus
                ?.takeIf { it.isNotBlank() }
                ?.replace('_', ' ')
                ?.replaceFirstChar { it.titlecase() }
        listOfNotNull("PR #$number", status).joinToString(" · ")
    }

internal fun workspaceStateLabel(workspace: Workspace): String = listOfNotNull(
    workspace.status.takeIf { it.isNotBlank() },
    workspaceDisplayStatus(workspace).label
).joinToString(" · ")

internal fun workspaceStatusImage(workspace: Workspace): MediaReference? =
    workspace.statusImageAssetId
        ?.takeIf { it.isNotBlank() }
        ?.let { assetId ->
            WorkspaceAsset(workspace.id, assetId).takeIf { it.isSafe() }?.let { asset ->
                MediaReference(name = "Workspace status image", image = true, asset = asset)
            }
        }
