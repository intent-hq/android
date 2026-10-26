package dev.intent.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceRouteRestoreTest {
    @Test
    fun legacyOverviewRestoresTheAgentsSection() {
        assertEquals(WorkspaceDestination.Conversations, workspaceDestination("home"))
    }

    @Test
    fun everyWorkspaceRouteSurvivesUtilityNavigation() {
        val destinations = listOf(
            WorkspaceDestination.Conversations,
            WorkspaceDestination.Browser,
            WorkspaceDestination.Git,
            WorkspaceDestination.Terminals,
            WorkspaceDestination.Notes,
            WorkspaceDestination.Note("note:with/separators")
        )
        destinations.forEach { assertEquals(it, workspaceDestination(it.routeKey())) }
    }
}
