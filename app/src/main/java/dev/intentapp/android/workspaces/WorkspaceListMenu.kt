package dev.intentapp.android.workspaces

import androidx.compose.runtime.Composable
import dev.intentapp.android.R
import dev.intentapp.android.drawers.DrawerButtons
import dev.intentapp.android.navigation.ChromeAction
import dev.intentapp.android.navigation.LocalLauncherActions

/** iOS WorkspaceListMenu: New workspace, recent agents and connections, then archived rows. */
@Composable
internal fun workspaceListMenu(
    showArchived: Boolean,
    drawers: DrawerButtons?,
    toggleArchived: () -> Unit
): List<ChromeAction> = listOfNotNull(
    LocalLauncherActions.current?.onCreate?.let {
        ChromeAction("New workspace", icon = R.drawable.ic_add, run = it)
    },
    drawers?.let {
        ChromeAction(
            "Recent agents",
            icon = R.drawable.ic_message_list,
            group = true,
            run = it.history
        )
    },
    drawers?.let {
        ChromeAction(
            "Recent connections",
            icon = R.drawable.ic_connection_laptop,
            run = it.connections
        )
    },
    ChromeAction(
        if (showArchived) "Hide archived" else "Show archived",
        icon = R.drawable.ic_archive,
        run = toggleArchived
    )
)
