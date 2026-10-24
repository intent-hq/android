package dev.intent.app.navigation

sealed interface WorkspaceDestination {
    data object Home : WorkspaceDestination
    data object Conversations : WorkspaceDestination
    data object Git : WorkspaceDestination
    data object Notes : WorkspaceDestination
    data class Note(val id: String) : WorkspaceDestination
}

fun WorkspaceDestination.parent(): WorkspaceDestination? = when (this) {
    WorkspaceDestination.Home -> null
    is WorkspaceDestination.Note -> WorkspaceDestination.Notes
    else -> WorkspaceDestination.Home
}
