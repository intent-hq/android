package dev.intent.app.notifications

import android.app.NotificationManager
import dev.intent.app.settings.AppPreferences
import dev.intent.app.settings.Appearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
}
