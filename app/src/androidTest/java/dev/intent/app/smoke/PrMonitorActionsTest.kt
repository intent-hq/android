package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.design.IntentTheme
import dev.intent.app.status.ConversationStatusStrip
import dev.intent.app.status.RpcWatchSource
import dev.intent.app.status.WatchFamily
import dev.intent.app.status.WatchStore
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionTransport
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrMonitorActionsTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test fun menuChecksFlushesOpensAndCancelsWithFakeSession() {
        val server = PrActionServer()
        val opened = CopyOnWriteArrayList<String>()
        RpcSession(server::transport, JsonObject(emptyMap())).use { session ->
            session.connect()
            WatchStore(RpcWatchSource(session, "ws")).use { store ->
                store.start()
                compose.setContent {
                    val state by store.state.collectAsState()
                    IntentTheme(darkTheme = true) {
                        CompositionLocalProvider(
                            LocalUriHandler provides object : UriHandler {
                                override fun openUri(uri: String) {
                                    opened.add(uri)
                                }
                            }
                        ) {
                            Surface(Modifier.fillMaxSize()) {
                                ConversationStatusStrip(
                                    state,
                                    Agent("a", "Coordinator"),
                                    store::select,
                                    {
                                        store.refresh(it)
                                    },
                                    {},
                                    onPrAction = store::prAction
                                )
                            }
                        }
                    }
                }
                compose.waitUntil(5_000) { store.state.value.rows(WatchFamily.PR, "a").size == 1 }
                compose.onNodeWithTag("pr-monitor-pr").performClick()
                compose.onNodeWithText("Fix preview — open").assertIsDisplayed()
                screenshot("pr-monitor-actions-dark")
                compose.onNodeWithText("Check and flush").performClick()
                compose.waitUntil(5_000) {
                    server.actions.size == 1 &&
                        store.state.value.prActions["pr"]?.busy == false
                }
                check(server.actions[0].method == "prMonitor.flush")
                check(
                    server.actions[0].params!!.jsonObject == WireJson.parseToJsonElement(
                        """{"workspaceId":"ws","monitorId":"pr","check":true}"""
                    )
                )
                compose.onNodeWithTag("pr-monitor-pr").performClick()
                compose.onNodeWithText("Open PR").performClick()
                compose.runOnIdle {
                    check(
                        opened.single() == "https://github.com/intent-hq/android/pull/42"
                    )
                }
                compose.onNodeWithTag("pr-monitor-pr").performClick()
                compose.onNodeWithText("Cancel monitor").performClick()
                compose.waitUntil(5_000) {
                    server.actions.size == 2 &&
                        store.state.value.rows(WatchFamily.PR, "a").isEmpty()
                }
                check(server.actions[1].method == "prMonitor.cancel")
                check(
                    server.actions[1].params!!.jsonObject == WireJson.parseToJsonElement(
                        """{"workspaceId":"ws","monitorId":"pr"}"""
                    )
                )
                compose.onNodeWithTag("pr-monitor-pr").assertDoesNotExist()
            }
        }
    }

    @Test fun failedActionKeepsMonitorAndCanRetry() {
        val server = PrActionServer(failFirst = true)
        RpcSession(server::transport, JsonObject(emptyMap())).use { session ->
            session.connect()
            WatchStore(RpcWatchSource(session, "ws")).use { store ->
                store.start()
                compose.setContent {
                    val state by store.state.collectAsState()
                    IntentTheme {
                        ConversationStatusStrip(
                            state,
                            Agent("a", "Coordinator"),
                            store::select,
                            { store.refresh(it) },
                            {},
                            onPrAction = store::prAction
                        )
                    }
                }
                compose.waitUntil(5_000) { store.state.value.rows(WatchFamily.PR, "a").size == 1 }
                compose.onNodeWithTag("pr-monitor-pr").performClick()
                compose.onNodeWithText("Check and flush").performClick()
                compose.waitUntil(5_000) { store.state.value.prActions["pr"]?.error != null }
                compose.onNodeWithText("Check and flush failed. Try again.").assertIsDisplayed()
                screenshot("pr-monitor-action-error")
                compose.onNodeWithTag("pr-monitor-pr").performClick()
                compose.onNodeWithText("Check and flush").performClick()
                compose.waitUntil(5_000) {
                    server.actions.size == 2 &&
                        store.state.value.prActions["pr"]?.busy == false
                }
                compose.onNodeWithText("Check and flush failed. Try again.").assertDoesNotExist()
                compose.onNodeWithTag("pr-monitor-pr").assertIsDisplayed()
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}

private class PrActionServer(private var failFirst: Boolean = false) {
    val actions = CopyOnWriteArrayList<JsonRpcMessage.Request>()
    private var cancelled = false
    fun transport(listener: WssTransport.Listener): SessionTransport = object : SessionTransport {
        override fun connect() = listener.onOpen()
        override fun close() = Unit
        override fun send(message: JsonRpcMessage): Boolean {
            val request = message as? JsonRpcMessage.Request ?: return true
            val response = when (request.method) {
                "client.hello" -> "{}"
                "events.subscribe" -> """{"subscriptionId":${request.id}}"""
                "prMonitor.list" -> if (cancelled) {
                    """{"monitors":[]}"""
                } else {
                    """{"monitors":[{"monitorId":"pr","workspaceId":"ws","agentId":"a",
                    "state":"active","repo":"intent-hq/android","prNumber":42,"title":"Fix preview",
                    "hasPendingChanges":true,"pendingChanges":["checks"],
                    "lastSnapshot":{"state":"open","mergeable":true,"checks":{"total":3,"passed":3}}}]}"""
                }
                "hook.list" -> """{"hooks":[]}"""
                "prMonitor.flush", "prMonitor.cancel" -> {
                    actions.add(request)
                    if (failFirst) {
                        failFirst = false
                        """{"ok":false}"""
                    } else {
                        if (request.method == "prMonitor.cancel") cancelled = true
                        """{"ok":true,"flushed":false}"""
                    }
                }
                else -> "{}"
            }
            listener.onMessage(
                JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(response))
            )
            return true
        }
    }
}
