package dev.intentapp.android.navigation

import androidx.annotation.DrawableRes

/** One overflow menu item. [group] draws a divider before the item, like iOS menu sections. */
data class ChromeAction(
    val label: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    @DrawableRes val icon: Int? = null,
    val group: Boolean = false,
    val run: () -> Unit
)
