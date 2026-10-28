package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.attachments.AttachmentMedia
import dev.intent.app.attachments.LocalAttachmentMedia
import dev.intent.app.design.IntentTheme
import dev.intent.app.markdown.MarkdownContent
import dev.intent.app.notes.NoteReaderScreen
import dev.intent.app.notes.NoteReaderState
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.attachments.AttachmentRpc
import dev.intent.core.protocol.models.Note
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedAssetImagesTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun noteReviewImageLoadsThroughAssetRpcAndOpensViewer() {
        val response = CompletableFuture<JsonObject>()
        val source = source { response }
        compose.setContent {
            IntentTheme(darkTheme = true) {
                CompositionLocalProvider(LocalAttachmentMedia provides source) {
                    NoteReaderScreen(
                        NoteReaderState(
                            note = Note("review", "Android app review", content = REVIEW_IMAGE),
                            loading = false
                        ),
                        onRetry = {}
                    )
                }
            }
        }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag("media.loading")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("media.loading").assertIsDisplayed()
        response.complete(imageResponse())
        waitImage()
        screenshot("saved-asset-note")
        compose.onNodeWithContentDescription("Attached image").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag("file.image")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("file.image").assertIsDisplayed()
        screenshot("saved-asset-viewer")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Attached image").assertIsDisplayed()
    }

    @Test
    fun conversationAssetFailureCanRetryWithoutHttpFallback() {
        var calls = 0
        val source = source {
            calls++
            if (calls == 1) {
                CompletableFuture.failedFuture(IllegalStateException("Missing asset"))
            } else {
                CompletableFuture.completedFuture(imageResponse())
            }
        }
        compose.setContent {
            IntentTheme {
                CompositionLocalProvider(LocalAttachmentMedia provides source) {
                    MarkdownContent(REVIEW_IMAGE)
                }
            }
        }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Cannot load attachment. Retry."))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Cannot load attachment. Retry.").assertIsDisplayed()
        screenshot("saved-asset-error")
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag("file.image")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, calls)
        compose.onNodeWithContentDescription("Back").performClick()
        waitImage()
    }

    private fun source(response: () -> CompletableFuture<JsonObject>): AttachmentMedia {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return AttachmentMedia(
            AttachmentRpc { method, params ->
                assertEquals("note.readAsset", method)
                assertEquals("port-create", params.getValue("workspaceId").jsonPrimitive.content)
                assertEquals(
                    "mvrslex7-8853d295.png",
                    params.getValue("asset").jsonPrimitive.content
                )
                assertEquals(2, params.size)
                response()
            },
            "different-current-workspace",
            context.cacheDir,
            "saved-assets-${System.nanoTime()}"
        )
    }

    private fun imageResponse(): JsonObject {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bytes = if (assets.list("")!!.contains("local-review-image.png")) {
            assets.open("local-review-image.png").use { it.readBytes() }
        } else {
            val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            ByteArrayOutputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                bitmap.recycle()
                it.toByteArray()
            }
        }
        return buildJsonObject {
            put("data", Base64.getEncoder().encodeToString(bytes))
            put("mimeType", "image/png")
        }
    }

    private fun waitImage() {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(
                hasContentDescription("Attached image")
            ).fetchSemanticsNodes().isNotEmpty()
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
}

private const val TIMEOUT = 10_000L
private const val REVIEW_IMAGE =
    "![Workspaces](workspace-asset://port-create/mvrslex7-8853d295.png)"
