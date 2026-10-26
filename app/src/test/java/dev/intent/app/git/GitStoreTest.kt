package dev.intent.app.git

import dev.intent.core.protocol.models.GitCommit
import dev.intent.core.protocol.models.GitCommitFile
import dev.intent.core.protocol.models.GitCommitPage
import dev.intent.core.protocol.models.GitDiff
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitRoot
import dev.intent.core.protocol.models.GitStatus
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitStoreTest {
    @Test
    fun loadsAfterSubscriptionAndRefreshesOnEvents() {
        val source = FakeGitSession()
        val store = GitStore(source)
        store.start()
        assertEquals(0, source.statusReads.size)
        source.refresh()
        source.complete("main", listOf(GitCommit("a")))
        assertEquals("main", store.state.value.status.branch)
        assertFalse(store.state.value.loading)
        source.refresh()
        source.complete("feature", listOf(GitCommit("b")))
        assertEquals("b", store.state.value.commits.single().hash)
    }

    @Test
    fun newerRefreshWinsAndCloseRejectsLateReads() {
        val source = FakeGitSession()
        val store = GitStore(source)
        store.refresh()
        store.refresh()
        source.complete("new", emptyList(), 1)
        source.complete("old", emptyList(), 0)
        assertEquals("new", store.state.value.status.branch)
        store.refresh()
        store.close()
        source.complete("closed", emptyList(), 2)
        assertEquals("new", store.state.value.status.branch)
    }

    @Test
    fun pagesDeduplicateAndRejectRepeatedTokens() {
        val source = FakeGitSession()
        val store = GitStore(source)
        store.refresh()
        source.statusReads[0].complete(GitStatus())
        source.pages[0].complete(GitCommitPage(listOf(GitCommit("a")), "next"))
        store.loadMore()
        assertEquals("next", source.tokens.last())
        source.pages[1].complete(GitCommitPage(listOf(GitCommit("a"), GitCommit("b")), "last"))
        assertEquals(listOf("a", "b"), store.state.value.commits.map { it.hash })
        store.loadMore()
        source.pages[2].complete(GitCommitPage(emptyList(), "last"))
        assertTrue(store.state.value.error!!.contains("did not advance"))
        assertFalse(store.state.value.loadingMore)
    }

    @Test
    fun refreshInvalidatesPendingHistoryPage() {
        val source = FakeGitSession()
        val store = GitStore(source)
        store.refresh()
        source.statusReads[0].complete(GitStatus())
        source.pages[0].complete(GitCommitPage(listOf(GitCommit("a")), "next"))
        store.loadMore()
        store.refresh()
        source.statusReads[1].complete(GitStatus())
        source.pages[2].complete(GitCommitPage(listOf(GitCommit("new"))))
        source.pages[1].complete(GitCommitPage(listOf(GitCommit("old"))))
        assertEquals("new", store.state.value.commits.single().hash)
    }

    @Test
    fun diffSelectionRejectsStaleResponsesAndRetainsStagedSide() {
        val source = FakeGitSession()
        val store = GitStore(source)
        store.select(GitFileStatus("a", "M", false))
        store.select(GitFileStatus("a", "M", true))
        assertTrue(source.files.last().staged)
        source.diffs[1].complete(listOf(GitDiff("a")))
        source.diffs[0].completeExceptionally(IllegalStateException("stale"))
        assertNull(store.state.value.diffError)
        assertEquals("a", store.state.value.diffs.single().path)
        store.select(null)
        assertTrue(store.state.value.diffs.isEmpty())
    }

    @Test
    fun failuresAreVisibleAndRetryRecovers() {
        val source = FakeGitSession()
        val store = GitStore(source)
        store.start()
        source.refresh()
        source.statusReads[0].completeExceptionally(IllegalStateException("offline"))
        source.pages[0].complete(GitCommitPage())
        assertTrue(store.state.value.error!!.contains("offline"))
        store.refresh()
        source.complete("main", emptyList(), 1)
        assertNull(store.state.value.error)
        store.close()
        assertTrue(source.closed)
    }
}

internal class FakeGitSession : GitSession {
    val statusReads = mutableListOf<CompletableFuture<GitStatus>>()
    val pages = mutableListOf<CompletableFuture<GitCommitPage>>()
    val tokens = mutableListOf<String?>()
    val files = mutableListOf<GitFileStatus>()
    val diffs = mutableListOf<CompletableFuture<List<GitDiff>>>()
    var refresh: () -> Unit = {}
    var closed = false
    var roots = listOf(GitRoot("secondary", "/secondary"))
    val requestedRoots = mutableListOf<GitRoot?>()
    val commitReads = mutableListOf<CompletableFuture<List<GitCommitFile>>>()
    override fun roots() = CompletableFuture.completedFuture(roots)
    override fun commitFiles(hash: String, root: GitRoot?) =
        CompletableFuture<List<GitCommitFile>>().also { commitReads.add(it) }
    override fun commitDiff(hash: String, path: String, root: GitRoot?) =
        CompletableFuture<List<GitDiff>>().also { diffs.add(it) }
    override fun status(root: GitRoot?) = CompletableFuture<GitStatus>().also {
        statusReads.add(it)
        requestedRoots.add(root)
    }
    override fun commits(nextToken: String?, root: GitRoot?) =
        CompletableFuture<GitCommitPage>().also {
            tokens.add(nextToken)
            pages.add(it)
        }
    override fun diff(file: GitFileStatus, root: GitRoot?) =
        CompletableFuture<List<GitDiff>>().also {
            files.add(file)
            diffs.add(it)
        }
    override fun watch(onRefresh: () -> Unit, onError: (Throwable) -> Unit): Closeable {
        refresh = onRefresh
        return Closeable { closed = true }
    }
    fun complete(branch: String, commits: List<GitCommit>, index: Int = statusReads.lastIndex) {
        statusReads[index].complete(GitStatus(branch = branch))
        pages[index].complete(GitCommitPage(commits))
    }
}
