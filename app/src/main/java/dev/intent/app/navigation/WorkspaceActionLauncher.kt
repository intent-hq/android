package dev.intent.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun WorkspaceActionLauncher(actions: LauncherActions, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(open) { open = false }
    FloatingActionButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Default.Menu, "Open app launcher")
    }
    if (open) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.matchParentSize().clickable { open = false }
                        .semantics { contentDescription = "Dismiss app launcher" }
                )
                Box(Modifier.align(Alignment.BottomCenter)) {
                    LauncherTiles(actions, { open = false })
                }
            }
        }
    }
}

@Composable
private fun LauncherTiles(actions: LauncherActions, close: () -> Unit) {
    val tiles = buildList {
        actions.onCreate?.let { add(ChromeAction("New workspace", run = it)) }
        add(ChromeAction("Connections: ${actions.connectionCount}", run = actions.onConnections))
        add(ChromeAction("Appearance: ${actions.appearance.name}", run = actions.onAppearance))
        add(ChromeAction("Settings", run = actions.onSettings))
    }
    Column(
        Modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.End
    ) {
        LazyRow(
            modifier = Modifier.testTag("launcher.tiles"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)
        ) {
            items(tiles, key = { it.label.substringBefore(':') }) { tile ->
                Card(
                    onClick = {
                        if (!tile.label.startsWith("Appearance:")) close()
                        tile.run()
                    },
                    modifier = Modifier.width(192.dp).height(240.dp)
                        .semantics { contentDescription = tile.label }
                ) {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        val icon = when (tile.label.substringBefore(':')) {
                            "Connections" -> Icons.Default.AccountBox
                            "Appearance" -> Icons.Default.Refresh
                            "Settings" -> Icons.Default.Settings
                            else -> Icons.Default.Add
                        }
                        Icon(icon, null, tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.weight(1f))
                        Text(tile.label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Box(Modifier.padding(end = 16.dp)) {
            FloatingActionButton(onClick = close) {
                Icon(Icons.Default.Close, "Close app launcher")
            }
        }
    }
}
