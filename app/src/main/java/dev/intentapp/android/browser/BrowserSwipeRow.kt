package dev.intentapp.android.browser

import androidx.compose.runtime.Composable
import dev.intentapp.android.design.ResourceSwipeRow

/** iOS WorkspaceSwipeContainer for a saved browser: swipe or long press to delete. */
@Suppress("LongParameterList")
@Composable
internal fun BrowserSwipeRow(
    browser: SavedBrowser,
    revealed: Boolean,
    enabled: Boolean,
    onReveal: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable (Float) -> Unit
) = ResourceSwipeRow(
    browser.id,
    "browser",
    revealed,
    enabled,
    onReveal,
    onOpen,
    onDelete,
    content = content
)
