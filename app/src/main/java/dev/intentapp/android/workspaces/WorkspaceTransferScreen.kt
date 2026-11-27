package dev.intentapp.android.workspaces

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import dev.intentapp.android.R
import dev.intentapp.android.connection.SavedConnection
import dev.intentapp.android.connection.createConnectionSession
import dev.intentapp.android.connection.displayName
import dev.intentapp.android.design.IntentButton
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing
import dev.intentapp.android.design.intentPageInsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS `WorkspaceTransferSheet`: heading, destination menu, archive toggle, preview, status and
 * error text, then one action row. Busy and completion states replace the form in place.
 */
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
@Composable
fun WorkspaceTransferScreen(
    source: RpcSession,
    workspaceId: String,
    destinations: List<SavedConnection>,
    onBusy: (Boolean) -> Unit,
    onDone: () -> Unit = {},
    workspaceTitle: String = workspaceId,
    sourceState: SessionState = SessionState.DISCONNECTED,
    createTarget: (
        SavedConnection,
        (SessionState) -> Unit
    ) -> RpcSession = { connection, observer ->
        createConnectionSession(connection, observer)
    }
) {
    val operations = remember(source) { RpcWorkspaceOperations(source) }
    val ready = sourceState == SessionState.READY
    var plan by remember(source, workspaceId) { mutableStateOf<TransferPlan?>(null) }
    var owner by remember(source, workspaceId) { mutableStateOf(false) }
    var statusMessage by remember(source, workspaceId) { mutableStateOf("") }
    var errorMessage by remember(source, workspaceId) { mutableStateOf<String?>(null) }
    var previewLoading by remember(source, workspaceId) { mutableStateOf(true) }
    var previewCancelled by remember(source, workspaceId) { mutableStateOf(false) }
    var retryPreview by remember(source, workspaceId) { mutableIntStateOf(0) }
    var destinationId by remember(source, workspaceId) { mutableStateOf<String?>(null) }
    var archive by remember(source, workspaceId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var control by remember(source, workspaceId) { mutableStateOf<TransferControl?>(null) }
    val progress = control?.state?.collectAsState()?.value
    val done = progress?.takeIf { it.phase == TransferPhase.DONE }
    val complete = done != null
    val selectedDestination = destinations.firstOrNull { it.id == destinationId }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active by rememberUpdatedState(busy)
    val current = control
    DisposableEffect(current, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && active) current?.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            current?.cancel()
        }
    }
    BackHandler(busy) { }
    LaunchedEffect(source, workspaceId, ready, retryPreview, previewCancelled) {
        if (busy || complete || plan != null) {
            previewLoading = false
            return@LaunchedEffect
        }
        if (previewCancelled) {
            previewLoading = false
            statusMessage = "Transfer preview cancelled."
            return@LaunchedEffect
        }
        previewLoading = true
        errorMessage = null
        statusMessage = "Loading transfer preview…"
        try {
            val loaded = withContext(Dispatchers.IO) {
                val isOwner = ready &&
                    transferPrincipalIsOwner(
                        operations.request("principal.me"),
                        source.serverCapabilities
                    )
                if (!isOwner) fault("Connect as the workspace owner to transfer this workspace.")
                transferPlan(
                    operations.request(
                        "workspace.transfer.plan",
                        kotlinx.serialization.json.buildJsonObject {
                            put(
                                "workspaceId",
                                kotlinx.serialization.json.JsonPrimitive(workspaceId)
                            )
                        }
                    ),
                    workspaceId
                )
            }
            owner = true
            plan = loaded
            statusMessage = ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            owner = false
            errorMessage =
                "Transfer preview unavailable. The host may need an update. " +
                transferErrorText(error)
            statusMessage = "Could not load the transfer preview."
        }
        previewLoading = false
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).intentPageInsets(),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.page)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.row)) {
            CreationHeading(if (complete) "Workspace transferred" else "Transfer workspace") {
                CreationIconAction(
                    R.drawable.intent_close,
                    "Close",
                    enabled = !busy,
                    onClick = onDone
                )
            }
            Text(
                workspaceTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentScreen.secondaryText
            )
        }
        when {
            done != null -> Text(
                done.message,
                Modifier.testTag("workspace-transfer.complete"),
                style = MaterialTheme.typography.bodyMedium
            )
            busy && progress != null -> TransferProgressView(progress.fraction, progress.message)
            previewLoading -> TransferProgressView(null, statusMessage)
            else -> {
                TransferForm(
                    destinations = destinations,
                    selected = selectedDestination,
                    onSelect = { destinationId = it?.id },
                    archive = archive,
                    onArchive = { archive = it },
                    plan = plan
                )
                if (statusMessage.isNotEmpty()) {
                    Text(
                        statusMessage,
                        Modifier.testTag("workspace-transfer.status"),
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentScreen.secondaryText
                    )
                }
            }
        }
        val error = if (done != null) done.error else errorMessage.takeUnless { busy }
        error?.let {
            Text(
                it,
                Modifier.testTag("workspace-transfer.error"),
                style = MaterialTheme.typography.bodyMedium,
                color = IntentScreen.error
            )
        }
        when {
            complete -> IntentButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
            busy -> SecondaryButton(
                "Cancel transfer",
                enabled = progress?.phase?.cancellable == true
            ) { control?.cancel() }
            previewLoading -> SecondaryButton("Cancel") { previewCancelled = true }
            else -> Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.row)) {
                if (plan == null) {
                    SecondaryButton("Retry", enabled = ready) {
                        previewCancelled = false
                        retryPreview++
                    }
                }
                IntentButton(
                    onClick = {
                        val destination = selectedDestination ?: return@IntentButton
                        if (busy || plan == null) return@IntentButton
                        val attempt = TransferControl()
                        control = attempt
                        busy = true
                        errorMessage = null
                        statusMessage = ""
                        onBusy(true)
                        scope.launch {
                            val result = withContext(Dispatchers.IO + NonCancellable) {
                                runCatching {
                                    if (!transferPrincipalIsOwner(
                                            operations.request("principal.me"),
                                            source.serverCapabilities
                                        )
                                    ) {
                                        fault(
                                            "Workspace transfer requires owner access on both machines."
                                        )
                                    }
                                    transferTo(
                                        operations,
                                        workspaceId,
                                        destination,
                                        archive,
                                        attempt,
                                        createTarget
                                    )
                                }
                            }
                            result.onFailure { settleEarlyFailure(attempt, it) }
                            val final = attempt.state.value
                            if (final.phase != TransferPhase.DONE) {
                                statusMessage = final.message
                                errorMessage = final.error
                            }
                            busy = false
                            onBusy(false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("workspace-transfer.submit"),
                    enabled = selectedDestination != null && plan != null && ready && owner
                ) {
                    Text("Transfer workspace")
                }
            }
        }
    }
}

