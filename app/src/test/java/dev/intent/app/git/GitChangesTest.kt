package dev.intent.app.git

import dev.intent.core.protocol.models.GitChangeStats
import dev.intent.core.protocol.models.GitCommit
import dev.intent.core.protocol.models.GitCommitFile
import dev.intent.core.protocol.models.GitCommitPage
import dev.intent.core.protocol.models.GitDiff
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitRoot
import dev.intent.core.protocol.models.GitStatus
import dev.intent.core.protocol.models.GitTrackedChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitChangesTest {
    @Test
    fun freshStatusOwnsMembershipStageAndStatus() {
        val files =
            listOf(
                GitFileStatus("src/a", "M", true),
                GitFileStatus("src/a", "D", false),
                GitFileStatus("b", "?", false)
            )
        val tracking = listOf(
            GitTrackedChange("src/a", "staged", GitChangeStats(2, 3)),
            GitTrackedChange("b", "committed", GitChangeStats(9, 9)),
            GitTrackedChange("stale", "unstaged", GitChangeStats(9, 9)),
            GitTrackedChange("/src/a", "unstaged", GitChangeStats(9, 9))
        )
        val result = enrichGitStatus(GitStatus(files = files, filesTruncated = true), tracking)
        assertEquals(files, result.files.map { it.copy(metadata = null) })
        assertEquals(2, result.files[0].metadata?.stats?.additions)
        assertNull(result.files[1].metadata)
        assertNull(result.files[2].metadata)
        assertTrue(result.filesTruncated)
        assertTrue(enrichGitStatus(GitStatus(), tracking).files.isEmpty())
    }

    @Test
    fun treeIsFoldersFirstAndCollapseKeepsSiblings() {
        val files = listOf("z", "src/z", "src/nested/b", "src/a", "a/b").map {
            GitFileStatus(it, "M", false)
        }
        val expanded = gitTreeRows(files, emptySet())
        assertEquals(
            listOf("a", "a/b", "src", "src/nested", "src/nested/b", "src/a", "src/z", "z"),
            expanded.map {
                it.path
            }
        )
        assertEquals(3, expanded.single { it.path == "src" }.count)
        assertEquals(2, expanded.single { it.path == "src/nested/b" }.depth)
        assertEquals(
            listOf("a", "a/b", "src", "z"),
            gitTreeRows(files, setOf("src")).map {
                it.path
            }
        )
        assertEquals(files.toSet(), expanded.mapNotNull { it.file }.toSet())
    }

    @Test
    fun rootSwitchAndRemovalRejectLateSnapshotsAndDiffs() {
        val source = FakeGitSession()
        val store = GitStore(source)
        store.refresh()
        source.complete("main", listOf(GitCommit("main")))
        store.select(GitFileStatus("old", "M", false))
        store.refresh()
        store.selectRoot("secondary")
        assertEquals("secondary", source.requestedRoots.last()?.id)
        assertTrue(store.state.value.commits.isEmpty())
        assertNull(store.state.value.selected)
        source.complete("secondary", listOf(GitCommit("root")), 2)
        source.complete("stale", listOf(GitCommit("stale")), 1)
        source.diffs[0].complete(listOf(GitDiff("old")))
        assertEquals("root", store.state.value.commits.single().hash)
        assertTrue(store.state.value.diffs.isEmpty())
        source.roots = emptyList()
        store.refresh()
        assertNull(source.requestedRoots.last())
        source.complete("main", emptyList(), 3)
        assertNull(store.state.value.selectedRoot)
        assertFalse(store.state.value.loading)
    }

    @Test
    fun secondaryHistoryStopsAtRegistrationBoundary() {
        val root = GitRoot("r", "/r", registeredCommitSha = "boundary")
        val page =
            GitCommitPage(listOf(GitCommit("new"), GitCommit("boundary"), GitCommit("old")), "next")
        assertEquals(listOf("new"), boundCommits(page, root).items.map { it.hash })
        assertNull(boundCommits(page, root).nextToken)
        assertEquals("next", boundCommits(page, root.copy(registeredCommitSha = "later")).nextToken)
    }

    @Test
    fun leavingCommitRejectsLateFileAndHunkReplies() {
        val source = FakeGitSession()
        val store = GitStore(source)
        store.selectCommit(GitCommit("a"))
        store.selectCommit(GitCommit("b"))
        source.commitReads[0].complete(listOf(GitCommitFile("old")))
        assertTrue(store.state.value.commitFiles.isEmpty())
        source.commitReads[1].complete(listOf(GitCommitFile("new")))
        assertEquals("new", store.state.value.selected?.path)
        store.select(null)
        source.diffs.single().complete(listOf(GitDiff("new")))
        assertNull(store.state.value.selectedCommit)
        assertTrue(store.state.value.diffs.isEmpty())
    }
}
