package dev.intent.app.git

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentTextButton as TextButton

@Composable
internal fun GitRepositoryPicker(
    state: GitState,
    repository: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    IntentSection("Repository") {
        if (state.roots.isNotEmpty()) {
            Box {
                TextButton(
                    onClick = { expanded = true },
                    modifier = Modifier.semantics {
                        contentDescription =
                            "Repository"
                    }
                ) {
                    RepositoryLabel(state.selectedRoot)
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Main repository") }, onClick = {
                        expanded =
                            false
                        onSelect(null)
                    })
                    state.roots.forEach { root ->
                        DropdownMenuItem(text = { RepositoryLabel(root) }, onClick = {
                            expanded =
                                false
                            onSelect(root.id)
                        })
                    }
                }
            }
        } else {
            Text(repository ?: "Main repository")
        }
        Text(state.status.branch ?: "No branch", color = IntentScreen.secondaryText)
        state.repositoryError?.let { Text(it, color = IntentScreen.error) }
    }
}

@Composable
private fun RepositoryLabel(root: dev.intent.core.protocol.models.GitRoot?) {
    Column {
        Text(root?.displayName ?: "Main repository")
        root?.let {
            Text(
                it.path,
                style = MaterialTheme.typography.bodySmall,
                color = IntentScreen.secondaryText
            )
        }
    }
}
