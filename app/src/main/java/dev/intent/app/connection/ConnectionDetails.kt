package dev.intent.app.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import dev.intent.app.design.IntentScreen
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionDetails(
    connection: SavedConnection,
    state: ConnectionState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(
                rememberScrollState()
            ).padding(IntentScreen.pageInsets),
            verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
        ) {
            Text("Connection details", style = MaterialTheme.typography.titleLarge)
            Text(connection.displayName, style = MaterialTheme.typography.titleMedium)
            val active = state.pathInUse(connection.id)
            Text("Path in use: ${active?.badge ?: "Not connected"}")
            Text("Known paths", style = MaterialTheme.typography.titleSmall)
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)) {
                    connection.knownPaths().forEach { path ->
                        Text(
                            "${path.type} · ${path.address}${if (path == active) " · In use" else ""}"
                        )
                    }
                }
            }
            Text(
                "Last connected: " + (
                    connection.metadata.lastConnectedAt?.let {
                        DateFormat.getDateTimeInstance().format(Date(it))
                    } ?: "Not yet connected"
                    ),
                style = MaterialTheme.typography.bodySmall
            )
            FingerprintDetails(connection.payload.certFingerprint)
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}

private const val FINGERPRINT_EDGE_LENGTH = 12

@Suppress("DEPRECATION")
@Composable
private fun FingerprintDetails(fingerprint: String?) {
    val clipboard = LocalClipboardManager.current
    val normalized = fingerprint?.replace(":", "")
    Text("Certificate fingerprint (SHA-256)", style = MaterialTheme.typography.titleSmall)
    Text(
        normalized?.let {
            "${it.take(FINGERPRINT_EDGE_LENGTH)}…${it.takeLast(FINGERPRINT_EDGE_LENGTH)}"
        }
            ?: "Unavailable"
    )
    if (fingerprint != null) {
        TextButton(onClick = { clipboard.setText(AnnotatedString(fingerprint)) }) {
            Text("Copy fingerprint")
        }
    }
}

@Composable
internal fun ConnectionPathBadge(state: ConnectionState, id: String) {
    state.pathInUse(id)?.let { path ->
        Text(
            path.badge,
            style = MaterialTheme.typography.labelMedium,
            color = IntentScreen.secondaryText
        )
    }
}
