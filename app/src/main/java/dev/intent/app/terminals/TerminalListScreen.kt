package dev.intent.app.terminals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.lists.RefreshableList

@Composable
internal fun TerminalListScreen(
    state: TerminalState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit
) {
    RefreshableList(state.loading, onRefresh) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = IntentScreen.pageInsets,
            verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
        ) {
            state.error?.let { item { Text(it, color = IntentScreen.error) } }
            if (!state.loading && state.error == null && state.terminals.isEmpty()) {
                item {
                    IntentSection("No terminal sessions") {
                        Text(
                            "Open this workspace in Intent on the desktop " +
                                "and start a terminal session."
                        )
                        Text(
                            "Then pull down to refresh this list.",
                            color = IntentScreen.secondaryText
                        )
                    }
                }
            }
            items(state.terminals, key = { it.id }) { terminal ->
                TextButton(onClick = { onSelect(terminal.id) }) {
                    Text("${terminal.name}\n${terminal.cwd}")
                }
            }
        }
    }
}
