package dev.intent.app.workspaces

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.intent.app.connection.SavedConnection
import dev.intent.app.connection.createConnectionSession
import dev.intent.app.connection.displayName
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentWarning
import dev.intent.app.design.intentPageInsets
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

@Suppress("LongMethod")
@Composable
fun WorkspaceTransferScreen(
    source: RpcSession,
    workspaceId: String,
    destinations: List<SavedConnection>,
    onBusy: (Boolean) -> Unit
) {
    val operations = remember(source) { RpcWorkspaceOperations(source) }
    var plan by remember { mutableStateOf<JsonObject?>(null) }
    var status by remember { mutableStateOf("Loading transfer preview…") }
    var busy by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(busy) { }
    LaunchedEffect(source, workspaceId) {
        runCatching {
            withContext(Dispatchers.IO) {
                operations.request(
                    "workspace.transfer.plan",
                    kotlinx.serialization.json.buildJsonObject {
                        put("workspaceId", kotlinx.serialization.json.JsonPrimitive(workspaceId))
                    }
                ).obj("plan")
            }
        }.onSuccess {
            plan = it
            status = "Review the preview and choose a destination."
        }
            .onFailure { status = "Transfer preview unavailable. Reconnect and retry." }
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).intentPageInsets(),
        verticalArrangement = Arrangement.spacedBy(IntentScreen.sectionSpacing)
    ) {
        Text(
            "Workspace transfer is under development.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentScreen.secondaryText
        )
        IntentSection("Transfer preview") {
            Text(status)
            plan?.let { preview ->
                val bytes = (preview["totalSizeBytes"] as? JsonPrimitive)?.longOrNull
                val size =
                    bytes?.let { Formatter.formatFileSize(LocalContext.current, it) } ?: "Unknown"
                Text("Estimated size: $size", color = IntentScreen.secondaryText)
            }
        }
        IntentWarning {
            Text("Before you transfer", style = MaterialTheme.typography.titleSmall)
            Text("Transfer stops active agents. Keep both hosts connected until it finishes.")
            (plan?.get("warnings") as? JsonArray)?.forEach { warning ->
                val message = (warning as? JsonObject)?.let(::transferWarningText)
                if (!message.isNullOrBlank()) Text(message)
            }
        }
        IntentSection("Source workspace") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(archive, { archive = it }, enabled = !busy && !finished)
                Text("Archive the source after confirmed import")
            }
        }
        IntentSection("Destination") {
            if (destinations.isEmpty()) {
                Text("Pair another host in Settings first.", color = IntentScreen.secondaryText)
            }
            destinations.forEach { destination ->
                Button(onClick = {
                    busy = true
                    onBusy(true)
                    status = "Transferring workspace…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO + NonCancellable) {
                            runCatching {
                                transferTo(operations, workspaceId, destination, archive)
                            }
                        }
                        result.onSuccess {
                            status = it
                            finished = true
                        }.onFailure {
                            status =
                                "Transfer was not confirmed. " +
                                "Check the destination before retrying. " +
                                "The source was not archived."
                            finished = true
                        }
                        busy = false
                        onBusy(false)
                    }
                }, enabled = plan != null && !busy && !finished) {
                    Text("Transfer to ${destination.displayName}")
                }
            }
        }
    }
}

private fun transferTo(
    source: WorkspaceOperations,
    workspaceId: String,
    destination: SavedConnection,
    archive: Boolean
): String {
    val ready = CompletableFuture<Unit>()
    val target = createConnectionSession(destination, { state ->
        if (state == SessionState.READY) ready.complete(Unit)
    })
    return target.use {
        target.connect()
        ready.get(CONNECT_SECONDS, TimeUnit.SECONDS)
        WorkspaceTransfer(source, RpcWorkspaceOperations(target), workspaceId).run(archive)
    }
}

private const val CONNECT_SECONDS = 30L
