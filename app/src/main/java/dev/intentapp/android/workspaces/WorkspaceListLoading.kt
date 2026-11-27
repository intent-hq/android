package dev.intentapp.android.workspaces

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import dev.intentapp.android.design.IntentScreen

/** iOS WorkspaceListView shows plain text, not a spinner, while the first page loads. */
internal fun LazyListScope.workspaceLoadingItem(state: WorkspaceListState) {
    if (!state.isLoading || state.workspaces.isNotEmpty()) return
    item(key = "loading") {
        Text(
            "Loading workspaces…",
            PageInset.padding(top = RECENT_TOP_SPACING),
            color = IntentScreen.secondaryText
        )
    }
}
