package dev.intent.app.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.app.design.IntentSpacing
import dev.intent.app.design.IntentTabs
import dev.intent.app.files.WorkspaceFilePreviewRoute
import dev.intent.app.files.WorkspaceFileRpc
import dev.intent.app.files.WorkspaceFileSource
import dev.intent.app.files.WorkspaceFilesList
import dev.intent.app.files.WorkspaceFilesStore
import dev.intent.app.lists.RefreshableList
import dev.intent.app.navigation.WorkspaceSectionAppBar
import dev.intent.core.protocol.models.NoteListResult
import dev.intent.core.protocol.session.RpcSession
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
        state,
        {
            store.refresh()
            files.refresh()
        },
        onSelected,
        modifier,
        files = {
            WorkspaceFilesList(filesState, files::toggle, files::retry) { selectedFile = it }
        },
        refreshingFiles = filesState.loading.isNotEmpty()
    )
}

@Suppress("LongParameterList") // Note hydration and folder refresh have separate loading states.
@Composable
internal fun NoteListScreen(
    state: NoteListState,
    onRefresh: () -> Unit,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    files: (@Composable () -> Unit)? = null,
    refreshingFiles: Boolean = false
) {
    var showFiles by rememberSaveable { mutableStateOf(false) }
    val savedState = rememberSaveableStateHolder()
    Column(modifier) {
        WorkspaceSectionAppBar("Context")
        if (files != null) {
            IntentTabs(
                titles = listOf("Notes", "Files"),
                selectedIndex = if (showFiles) 1 else 0,
                onSelect = { showFiles = it == 1 },
                modifier = Modifier.padding(horizontal = IntentSpacing.page)
            )
        }
        RefreshableList(state.loading || refreshingFiles, onRefresh) {
            if (showFiles && files != null) {
                savedState.SaveableStateProvider("files") { files() }
            } else if (state.loading && state.notes.isEmpty()) {
                Spacer(Modifier.fillMaxSize())
            } else {
                savedState.SaveableStateProvider("notes") {
                    ContextNoteList(
                        state.notes,
                        onSelected,
                        loading = state.loading,
                        error = state.error
                    )
                }
            }
        }
    }
}
