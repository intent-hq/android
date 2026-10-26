package dev.intent.app.navigation

import android.content.Context

/** Store only the section, never a child screen, and isolate paired daemon accounts. */
internal class WorkspaceSectionPreferences(context: Context, private val scope: String) {
    private val preferences = context.getSharedPreferences("workspace-sections", Context.MODE_PRIVATE)

    fun load(workspaceId: String): WorkspaceDestination =
        workspaceDestination(preferences.getString("$scope:$workspaceId", null).orEmpty())
            .takeIf { it.isSection() } ?: WorkspaceDestination.Conversations

    fun save(workspaceId: String, section: WorkspaceDestination) {
        require(section.isSection())
        preferences.edit().putString("$scope:$workspaceId", section.routeKey()).apply()
    }
}
