package dev.intent.app.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentButton as Button
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.lists.RefreshableList
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.app.navigation.LocalChromeActions

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // Thread navigation and its confirmation dialog.
fun AssistantScreen(
    store: AssistantStore,
    onBack: () -> Unit,
    conversation: @Composable (String) -> Unit
) {
    val state by store.state.collectAsState()
    var history by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    BackHandler(history) { history = false }
    LaunchedEffect(store) { store.refresh() }
    CompositionLocalProvider(LocalChromeActions provides emptyList()) {
        Column {
            CompactAppBar(
                "Assistant",
                onBack = {
                    if (history) history = false else onBack()
                },
                actions = listOf(
                    ChromeAction("New conversation", state.loaded && state.owner && !state.busy) {
                        store.create()
                        history =
                            false
                    },
                    ChromeAction("Assistant history") { history = true },
                    ChromeAction("Refresh Assistant", !state.busy) { store.refresh() }
                )
            )
            state.error?.let {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = store::refresh) { Text("Retry Assistant") }
            }
            when {
                state.owner && state.selectedId != null -> key(state.selectedId) {
                    conversation(state.selectedId!!)
                }
                state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                else -> Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Plan your work and get help across all your workspaces.")
                    Button(
                        onClick = store::create,
                        enabled =
                        state.loaded && state.owner && !state.busy
                    ) { Text("Start a conversation") }
                }
            }
        }
    }
    if (history) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { history = false }) {
            androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large) {
                Column {
                    TextButton(onClick = { history = false }) { Text("Close history") }
                    RefreshableList(state.loading, store::refresh) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                            item {
                                Button(
                                    onClick = {
                                        store.create()
                                        history = false
                                    },
                                    enabled =
                                    state.loaded && state.owner && !state.busy
                                ) { Text("New conversation") }
                            }
                            items(state.threads, key = { it.id }) { thread ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(modifier = Modifier.weight(1f), onClick = {
                                        store.select(thread.id)
                                        history = false
                                    }) {
                                        Text(
                                            thread.name +
                                                if (thread.id ==
                                                    state.selectedId
                                                ) {
                                                    " · Selected"
                                                } else {
                                                    ""
                                                }
                                        )
                                    }
                                    IconButton(onClick = {
                                        deleting = thread.id
                                    }, enabled = !state.busy) {
                                        Icon(Icons.Default.Delete, "Delete ${thread.name}")
                                    }
                                }
                            }
                            if (state.threads.isEmpty()) {
                                item {
                                    Text("No Assistant conversations yet.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = {
                deleting = null
            },
            title = { Text("Delete conversation?") },
            text = { Text("This removes the Assistant conversation and its messages.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    store.delete(id)
                }) { Text("Delete conversation") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}
