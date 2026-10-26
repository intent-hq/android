package dev.intent.app.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.intent.app.lists.RefreshableList
import dev.intent.app.navigation.CompactAppBar
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
    val state by store.state.collectAsState()
    DisposableEffect(store) {
        store.refresh()
        onDispose { store.close() }
    }
    NoteListScreen(state, store::refresh, onSelected, modifier)
}

@Composable
internal fun NoteListScreen(
    state: NoteListState,
    onRefresh: () -> Unit,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        CompactAppBar("Context")
        RefreshableList(state.loading, onRefresh) {
            ContextNoteList(state.notes, onSelected, loading = state.loading, error = state.error)
        }
    }
}