@Suppress("LongParameterList") // iOS form state is owned by the screen.
@Composable
private fun TransferForm(
    destinations: List<SavedConnection>,
    selected: SavedConnection?,
    onSelect: (SavedConnection?) -> Unit,
    archive: Boolean,
    onArchive: (Boolean) -> Unit,
    plan: TransferPlan?
) {
    Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.page)) {
        DestinationField(destinations, selected, onSelect)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(FieldShape)
                .background(IntentScreen.surface)
                .toggleable(archive, role = Role.Switch, onValueChange = onArchive)
                .padding(horizontal = IntentSpacing.content, vertical = IntentSpacing.row)
                .testTag("workspace-transfer.archive-source"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Archive on source machine",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(checked = archive, onCheckedChange = null)
        }
        plan?.let { preview ->
            Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.row)) {
                val size = Formatter.formatFileSize(LocalContext.current, preview.estimatedBytes)
                Text(
                    "Estimated size: $size",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentScreen.secondaryText
                )
                preview.warnings.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentScreen.secondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationField(
    destinations: List<SavedConnection>,
    selected: SavedConnection?,
    onSelect: (SavedConnection?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.displayName ?: CHOOSE_MACHINE
    val enabled = destinations.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.row)) {
        Text(
            "Destination",
            style = MaterialTheme.typography.bodySmall,
            color = IntentScreen.secondaryText
        )
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = FIELD_HEIGHT)
                    .clip(FieldShape)
                    .background(IntentScreen.surface)
                    .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                    .semantics {
                        contentDescription = "Destination"
                        stateDescription = label
                    }
                    .padding(horizontal = IntentSpacing.content)
                    .testTag("workspace-transfer.destination"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) IntentScreen.text else IntentScreen.secondaryText
                )
                Icon(
                    painterResource(R.drawable.ic_chevron_down),
                    null,
                    Modifier.size(20.dp),
                    tint = IntentScreen.secondaryText
                )
            }
            DestinationMenu(expanded, destinations, selected, onDismiss = { expanded = false }) {
                onSelect(it)
                expanded = false
            }
        }
        if (!enabled) {
            Text(
                "Pair another machine from Settings to transfer this workspace. " +
                    "You must be an owner on both machines.",
                style = MaterialTheme.typography.bodyMedium,
                color = IntentScreen.secondaryText
            )
        }
    }
}

