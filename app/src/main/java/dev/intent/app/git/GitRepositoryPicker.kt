package dev.intent.app.git

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                    Text(state.selectedRoot?.displayName ?: "Main repository")
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Main repository") }, onClick = {
                        expanded =
                            false
                        onSelect(null)
                    })
                    state.roots.forEach { root ->
                        DropdownMenuItem(text = { Text(root.displayName) }, onClick = {
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
