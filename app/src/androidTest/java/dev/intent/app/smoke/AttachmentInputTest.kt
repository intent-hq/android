package dev.intent.app.smoke

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.attachments.AndroidAttachmentSource
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.attachments.PhotoAttachments
import dev.intent.app.attachments.PhotoPicker
import dev.intent.app.conversation.ConversationComposer
import dev.intent.app.conversation.ConversationSendStore
import dev.intent.app.conversation.RpcConversationCommands
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.attachments.AttachmentRpc
import dev.intent.core.protocol.attachments.AttachmentUploadService
import dev.intent.core.protocol.attachments.messageBlock
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import java.io.File
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AttachmentInputTest {
    @get:Rule val compose = createIntentComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun filePickerRetryAndRemoveKeepOwnedPayloadAndKey() {
        val rpc = UploadFixture(failFirst = true)
        val store = store(rpc)
        val registry = PickerFixture(uri("plan.txt", "Plan".toByteArray()))
        show(store, registry)
        pick("Files")
        compose.waitUntil(TIMEOUT) { store.state.value.photos.singleOrNull()?.error != null }
        val firstKey = rpc.keys.single()
        screenshot("attachment-file-retry")
        compose.onNodeWithText("Retry upload").performClick()
        compose.waitUntil(TIMEOUT) { store.state.value.photos.singleOrNull()?.placed != null }
        assertEquals(listOf(firstKey, firstKey), rpc.keys)
        assertEquals("text/plain", store.state.value.photos.single().placed?.mimeType)
        assertEquals("android.intent.action.OPEN_DOCUMENT", registry.action)
        screenshot("attachment-file-ready")
        val copy = store.state.value.photos.single().file!!
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(TIMEOUT) { store.state.value.photos.isEmpty() }
        assertFalse(copy.exists())
        store.close()
    }

    @Test
    fun videoPickerStreamsAndPasteAddsImage() {
        val rpc = UploadFixture()
        val store = store(rpc)
        val registry = PickerFixture(uri("clip.mp4", ByteArray(VIDEO_BYTES)))
        show(store, registry)
        pick("Photos and videos")
        compose.waitUntil(TIMEOUT) { store.state.value.photos.singleOrNull()?.placed != null }
        assertTrue("file.attachmentUpload.chunk" in rpc.methods)
        assertEquals("video/mp4", store.state.value.photos.single().placed?.mimeType)
        screenshot("attachment-video")
        compose.onNodeWithText("Remove").performClick()
        val image = File.createTempFile("paste", ".png", File(context.cacheDir, "attachments"))
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val imageUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.attachments",
            image
        )
        compose.runOnIdle {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newUri(context.contentResolver, "image", imageUri)
            )
        }
        pick("Paste image")
        compose.waitUntil(TIMEOUT) { store.state.value.photos.singleOrNull()?.placed != null }
        assertTrue(store.state.value.photos.single().description?.image == true)
        screenshot("attachment-paste")
        store.close()
        image.delete()
    }

    @Test
    fun cancelVideoWaitsForChunkThenAbortsAndDeletesCopy() {
        val chunk = CompletableFuture<JsonObject>()
        val rpc = UploadFixture(heldChunk = chunk)
        val store = store(rpc)
        show(store, PickerFixture(uri("cancel.mp4", ByteArray(VIDEO_BYTES))))
        pick("Photos and videos")
        compose.waitUntil(TIMEOUT) { "file.attachmentUpload.chunk" in rpc.methods }
        val file = store.state.value.photos.single().file!!
        screenshot("attachment-cancel")
        compose.onNodeWithText("Cancel upload").performClick()
        assertTrue(file.exists())
        chunk.complete(JsonObject(emptyMap()))
        compose.waitUntil(TIMEOUT) { !file.exists() }
        assertTrue("file.attachmentUpload.abort" in rpc.methods)
        assertFalse("file.attachmentUpload.commit" in rpc.methods)
        assertTrue(store.state.value.photos.isEmpty())
        store.close()
    }

    @Test
    fun questionSendsItsFileWithAnswerMetadata() {
        val ready = AtomicBoolean()
        lateinit var server: SmokeServer
        RpcSession({ listener ->
            SmokeServer(listener, questionResponses()).also { server = it }
        }, JsonObject(emptyMap()), onState = {
            ready.set(it == SessionState.READY)
        }).use { session ->
            session.connect()
            compose.waitUntil(TIMEOUT) { ready.get() }
            ConversationSendStore(
                RpcConversationCommands(session, "workspace", "agent"),
                "agent"
            ).use { sends ->
                sends.refresh()
                compose.waitUntil(TIMEOUT) { sends.state.value.questions != null }
                val composer = store(UploadFixture())
                val question = composer.independent()
                question.addFiles(listOf(uri("answer.txt", "Answer".toByteArray()).toString()))
                compose.waitUntil(TIMEOUT) { question.state.value.ready }
                compose.setContent {
                    val state by sends.state.collectAsState()
                    AttachmentTestTheme {
                        ConversationComposer(state, { false }, sends::retract, { id, answers ->
                            val accepted = sends.answer(
                                id,
                                answers,
                                files = question.state.value.photos.mapNotNull {
                                    it.placed?.messageBlock()
                                }
                            )
                            if (accepted) question.consume()
                            accepted
                        }, questionPhotos = question)
                    }
                }
                compose.onNodeWithText("Send answers").performScrollTo()
                screenshot("attachment-question")
                compose.onNodeWithText("Send answers").performClick()
                compose.waitUntil(TIMEOUT) {
                    server.requests.any { it.method == "agent.sendMessage" }
                }
                val params = server.requests.first {
                    it.method == "agent.sendMessage"
                }.params!!.jsonObject
                assertEquals(
                    "questions",
                    params.getValue("messageMetadata").jsonObject
                        .getValue("answeredQuestionsMessageId").jsonPrimitive.content
                )
                assertEquals(
                    "answer.txt",
                    params.getValue("fileBlocks").jsonArray.single().jsonObject
                        .getValue("fileName").jsonPrimitive.content
                )
                assertTrue(composer.state.value.photos.isEmpty())
                assertTrue(question.state.value.photos.isEmpty())
                question.close()
                composer.close()
            }
        }
    }

    private fun show(store: PhotoAttachmentStore, registry: PickerFixture) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                AttachmentTestTheme {
                    Column {
                        PhotoPicker(store)
                        PhotoAttachments(store)
                    }
                }
            }
        }
    }

    private fun pick(label: String) {
        compose.onNodeWithContentDescription("Attach files (0)").performClick()
        compose.onNodeWithText(label).performClick()
    }

    private fun store(rpc: AttachmentRpc) = PhotoAttachmentStore(
        AndroidAttachmentSource(context, AttachmentUploadService(rpc, "workspace"))
    )

    private fun uri(name: String, bytes: ByteArray): Uri {
        val directory = File(context.cacheDir, "attachments").also { it.mkdirs() }
        val file = File(directory, name).also { it.writeBytes(bytes) }
        return FileProvider.getUriForFile(context, "${context.packageName}.attachments", file)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory =
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
                ?: File(context.externalMediaDirs.first(), "additional_test_output")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name-dark.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}

