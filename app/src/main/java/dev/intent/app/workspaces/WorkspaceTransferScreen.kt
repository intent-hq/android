package dev.intent.app.workspaces

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

// Keep session injection separate from navigation; failures retain the review form.
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
@Composable
fun WorkspaceTransferScreen(
    source: RpcSession,
    workspaceId: String,
    destinations: List<SavedConnection>,
    onBusy: (Boolean) -> Unit,
    onDone: () -> Unit = {},
    workspaceTitle: String = workspaceId,
    sourceName: String = "Connected host",
    sourceState: SessionState = SessionState.DISCONNECTED,
    createTarget: (
        SavedConnection,
        (SessionState) -> Unit
    ) -> RpcSession = { connection, observer ->
        createConnectionSession(connection, observer)
    }
) {
    val operations = remember(source) { RpcWorkspaceOperations(source) }
    var plan by remember(source, workspaceId) { mutableStateOf<JsonObject?>(null) }
    var status by remember { mutableStateOf("Loading transfer preview…") }
    var busy by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var attempt by remember(source, workspaceId) { mutableStateOf(0) }
    var retryPreview by remember(source, workspaceId) { mutableStateOf(0) }
    var previewFailed by remember(source, workspaceId) { mutableStateOf(false) }
    var previewLoading by remember(source, workspaceId) { mutableStateOf(true) }
    var owner by remember(source, sourceState) { mutableStateOf(false) }
    var destinationId by remember(source, workspaceId) { mutableStateOf<String?>(null) }
    val selectedDestination = destinations.firstOrNull { it.id == destinationId }
    val control = remember(source, workspaceId, attempt) { TransferControl() }
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
    LaunchedEffect(source, workspaceId, sourceState, retryPreview, attempt) {
        if (busy || finished) return@LaunchedEffect
        plan = null
        owner = false
        previewFailed = false
        previewLoading = true
        status = "Loading transfer preview…"
        if (sourceState != SessionState.READY) {
            status = "Reconnect to the source host to preview this transfer."
            previewLoading = false
            return@LaunchedEffect
        }
        try {
            owner = withContext(Dispatchers.IO) {
                transferPrincipalIsOwner(
                    operations.request("principal.me"),
                    source.serverCapabilities
                )
            }
            if (!owner) {
                status =
                    "Only a host owner can transfer a workspace. Check your access on the host."
                return@LaunchedEffect
            }
            plan = withContext(Dispatchers.IO) {
                operations.request(
                    "workspace.transfer.plan",
                    kotlinx.serialization.json.buildJsonObject {
                        put("workspaceId", kotlinx.serialization.json.JsonPrimitive(workspaceId))
                    }
                ).obj("plan")
            }
            status = "Review the preview and choose a destination."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            previewFailed = true
            status = "Transfer preview unavailable. Check the source connection, then retry."
        } finally {
            previewLoading = false
        }
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
            Text(workspaceTitle, style = MaterialTheme.typography.titleMedium)
            Text("From $sourceName", color = IntentScreen.secondaryText)
            selectedDestination?.let {
                Text("To ${it.displayName}", color = IntentScreen.secondaryText)
            }
            TransferStatus(progress, control::cancel, onDone) {
                finished = false
                attempt++
            }
            return@Column
        }
        IntentSection("Transfer preview") {
            Text(status)
            if (previewFailed) {
                Button(onClick = { retryPreview++ }, enabled = sourceState == SessionState.READY) {
                    Text("Retry preview")
                }
            }
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
            Text(workspaceTitle, style = MaterialTheme.typography.bodyLarge)
            Text(sourceName, color = IntentScreen.secondaryText)
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
                FilterChip(
                    selected = destination.id == destinationId,
                    onClick = { destinationId = destination.id },
                    label = { Text(destination.displayName) }
                )
            }
            Text(
                selectedDestination?.let { "Selected destination: ${it.displayName}" }
                    ?: "Choose a destination before starting the transfer.",
                color = IntentScreen.secondaryText
            )
        }
        val sourceEligible = owner && sourceState == SessionState.READY
        val previewReady = !previewLoading && plan != null
        Button(
            onClick = {
                val destination = selectedDestination
                if (busy || destination == null || !sourceEligible) {
                    return@Button
                }
                busy = true
                onBusy(true)
                scope.launch {
                    val result = withContext(Dispatchers.IO + NonCancellable) {
                        runCatching {
                            check(
                                transferPrincipalIsOwner(
                                    operations.request("principal.me"),
                                    source.serverCapabilities
                                )
                            ) {
                                "Owner access is required on the source host."
                            }
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
                    result.onFailure { connectionFailed(control) }
                    finished = true
                    busy = false
                    onBusy(false)
                }
            },
            enabled = previewReady && selectedDestination != null && sourceEligible
        ) {
            Text("Transfer workspace")
        }
    }
}

private fun connectionFailed(control: TransferControl) {
    if (control.state.value.phase.busy) {
        control.finish(
            TransferPhase.FAILED,
            "Could not confirm the connection and owner access on both hosts. The source was not changed."
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
            check(
                transferPrincipalIsOwner(
                    RpcWorkspaceOperations(target).request("principal.me"),
                    target.serverCapabilities
                )
            ) {
                "Owner access is required on the destination host."
            }
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
private fun TransferStatus(
    progress: TransferProgress,
    cancel: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit
) {
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
            if (progress.phase == TransferPhase.FAILED) {
                Button(onClick = onRetry) { Text("Review and retry") }
            }
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

private const val PERCENT = 100
private const val CONNECT_POLL_MS = 100L
private const val CONNECT_SECONDS = 30L
