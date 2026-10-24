package dev.intent.app.notes

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.app.cache.OfflineCache

/** Route entry point; the caller owns the shared session, this route owns its note subscription. */
@Composable
fun NoteReaderRoute(
    source: NoteSource,
    workspaceId: String,
    noteId: String,
    modifier: Modifier = Modifier,
    cache: OfflineCache? = null
) {
    val store = remember(source, workspaceId, noteId, cache) { NoteStore(source, cache) }
    var state by remember(store) { mutableStateOf(store.state) }
    DisposableEffect(store) {
        val handler = Handler(Looper.getMainLooper())
        var active = true
        val observer = store.observe { next -> handler.post { if (active) state = next } }
        store.open(workspaceId, noteId)
        onDispose {
            active = false
            observer.close()
            store.close()
        }
    }
    NoteReaderScreen(state, onRetry = store::refresh, modifier = modifier)
}
