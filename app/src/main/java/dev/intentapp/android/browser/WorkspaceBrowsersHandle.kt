package dev.intentapp.android.browser

/** Saved-browser state and actions shared by the Browsers screen and the Agents page section. */
class WorkspaceBrowsersHandle internal constructor(
    val state: SavedBrowsersState,
    val connected: Boolean,
    val open: (SavedBrowser) -> Unit,
    val remove: (String) -> Unit,
    val retry: () -> Unit,
    val new: () -> Unit
)
