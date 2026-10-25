package dev.intent.app.notifications

import android.Manifest
import android.app.NotificationManager
import dev.intent.app.settings.AppPreferences
import dev.intent.app.settings.Appearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationSettingsTest {
    @Test fun appearanceAndNotificationPreferenceSurviveRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val first = AppPreferences(context)
        first.setAppearance(Appearance.Dark)
        first.setNotifications(true)
        val restored = AppPreferences(context)
        assertEquals(Appearance.Dark, restored.appearance.value)
        assertTrue(restored.notifications.value)
    }

    @Test fun channelsAreRegisteredWithStableIds() {
        val context = RuntimeEnvironment.getApplication()
        LocalNotifications(context)
        LocalNotifications(context)
        val channels = context.getSystemService(
            NotificationManager::class.java
        ).notificationChannels
        assertEquals(setOf("attention", "completion"), channels.map { it.id }.toSet())
    }

    @Test fun missingPermissionRequestsPostNotificationsWithoutEnabling() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val preferences = AppPreferences(context)
        preferences.setNotifications(false)
        val requests = mutableListOf<String>()
        NotificationPermissionFlow(context, preferences).toggle(requests::add)
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), requests)
        assertFalse(AppPreferences(context).notifications.value)
    }

    @Test fun grantCallbackPersistsOptIn() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = AppPreferences(context)
        preferences.setNotifications(false)
        NotificationPermissionFlow(context, preferences).onPermissionResult(true)
        assertTrue(AppPreferences(context).notifications.value)
    }

    @Test fun denialCallbackPersistsOptOut() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = AppPreferences(context)
        preferences.setNotifications(true)
        NotificationPermissionFlow(context, preferences).onPermissionResult(false)
        assertFalse(AppPreferences(context).notifications.value)
    }

    @Test fun grantedPermissionEnablesWithoutAnotherRequest() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val preferences = AppPreferences(context)
        preferences.setNotifications(false)
        NotificationPermissionFlow(context, preferences).toggle {
            error("Unexpected permission request")
        }
        assertTrue(AppPreferences(context).notifications.value)
    }

    @Test
    @Config(sdk = [32])
    fun beforeAndroid33EnablesWithoutRuntimePermission() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = AppPreferences(context)
        preferences.setNotifications(false)
        NotificationPermissionFlow(context, preferences).toggle {
            error("Unexpected permission request")
        }
        assertTrue(AppPreferences(context).notifications.value)
    }

    @Test fun disablePersistsOptOutWithoutPermissionRequest() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val preferences = AppPreferences(context)
        preferences.setNotifications(true)
        NotificationPermissionFlow(context, preferences).toggle {
            error("Unexpected permission request")
        }
        assertFalse(AppPreferences(context).notifications.value)
    }
}
