package dev.intent.app.smoke

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.attachments.MediaPreview
import dev.intent.app.attachments.MediaReference
import dev.intent.app.attachments.MediaSource
import dev.intent.app.attachments.MediaViewerRoute
import dev.intent.app.design.IntentTheme
import dev.intent.app.files.WorkspaceFilePreviewRoute
import dev.intent.app.files.WorkspaceFileRpc
import dev.intent.app.files.WorkspaceFileSource
import dev.intent.app.mermaid.MermaidFence
import dev.intent.app.testing.createIntentComposeRule
import java.io.File
import java.util.Base64
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaViewersTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun pdfFromFilesPagesAndZooms() {
        val file = fixture("report.pdf")
        val pdf = PdfDocument()
        try {
            repeat(3) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(200, 300, index + 1).create())
                page.canvas.drawColor(android.graphics.Color.WHITE)
                page.canvas.drawText(
                    "Fixture page ${index + 1}",
                    20f,
                    40f,
                    android.graphics.Paint()
                )
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally {
            pdf.close()
        }
        val bytes = file.readBytes()
        val source = WorkspaceFileSource(
            WorkspaceFileRpc { _, _ ->
                CompletableFuture.completedFuture(
                    buildJsonObject {
                        put("content", Base64.getEncoder().encodeToString(bytes))
                        put("size", bytes.size)
                        put("bytesRead", bytes.size)
                    }
                )
            },
            "workspace"
        )
        compose.setContent { IntentTheme { WorkspaceFilePreviewRoute(source, "report.pdf", {}) } }
        waitTag("file.pdf")
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag("file.image")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("Zoom in")[0].performClick()
        compose.onNodeWithTag("file.pdf").performScrollToIndex(2)
        compose.onNodeWithText("Page 3").assertIsDisplayed()
        screenshot("media-pdf")
    }

    @Test
    fun chatImageOpensViewerWithSaveCopyAndBack() {
        val file = fixture("picture.png")
        val image = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.BLUE)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        compose.setContent {
            IntentTheme {
                MediaPreview(
                    MediaReference("picture.png", true),
                    MediaSource {
                        CompletableFuture.completedFuture(file)
                    }
                )
            }
        }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Attached image"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Attached image").performClick()
        waitTag("file.image")
        compose.onNodeWithText("Save image").assertIsDisplayed()
        screenshot("media-image")
        compose.onNodeWithText("Copy image").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText("Image copied.")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Attached image").assertIsDisplayed()
    }

    @Test
    fun chatVideoLoadsFileBackedPlayer() {
        val file = fixture("preview.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open(
            "preview.mp4"
        ).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        compose.setContent {
            IntentTheme {
                MediaPreview(
                    MediaReference("preview.mp4", false),
                    MediaSource {
                        CompletableFuture.completedFuture(file)
                    }
                )
            }
        }
        compose.onNodeWithText("Open attachment").performClick()
        waitTag("file.video")
        compose.onNodeWithTag("file.video").assertIsDisplayed()
        compose.waitUntil(TIMEOUT) {
            var ready = false
            compose.runOnIdle {
                val player = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .flatMap(
                        ::descendants
                    ).filterIsInstance<androidx.media3.ui.PlayerView>().single().player!!
                ready = player.playbackState == androidx.media3.common.Player.STATE_READY
            }
            ready
        }
        compose.runOnIdle {
            android.view.inspector.WindowInspector.getGlobalWindowViews()
                .flatMap(
                    ::descendants
                ).filterIsInstance<androidx.media3.ui.PlayerView>().single().player!!.play()
        }
        compose.waitUntil(TIMEOUT) {
            var played = false
            compose.runOnIdle {
                val view = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .flatMap(
                        ::descendants
                    ).filterIsInstance<androidx.media3.ui.PlayerView>().single()
                played = view.player!!.currentPosition > 0
            }
            played
        }
        screenshot("media-video")
        compose.onNodeWithText("Open in another app").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Open").assertIsDisplayed()
    }

    @Test
    fun htmlAndTextUseInAppViewers() {
        val file = fixture("page.html").apply {
            writeText("<h1>HTML fixture</h1><script>document.body.innerHTML='SCRIPT RAN'</script>")
        }
        compose.setContent {
            IntentTheme {
                var html by remember { mutableStateOf(true) }
                MediaViewerRoute(
                    file,
                    MediaReference(if (html) "page.html" else "page.txt", false)
                ) {
                    html =
                        false
                }
            }
        }
        waitTag("file.html")
        compose.onNodeWithTag("file.html").assertIsDisplayed()
        compose.waitUntil(TIMEOUT) {
            var loaded = false
            compose.runOnIdle {
                val web = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .flatMap(::descendants).filterIsInstance<android.webkit.WebView>().single()
                loaded = web.progress == 100 && web.contentHeight > 0
            }
            loaded
        }
        screenshot("media-html")
        compose.runOnIdle {
            val web = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .flatMap(::descendants).filterIsInstance<android.webkit.WebView>().single()
            org.junit.Assert.assertFalse(web.settings.javaScriptEnabled)
            org.junit.Assert.assertTrue(web.settings.blockNetworkLoads)
            org.junit.Assert.assertFalse(web.settings.allowFileAccess)
            org.junit.Assert.assertFalse(web.settings.allowContentAccess)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        waitTag("file.text")
        compose.onNodeWithTag("file.text").assertIsDisplayed()
    }

    @Test
    fun mermaidExpandsAndCloses() {
        compose.setContent { IntentTheme { MermaidFence("mermaid", "graph TD\nA --> B") } }
        compose.onNodeWithText("Expand diagram").performClick()
        compose.onNodeWithText("Close diagram").assertIsDisplayed().performClick()
        compose.onNodeWithText("Expand diagram").assertIsDisplayed()
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

    private fun descendants(view: android.view.View): List<android.view.View> =
        listOf(view) + if (view is android.view.ViewGroup) {
            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        } else {
            emptyList()
        }

    private fun fixture(name: String): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "attachments/test-$name"
    ).apply { parentFile!!.mkdirs() }

    private fun waitTag(tag: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
