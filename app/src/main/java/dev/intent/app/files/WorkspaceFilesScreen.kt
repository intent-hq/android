package dev.intent.app.files

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.app.lists.SearchableList

@Composable
internal fun WorkspaceFilesList(
    state: WorkspaceFilesState,
    onToggle: (String) -> Unit,
    onRetry: (String) -> Unit,
    onSelected: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    SearchableList(query, {
        query = it
    }, "Search loaded files", state.rows.isNotEmpty()) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            workspaceFiles(state, onToggle, onRetry, onSelected, query)
        }
    }
}
