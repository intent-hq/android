package dev.intent.app.git

import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitStatus
import dev.intent.core.protocol.models.Workspace
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitPresentationTest {
    @Test
    fun cleanRequiresACompleteSuccessfulStatus() {
        val state = GitState(loading = false)
        assertTrue(state.isClean())
        assertFalse(state.copy(loading = true).isClean())
        assertFalse(state.copy(error = "Disconnected").isClean())
        listOf(
            GitStatus(filesTruncated = true),
            GitStatus(hasUncommittedChanges = true),
            GitStatus(hasUntrackedFiles = true),
            GitStatus(files = listOf(GitFileStatus("new.kt", "A", true)))
        ).forEach { assertFalse(state.copy(status = it).isClean()) }
    }

    @Test
    fun repositoryUsesIdentityInsteadOfWorkspaceTitleOrWorktreeName() {
        val workspace = Workspace(
            "workspace",
            "Fix preview",
            repositoryOwner = "intent-hq",
            repositoryName = "android",
            repositoryPath = "/repos/android",
            worktreePath = "/workspaces/fix-preview"
        )
        assertEquals("intent-hq/android", repositoryLabel(workspace))
        assertEquals("android", repositoryLabel(workspace.copy(repositoryOwner = null)))
        assertEquals("android", repositoryLabel(workspace.copy(repositoryName = " ")))
        assertNull(repositoryLabel(workspace.copy(repositoryName = null, repositoryPath = null)))
        assertNull(repositoryLabel(null))
    }

    @Test
    fun dateIsShortAndUsesTheLocalDay() {
        assertEquals(
            "Oct 24, 2026",
            commitDate("2026-10-25T01:30:00Z", ZoneId.of("America/Los_Angeles"), Locale.US)
        )
        assertEquals(
            "Oct 25, 2026",
            commitDate("2026-10-25T01:30:00.123+00:00", ZoneId.of("UTC"), Locale.US)
        )
        assertEquals("Date unavailable", commitDate("invalid"))
        assertEquals("Date unavailable", commitDate(""))
    }
}
