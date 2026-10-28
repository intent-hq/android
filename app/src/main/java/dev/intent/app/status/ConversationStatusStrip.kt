package dev.intent.app.status

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.intent.app.agents.avatarState
import dev.intent.app.conversation.conversationRunning
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.core.protocol.models.Agent
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

val LocalWatchState = staticCompositionLocalOf { WatchState() }

@Suppress("LongParameterList")
@Composable
fun ConversationStatusStrip(
    state: WatchState,
    agent: Agent?,
    agents: List<Agent>,
    onSelect: (JsonObject) -> Unit,
    onRetry: (WatchFamily) -> Unit,
    onMore: () -> Unit,
    onAgent: (String) -> Unit = {},
    isStreaming: Boolean = agent?.conversationRunning() == true
) {
    val now = watchClock()
    Column(
        Modifier.testTag(
            "conversation-status"
        ).heightIn(max = 220.dp).verticalScroll(rememberScrollState())
    ) {
        if (isStreaming) {
            Text(
                "Working…",
                modifier = Modifier.testTag("conversation-working"),
                style = MaterialTheme.typography.labelMedium
            )
        }
        WatchFamily.entries.forEach { family ->
            WatchRow(family, state, agent?.id.orEmpty(), now, onSelect, onRetry, onMore)
        }
        if (agent != null &&
            (agent.isWaitingForOtherAgents || agent.waitingForAgentIds.isNotEmpty())
        ) {
            Text("Waiting for agents", style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                agent.waitingForAgentIds.forEach { id ->
                    val child = agents.firstOrNull { it.id == id }
                    AssistChip(onClick = { onAgent(id) }, label = {
                        Text(
                            "${child?.name ?: id} · ${child?.avatarState()?.label ?: "Status unavailable"}"
                        )
                    })
                }
            }
        }
    }
}

@Suppress("LongParameterList", "LongMethod") // One compact row per watch family.
@Composable
private fun WatchRow(
    family: WatchFamily,
    state: WatchState,
    agentId: String,
    now: Instant,
    onSelect: (JsonObject) -> Unit,
    onRetry: (WatchFamily) -> Unit,
    onMore: () -> Unit
) {
    val bucket = state.buckets[family] ?: return
    val rows = state.rows(family, agentId)
    val empty = rows.isEmpty() && bucket.page.nextCursor == null
    if (empty && !bucket.loading && bucket.error == null) return

    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (bucket.loading) {
            Text(
                "Loading ${family.label()}…",
                style = MaterialTheme.typography.labelSmall
            )
        }
        bucket.error?.let {
            TextButton(onClick = {
                onRetry(family)
            }) { Text("${family.label()} unavailable · Retry") }
        }
        rows.forEach { row ->
            if (family == WatchFamily.SCRIPT) {
                AssistChip(onClick = {
                    onSelect(row)
                }, modifier = Modifier.testTag("watch-${row.text("monitorId")}"), label = {
                    Column(Modifier.widthIn(max = 280.dp)) {
                        Text("Watching ${row.text("scriptName")}")
                        Text(
                            WatchTime.elapsed(row.text("createdAt"), now),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            WatchTime.expiry(row.text("expiresAt"), now),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                })
            } else {
                Column(Modifier.widthIn(max = 280.dp).padding(vertical = 6.dp)) {
                    if (family == WatchFamily.HOOK) {
                        HookStatus(row, now)
                    } else {
                        Text(
                            "Monitoring PR ${row.text("repo")}: #${row.number("prNumber")}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(prStatus(row), style = MaterialTheme.typography.labelSmall)
                        if (row.text("hasPendingChanges") ==
                            "true"
                        ) {
                            Text("Pending changes", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if (bucket.page.nextCursor !=
            null
        ) {
            TextButton(onClick = onMore, enabled = !bucket.loading) {
                Text("Load more script watches")
            }
        }
    }
}

private fun WatchFamily.label() = when (this) {
    WatchFamily.SCRIPT -> "Script watches"
    WatchFamily.HOOK -> "Hooks"
    WatchFamily.PR -> "PR monitors"
}

@Composable
private fun HookStatus(row: JsonObject, now: Instant) {
    Text(
        "${if (row.text("perpetual") == "true") "↻ " else ""}${row.text("name")}",
        style = MaterialTheme.typography.labelMedium
    )
    if (row.text("state") == "running") {
        Text("Running hook…", style = MaterialTheme.typography.labelSmall)
    } else {
        Text(
            WatchTime.next(row.text("nextRunAt"), now),
            style = MaterialTheme.typography.labelSmall
        )
        LinearProgressIndicator(
            progress = {
                WatchTime.fraction(row.text("nextRunAt"), row.number("delayMs") ?: 0, now)
            },
            modifier = Modifier.fillMaxWidth().testTag("hook-time-${row.text("hookId")}")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptWatchSheet(
    selected: WatchDetail,
    owner: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val now = watchClock()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Script watch", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onDismiss) { Text("Close") }
            if (selected.unavailable) {
                Text("This watch is no longer available")
            } else {
                val row = selected.detail ?: selected.summary
                Text(row.text("scriptName").orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(WatchTime.expiry(row.text("expiresAt"), now))
                if (selected.loading) Text("Loading full watch details…")
                selected.error?.let {
                    Text(it)
                    TextButton(onClick = onRetry) { Text("Retry details") }
                }
                WatchMetadata(row, owner, selected.detail != null)
            }
        }
    }
}

@Composable
private fun WatchMetadata(row: JsonObject, owner: String?, full: Boolean) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "Owner" to (owner ?: row.text("agentId")),
                "Mode" to row.text("mode"),
                "Script ID" to row.text("scriptId"),
                "Watched run ID" to row.text("runId"),
                "Created" to WatchTime.absolute(row.text("createdAt")),
                "Watch expires" to WatchTime.absolute(row.text("expiresAt"))
            ).forEach { (name, value) ->
                Text(name, style = MaterialTheme.typography.labelSmall)
                Text(value ?: "Unavailable")
            }
            Text("Notifies the agent when this run finishes or the watch expires.")
            row.text(if (full) "outputPattern" else "outputPatternPreview")?.let {
                Text(
                    if (full) {
                        "Also notifies when new output matches this pattern"
                    } else {
                        "Output pattern preview — full pattern loads with details"
                    }
                )
                Text(it)
            }
            row.number("lineCount")?.let {
                Text("Also notifies after $it new output ${if (it == 1L) "line" else "lines"}.")
            }
        }
    }
}

@Composable
private fun watchClock(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = Instant.now()
                delay(CLOCK_TICK_MS)
            }
        }
    }
    return now
}

private const val CLOCK_TICK_MS = 1000L
