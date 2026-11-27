package dev.intentapp.android.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.models.NoteListResult
import dev.intent.core.protocol.session.RpcSession
import dev.intentapp.android.design.IntentCollapsibleSectionHeader
import dev.intentapp.android.design.IntentSpacing
import dev.intentapp.android.files.WorkspaceFilePreviewRoute
import dev.intentapp.android.files.WorkspaceFileRpc
import dev.intentapp.android.files.WorkspaceFileSource
import dev.intentapp.android.files.WorkspaceFilesState
import dev.intentapp.android.files.WorkspaceFilesStore
import dev.intentapp.android.files.workspaceFiles
import dev.intentapp.android.lists.RefreshableList
import dev.intentapp.android.navigation.WorkspaceSectionAppBar
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun NoteListRoute(
    session: RpcSession,
    workspaceId: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val store = remember(session, workspaceId) {
        NoteListStore {
            session.request(
                "note.list",
                buildJsonObject {
                    put("workspaceId", workspaceId)
                    put("projection", "slim")
                },
                NoteListResult.serializer()
            ).thenApply { it.notes }
        }
    }
    val filesSource = remember(session, workspaceId) {
        WorkspaceFileSource(
            WorkspaceFileRpc { method, params ->
                session.request(method, params)
            },
            workspaceId
        )
    }
    val files = remember(filesSource) { WorkspaceFilesStore(filesSource::list) }
    val filesState by files.state.collectAsState()
    var selectedFile by rememberSaveable(session, workspaceId) { mutableStateOf<String?>(null) }
    DisposableEffect(files) {
        files.refresh()
        onDispose { files.close() }
    }
    selectedFile?.let { path ->
        WorkspaceFilePreviewRoute(filesSource, path) { selectedFile = null }
    }
    val state by store.state.collectAsState()
    DisposableEffect(store) {
        store.refresh()
        onDispose { store.close() }
    }
    NoteListScreen(
        state = state,
        onRefresh = {
            store.refresh()
            files.refresh()
        },
        onSelected = onSelected,
        modifier = modifier,
        filesState = filesState,
        onToggleFolder = files::toggle,
        onRetryFolder = files::retry,
        onSelectFile = { selectedFile = it }
    )
}

@Suppress("LongParameterList") // Coordinates notes and files in one unified Context scroll list.
@Composable
internal fun NoteListScreen(
    state: NoteListState,
    onRefresh: () -> Unit,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    filesState: WorkspaceFilesState? = null,
    onToggleFolder: (String) -> Unit = {},
    onRetryFolder: (String) -> Unit = {},
    onSelectFile: (String) -> Unit = {}
) {
    var notesExpanded by rememberSaveable { mutableStateOf(true) }
    var filesExpanded by rememberSaveable { mutableStateOf(true) }
    var collapsedNotes by remember { mutableStateOf(emptySet<String>()) }
    val rows = remember(state.notes, collapsedNotes) {
        contextNoteRows(state.notes, collapsedNotes)
    }
    val refreshing = state.loading || filesState?.loading?.isNotEmpty() == true
    val listState = rememberLazyListState()
    Column(modifier) {
        WorkspaceSectionAppBar("Context")
        RefreshableList(refreshing, onRefresh) {
            if (state.loading && state.notes.isEmpty()) {
                Spacer(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        horizontal = IntentSpacing.page,
                        vertical = IntentSpacing.medium
                    )
                ) {
                    item(key = "section.notes") {
                        IntentCollapsibleSectionHeader("Notes", notesExpanded) {
                            notesExpanded = !notesExpanded
                        }
                    }
                    if (notesExpanded) {
                        contextNoteItems(
                            rows = rows,
                            collapsed = collapsedNotes,
                            loading = state.loading,
                            error = state.error,
                            notes = state.notes,
                            onSelected = onSelected,
                            onToggle = { id ->
                                collapsedNotes = collapsedNotes.toggled(id)
                            }
                        )
                    }
                    if (filesState != null) {
                        item(key = "section.files") {
                            Column {
                                Spacer(Modifier.height(20.dp))
                                IntentCollapsibleSectionHeader("Files", filesExpanded) {
                                    filesExpanded = !filesExpanded
                                }
                            }
                        }
                        if (filesExpanded) {
                            workspaceFiles(filesState, onToggleFolder, onRetryFolder, onSelectFile)
                        }
                    }
                }
            }
        }
    }
}

private fun Set<String>.toggled(id: String): Set<String> = if (id in this) this - id else this + id
