package dev.intent.app.files

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkspaceFilesState(
    val entries: Map<String, List<WorkspaceFileEntry>> = emptyMap(),
    val expanded: Set<String> = emptySet(),
    val loading: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap()
) {
    val rows: List<WorkspaceFileRow>
        get() {
            val result = mutableListOf<WorkspaceFileRow>()
            val pending = ArrayDeque(entries["."].orEmpty().map { WorkspaceFileRow(it, 0) })
            while (pending.isNotEmpty()) {
                val row = pending.removeFirst()
                result.add(row)
                if (row.entry.isDirectory && row.entry.path in expanded) {
                    entries[row.entry.path].orEmpty().asReversed().forEach {
                        pending.addFirst(WorkspaceFileRow(it, row.depth + 1))
                    }
                }
            }
            return result
        }
}

class WorkspaceFilesStore(
    private val list: (String) -> CompletableFuture<List<WorkspaceFileEntry>>
) : Closeable {
    private val mutableState = MutableStateFlow(WorkspaceFilesState())
    val state = mutableState.asStateFlow()
    private var revision = 0L
    private var closed = false

    @Synchronized
    fun toggle(path: String) {
        if (closed || !isDirectory(path)) return
        val expanded = state.value.expanded
        if (path in expanded) {
            mutableState.value = state.value.copy(expanded = expanded - path)
        } else {
            mutableState.value = state.value.copy(expanded = expanded + path)
            loadExpanded(revision, false, emptySet())
        }
    }

    @Synchronized
    fun refresh() {
        if (closed) return
        val current = ++revision
        mutableState.value = state.value.copy(loading = emptySet())
        load(".", current, true).thenRun {
            synchronized(this) {
                if (current == revision && state.value.errors["."] == null) {
                    loadExpanded(current, true, emptySet())
                }
            }
        }
    }

    @Synchronized
    fun retry(path: String) {
        if (path == ".") {
            refresh()
        } else {
            val current = revision
            load(path, current, true).thenRun {
                synchronized(this) { loadExpanded(current, false, emptySet()) }
            }
        }
    }

    private fun loadExpanded(current: Long, force: Boolean, visited: Set<String>) {
        if (closed || current != revision) return
        val path = state.value.rows.firstOrNull {
            it.entry.isDirectory &&
                it.entry.path in state.value.expanded &&
                it.entry.path !in visited &&
                (force || it.entry.path !in state.value.errors)
        }?.entry?.path ?: return
        load(path, current, force).thenRun {
            synchronized(this) { loadExpanded(current, force, visited + path) }
        }
    }

    private fun load(path: String, current: Long, force: Boolean): CompletableFuture<Unit> {
        val before = state.value
        val cached = !force && path in before.entries
        if (!isCurrent(path, current) || path in before.loading || cached
        ) {
            return CompletableFuture.completedFuture(Unit)
        }
        mutableState.value =
            before.copy(loading = before.loading + path, errors = before.errors - path)
        val request = runCatching { list(path) }.getOrElse { failure ->
            CompletableFuture<List<WorkspaceFileEntry>>().apply { completeExceptionally(failure) }
        }
        return request.handle { entries, error ->
            synchronized(this) {
                if (isCurrent(path, current) && path in state.value.loading) {
                    if (error == null) removeMissing(path, entries)
                    val latest = state.value
                    mutableState.value = if (error == null) {
                        latest.copy(
                            entries = latest.entries + (path to entries),
                            loading =
                            latest.loading - path
                        )
                    } else {
                        latest.copy(
                            loading = latest.loading - path,
                            errors =
                            latest.errors +
                                (path to "Cannot load files. Check your connection and retry.")
                        )
                    }
                }
            }
        }
    }

    private fun isCurrent(path: String, current: Long): Boolean =
        !closed && current == revision && validDirectory(path)

    private fun validDirectory(path: String): Boolean = path == "." || isDirectory(path)

    private fun isDirectory(path: String): Boolean = state.value.entries[
        path.substringBeforeLast('/', ".")
    ].orEmpty().any { it.path == path && it.isDirectory }

    private fun removeMissing(path: String, entries: List<WorkspaceFileEntry>) {
        val directories = entries.filter { it.isDirectory }.map { it.path }.toSet()
        val removed = state.value.entries[path].orEmpty()
            .filter { it.isDirectory && it.path !in directories }.map { it.path }
        fun keep(candidate: String) =
            removed.none { candidate == it || candidate.startsWith("$it/") }
        val old = state.value
        mutableState.value = old.copy(
            entries = old.entries.filterKeys(::keep),
            expanded = old.expanded.filter(::keep).toSet(),
            loading = old.loading.filter(::keep).toSet(),
            errors = old.errors.filterKeys(::keep)
        )
    }

    @Synchronized
    override fun close() {
        closed = true
        revision++
    }
}
