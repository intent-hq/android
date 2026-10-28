package dev.intent.app.testing

import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule

fun createIntentComposeRule() = IntentComposeRule()

class IntentComposeRule private constructor(
    private val delegate: androidx.compose.ui.test.junit4.AndroidComposeTestRule<
        androidx.test.ext.junit.rules.ActivityScenarioRule<ComposeTestActivity>,
        ComposeTestActivity
        >
) : ComposeContentTestRule by delegate {
    constructor() : this(createAndroidComposeRule<ComposeTestActivity>())

    override fun setContent(composable: @Composable () -> Unit) {
        // The API 34 managed emulator can loop Bluetooth crashes and steal window focus.
        if (android.os.Build.FINGERPRINT.contains("generic") ||
            android.os.Build.FINGERPRINT.contains("emu64")
        ) {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("svc bluetooth disable")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        delegate.activityRule.scenario.onActivity { it.setTestContent(composable) }
        delegate.waitForIdle()
    }

    fun recreateActivity() {
        delegate.activityRule.scenario.recreate()
        delegate.waitForIdle()
    }
}
