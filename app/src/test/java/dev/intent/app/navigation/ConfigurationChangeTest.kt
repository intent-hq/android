package dev.intent.app.navigation

import android.content.res.Configuration
import dev.intent.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise framework recreation decisions using the application's merged manifest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "port-notnight")
class ConfigurationChangeTest {
    @Test
    fun themeAndRotationKeepTheActivityAndItsScreenTree() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            val screen = activity.window.decorView
            val dark = Configuration(activity.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    Configuration.UI_MODE_NIGHT_YES
            }
            controller.configurationChange(dark)
            assertSame(
                "Theme must retain the activity and its remembered state",
                activity,
                controller.get()
            )
            assertSame(screen, controller.get().window.decorView)
            assertEquals(
                Configuration.UI_MODE_NIGHT_YES,
                activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            )
            val landscape = Configuration(dark).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = dark.screenHeightDp
                screenHeightDp = dark.screenWidthDp
            }
            controller.configurationChange(landscape)
            assertSame(
                "Rotation must retain navigation and screen stores",
                activity,
                controller.get()
            )
            assertSame(screen, controller.get().window.decorView)
            assertEquals(
                Configuration.ORIENTATION_LANDSCAPE,
                activity.resources.configuration.orientation
            )
            controller.configurationChange(dark)
            assertSame(activity, controller.get())
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
