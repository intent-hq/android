package dev.intent.app.history

internal data class AgentHistoryIdentity(val title: String, val subtitle: String)

/** Workspace IDs and their live titles belong to the active connection only. */
internal fun AgentVisit.historyIdentity(
    activeConnectionId: String?,
    liveTitles: Map<String, String>,
    deviceName: String?
): AgentHistoryIdentity {
    val workspace = liveTitles[workspaceId]
        ?.takeIf { connectionId == activeConnectionId && it.isNotBlank() }
        ?: workspaceTitle?.takeIf { it.isNotBlank() }
    return AgentHistoryIdentity(
        title = workspace ?: agentName,
        subtitle = listOfNotNull(deviceName, agentName.takeIf { workspace != null })
            .filter { it.isNotBlank() }.joinToString(" · ")
    )
}
