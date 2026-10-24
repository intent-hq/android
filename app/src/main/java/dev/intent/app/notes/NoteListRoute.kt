package dev.intent.app.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    Column(modifier) {
        Text("Notes")
        TextButton(onClick = store::refresh, enabled = !state.loading) { Text("Refresh") }
        if (state.loading) CircularProgressIndicator()
        state.error?.let { Text(it) }
        if (!state.loading && state.error == null && state.notes.isEmpty()) Text("No notes")
        LazyColumn {
            items(state.notes, key = { it.id }) { note ->
                TextButton(onClick = { onSelected(note.id) }) { Text(note.title) }
            }
        }
    }
}
