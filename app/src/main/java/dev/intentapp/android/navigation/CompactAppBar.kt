package dev.intentapp.android.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentSpacing

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
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = IntentSpacing.page),
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
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null)
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Text(
            title,
            Modifier.weight(1f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
        ChromeActionsMenu(actions)
    }
}

@Composable
internal fun ChromeActionsMenu(actions: List<ChromeAction>) {
    var expanded by remember { mutableStateOf(false) }
    if (actions.isNotEmpty()) {
        Box {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = "More actions"
                }
            ) { Icon(painterResource(R.drawable.ic_more), contentDescription = null) }
            DropdownMenu(
                expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                actions.forEachIndexed { index, action ->
                    if (action.group &&
                        index > 0
                    ) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                    DropdownMenuItem(
                        text = { ActionLabel(action) },
                        enabled = action.enabled,
                        trailingIcon = action.icon?.let { icon ->
                            {
                                Icon(
                                    painterResource(icon),
                                    null,
                                    Modifier.size(20.dp),
                                    tint = actionColor(action)
                                )
                            }
                        },
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

@Composable
private fun actionColor(action: ChromeAction) = (
    if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ).copy(alpha = if (action.enabled) 1f else DISABLED_ALPHA)

private const val DISABLED_ALPHA = 0.38f

@Composable
private fun ActionLabel(action: ChromeAction) {
    Text(action.label, style = MaterialTheme.typography.bodyLarge, color = actionColor(action))
}
