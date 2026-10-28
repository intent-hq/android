package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.design.IntentTheme
import dev.intent.app.terminals.WorkspaceTerminals
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import dev.intent.core.protocol.session.SessionTransport
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalLifecycleTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test fun createOpenCancelAndConfirmDelete() {
        withTerminal { server ->
            waitText("No terminal sessions")
            screenshot("terminal-empty")
            compose.onNodeWithText("New terminal").performClick()
            waitText("Basic ANSI display. Full-screen apps have limited rendering.")
            waitText("Terminal input")
            screenshot("terminal-created")
            val create = server.actions.single()
            assertEquals("terminal.create", create.method)
            assertEquals(
                WireJson.parseToJsonElement("""{"workspaceId":"ws","cols":80,"rows":24}"""),
                create.params
            )
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithTag("terminal-open-new").performClick()
            waitText("Terminal input")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithTag("terminal-delete-new").performClick()
            compose.onNodeWithText("Delete terminal?").assertIsDisplayed()
            compose.onNodeWithText(
                "This stops the shell and any commands running in it."
            ).assertIsDisplayed()
            screenshot("terminal-delete-confirmation")
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(1, server.actions.size)
            compose.onNodeWithTag("terminal-open-new").assertIsDisplayed()
            compose.onNodeWithTag("terminal-delete-new").performClick()
            compose.onNodeWithText("Delete terminal").performClick()
            waitText("No terminal sessions")
            assertEquals("terminal.kill", server.actions.last().method)
            assertEquals(
                WireJson.parseToJsonElement("""{"workspaceId":"ws","terminalId":"new"}"""),
                server.actions.last().params
            )
        }
    }

    @Test fun failuresStayVisibleAndAllowExplicitRetry() {
        withTerminal(failFirst = true) { server ->
            waitText("No terminal sessions")
            compose.onNodeWithText("New terminal").performClick()
            waitText("Cannot create the terminal. Refresh before trying again.")
            screenshot("terminal-create-error")
            compose.onNodeWithText("New terminal").performClick()
            waitText("Terminal input")
            compose.onNodeWithContentDescription("Back").performClick()
            server.failKill = true
            compose.onNodeWithTag("terminal-delete-new").performClick()
            compose.onNodeWithText("Delete terminal").performClick()
            waitText("Cannot delete the terminal. Refresh before trying again.")
            compose.onNodeWithTag("terminal-open-new").assertIsDisplayed()
            screenshot("terminal-delete-error")
            compose.onNodeWithTag("terminal-delete-new").performClick()
            compose.onNodeWithText("Delete terminal").performClick()
            waitText("No terminal sessions")
            assertEquals(4, server.actions.size)
        }
    }

    @Test fun resourceRequestCreatesAndOpensOnce() {
        withTerminal(createOnOpen = true) { server ->
            waitText("Terminal input")
            assertEquals(1, server.actions.size)
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.onNodeWithText("Refresh").performClick()
            waitText("Terminal input")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithTag("terminal-open-new").assertIsDisplayed()
            compose.waitForIdle()
            assertEquals(1, server.actions.size)
        }
    }

    private fun withTerminal(
        failFirst: Boolean = false,
        createOnOpen: Boolean = false,
        test: (TerminalLifecycleServer) -> Unit
    ) {
        val server = TerminalLifecycleServer(failFirst)
        val ready = AtomicBoolean()
        RpcSession(
            server::transport,
            JsonObject(emptyMap()),
            onState = { ready.set(it == SessionState.READY) }
        ).use { session ->
            session.connect()
            compose.waitUntil(10_000) { ready.get() }
            compose.setContent {
                IntentTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize()) {
                        WorkspaceTerminals(session, "ws", {}, createOnOpen)
                    }
                }
            }
            test(server)
        }
    }

    private fun waitText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name-dark.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}

private class TerminalLifecycleServer(private var failCreate: Boolean) {
    val actions = CopyOnWriteArrayList<JsonRpcMessage.Request>()

    @Volatile var failKill = false
    private var exists = false
    fun transport(listener: WssTransport.Listener): SessionTransport = object : SessionTransport {
        override fun connect() = listener.onOpen()
        override fun close() = Unit
        override fun send(message: JsonRpcMessage): Boolean {
            val request = message as? JsonRpcMessage.Request ?: return true
            val response = when (request.method) {
                "client.hello" -> "{}"
                "events.subscribe" -> """{"subscriptionId":${request.id}}"""
                "terminal.list" -> if (exists) {
                    """{"terminals":[{"id":"new","name":"Terminal"}],"daemonBootId":"boot"}"""
                } else {
                    """{"terminals":[],"daemonBootId":"boot"}"""
                }
                "terminal.create" -> {
                    actions.add(request)
                    if (failCreate) {
                        failCreate = false
                        """{"terminalId":""}"""
                    } else {
                        exists = true
                        """{"terminalId":"new"}"""
                    }
                }
                "terminal.kill" -> {
                    actions.add(request)
                    if (failKill) {
                        failKill = false
                        """{"ok":false}"""
                    } else {
                        exists = false
                        """{"ok":true}"""
                    }
                }
                "terminal.getBuffer" ->
                    """{"terminalId":"new","data":"","daemonBootId":"boot","startOffset":"0","endOffset":"0"}"""
                "terminal.resize", "terminal.write" -> """{"ok":true}"""
                else -> "{}"
            }
            listener.onMessage(
                JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(response))
            )
            return true
        }
    }
}
