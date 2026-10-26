package dev.intent.app.git

import dev.intent.core.protocol.models.GitCommit
import dev.intent.core.protocol.models.GitCommitFile
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitRoot
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GitStore(private val source: GitSession) : Closeable {
    private val mutable = MutableStateFlow(GitState())
    val state = mutable.asStateFlow()
    private var lease: Closeable? = null
    private var closed = false
    private var revision = 0L
    private var diffRevision = 0L
    private var commitRevision = 0L

    @Synchronized
    fun start() {
        if (closed || lease != null) return
        lease = source.watch(::refresh) { error ->
            synchronized(this) {
                if (!closed) {
                    mutable.value =
                        state.value.copy(loading = false, error = error.message)
                }
            }
        }
    }

    @Synchronized
    fun refresh() {
        if (closed) return
        val read = ++revision
        mutable.value = state.value.copy(loading = true, loadingMore = false, error = null)
        source.roots().whenComplete { roots, rootError ->
            synchronized(this) {
                if (closed || read != revision) return@whenComplete
                val selected = state.value.selectedRoot
                if (rootError == null && selected != null && roots.none { it.id == selected.id }) {
                    clearRows(null)
                }
                mutable.value = state.value.copy(
                    roots = roots ?: state.value.roots,
                    selectedRoot = if (rootError ==
                        null
                    ) {
                        roots.firstOrNull { it.id == state.value.selectedRoot?.id }
                    } else {
                        state.value.selectedRoot
                    },
                    repositoryError = rootError?.message
                )
                readSnapshot(read)
            }
        }
        if (state.value.selectedCommit == null) state.value.selected?.let(::select)
    }

    private fun readSnapshot(read: Long) {
        val root = state.value.selectedRoot
        source.status(root).thenCombine(source.commits(root = root)) { status, page ->
            status to
                page
        }
            .whenComplete { result, error ->
                synchronized(this) {
                    if (!closed && read == revision) {
                        mutable.value = if (error != null) {
                            state.value.copy(
                                loading = false,
                                error =
                                error.message ?: "Cannot load git changes"
                            )
                        } else {
                            state.value.copy(
                                status = result.first,
                                commits = result.second.items.distinctBy { it.hash },
                                nextToken = result.second.nextToken,
                                boundarySha = result.second.boundarySha,
                                loading = false
                            )
                        }
                    }
                }
            }
    }

    @Synchronized
    fun selectRoot(id: String?) {
        if (closed || id == state.value.selectedRoot?.id) return
        val root = state.value.roots.firstOrNull { it.id == id }
        if (id != null && root == null) {
            mutable.value =
                state.value.copy(
                    repositoryError = "This repository is no longer available. Refresh to retry."
                )
            return
        }
        clearRows(root)
        refresh()
    }

    private fun clearRows(root: GitRoot?) {
        diffRevision++
        commitRevision++
        mutable.value = GitState(roots = state.value.roots, selectedRoot = root)
    }

    @Synchronized
    fun loadMore() {
        val token = state.value.nextToken ?: return
        if (closed || state.value.loading || state.value.loadingMore) return
        val read = revision
        mutable.value = state.value.copy(loadingMore = true, error = null)
        source.commits(token, state.value.selectedRoot).whenComplete { page, error ->
            synchronized(this) {
                if (!closed && read == revision) {
                    if (error == null && page.boundarySha != state.value.boundarySha) {
                        refresh()
                        return@whenComplete
                    }
                    mutable.value = when {
                        error != null -> state.value.copy(
                            loadingMore = false,
                            error = error.message
                        )
                        page.nextToken == token -> state.value.copy(
                            loadingMore = false,
                            error = "History did not advance. Refresh to retry."
                        )
                        else -> state.value.copy(
                            commits = (state.value.commits + page.items).distinctBy { it.hash },
                            nextToken = page.nextToken,
                            loadingMore = false
                        )
                    }
                }
            }
        }
    }

    @Synchronized
    fun select(file: GitFileStatus?) {
        if (closed) return
        commitRevision++
        val read = ++diffRevision
        mutable.value = state.value.copy(
            selected = file,
            selectedCommit = null,
            commitFiles = emptyList(),
            commitLoading = false,
            commitError = null,
            diffs = emptyList(),
            diffLoading = file != null,
            diffError = null
        )
        if (file == null) return
        source.diff(file, state.value.selectedRoot).whenComplete { diffs, error ->
            synchronized(this) {
                if (!closed && read == diffRevision) {
                    mutable.value = state.value.copy(
                        diffs = diffs.orEmpty().filter { it.path == file.path },
                        diffLoading = false,
                        diffError = error?.message
                    )
                }
            }
        }
    }

    @Synchronized
    fun selectCommit(commit: GitCommit) {
        if (closed) return
        val read = ++commitRevision
        diffRevision++
        mutable.value = state.value.copy(
            selectedCommit = commit,
            selected = null,
            commitFiles = emptyList(),
            diffs = emptyList(),
            commitLoading = true,
            commitError = null,
            diffLoading = false,
            diffError = null
        )
        source.commitFiles(commit.hash, state.value.selectedRoot).whenComplete { files, error ->
            synchronized(this) {
                if (!closed && read == commitRevision) {
                    mutable.value = state.value.copy(
                        commitFiles = files.orEmpty(),
                        commitLoading = false,
                        commitError = error?.message
                    )
                    if (error == null) files.firstOrNull()?.let(::selectCommitFile)
                }
            }
        }
    }

    @Synchronized
    fun selectCommitFile(file: GitCommitFile) {
        val commit = state.value.selectedCommit ?: return
        if (closed || file !in state.value.commitFiles) return
        val read = ++diffRevision
        mutable.value = state.value.copy(
            selected = GitFileStatus(file.path, "M", false),
            diffs = emptyList(),
            diffLoading = true,
            diffError = null
        )
        source.commitDiff(commit.hash, file.path, state.value.selectedRoot).whenComplete {
                diffs,
                error
            ->
            synchronized(this) {
                if (!closed && read == diffRevision) {
                    mutable.value = state.value.copy(
                        diffs = diffs.orEmpty().filter {
                            it.path == file.path
                        },
                        diffLoading = false,
                        diffError = error?.message
                    )
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        revision++
        diffRevision++
        commitRevision++
        lease?.close()
        lease = null
    }
}
