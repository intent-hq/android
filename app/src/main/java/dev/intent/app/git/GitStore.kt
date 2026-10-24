package dev.intent.app.git

import dev.intent.core.protocol.models.GitCommit
import dev.intent.core.protocol.models.GitDiff
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitStatus
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GitState(
    val status: GitStatus = GitStatus(),
    val commits: List<GitCommit> = emptyList(),
    val nextToken: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val selected: GitFileStatus? = null,
    val diffs: List<GitDiff> = emptyList(),
    val diffLoading: Boolean = false,
    val error: String? = null,
    val diffError: String? = null
)

class GitStore(private val source: GitSession) : Closeable {
    private val mutable = MutableStateFlow(GitState())
    val state = mutable.asStateFlow()
    private var lease: Closeable? = null
    private var closed = false
    private var revision = 0L
    private var diffRevision = 0L

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
        source.status().thenCombine(source.commits()) { status, page -> status to page }
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
                                loading = false
                            )
                        }
                    }
                }
            }
        state.value.selected?.let(::select)
    }

    @Synchronized
    fun loadMore() {
        val token = state.value.nextToken ?: return
        if (closed || state.value.loading || state.value.loadingMore) return
        val read = revision
        mutable.value = state.value.copy(loadingMore = true, error = null)
        source.commits(token).whenComplete { page, error ->
            synchronized(this) {
                if (!closed && read == revision) {
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
        val read = ++diffRevision
        mutable.value = state.value.copy(
            selected = file,
            diffs = emptyList(),
            diffLoading = file != null,
            diffError = null
        )
        if (file == null) return
        source.diff(file).whenComplete { diffs, error ->
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
    override fun close() {
        closed = true
        revision++
        diffRevision++
        lease?.close()
        lease = null
    }
}
