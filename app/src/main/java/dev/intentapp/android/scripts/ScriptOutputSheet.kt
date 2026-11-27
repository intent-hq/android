package dev.intentapp.android.scripts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentButton
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScriptOutputSheet(
    output: ScriptOutputState,
    row: WorkspaceScriptRow?,
    state: WorkspaceScriptsState,
    actions: ScriptOutputActions
) {
    ModalBottomSheet(
        onDismissRequest = actions.dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null
    ) {
        ScriptOutputContent(output, row, state, actions)
    }
}

/** iOS ScriptMonitorDetailView layout: close, heading, a field card, then a labeled output card. */
@Composable
fun ScriptOutputContent(
    output: ScriptOutputState,
    row: WorkspaceScriptRow?,
    state: WorkspaceScriptsState,
    actions: ScriptOutputActions
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = IntentSpacing.page)
            .padding(top = IntentSpacing.content, bottom = SHEET_BOTTOM)
            .testTag("script.output"),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING)
    ) {
        IconButton(onClick = actions.dismiss, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.intent_close), contentDescription = "Close")
        }
        ScriptOutputHeading(row, state)
        row?.let { ScriptFields(it) }
        row?.let { ScriptControls(it, state, actions) }
        ScriptOutputText(output)
    }
}

@Composable
private fun ScriptOutputHeading(row: WorkspaceScriptRow?, state: WorkspaceScriptsState) {
    Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)) {
        Text(
            "Script output",
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall
        )
        row?.let {
            Text(it.name, style = MaterialTheme.typography.titleSmall)
            Text(
                it.runtime?.error ?: scriptStatusLabel(it, state),
                style = MaterialTheme.typography.bodyMedium,
                color = IntentScreen.secondaryText
            )
        }
    }
}

@Composable
private fun ScriptOutputText(output: ScriptOutputState) {
    val secondary = IntentScreen.secondaryText
    Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)) {
        Text(
            "OUTPUT",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.24.sp),
            color = secondary
        )
        OutputCard {
            when {
                output.error != null -> Text(output.error, color = secondary)
                output.text == null -> Text("Loading output…", color = secondary)
                else -> SelectionContainer {
                    Text(
                        output.text.ifEmpty { "No output yet." },
                        Modifier.horizontalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.sp
                        ),
                        softWrap = false
                    )
                }
            }
        }
        if (output.loading && output.text != null) {
            Text("Refreshing…", style = MaterialTheme.typography.bodyMedium, color = secondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptControls(
    row: WorkspaceScriptRow,
    state: WorkspaceScriptsState,
    actions: ScriptOutputActions
) {
    val enabled = state.connected && !state.isUpdating(row.id)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
    ) {
        if (row.active) {
            IntentButton(onClick = { actions.stop(row.id) }, enabled = enabled) {
                Text("Stop script")
            }
        } else {
            IntentButton(
                onClick = { actions.launch(row.id) },
                enabled = enabled && !state.isLaunching(row)
            ) { Text("Run script") }
        }
        row.browserUrl?.let { url ->
            ScriptSecondaryButton("Open preview", { actions.openBrowser(url) }, state.connected)
        }
        ScriptSecondaryButton("Refresh output", actions.refresh, state.connected)
    }
}

@Composable
private fun ScriptFields(row: WorkspaceScriptRow) {
    OutputCard {
        Field("Mode", if (row.mode == "service") "Service" else "Command")
        Field("Command", row.command, monospace = true)
        row.runtime?.exitCode?.let { Field("Exit code", it.toString()) }
        row.runtime?.detectedUrl?.let { Field("Address", it) }
        Field("Script ID", row.id)
    }
}

@Composable
private fun OutputCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_RADIUS),
        color = MaterialTheme.colorScheme.surface,
        contentColor = IntentScreen.text
    ) {
        Column(
            Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
            content = content
        )
    }
}

@Composable
private fun Field(label: String, value: String, monospace: Boolean = false) {
    Column(
        Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(FIELD_SPACING)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = IntentScreen.secondaryText)
        SelectionContainer {
            Text(
                value,
                style = if (monospace) {
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.sp
                    )
                } else {
                    MaterialTheme.typography.bodyLarge
                }
            )
        }
    }
}

// iOS ScriptMonitorDetailView.Layout and CreateWorkspaceFormStyle card metrics.
private val SECTION_SPACING = 24.dp
private val ROW_SPACING = 16.dp
private val FIELD_SPACING = 4.dp
private val CARD_RADIUS = 20.dp
private val CARD_PADDING = 20.dp
private val SHEET_RADIUS = 44.dp
private val SHEET_BOTTOM = 48.dp
