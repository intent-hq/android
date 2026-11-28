package dev.intentapp.android.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Appearance { System, Light, Dark }

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        "appearance-notifications",
        Context.MODE_PRIVATE
    )
    private val appearanceState = MutableStateFlow(
        Appearance.entries.firstOrNull { it.name == preferences.getString("appearance", null) }
            ?: Appearance.System
    )
    val appearance = appearanceState.asStateFlow()
    private val notificationState = MutableStateFlow(preferences.getBoolean("notifications", false))
    val notifications = notificationState.asStateFlow()
    private val lockOrientationState =
        MutableStateFlow(preferences.getBoolean("lock-orientation", true))
    val lockOrientation = lockOrientationState.asStateFlow()

    fun setAppearance(value: Appearance) {
        preferences.edit().putString("appearance", value.name).apply()
        appearanceState.value = value
    }

    fun setNotifications(value: Boolean) {
        preferences.edit().putBoolean("notifications", value).apply()
        notificationState.value = value
    }

    fun setLockOrientation(value: Boolean) {
        preferences.edit().putBoolean("lock-orientation", value).apply()
        lockOrientationState.value = value
    }
}
