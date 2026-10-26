package dev.intent.app.workspaces

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import dev.intent.app.agents.RpcAgentSession
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.session.RpcSession
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await

/** A row swipe opens Agents even when another workspace section was saved. */
val LocalOpenRecentWorkspaceAgent = staticCompositionLocalOf { false }

internal fun mostRecentWorkspaceAgent(agents: List<Agent>): Agent? = agents
    .filter { it.retiredAt == null }
    .sortedWith(
        compareByDescending<Agent> {
            runCatching {
                Instant.parse(it.lastActivity ?: it.updatedAt ?: it.createdAt)
            }.getOrNull()
        }.thenBy { it.id }
    ).firstOrNull()

/** Leaving the list or opening creation cancels a pending recent-agent lookup. */
@Suppress("TooGenericExceptionCaught")
@Composable
internal fun recentWorkspaceAgentEntry(
    session: RpcSession,
    workspaceId: String,
    selected: String?,
    creating: Boolean,
    onSelected: (String) -> Unit
): String? {
    val openRecent = LocalOpenRecentWorkspaceAgent.current
    var attempted by rememberSaveable(session, workspaceId) { mutableStateOf(false) }
    var errorMessage by remember(session, workspaceId) { mutableStateOf<String?>(null) }
    LaunchedEffect(session, workspaceId, selected, creating) {
        if (openRecent && !attempted) {
            attempted = true
            try {
                mostRecentWorkspaceAgent(RpcAgentSession(session, workspaceId).list().await())
                    ?.let { onSelected(it.id) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message ?: "Cannot open the most recent agent."
            }
        }
    }
    return errorMessage
}
