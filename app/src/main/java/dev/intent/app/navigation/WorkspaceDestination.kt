package dev.intent.app.navigation

import androidx.compose.runtime.saveable.Saver

sealed interface WorkspaceDestination {
    data object Browser : WorkspaceDestination
    data object Conversations : WorkspaceDestination
    data object Git : WorkspaceDestination
    data object Terminals : WorkspaceDestination
    data object Notes : WorkspaceDestination
    data class Note(val id: String) : WorkspaceDestination
}

internal fun WorkspaceDestination.isSection(): Boolean =
    this == WorkspaceDestination.Conversations ||
        this == WorkspaceDestination.Notes ||
        this == WorkspaceDestination.Git

fun WorkspaceDestination.parent(origin: WorkspaceDestination? = null): WorkspaceDestination? =
    when {
        isSection() -> null
        origin != null -> origin
        this is WorkspaceDestination.Note -> WorkspaceDestination.Notes
        else -> WorkspaceDestination.Conversations
    }

internal val workspaceDestinationSaver = Saver<WorkspaceDestination, String>(
    save = { it.routeKey() },
    restore = { workspaceDestination(it) }
)

internal fun WorkspaceDestination.routeKey(): String = when (this) {
    WorkspaceDestination.Browser -> "browser"
    WorkspaceDestination.Conversations -> "conversations"
    WorkspaceDestination.Git -> "git"
    WorkspaceDestination.Terminals -> "terminals"
    WorkspaceDestination.Notes -> "notes"
    is WorkspaceDestination.Note -> "note:$id"
}

internal fun workspaceDestination(key: String): WorkspaceDestination = when (key) {
    "browser" -> WorkspaceDestination.Browser
    "conversations" -> WorkspaceDestination.Conversations
    "git" -> WorkspaceDestination.Git
    "terminals" -> WorkspaceDestination.Terminals
    "notes" -> WorkspaceDestination.Notes
    else -> if (key.startsWith("note:")) {
        WorkspaceDestination.Note(key.removePrefix("note:"))
    } else {
        WorkspaceDestination.Conversations
    }
}
