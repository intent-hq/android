package dev.intent.app.workspaces

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import dev.intent.app.design.IntentScreen

/** iOS WorkspaceListView shows plain text, not a spinner, while the first page loads. */
internal fun LazyListScope.workspaceLoadingItem(state: WorkspaceListState) {
    if (!state.isLoading || state.workspaces.isNotEmpty()) return
    item(key = "loading") {
        Text(
            "Loading workspaces…",
            Modifier.padding(top = RECENT_TOP_SPACING),
            color = IntentScreen.secondaryText
        )
    }
}
