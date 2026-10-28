package dev.intent.app.smoke

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.cache.RoomOfflineCache
import dev.intent.app.design.IntentTheme
import dev.intent.app.navigation.WorkspaceContent
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostLocalLinksTest {
    @get:Rule val compose = createIntentComposeRule()
    private val external = CopyOnWriteArrayList<String>()

    @Test
    fun conversationLinkLoadsThroughTunnelAndPageLinksUseTheHost() = connected { tunnel ->
        waitText("Link agent")
        compose.onNodeWithText("Link agent").performClick()
        openLink("Local preview")
        assertPreview(tunnel, 4321, "/preview?q=%2F")
        compose.onNodeWithContentDescription("Browser URL").assertTextContains(LOCAL_URL)
        compose.waitUntil(TIMEOUT) { pageReady() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView()!!.evaluateJavascript("document.getElementById('next').click()", null)
        }
        assertPreview(tunnel, 4322, "/next")
        compose.waitUntil(TIMEOUT) { pageReady() }
        compose.onNodeWithContentDescription("Page back").performClick()
        compose.waitUntil(TIMEOUT) {
            runCatching {
                compose.onNodeWithContentDescription("Browser URL").assertTextContains(LOCAL_URL)
            }.isSuccess
        }
        compose.onNodeWithContentDescription("Page forward").performClick()
        compose.waitUntil(TIMEOUT) {
            runCatching {
                compose.onNodeWithContentDescription(
                    "Browser URL"
                ).assertTextContains("http://[::1]:4322/next")
            }.isSuccess
        }
        val beforeReload = tunnel.requests.size
        compose.onNodeWithContentDescription("Reload page").performClick()
        compose.waitUntil(TIMEOUT) { tunnel.requests.size > beforeReload }
        screenshot("host-link-browser")
        assertTrue(external.isEmpty())
        compose.onNodeWithContentDescription("Close preview").performClick()
        compose.onNodeWithContentDescription("Message").assertIsDisplayed()
        openLink("Public site")
        assertEquals(listOf("https://example.com/docs"), external.toList())
    }

    @Test
    fun noteLinkLoadsThroughTunnelAndBackKeepsTheNote() = connected { tunnel ->
        compose.onNodeWithText("Context").performClick()
        waitText("Link note")
        compose.onNodeWithText("Link note").performClick()
        openLink("Local preview")
        assertPreview(tunnel, 4321, "/preview?q=%2F")
        compose.onNodeWithContentDescription("Close preview").performClick()
        compose.onNodeWithText("Link note").assertIsDisplayed()
        openLink("Public site")
        assertEquals(listOf("https://example.com/docs"), external.toList())
    }

    @Test
    fun unavailableTunnelShowsHostInstructionsWithoutExternalFallback() =
        connected(fail = true) { _ ->
            waitText("Link agent")
            compose.onNodeWithText("Link agent").performClick()
            openLink("Local preview")
            waitText(
                "The browser tunnel is unavailable. Reconnect and update Intent on the host, then retry."
            )
            compose.onNodeWithContentDescription("Retry preview").assertIsDisplayed()
            screenshot("host-link-tunnel-error")
            assertTrue(external.isEmpty())
        }

    private fun openLink(label: String) {
        waitText(label)
        compose.onNodeWithText(label).performTouchInput { click(Offset(20f, center.y)) }
    }

    private fun assertPreview(tunnel: PreviewTunnelServer, port: Int, path: String) {
        compose.onNodeWithContentDescription("Browser URL").assertIsDisplayed()
        compose.waitUntil(TIMEOUT) {
            tunnel.requests.any { it.first == port && it.second.startsWith("GET $path ") }
        }
        assertTrue(external.isEmpty())
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { java.io.File(it) }
            ?: java.io.File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun pageReady(): Boolean {
        var ready = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ready = webView()?.progress == 100
        }
        return ready
    }

    private fun webView(): WebView? = WindowInspector.getGlobalWindowViews()
        .firstNotNullOfOrNull(::findWebView)

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull {
            findWebView(view.getChildAt(it))
        }
        else -> null
    }

    private fun waitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun connected(fail: Boolean = false, test: (PreviewTunnelServer) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ready = AtomicBoolean()
        val tunnel = PreviewTunnelServer(fail)
        RpcSession(
            { SmokeServer(it, responses) },
            JsonObject(emptyMap()),
            onState = { ready.set(it == SessionState.READY) },
            browserTransportFactory = tunnel::create
        ).use { session ->
            session.connect()
            compose.waitUntil(TIMEOUT) { ready.get() }
            RoomOfflineCache.open(context, "host-links-${System.nanoTime()}").use { cache ->
                val visible = mutableStateOf(true)
                compose.setContent {
                    if (visible.value) {
                        IntentTheme {
                            CompositionLocalProvider(
                                LocalUriHandler provides object : UriHandler {
                                    override fun openUri(uri: String) {
                                        external.add(uri)
                                    }
                                }
                            ) {
                                WorkspaceContent(session, "host-links", cache, {
                                }, "host-links-${session.hashCode()}")
                            }
                        }
                    }
                }
                try {
                    test(tunnel)
                } finally {
                    compose.runOnIdle { visible.value = false }
                    compose.waitForIdle()
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT = 15_000L
        const val LOCAL_URL = "http://localhost:4321/preview?q=%2F#part"
        const val MARKDOWN =
            "[Local preview]($LOCAL_URL)\n\n" + "[Public site](https://example.com/docs)"
        val message = """{"id":"message","role":"assistant","contentBlocks":[
            {"type":"text","text":${kotlinx.serialization.json.JsonPrimitive(MARKDOWN)}}]}"""
        val responses = mapOf(
            "agent.list" to """{"agents":[{"id":"agent","name":"Link agent"}]}""",
            "agent.get" to """{"agent":{"id":"agent","name":"Link agent"}}""",
            "agent.getQueue" to """{"success":true,"queue":[]}""",
            "agent.getConversation" to """{"agentId":"agent","messages":[$message]}""",
            "chat.subscribe" to """{"subscriptionId":"chat"}""",
            "chat.snapshot" to """{"subscriptionId":"chat","kind":"snapshot","seq":0,
                "snapshot":{"agentId":"agent","messages":[$message]}}""",
            "chat.unsubscribe" to "{}",
            "note.list" to """{"notes":[{"id":"note","title":"Link note"}]}""",
            "note.get" to """{"note":{"id":"note","title":"Link note",
                "content":${kotlinx.serialization.json.JsonPrimitive(MARKDOWN)}}}""",
            "note.listTasks" to "[]"
        )
    }
}
