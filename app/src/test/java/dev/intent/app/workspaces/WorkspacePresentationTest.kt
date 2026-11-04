package dev.intent.app.workspaces

import dev.intent.core.protocol.models.Workspace
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePresentationTest {
    @Test fun canonicalStatusesUseServerValueWithoutActivityOverrides() {
        WorkspaceDisplayStatus.entries.filter { it != WorkspaceDisplayStatus.Unknown }.forEach {
            assertEquals(
                it,
                workspaceDisplayStatus(
                    Workspace(
                        "id",
                        "Workspace",
                        displayStatus = it.wire,
                        activity = "agent_running"
                    )
                )
            )
        }
        assertEquals(
            WorkspaceDisplayStatus.NotStarted,
            workspaceDisplayStatus(Workspace("id", "Name"))
        )
        listOf("future_status", "", "working", "Active").forEach {
            assertEquals(
                WorkspaceDisplayStatus.Unknown,
                workspaceDisplayStatus(Workspace("id", "Name", displayStatus = it))
            )
        }
        assertEquals(
            WorkspaceDisplayStatus.Idle,
            workspaceDisplayStatus(Workspace("id", "Name", displayStatus = "unread"))
        )
        assertEquals("PR mergeable", WorkspaceDisplayStatus.PrReady.label)
        assertEquals("No code changes", WorkspaceDisplayStatus.NotStarted.label)
    }

    @Test fun searchIncludesRepositoryOwnerAndNameAndHidesDeletedEvenWithArchives() {
        val workspaces =
            listOf(
                Workspace("active", "One", repositoryOwner = "Intent", repositoryName = "Android"),
                Workspace("archived", "Two", status = "Archived", repositoryName = "Android"),
                Workspace("flag", "Three", archived = true, repositoryName = "Android"),
                Workspace("deleted", "Four", status = "Deleted", repositoryName = "Android")
            )
        assertEquals(listOf("active"), ids(workspaces, "  intent/ANDROID "))
        assertEquals(listOf("active"), ids(workspaces, "android"))
        assertEquals(
            listOf("active", "archived", "flag"),
            ids(workspaces, "android", archived = true)
        )
        assertTrue(ids(workspaces, "missing").isEmpty())
    }

    @Test fun recentUsesActivityThenCreationThenUpdateAndKeepsStableTies() {
        val active =
            Workspace(
                "active",
                "Active",
                lastActivity = "2026-11-04T12:00:00Z",
                updatedAt = "2026-11-05T00:00:00Z"
            )
        val metadata =
            Workspace(
                "metadata",
                "Metadata",
                createdAt = "2026-11-01T00:00:00Z",
                updatedAt = "2026-11-06T00:00:00Z"
            )
        val fallback =
            Workspace(
                "fallback",
                "Fallback",
                lastActivity = "invalid",
                createdAt = "",
                updatedAt = "2026-11-03T00:00:00Z"
            )
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), workspaceActivityTime(metadata))
        assertEquals(
            listOf("active", "fallback", "metadata", "tie-a", "tie-b"),
            ids(
                listOf(metadata, Workspace("tie-a", "A"), fallback, active, Workspace("tie-b", "B"))
            )
        )
        assertNull(workspaceActivityLabel(Workspace("id", "No timestamp")))
    }

    @Test fun repoGroupsFollowMostRecentMemberAndKeepDifferentOwnersSeparate() {
        val groups =
            workspaceGroups(
                listOf(
                    Workspace("old", "Old", repositoryOwner = "one", repositoryName = "app"),
                    Workspace("other", "Other", repositoryOwner = "two", repositoryName = "app"),
                    Workspace(
                        "new",
                        "New",
                        repositoryOwner = "one",
                        repositoryName = "app",
                        lastActivity = "2026-11-04T00:00:00Z"
                    ),
                    Workspace("none", "None")
                ),
                "",
                false,
                WorkspaceGrouping.Repo
            )
        assertEquals(listOf("one/app", "two/app", "No repository"), groups.map { it.title })
        assertEquals(listOf("new", "old"), groups.first().workspaces.map { it.id })
    }

    @Test fun statusGroupingPrioritizesAttentionAndPlacesUnknownLast() {
        val groups =
            workspaceGroups(
                listOf("future", "pr_queued", "in_progress", "needs_attention", "failed").map {
                    Workspace(it, it, displayStatus = it)
                },
                "",
                false,
                WorkspaceGrouping.Status
            )
        assertEquals(
            listOf("Failed", "Needs attention", "In progress", "PR queued", "Unknown status"),
            groups.map { it.title }
        )
    }

    @Test fun metadataKeepsIndependentSignalsAndNeverInventsPullRequests() {
        val workspace =
            Workspace(
                "id",
                "Name",
                activity = "agent_running",
                attention = "unread",
                waiting = true,
                scriptsRunning = true,
                prNumber = 42,
                prStatus = "Open"
            )
        assertEquals(
            listOf("Agent running", "Unread updates", "Waiting", "Scripts running"),
            workspaceSignals(workspace)
        )
        assertEquals("PR #42 · Open", workspacePrLabel(workspace))
        assertNull(workspacePrLabel(workspace.copy(prNumber = null)))
        assertFalse(
            workspaceSignals(
                workspace.copy(attention = "review_required")
            ).contains("Unread updates")
        )
        assertEquals(
            listOf("Review required"),
            workspaceSignals(Workspace("id", "Name", attention = "review_required"))
        )
    }

    @Test fun statusImagesUseSafeWorkspaceAssets() {
        val workspace = Workspace("id", "Name", statusImageAssetId = "status.png")
        val image = requireNotNull(workspaceStatusImage(workspace))
        assertEquals("id", image.asset?.workspaceId)
        assertEquals("status.png", image.asset?.assetId)
        assertTrue(image.image)
        assertNull(workspaceStatusImage(workspace.copy(statusImageAssetId = "../status.png")))
        assertNull(workspaceStatusImage(workspace.copy(statusImageAssetId = " ")))
    }

    private fun ids(workspaces: List<Workspace>, query: String = "", archived: Boolean = false) =
        workspaceGroups(workspaces, query, archived, WorkspaceGrouping.Recent)
            .flatMap { it.workspaces }
            .map { it.id }
}
