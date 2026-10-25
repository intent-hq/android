package dev.intent.app.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.intent.app.settings.AppPreferences

/** The settings button and the permission result share this persisted opt-in policy. */
class NotificationPermissionFlow(
    private val context: Context,
    private val preferences: AppPreferences
) {
    fun toggle(requestPermission: (String) -> Unit) {
        when {
            preferences.notifications.value -> preferences.setNotifications(false)
            needsPermission() -> requestPermission(Manifest.permission.POST_NOTIFICATIONS)
            else -> preferences.setNotifications(true)
        }
    }

    fun onPermissionResult(granted: Boolean) = preferences.setNotifications(granted)

    private fun needsPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
}
