package dev.intent.app.workspaces

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.intent.app.connection.SavedConnection
import dev.intent.app.connection.createConnectionSession
import dev.intent.app.connection.displayName
import dev.intent.app.design.IntentButton as Button
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

@Suppress("LongMethod", "LongParameterList") // Keep session injection separate from navigation.
@Composable
fun WorkspaceTransferScreen(
    source: RpcSession,
    workspaceId: String,
    destinations: List<SavedConnection>,
    onBusy: (Boolean) -> Unit,
    onDone: () -> Unit = {},
    createTarget: (
        SavedConnection,
        (SessionState) -> Unit
    ) -> RpcSession = { connection, observer ->
        createConnectionSession(connection, observer)
    }
) {
    val operations = remember(source) { RpcWorkspaceOperations(source) }
    var plan by remember { mutableStateOf<JsonObject?>(null) }
    var status by remember { mutableStateOf("Loading transfer preview…") }
    var busy by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val control = remember(source, workspaceId) { TransferControl() }
    val progress by control.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active by rememberUpdatedState(busy)
    DisposableEffect(control, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && active) control.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            control.cancel()
        }
    }
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
        if (busy || finished) {
            TransferStatus(progress, control::cancel, onDone)
            return@Column
        }
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
                Checkbox(archive, { archive = it })
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
                                transferTo(
                                    operations,
                                    workspaceId,
                                    destination,
                                    archive,
                                    control,
                                    createTarget
                                )
                            }
                        }
                        result.onFailure {
                            connectionFailed(control)
                        }
                        finished = true
                        busy = false
                        onBusy(false)
                    }
                }, enabled = plan != null) {
                    Text("Transfer to ${destination.displayName}")
                }
            }
        }
    }
}

private fun connectionFailed(control: TransferControl) {
    if (control.state.value.phase.busy) {
        control.finish(
            TransferPhase.FAILED,
            "Cannot connect to the destination. The source was not changed."
        )
    }
}

@Suppress("LongParameterList") // One attempt carries its owned control and destination factory.
private fun transferTo(
    source: WorkspaceOperations,
    workspaceId: String,
    destination: SavedConnection,
    archive: Boolean,
    control: TransferControl,
    createTarget: (SavedConnection, (SessionState) -> Unit) -> RpcSession
): String {
    val ready = CompletableFuture<Unit>()
    val target = createTarget(destination) { state ->
        if (state == SessionState.READY) ready.complete(Unit)
    }
    return target.use {
        target.connect()
        try {
            awaitDestination(ready, control)
            control.checkCancellation()
            WorkspaceTransfer(
                source,
                RpcWorkspaceOperations(target),
                workspaceId,
                control
            ).run(archive)
        } catch (error: java.util.concurrent.CancellationException) {
            if (control.state.value.phase.busy) {
                control.finish(
                    TransferPhase.CANCELLED,
                    "Transfer cancelled. The source was not changed."
                )
            }
            throw error
        }
    }
}

private fun awaitDestination(ready: CompletableFuture<Unit>, control: TransferControl) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CONNECT_SECONDS)
    while (!ready.isDone) {
        control.checkCancellation()
        check(System.nanoTime() < deadline) { "Destination connection timed out" }
        try {
            ready.get(CONNECT_POLL_MS, TimeUnit.MILLISECONDS)
        } catch (_: java.util.concurrent.TimeoutException) {
            // Poll cancellation without interrupting a transfer RPC.
        }
    }
}

@Composable
private fun TransferStatus(progress: TransferProgress, cancel: () -> Unit, onDone: () -> Unit) {
    IntentSection(
        when (progress.phase) {
            TransferPhase.CANCELLED -> "Transfer cancelled"
            TransferPhase.FAILED -> "Transfer failed"
            TransferPhase.UNCERTAIN -> "Transfer not confirmed"
            TransferPhase.DONE -> "Workspace transferred"
            else -> "Transfer progress"
        }
    ) {
        Text(progress.message, Modifier.testTag("transfer-status"))
        if (progress.phase.busy) {
            val fraction = progress.fraction
            if (fraction == null) {
                LinearProgressIndicator()
            } else {
                LinearProgressIndicator(progress = { fraction })
                val context = LocalContext.current
                Text(
                    "${(fraction * PERCENT).toInt()}% · " +
                        "${Formatter.formatFileSize(context, progress.receivedBytes)} of " +
                        Formatter.formatFileSize(context, requireNotNull(progress.totalBytes))
                )
            }
            Button(onClick = cancel, enabled = progress.phase.cancellable) {
                Text("Cancel transfer")
            }
            if (progress.phase == TransferPhase.COMMITTING ||
                progress.phase == TransferPhase.FINALIZING
            ) {
                Text("The transfer can no longer be cancelled. Keep both hosts connected.")
            }
        } else {
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

private const val PERCENT = 100
private const val CONNECT_POLL_MS = 100L
private const val CONNECT_SECONDS = 30L
