package dev.intent.app.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.app.files.WorkspaceFilePreviewRoute
import dev.intent.app.files.WorkspaceFileRpc
import dev.intent.app.files.WorkspaceFileSource
import dev.intent.app.files.WorkspaceFilesStore
import dev.intent.app.files.workspaceFiles
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
        state.copy(loading = state.loading || filesState.loading.isNotEmpty()),
        {
            store.refresh()
            files.refresh()
        },
        onSelected,
        modifier,
        files = { workspaceFiles(filesState, files::toggle, files::retry) { selectedFile = it } }
    )
}

@Composable
internal fun NoteListScreen(
    state: NoteListState,
    onRefresh: () -> Unit,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    files: (LazyListScope.() -> Unit)? = null
) {
    Column(modifier) {
        WorkspaceSectionAppBar("Context")
        RefreshableList(state.loading, onRefresh) {
            ContextNoteList(
                state.notes,
                onSelected,
                loading = state.loading,
                error = state.error,
                files = files
            )
        }
    }
}
