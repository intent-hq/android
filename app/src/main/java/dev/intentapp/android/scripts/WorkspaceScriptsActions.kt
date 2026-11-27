package dev.intentapp.android.scripts

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Screen callbacks, kept separate from the store so screenshots need no host. */
data class WorkspaceScriptsActions(
    val launch: (String) -> Unit = {},
    val stop: (String) -> Unit = {},
    val remove: (String) -> Unit = {},
    val showOutput: (String) -> Unit = {},
    val reload: () -> Unit = {}
)

/** Output sheet callbacks. */
data class ScriptOutputActions(
    val dismiss: () -> Unit = {},
    val refresh: () -> Unit = {},
    val launch: (String) -> Unit = {},
    val stop: (String) -> Unit = {},
    val openBrowser: (String) -> Unit = {}
)

/** iOS IntentButtonStyle(.secondary): a bordered button with foreground, not accent, text. */
@Composable
internal fun ScriptSecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) { Text(text) }
}
