package dev.intent.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val LocalChromeActions = staticCompositionLocalOf { emptyList<ChromeAction>() }
val LocalChromeBack = staticCompositionLocalOf<(() -> Unit)?> { null }
val LocalWorkspaceDetails =
    staticCompositionLocalOf<dev.intent.core.protocol.models.Workspace?> { null }
val LocalWorkspaceTitle = staticCompositionLocalOf { "Workspace" }

@Composable
fun CompactAppBar(
    title: String,
    onBack: (() -> Unit)? = LocalChromeBack.current,
    actions: List<ChromeAction> = emptyList(),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    var expanded by remember(title) { mutableStateOf(false) }
    val menu = actions + LocalChromeActions.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
        } else if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = "Back"
                }
            ) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Text(
            title,
            Modifier.weight(1f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
        if (menu.isNotEmpty()) {
            Box {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "More actions"
                    }
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                ) { Text("⋮", style = MaterialTheme.typography.headlineSmall) }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    menu.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            enabled = action.enabled,
                            onClick = {
                                expanded = false
                                action.run()
                            }
                        )
                    }
                }
            }
        }
    }
}
