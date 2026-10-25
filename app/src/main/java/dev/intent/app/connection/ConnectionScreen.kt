package dev.intent.app.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.pairing.PairingPayload

@Composable
fun ConnectionScreen(
    state: ConnectionState,
    onSelect: (String) -> Unit,
    onForget: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Button(onClick = onAdd) { Text("Add connection") }
        }
        items(state.connections, key = { it.id }) { connection ->
            Column {
                Text(connection.label)
                Row {
                    TextButton(onClick = { onSelect(connection.id) }) {
                        Text(if (state.activeId == connection.id) "Reconnect" else "Connect")
                    }
                    TextButton(onClick = { onForget(connection.id) }) { Text("Forget") }
                }
            }
        }
    }
}

@Suppress("UnusedPrivateMember", "MagicNumber")
@Preview(showBackground = true)
@Composable
private fun ConnectionsPreview() {
    MaterialTheme {
        ConnectionScreen(
            ConnectionState(
                connections = listOf(
                    SavedConnection(
                        "preview",
                        PairingPayload(listOf("desktop.local"), 443, "preview")
                    )
                )
            ),
            {},
            {},
            {}
        )
    }
}
