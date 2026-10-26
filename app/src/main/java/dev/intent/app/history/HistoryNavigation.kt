package dev.intent.app.history

import androidx.compose.runtime.staticCompositionLocalOf

class HistoryNavigation(
    val store: AgentHistoryStore,
    val connectionId: String?,
    val target: AgentVisit?,
    val consumeTarget: () -> Unit
)

val LocalHistoryNavigation = staticCompositionLocalOf<HistoryNavigation?> { null }
val LocalHistoryWorkspaceTitles = staticCompositionLocalOf { emptyMap<String, String>() }
val LocalHistoryAgent = staticCompositionLocalOf<String?> { null }

@androidx.compose.runtime.Composable
fun RecordAgentVisit(workspaceId: String, agent: dev.intent.core.protocol.models.Agent?) {
    val history = LocalHistoryNavigation.current
    val title = dev.intent.app.navigation.LocalWorkspaceDetails.current?.title
    androidx.compose.runtime.LaunchedEffect(agent?.id, agent?.name, title) {
        if (agent != null && history?.connectionId != null) {
            history.store.record(
                AgentVisit(
                    history.connectionId,
                    workspaceId,
                    agent.id,
                    agent.name ?: "Agent",
                    title
                )
            )
        }
    }
}
