package dev.intent.app.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry

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
        if (isManagedEmulator(
                shell("getprop ro.kernel.qemu").trim(),
                android.os.Build.FINGERPRINT
            )
        ) {
            // Disabling the radio alone does not stop an already crashing Bluetooth service.
            val packages = shell("pm list packages").lineSequence().map {
                it.removePrefix("package:")
            }
                .filter { it == "com.google.android.bluetooth" || it == "com.android.bluetooth" }
            packages.forEach { name ->
                check(shell("pm disable-user --user 0 $name").contains("disabled-user")) {
                    "Could not disable emulator Bluetooth package $name"
                }
            }
        }
        delegate.activityRule.scenario.onActivity { it.setTestContent(composable) }
        delegate.waitForIdle()
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader().use { it.readText() }
    }

    fun recreateActivity() {
        delegate.activityRule.scenario.recreate()
        delegate.waitForIdle()
    }
}

internal fun isManagedEmulator(qemu: String, fingerprint: String): Boolean =
    qemu == "1" && fingerprint.startsWith("google/sdk_gphone") && fingerprint.contains("/emu64")
