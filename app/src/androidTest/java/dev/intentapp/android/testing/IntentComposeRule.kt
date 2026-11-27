package dev.intentapp.android.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runners.model.Statement

fun createIntentComposeRule() = IntentComposeRule()

class IntentComposeRule private constructor(
    private val delegate: androidx.compose.ui.test.junit4.AndroidComposeTestRule<
        androidx.test.ext.junit.rules.ActivityScenarioRule<ComposeTestActivity>,
        ComposeTestActivity
        >
) : ComposeContentTestRule by delegate {
    constructor() : this(createAndroidComposeRule<ComposeTestActivity>())

    override fun apply(base: Statement, description: Description) = delegate.apply(
        object : Statement() {
            override fun evaluate() {
                try {
                    base.evaluate()
                } catch (failure: Throwable) {
                    captureFailureScreenshot(description, failure)
                    throw failure
                }
            }
        },
        description
    )

    override fun setContent(composable: @Composable () -> Unit) {
        // The API 34 managed emulator can loop Bluetooth crashes and steal window focus.
        if (isManagedEmulator(
                shell("getprop ro.kernel.qemu").trim(),
                android.os.Build.FINGERPRINT
            )
        ) {
            disableEmulatorBluetooth()
        }
        delegate.activityRule.scenario.onActivity { it.setTestContent(composable) }
        delegate.waitForIdle()
    }

    /**
     * Disabling the radio alone does not stop an already crashing Bluetooth service, but
     * disabling the package while the radio is on makes system_server rebind and crash.
     */
    private fun disableEmulatorBluetooth() {
        val disabled = packages("pm list packages -d").toSet()
        val packages = packages("pm list packages")
            .filter { it == "com.google.android.bluetooth" || it == "com.android.bluetooth" }
            .filterNot { it in disabled }
        if (packages.isEmpty()) return
        shell("settings put global bluetooth_on 0")
        shell("cmd bluetooth_manager disable")
        shell("cmd bluetooth_manager wait-for-state:STATE_OFF")
        packages.forEach { name ->
            check(shell("pm disable-user --user 0 $name").contains("disabled-user")) {
                "Could not disable emulator Bluetooth package $name"
            }
        }
    }

    private fun packages(command: String) = shell(command).lineSequence()
        .map { it.trim().removePrefix("package:") }
        .filter { it.isNotEmpty() }
        .toList()

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
