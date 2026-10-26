package dev.intent.app.workspaces

import androidx.compose.runtime.staticCompositionLocalOf
import dev.intent.core.protocol.models.Agent
import java.time.Instant

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
