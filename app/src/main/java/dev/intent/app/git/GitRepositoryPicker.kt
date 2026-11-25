package dev.intent.app.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing

@Composable
internal fun GitRepositoryPicker(state: GitState, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = IntentSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)
    ) {
        if (state.roots.isNotEmpty()) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .semantics { contentDescription = "Repository" }
                        .clickable { expanded = true }
                        .padding(vertical = IntentSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
                ) {
                    Column(Modifier.weight(1f)) {
                        RepositoryLabel(state.selectedRoot)
                    }
                    Text(
                        "⌄",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentScreen.secondaryText
                    )
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Main repository") }, onClick = {
                        expanded = false
                        onSelect(null)
                    })
                    state.roots.forEach { root ->
                        DropdownMenuItem(text = { RepositoryLabel(root) }, onClick = {
                            expanded = false
                            onSelect(root.id)
                        })
                    }
                }
            }
        }
        val branch = state.status.branch ?: "No branch"
        Text(
            branch,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = IntentScreen.secondaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        state.repositoryError?.let { Text(it, color = IntentScreen.error) }
    }
}

@Composable
private fun RepositoryLabel(root: dev.intent.core.protocol.models.GitRoot?) {
    Column {
        Text(
            root?.displayName ?: "Main repository",
            style = MaterialTheme.typography.titleSmall,
            color = IntentScreen.text
        )
        root?.let {
            Text(
                it.path,
                style = MaterialTheme.typography.bodySmall,
                color = IntentScreen.secondaryText
            )
        }
    }
}
