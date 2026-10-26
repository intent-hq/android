package dev.intent.app.smoke

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.design.IntentTheme
import dev.intent.app.notes.NoteListRoute
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionTransport
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceFilesTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun foldersLoadLazilyAndTextBackKeepsContextPosition() = connected { server ->
        scrollTo("files.folder.src")
        assertEquals(listOf("."), server.paths("file.list"))
        compose.onNodeWithTag("files.folder.src").performClick()
        scrollTo("files.file.src/readme.txt")
        val before = compose.onNodeWithTag("files.folder.src").fetchSemanticsNode().positionInRoot
        compose.onNodeWithTag("files.file.src/readme.txt").performClick()
        waitForTag("file.text")
        compose.onNodeWithTag("file.text").assertIsDisplayed()
        val textNodes = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            .findAccessibilityNodeInfosByText("Hello from the workspace.")
        org.junit.Assert.assertTrue(textNodes.any { it.isVisibleToUser })
        screenshot("files-text")
        compose.onNodeWithText("Open in another app").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("files.file.src/readme.txt").assertIsDisplayed()
        assertEquals(
            before,
            compose.onNodeWithTag("files.folder.src").fetchSemanticsNode().positionInRoot
        )
        screenshot("files-tree")
        assertEquals(listOf(".", "src"), server.paths("file.list"))
        assertEquals(listOf("src/readme.txt"), server.paths("file.readChunk"))
    }

    @Test
    fun imageOpensWithZoomControlsAndSystemBackReturnsToTree() = connected {
        scrollTo("files.file.picture.png")
        compose.onNodeWithTag("files.file.picture.png").performClick()
        waitForTag("file.image")
        compose.onNodeWithTag("file.image").assertIsDisplayed()
        screenshot("files-image")
        compose.onNodeWithText("Zoom in").performClick()
        compose.onNodeWithTag("file.image").performTouchInput {
            swipe(center, center + Offset(40f, 40f))
        }
        compose.onNodeWithText("Reset zoom").performClick()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("files.file.picture.png").assertIsDisplayed()
    }

    @Test
    fun emptyFoldersAndUnsupportedPreviewHaveClearStates() = connected {
        scrollTo("files.folder.empty")
        compose.onNodeWithTag("files.folder.empty").performClick()
        compose.onNodeWithText("Empty folder").assertIsDisplayed()
        scrollTo("files.file.report.pdf")
        compose.onNodeWithTag("files.file.report.pdf").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText("Preview not available yet for this file.")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Preview not available yet for this file.").assertIsDisplayed()
        compose.onNodeWithText("Open in another app").assertIsDisplayed()
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

    private fun connected(test: (FilesTestServer) -> Unit) {
        lateinit var server: FilesTestServer
        RpcSession({
            FilesTestServer(it).also { fixture -> server = fixture }
        }, JsonObject(emptyMap())).use { session ->
            session.connect()
            compose.setContent { IntentTheme { NoteListRoute(session, "workspace", {}) } }
            compose.waitUntil(TIMEOUT) { server.paths("file.list").isNotEmpty() }
            test(server)
        }
    }

    private fun scrollTo(tag: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}

private class FilesTestServer(private val listener: WssTransport.Listener) : SessionTransport {
    private val requests = CopyOnWriteArrayList<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun close() = Unit
    override fun send(message: JsonRpcMessage): Boolean {
        val request = message as JsonRpcMessage.Request
        requests.add(request)
        val path = request.params?.jsonObject?.get("path")?.jsonPrimitive?.content
        val result = when (request.method) {
            "client.hello" -> "{}"
            "note.list" -> """{"notes":[${(1..30).joinToString {
                """{"id":"note-$it","title":"Note $it"}"""
            }}]}"""
            "file.list" -> when (path) {
                "." ->
                    """[{"name":"src","type":"directory"},{"name":"empty","type":"directory"},
                    {"name":"picture.png","type":"file"},{"name":"report.pdf","type":"file"}]"""
                "src" -> """[{"name":"readme.txt","type":"file"}]"""
                else -> "[]"
            }
            "file.readChunk" -> {
                val bytes = if (path ==
                    "picture.png"
                ) {
                    imageBytes()
                } else {
                    "Hello from the workspace.\nText wraps here.".toByteArray()
                }
                buildJsonObject {
                    put("content", Base64.getEncoder().encodeToString(bytes))
                    put("bytesRead", bytes.size)
                    put("size", bytes.size)
                }.toString()
            }
            else -> error("Unexpected method ${request.method}")
        }
        listener.onMessage(JsonRpcMessage.Success(request.id, Json.parseToJsonElement(result)))
        return true
    }

    fun paths(method: String) = requests.filter { it.method == method }.map {
        it.params!!.jsonObject.getValue("path").jsonPrimitive.content
    }

    private fun imageBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        output.toByteArray()
    }
}
