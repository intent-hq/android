package dev.intent.app.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

enum class PrMonitorAction(val method: String, val label: String) {
    FLUSH("flush", "Check and flush"),
    CANCEL("cancel", "Cancel monitor")
}

data class PrActionState(val busy: Boolean = false, val error: String? = null)

@Composable
internal fun PrMonitorRow(
    row: JsonObject,
    actionState: PrActionState?,
    onAction: (String, PrMonitorAction) -> Unit
) {
    val id = row.text("monitorId").orEmpty()
    var expanded by remember(id) { mutableStateOf(false) }
    var openError by remember(id) { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    Box {
        PrMonitorLabel(row, actionState, openError) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            prDetailLines(row).forEach {
                Text(
                    it,
                    Modifier.widthIn(max = 300.dp).padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text("Check and flush")
                },
                enabled = actionState?.busy != true,
                onClick = {
                    expanded = false
                    onAction(id, PrMonitorAction.FLUSH)
                }
            )
            DropdownMenuItem(
                text = { Text("Open PR") },
                enabled = prUrl(row) != null,
                onClick = {
                    expanded = false
                    openError = runCatching { uriHandler.openUri(requireNotNull(prUrl(row))) }
                        .exceptionOrNull()?.let { "Cannot open this PR." }
                }
            )
            DropdownMenuItem(
                text = {
                    Text("Cancel monitor", color = MaterialTheme.colorScheme.error)
                },
                enabled = actionState?.busy != true,
                onClick = {
                    expanded = false
                    onAction(id, PrMonitorAction.CANCEL)
                }
            )
        }
    }
}

@Composable
private fun PrMonitorLabel(
    row: JsonObject,
    actionState: PrActionState?,
    openError: String?,
    onClick: () -> Unit
) {
    val id = row.text("monitorId").orEmpty()
    Column(Modifier.testTag("pr-monitor-$id").clickable(onClick = onClick)) {
        Text(
            "Monitoring PR ${row.text("repo")}: #${row.number("prNumber")}",
            style = MaterialTheme.typography.labelMedium
        )
        Text(prStatus(row), style = MaterialTheme.typography.labelSmall)
        if (row.text("hasPendingChanges") == "true") {
            Text("Pending changes", style = MaterialTheme.typography.labelSmall)
        }
        if (actionState?.busy ==
            true
        ) {
            Text("Updating monitor…", style = MaterialTheme.typography.labelSmall)
        }
        (actionState?.error ?: openError)?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

internal fun prUrl(row: JsonObject): String? {
    val value =
        row.text("url")
            ?: "https://github.com/${row.text(
                "repo"
            )}/pull/${row.number("prNumber") ?: return null}"
    return runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme?.lowercase() in setOf("http", "https") &&
            !it.host.isNullOrBlank() &&
            it.userInfo == null
    }?.toString()
}

internal fun prDetailLines(row: JsonObject): List<String> = buildList {
    val identity = "${row.text("repo")}#${row.number("prNumber")}"
    val snapshot = row.objectValue("lastSnapshot")
    add((row.text("title") ?: identity) + (snapshot?.text("state")?.let { " — $it" } ?: ""))
    val summary = mutableListOf(identity)
    snapshot?.objectValue("checks")?.let(::prChecksSummary)?.let(summary::add)
    snapshot?.objectValue("approvals")?.let(::prApprovalsSummary)?.let(summary::add)
    snapshot?.objectValue("threads")?.number("unresolved")?.takeIf { it > 0 }?.let {
        summary.add("$it unresolved ${if (it == 1L) "thread" else "threads"}")
    }
    summary.joinToString(" · ").takeIf { it != first() }?.let(::add)
    add(prStatus(row))
    row.text("lastChangeAt")?.let { add("Last change ${WatchTime.absolute(it)}") }
    add(prPendingSummary(row))
}

private fun prChecksSummary(checks: JsonObject): String? {
    val total = checks.number("total") ?: 0
    if (total == 0L) return null
    val (key, label) = when {
        (checks.number("failed") ?: 0) > 0 -> "failed" to "failing"
        (checks.number("pending") ?: 0) > 0 -> "pending" to "pending"
        else -> "passed" to "passing"
    }
    return "${checks.number(key) ?: 0}/$total $label"
}

private fun prApprovalsSummary(approvals: JsonObject): String {
    val have = approvals.number("have") ?: 0
    val needed = approvals.number("needed")
    val noun = if ((needed ?: have) == 1L) "approval" else "approvals"
    return "${approvals.text("decision")} ($have${needed?.let { "/$it" } ?: ""} $noun)"
}

private fun prPendingSummary(row: JsonObject): String {
    val pending = (row["pendingChanges"] as? JsonArray)?.size ?: 0
    return when {
        row.text("hasPendingChanges") != "true" -> "No pending changes"
        pending == 0 -> "Pending changes"
        pending == 1 -> "1 pending change"
        else -> "$pending pending changes"
    }
}
