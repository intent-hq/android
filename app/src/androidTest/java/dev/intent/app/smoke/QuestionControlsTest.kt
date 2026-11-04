package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.conversation.ConversationComposer
import dev.intent.app.conversation.ConversationSendState
import dev.intent.app.conversation.QuestionAnswer
import dev.intent.app.conversation.QuestionSet
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.models.Question
import dev.intent.core.protocol.models.QuestionOption
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QuestionControlsTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun pagesKeepAnswersAndWholeRowsUseRadioOrCheckboxSemantics() {
        var submitted = emptyMap<String, QuestionAnswer>()
        val dark = mutableStateOf(false)
        compose.setContent {
            IntentTheme(darkTheme = dark.value) {
                Surface {
                    ConversationComposer(
                        ConversationSendState(questions = questions()),
                        { false },
                        {},
                        { id, answers ->
                            assertEquals("questions", id)
                            submitted = answers
                            true
                        }
                    )
                }
            }
        }
        compose.onNodeWithText("Which platform?").assertExists()
        compose.onNodeWithText("Which checks?").assertDoesNotExist()
        compose.onNodeWithText("Android").performScrollTo().performClick()
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithText("iOS").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Android").assertIsNotSelected()
        compose.onNodeWithText("iOS").performClick().assertIsSelected()
        screenshot("question-single-choice-light")
        compose.onNodeWithText("Question 1 of 3 · Hide").performClick()
        compose.onNodeWithText("Which platform?").assertDoesNotExist()
        screenshot("questions-collapsed-light")
        compose.onNodeWithText("Questions · 3 · Show").performClick()
        compose.onNodeWithText("iOS").assertIsSelected()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Which platform?").assertDoesNotExist()
        compose.onNodeWithText("Unit tests").performScrollTo().performClick().assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        compose.onNodeWithText("Device tests").performScrollTo().performClick().assertIsOn()
        compose.runOnIdle { dark.value = true }
        screenshot("question-multi-choice-dark")
        compose.onNodeWithText("Unit tests").performClick().assertIsOff()
        compose.onNodeWithText("Other").performScrollTo().performTextReplacement("Manual review")
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("iOS").assertIsSelected()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Device tests").assertIsOn()
        compose.onNodeWithText("Manual review").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Skip question").performClick()
        compose.onNodeWithText("Send answers").performClick()
        compose.runOnIdle {
            assertEquals(listOf("iOS"), submitted.getValue("platform").selected)
            assertEquals(listOf("Device tests"), submitted.getValue("checks").selected)
            assertEquals("Manual review", submitted.getValue("checks").other)
            assertEquals(QuestionAnswer(skipped = true), submitted.getValue("timing"))
        }
    }

    @Test
    fun skippingAdvancesAndReturningAllowsAnAnswer() {
        var submitted = emptyMap<String, QuestionAnswer>()
        compose.setContent {
            IntentTheme {
                ConversationComposer(
                    ConversationSendState(
                        questions = questions().copy(questions = questions().questions.take(2))
                    ),
                    { false },
                    {},
                    { _, answers ->
                        submitted = answers
                        true
                    }
                )
            }
        }
        compose.onNodeWithText("Skip question").performClick()
        compose.onNodeWithText("Question 2 of 2 · Hide").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Skipped").assertExists()
        compose.onNodeWithText("Android").performScrollTo().performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Send answers").performClick()
        compose.runOnIdle {
            assertEquals(
                QuestionAnswer(selected = listOf("Android")),
                submitted.getValue("platform")
            )
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File)
            ?: File(
                instrumentation.targetContext.externalMediaDirs.first(),
                "additional_test_output"
            )
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun questions() = QuestionSet(
        "questions",
        listOf(
            Question(
                "platform",
                "Platform",
                "Which platform?",
                listOf(QuestionOption("Android"), QuestionOption("iOS"))
            ),
            Question(
                "checks",
                "Checks",
                "Which checks?",
                listOf(QuestionOption("Unit tests"), QuestionOption("Device tests")),
                multiSelect = true
            ),
            Question(
                "timing",
                "Timing",
                "When?",
                listOf(QuestionOption("Today"), QuestionOption("Tomorrow"))
            )
        )
    )
}
