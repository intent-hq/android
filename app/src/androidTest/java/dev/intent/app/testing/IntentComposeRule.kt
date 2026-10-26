package dev.intent.app.testing

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
        delegate.activityRule.scenario.onActivity { it.setTestContent(composable) }
        delegate.waitForIdle()
    }

    fun recreateActivity() {
        delegate.activityRule.scenario.recreate()
        delegate.waitForIdle()
    }
}
