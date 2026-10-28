package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.agents.AgentListScreen
import dev.intent.app.agents.AgentListState
import dev.intent.app.conversation.ConversationComposer
import dev.intent.app.conversation.ConversationScreen
import dev.intent.app.conversation.ConversationSendState
import dev.intent.app.conversation.ConversationState
import dev.intent.app.design.IntentTheme
import dev.intent.app.status.ConversationStatusStrip
import dev.intent.app.status.LocalWatchState
import dev.intent.app.status.ScriptWatchSheet
import dev.intent.app.status.WatchBucket
import dev.intent.app.status.WatchDetail
import dev.intent.app.status.WatchFamily
import dev.intent.app.status.WatchPage
import dev.intent.app.status.WatchState
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.WireJson
import java.io.File
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationStatusTest {
    @get:Rule val compose = createIntentComposeRule()
    private val agent = Agent("a", "Coordinator")

    @Test fun scriptsHooksAndPrsShowAboveComposerAndOpenDetails() {
        val selected = mutableStateOf<WatchDetail?>(null)
        compose.setContent {
            IntentTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConversationScreen(ConversationState(isLoading = false), {}, { _, _ -> }, {}) {
                        ConversationStatusStrip(fixture(), agent, listOf(agent), {
                            selected.value = WatchDetail(it, it, loading = false)
                        }, {}, {})
                        ConversationComposer(ConversationSendState(), {
                            false
                        }, {}, { _, _ -> false })
                    }
                    selected.value?.let {
                        ScriptWatchSheet(it, "Coordinator", {}, { selected.value = null })
                    }
                }
            }
        }
        compose.onNodeWithText("Watching Build Android").assertIsDisplayed()
        compose.onNodeWithText("Check deployment").assertIsDisplayed()
        compose.onNodeWithTag("hook-time-hook").assertIsDisplayed()
        compose.onNodeWithText("Monitoring PR intent-hq/android: #42").assertIsDisplayed()
        compose.onNodeWithText("Mergeable").assertIsDisplayed()
        screenshot("status-strip")
        compose.onNodeWithTag("watch-watch").performClick()
        compose.onNodeWithText("Script watch").assertIsDisplayed()
        compose.onNodeWithText("Owner").assertIsDisplayed()
        screenshot("script-watch-details")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Script watch").assertDoesNotExist()
    }

    @Test fun idleAgentRowShowsWaitingIndicatorAndClearsAfterWatchEnds() {
        val state = mutableStateOf(fixture())
        compose.setContent {
            IntentTheme {
                CompositionLocalProvider(LocalWatchState provides state.value) {
                    AgentListScreen(AgentListState(listOf(agent), isLoading = false), {}, {})
                }
            }
        }
        compose.onNodeWithText("Waiting on watches").assertIsDisplayed()
        screenshot("agent-waiting-indicator")
        compose.runOnIdle { state.value = WatchState() }
        compose.onNodeWithText("Waiting on watches").assertDoesNotExist()
    }

    @Test fun terminalWatchKeepsSheetOpenWithUnavailableMessage() {
        val selection = mutableStateOf(WatchDetail(script(), loading = true))
        compose.setContent {
            IntentTheme { ScriptWatchSheet(selection.value, "Coordinator", {}, {}) }
        }
        compose.onNodeWithText("Script watch").assertIsDisplayed()
        compose.runOnIdle {
            selection.value =
                selection.value.copy(loading = false, unavailable = true)
        }
        compose.onNodeWithText("This watch is no longer available").assertIsDisplayed()
        compose.onNodeWithText("Build Android").assertDoesNotExist()
    }

    @Test fun childWaitsAndEmptyStripDoNotInventActivity() {
        val current = mutableStateOf(agent.copy(waitingForAgentIds = listOf("child")))
        compose.setContent {
            IntentTheme {
                ConversationStatusStrip(
                    WatchState(),
                    current.value,
                    listOf(Agent("child", "Implementor", status = "active")),
                    {},
                    {},
                    {}
                )
            }
        }
        compose.onNodeWithText("Waiting for agents").assertIsDisplayed()
        compose.onNodeWithText("Implementor · Running").assertIsDisplayed()
        compose.runOnIdle { current.value = agent }
        compose.onNodeWithText("Waiting for agents").assertDoesNotExist()
        compose.onNodeWithText("Working…").assertDoesNotExist()
    }

    @Test fun sentMarkAndLiveFooterFollowHeadlessTurnWithoutAvatarActivity() {
        val state = mutableStateOf(
            ConversationState(
                messages = listOf(
                    dev.intent.core.protocol.models.ConversationMessage(
                        "sent",
                        "user",
                        listOf(
                            dev.intent.core.protocol.models.ContentBlock(
                                "text",
                                text = "Check the build."
                            )
                        ),
                        timestamp = "2026-10-28T02:00:00Z"
                    )
                ),
                isLoading = false,
                isStreaming = true
            )
        )
        compose.setContent {
            IntentTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize()) {
                    ConversationScreen(state.value, {}, { _, _ -> }, {}) {
                        ConversationStatusStrip(WatchState(), agent, listOf(agent), {
                        }, {}, {}, isStreaming = state.value.isStreaming)
                        ConversationComposer(ConversationSendState(), {
                            false
                        }, {}, { _, _ -> false })
                    }
                }
            }
        }
        compose.onNodeWithText("Sent ·", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("conversation-thinking").assertIsDisplayed()
        compose.onNodeWithTag("conversation-working").assertIsDisplayed()
        screenshot("conversation-sent-thinking-dark")
        compose.runOnIdle { state.value = state.value.copy(isStreaming = false) }
        compose.onNodeWithTag("conversation-thinking").assertDoesNotExist()
        compose.onNodeWithTag("conversation-working").assertDoesNotExist()
        compose.onNodeWithText("Sent ·", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Send").assertIsDisplayed()
        screenshot("conversation-sent-idle-dark")
    }

    @Test fun failedSendOffersRetryAndUnconfirmedDeliveryOffersOnlyCheck() {
        val attempt = dev.intent.app.conversation.SendAttempt(
            "outgoing",
            "Check the build.",
            status = dev.intent.app.conversation.SendStatus.NOT_SENT
        )
        val state = mutableStateOf(ConversationSendState(attempts = listOf(attempt)))
        var retries = 0
        var checks = 0
        compose.setContent {
            IntentTheme {
                ConversationComposer(
                    state.value,
                    { false },
                    {},
                    { _, _ -> false },
                    onRetry = { retries++ },
                    onCheckDelivery = { checks++ }
                )
            }
        }
        compose.onNodeWithText("Not sent").assertIsDisplayed()
        compose.onNodeWithText("Retry send").performClick()
        compose.runOnIdle {
            check(retries == 1)
            state.value =
                state.value.copy(
                    attempts = listOf(
                        attempt.copy(status = dev.intent.app.conversation.SendStatus.UNCONFIRMED)
                    )
                )
        }
        compose.onNodeWithText("Retry send").assertDoesNotExist()
        compose.onNodeWithText("Check delivery").performClick()
        compose.runOnIdle { check(checks == 1) }
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory =
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?.let(::File) ?: File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = if (name.startsWith("conversation-sent-")) {
            compose.onRoot().captureToImage().asAndroidBitmap()
        } else {
            checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        }
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun script() = json(
        """{"monitorId":"watch",
"workspaceId":"ws",
"agentId":"a",
"scriptId":"build",
"runId":"run",
"scriptName":"Build Android",
"mode":"command",
"state":"active",
"createdAt":"${Instant.now().minusSeconds(
            60
        )}",
"expiresAt":"${Instant.now().plusSeconds(300)}"}"""
    )
    private fun fixture() = WatchState(
        mapOf(
            WatchFamily.SCRIPT to WatchBucket(WatchPage(listOf(script()))),
            WatchFamily.HOOK to
                WatchBucket(
                    WatchPage(
                        listOf(
                            json(
                                """{"hookId":"hook",
"agentId":"a",
"name":"Check deployment",
"state":"scheduled",
"nextRunAt":"${Instant.now().plusSeconds(
                                    30
                                )}",
"delayMs":60000}"""
                            )
                        )
                    )
                ),
            WatchFamily.PR to
                WatchBucket(
                    WatchPage(
                        listOf(
                            json(
                                """{"monitorId":"pr",
"agentId":"a",
"state":"active",
"repo":"intent-hq/android",
"prNumber":42,
"lastSnapshot":{"mergeable":true}}"""
                            )
                        )
                    )
                )
        )
    )
    private fun json(value: String): JsonObject = WireJson.parseToJsonElement(value).jsonObject
}
