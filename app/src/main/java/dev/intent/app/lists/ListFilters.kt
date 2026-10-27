package dev.intent.app.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.intent.app.design.IntentSpacing
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.IntentTextField as OutlinedTextField

@Composable
internal fun ListFilters(
    query: String,
    currentOnly: Boolean,
    label: String,
    onQuery: (String) -> Unit,
    onCurrentOnly: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("Clear") }
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium)) {
            FilterChip(currentOnly, { onCurrentOnly(true) }, label = { Text("Current") })
            FilterChip(!currentOnly, { onCurrentOnly(false) }, label = { Text("All") })
        }
    }
}

/** Include each matching row's ancestors. Missing parents and cycles terminate the walk. */
internal fun <T> retainMatchingBranches(
    items: List<T>,
    id: (T) -> String,
    parentId: (T) -> String?,
    matches: (T) -> Boolean
): List<T> {
    val byId = items.associateBy(id)
    val included = mutableSetOf<String>()
    items.filter(matches).forEach { match ->
        var current: T? = match
        while (current != null && included.add(id(current))) {
            current = parentId(current)?.let(byId::get)
        }
    }
    return items.filter { id(it) in included }
}