@Composable
private fun DestinationMenu(
    expanded: Boolean,
    destinations: List<SavedConnection>,
    selected: SavedConnection?,
    onDismiss: () -> Unit,
    onSelect: (SavedConnection?) -> Unit
) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(CHOOSE_MACHINE) },
            onClick = { onSelect(null) },
            trailingIcon = { if (selected == null) MenuCheck() }
        )
        destinations.forEach { destination ->
            DropdownMenuItem(
                text = { Text(destination.displayName) },
                onClick = { onSelect(destination) },
                trailingIcon = { if (destination.id == selected?.id) MenuCheck() }
            )
        }
    }
}

@Composable
private fun MenuCheck() {
    Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp))
}

@Composable
private fun TransferProgressView(fraction: Float?, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.row)) {
        if (fraction == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
        Text(
            message,
            Modifier.testTag("workspace-transfer.progress"),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** iOS `IntentButtonStyle(variant: .secondary)`: neutral filled surface. */
@Composable
private fun SecondaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = FIELD_HEIGHT),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = IntentScreen.surface,
            contentColor = IntentScreen.text,
            disabledContainerColor = IntentScreen.surface.copy(alpha = DISABLED_ALPHA),
            disabledContentColor = IntentScreen.text.copy(alpha = DISABLED_ALPHA)
        )
    ) {
        Text(label)
    }
}

/** Failures before the relay starts (connect, owner check) still settle the attempt. */
private fun settleEarlyFailure(control: TransferControl, error: Throwable) {
    val state = control.state.value
    if (!state.phase.busy) return
    if (state.phase == TransferPhase.CANCELLING) {
        control.finish(TransferPhase.CANCELLED, TRANSFER_CANCELLED)
    } else {
        control.finish(TransferPhase.FAILED, TRANSFER_FAILED, transferErrorText(error))
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
): String? {
    val ready = CompletableFuture<Unit>()
    val target = createTarget(destination) { state ->
        if (state == SessionState.READY) ready.complete(Unit)
    }
    return target.use {
        target.connect()
        awaitDestination(ready, control)
        control.checkCancellation()
        if (!transferPrincipalIsOwner(
                RpcWorkspaceOperations(target).request("principal.me"),
                target.serverCapabilities
            )
        ) {
            fault("Workspace transfer requires owner access on the destination.")
        }
        WorkspaceTransfer(source, RpcWorkspaceOperations(target), workspaceId, control)
            .run(archive)
    }
}

private fun awaitDestination(ready: CompletableFuture<Unit>, control: TransferControl) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CONNECT_SECONDS)
    while (!ready.isDone) {
        control.checkCancellation()
        if (System.nanoTime() >= deadline) {
            fault("The destination took too long to connect. Check the machine and try again.")
        }
        try {
            ready.get(CONNECT_POLL_MS, TimeUnit.MILLISECONDS)
        } catch (_: java.util.concurrent.TimeoutException) {
            // Poll cancellation without interrupting a transfer RPC.
        }
    }
}

private const val CHOOSE_MACHINE = "Choose a machine"
private val FieldShape = RoundedCornerShape(20.dp)
private val FIELD_HEIGHT = 48.dp
private const val DISABLED_ALPHA = 0.5f
private const val CONNECT_POLL_MS = 100L
private const val CONNECT_SECONDS = 15L
