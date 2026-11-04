package dev.intent.app.assistant

import android.text.format.DateUtils
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.intent.app.design.IntentButton as Button
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.lists.RefreshableList
import dev.intent.core.protocol.models.Agent
import java.time.Instant
import java.time.format.DateTimeParseException

@Composable
@Suppress("LongParameterList") // Dialog actions belong to the parent screen.
internal fun AssistantHistoryDialog(
    state: AssistantState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Conversations",
                        Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onDismiss) { Text("Close history") }
                }
                RefreshableList(state.loading, onRefresh) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { AssistantError(state, onRefresh) }
                        item {
                            Button(
                                onClick = onCreate,
                                enabled = state.loaded && state.owner && !state.busy
                            ) { Text("New conversation") }
                        }
                        items(state.threads, key = { it.id }) { thread ->
                            AssistantHistoryRow(thread, state, onSelect, onDelete)
                        }
                        if (state.threads.isEmpty() && !state.loading && state.error == null) {
                            item { Text("No Assistant conversations yet.") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AssistantError(state: AssistantState, onRetry: () -> Unit) {
    state.error?.let {
        Column(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry, enabled = !state.busy && !state.loading) {
                Text("Retry Assistant")
            }
        }
    }
}

@Composable
private fun AssistantHistoryRow(
    thread: Agent,
    state: AssistantState,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val current = thread.id == state.selectedId
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            modifier = Modifier.weight(1f).testTag("assistant.history.${thread.id}")
                .semantics { selected = current },
            onClick = { onSelect(thread.id) },
            enabled = !state.busy
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(thread.name, style = MaterialTheme.typography.titleSmall)
                assistantHistoryTimestamp(thread)?.let {
                    Text(
                        DateUtils.getRelativeTimeSpanString(it).toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentScreen.secondaryText
                    )
                }
            }
            if (current) Icon(Icons.Default.Check, null)
        }
        IconButton(onClick = { onDelete(thread.id) }, enabled = !state.busy) {
            Icon(Icons.Default.Delete, "Delete ${thread.name}")
        }
    }
}

internal fun assistantHistoryTimestamp(thread: Agent): Long? =
    sequenceOf(thread.lastActivity, thread.updatedAt, thread.createdAt).mapNotNull { value ->
        try {
            value?.let { Instant.parse(it).toEpochMilli() }
        } catch (_: DateTimeParseException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }.firstOrNull()