private class PickerFixture(private val uri: Uri) :
    ActivityResultRegistry(),
    ActivityResultRegistryOwner {
    var action: String? = null
    override val activityResultRegistry: ActivityResultRegistry get() = this
    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?
    ) {
        action =
            contract.createIntent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                input
            ).action
        dispatchResult(requestCode, listOf(uri))
    }
}

private class UploadFixture(
    private var failFirst: Boolean = false,
    private val heldChunk: CompletableFuture<JsonObject>? = null
) : AttachmentRpc {
    val keys = CopyOnWriteArrayList<String>()
    val methods = CopyOnWriteArrayList<String>()
    private var name = ""
    private var mime = ""
    private var size = 0L
    override fun request(method: String, params: JsonObject): CompletableFuture<JsonObject> {
        methods.add(method)
        if (method == "file.placeAttachment" || method == "file.attachmentUpload.begin") {
            keys.add(params["idempotencyKey"]!!.jsonPrimitive.content)
            name = params["fileName"]!!.jsonPrimitive.content
            mime = params["mimeType"]!!.jsonPrimitive.content
            size = params["sizeBytes"]?.jsonPrimitive?.content?.toLong()
                ?: Base64.getDecoder().decode(params["data"]!!.jsonPrimitive.content).size.toLong()
            if (failFirst) {
                failFirst = false
                return CompletableFuture.failedFuture(IllegalStateException("Retry fixture"))
            }
        }
        if (method == "file.attachmentUpload.chunk" && heldChunk != null) return heldChunk
        val response = when (method) {
            "file.attachmentUpload.begin" -> """{"uploadId":"upload","maxChunkBytes":65536}"""
            "file.placeAttachment", "file.attachmentUpload.commit" ->
                """{"attachmentId":"placed","fileName":"$name","path":".intent/attachments/$name","size":$size,"mimeType":"$mime"}"""
            else -> "{}"
        }
        return CompletableFuture.completedFuture(WireJson.parseToJsonElement(response).jsonObject)
    }
}

private fun questionResponses() = mapOf(
    "agent.get" to
        """{"agent":{"id":"agent","name":"Agent","metadata":{"pendingQuestionsMessageId":"questions"}}}""",
    "agent.getQueue" to """{"success":true,"queue":[]}""",
    "agent.getConversation" to
        """{"agentId":"agent","messages":[{"id":"questions","role":"assistant","createdAt":"now","contentBlocks":[{"type":"resource","resource":{"mimeType":"application/vnd.intent.question+json","text":"{\"attachmentId\":\"q\",\"header\":\"Plan\",\"question\":\"Choose?\",\"options\":[{\"label\":\"One\"},{\"label\":\"Two\"}]}"}}]}]}"""
)

private const val TIMEOUT = 10_000L
private const val VIDEO_BYTES = 150_000

@Composable
private fun AttachmentTestTheme(content: @Composable () -> Unit) {
    IntentTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize()) { Box(Modifier.padding(16.dp)) { content() } }
    }
}
