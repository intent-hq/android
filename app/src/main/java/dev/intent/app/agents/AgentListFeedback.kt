package dev.intent.app.agents

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import dev.intent.app.design.IntentTextButton as TextButton

internal val AgentListState.hasAgents: Boolean
    get() = agents.isNotEmpty() || retiredAgents.isNotEmpty()

private val AgentListState.finishedLoading: Boolean
    get() = !isLoading && !retiredLoading

internal fun LazyListScope.agentListFeedback(
    state: AgentListState,
    noMatches: Boolean,
    onRefresh: () -> Unit
) {
    state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    if (state.finishedLoading && !state.hasAgents && state.error == null) {
        item { Text("No agents yet") }
    }
    if (noMatches) item { Text("No matching agents. Change the search or select All.") }
    if (state.retiredLoading) item { Text("Loading retired agents…") }
    state.retiredError?.let { error ->
        item { TextButton(onClick = onRefresh) { Text("$error · Retry") } }
    }
}
