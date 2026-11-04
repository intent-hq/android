package dev.intent.app.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.intent.app.R
import dev.intent.app.design.IntentButton as Button
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton as TextButton
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
                        history = false
                    },
                    ChromeAction("Assistant history") { history = true },
                    ChromeAction("Refresh Assistant", !state.busy) { store.refresh() }
                )
            )
            if (!history) AssistantError(state, store::refresh)
            when {
                state.owner && state.selectedId != null -> key(state.selectedId) {
                    conversation(state.selectedId!!)
                }
                state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                else -> AssistantEmptyState(state.loaded && state.owner && !state.busy, store::create)
            }
        }
    }
    if (history) {
        AssistantHistoryDialog(
            state = state,
            onDismiss = { history = false },
            onRefresh = store::refresh,
            onCreate = {
                store.create()
                history = false
            },
            onSelect = {
                store.select(it)
                history = false
            },
            onDelete = { deleting = it }
        )
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
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

@Composable
private fun AssistantEmptyState(canCreate: Boolean, onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(R.drawable.avatar_chief_of_staff), null, Modifier.size(48.dp))
        Text(
            "Assistant",
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Plan your work and get help across all your workspaces.",
            color = IntentScreen.secondaryText,
            textAlign = TextAlign.Center
        )
        Button(onClick = onCreate, enabled = canCreate) { Text("Start a conversation") }
    }
}
