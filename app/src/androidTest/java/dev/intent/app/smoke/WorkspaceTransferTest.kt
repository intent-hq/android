package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.connection.ConnectionMetadata
import dev.intent.app.connection.SavedConnection
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.app.workspaces.WorkspaceTransferScreen
import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import dev.intent.core.protocol.session.SessionTransport
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceTransferTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test fun exportAndImportProgressThenCommitDisablesCancel() {
        withTransfer { source, target ->
            start(source)
            listOf(
                "stopping-agents" to "Stopping running agents…",
                "exporting-rows" to "Preparing workspace data…",
                "bundling-git" to "Preparing repository files…",
                "writing-archive" to "Building the transfer archive…"
            ).forEach { (stage, message) ->
                source.progress(stage)
                waitText(message)
                compose.onNodeWithText("Cancel transfer").assertIsEnabled()
            }
            screenshot("transfer-export")
            source.archiveReady()
            waitHeld(target, "workspace.import.chunk")
            target.release("workspace.import.chunk")
            waitText("Transferring workspace…")
            waitHeld(target, "workspace.import.chunk")
            screenshot("transfer-half")
            target.release("workspace.import.chunk")
            waitHeld(target, "workspace.import.commit")
            waitText("Finishing the transfer on the destination…")
            compose.onNodeWithText("Cancel transfer").assertIsNotEnabled()
            screenshot("transfer-commit")
            target.release("workspace.import.commit")
            waitText("Workspace transferred.")
            screenshot("transfer-done")
            assertEquals(1, target.calls.count { it == "workspace.import.commit" })
            assertEquals(1, source.calls.count { it == "workspace.export.finalize" })
        }
    }

    @Test fun cancelDuringExportCleansSourceWithoutImport() {
        withTransfer { source, target ->
            start(source)
            source.progress("bundling-git")
            waitText("Preparing repository files…")
            compose.onNodeWithText("Cancel transfer").performClick()
            waitText("Transfer cancelled")
            screenshot("transfer-cancelled")
            assertEquals(1, source.calls.count { it == "workspace.export.abort" })
            assertFalse(target.calls.contains("workspace.import.begin"))
            assertFalse(source.calls.contains("workspace.export.finalize"))
        }
    }

    @Test fun cancelDuringUploadWaitsForReplyAndAbortsBothHosts() {
        withTransfer { source, target ->
            start(source)
            source.archiveReady()
            waitHeld(target, "workspace.import.chunk")
            compose.onNodeWithText("Cancel transfer").performClick()
            waitText("Cancelling the transfer… Waiting for the current request and cleanup.")
            compose.onNodeWithText("Cancel transfer").assertIsNotEnabled()
            screenshot("transfer-cancelling")
            assertFalse(target.calls.contains("workspace.import.abort"))
            target.release("workspace.import.chunk")
            waitText("Transfer cancelled")
            assertEquals(1, target.calls.count { it == "workspace.import.abort" })
            assertEquals(1, source.calls.count { it == "workspace.export.abort" })
            assertFalse(target.calls.contains("workspace.import.commit"))
        }
    }

    @Test fun exportFailureDoesNotStartImport() {
        withTransfer { source, target ->
            start(source)
            source.failed()
            waitText("Transfer failed")
            screenshot("transfer-failed")
            compose.onNodeWithText("Review and retry").performClick()
            waitText("Review the preview and choose a destination.")
            assertEquals(1, source.calls.count { it == "workspace.export.start" })
            assertFalse(target.calls.contains("workspace.import.commit"))
        }
    }

    @Test fun uncertainCommitNeverArchivesOrOffersCancel() {
        withTransfer { source, target ->
            start(source)
            source.archiveReady()
            repeat(2) {
                waitHeld(target, "workspace.import.chunk")
                target.release("workspace.import.chunk")
            }
            waitHeld(target, "workspace.import.commit")
            target.release("workspace.import.commit", "{}")
            waitText("Transfer not confirmed")
            waitText(
                "The destination did not confirm import. Check it before retrying. The source was not archived."
            )
            screenshot("transfer-uncertain")
            assertEquals(
                0,
                compose.onAllNodesWithText("Review and retry").fetchSemanticsNodes().size
            )
            assertFalse(source.calls.contains("workspace.export.finalize"))
            assertFalse(target.calls.contains("workspace.import.abort"))
            assertEquals(1, target.calls.count { it == "workspace.import.commit" })
        }
    }

    @Test fun previewRetryDoesNotStartTransfer() {
        withTransfer(source = TransferServer(failPreview = true)) { source, _ ->
            waitText("Retry preview")
            compose.onNodeWithText("Transfer workspace").performScrollTo().assertIsNotEnabled()
            source.failPreview = false
            compose.onNodeWithText("Retry preview").performScrollTo().performClick()
            waitText("Review the preview and choose a destination.")
            compose.onNodeWithText("destination").performScrollTo().performClick()
            compose.onNodeWithText("Selected destination: destination").assertIsDisplayed()
            screenshot("transfer-preview-recovered")
            assertFalse(source.calls.contains("workspace.export.start"))
        }
    }

    @Test fun nonOwnerCannotPreviewOrStartTransfer() {
        withTransfer(source = TransferServer(owner = false)) { source, _ ->
            waitText("Only a host owner can transfer a workspace. Check your access on the host.")
            compose.onNodeWithText("destination").performScrollTo().performClick()
            compose.onNodeWithText("Transfer workspace").performScrollTo().assertIsNotEnabled()
            assertFalse(source.calls.contains("workspace.transfer.plan"))
            assertFalse(source.calls.contains("workspace.export.start"))
        }
    }

    @Test fun disconnectedSourceCannotPreviewOrStartTransfer() {
        withTransfer(sourceState = SessionState.DISCONNECTED) { source, _ ->
            waitText("Reconnect to the source host to preview this transfer.")
            compose.onNodeWithText("destination").performScrollTo().performClick()
            compose.onNodeWithText("Transfer workspace").performScrollTo().assertIsNotEnabled()
            assertFalse(source.calls.contains("workspace.transfer.plan"))
            assertFalse(source.calls.contains("workspace.export.start"))
        }
    }

    private fun start(source: TransferServer) {
        waitText("Review the preview and choose a destination.")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Review the preview and choose a destination.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Transfer workspace").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("destination").performScrollTo().performClick()
        compose.onNodeWithText("destination").assertIsSelected()
        assertFalse(source.calls.contains("workspace.export.start"))
        compose.onNodeWithText("Transfer workspace").performScrollTo().performClick()
        compose.waitUntil(10_000) { source.calls.contains("workspace.export.start") }
    }

    private fun withTransfer(
        source: TransferServer = TransferServer(),
        sourceState: SessionState = SessionState.READY,
        test: (TransferServer, TransferServer) -> Unit
    ) {
        val target =
            TransferServer(hold = setOf("workspace.import.chunk", "workspace.import.commit"))
        val ready = AtomicBoolean()
        RpcSession(source::transport, JsonObject(emptyMap()), onState = {
            ready.set(it == SessionState.READY)
        }).use { session ->
            session.connect()
            compose.waitUntil(10_000) { ready.get() }
            compose.setContent {
                IntentTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize()) {
                        WorkspaceTransferScreen(
                            session,
                            "ws",
                            listOf(
                                SavedConnection(
                                    "target",
                                    PairingPayload(listOf("destination"), 443, "test"),
                                    ConnectionMetadata(name = "destination")
                                )
                            ),
                            onBusy = {},
                            workspaceTitle = "Android workspace",
                            sourceName = "Source Mac",
                            sourceState = sourceState,
                            createTarget = { _, observer ->
                                RpcSession(
                                    target::transport,
                                    JsonObject(emptyMap()),
                                    onState = observer
                                )
                            }
                        )
                    }
                }
            }
            test(source, target)
        }
    }

    private fun waitHeld(server: TransferServer, method: String) {
        compose.waitUntil(10_000) { server.pending.containsKey(method) }
    }

    private fun waitText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
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

private class TransferServer(
    private val hold: Set<String> = emptySet(),
    private val owner: Boolean = true,
    var failPreview: Boolean = false
) {
    val calls = CopyOnWriteArrayList<String>()
    val pending = ConcurrentHashMap<String, JsonRpcMessage.Request>()
    private lateinit var listener: WssTransport.Listener
    private var received = 0

    fun transport(listener: WssTransport.Listener): SessionTransport {
        this.listener = listener
        return object : SessionTransport {
            override fun connect() = listener.onOpen()
            override fun close() = Unit
            override fun send(message: JsonRpcMessage): Boolean {
                val request = message as? JsonRpcMessage.Request ?: return true
                calls += request.method
                if (request.method in hold) pending[request.method] = request else reply(request)
                return true
            }
        }
    }

    fun release(method: String, result: String? = null) =
        reply(checkNotNull(pending.remove(method)), result)

    private fun reply(request: JsonRpcMessage.Request, result: String? = null) {
        val response = result ?: when (request.method) {
            "principal.me" -> """{"id":"person","isAdministrator":$owner}"""
            "system.status" -> """{"version":"1"}"""
            "workspace.list" -> """{"workspaces":[]}"""
            "events.subscribe" -> """{"subscriptionId":"transfer"}"""
            "workspace.transfer.plan" -> if (failPreview) {
                "{}"
            } else {
                """{"plan":{"manifest":$MANIFEST,"totalSizeBytes":2,"warnings":[]}}"""
            }
            "workspace.export.start" -> """{"exportId":"export"}"""
            "workspace.import.begin" -> """{"importId":"import","maxChunkBytes":1}"""
            "workspace.export.read" -> {
                val seq = (request.params as JsonObject)["seq"]
                """{"exportId":"export","seq":$seq,"totalChunks":2,"data":"YQ=="}"""
            }
            "workspace.import.chunk" -> {
                received += 1
                """{"importId":"import","seq":${received - 1},"receivedBytes":$received}"""
            }
            "workspace.import.commit" -> """{"workspace":{"id":"ws"}}"""
            "workspace.export.finalize" -> """{"exportId":"export","finalized":true}"""
            else -> "{}"
        }
        listener.onMessage(
            JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(response))
        )
    }

    fun progress(stage: String) = event("progress", "\"stage\":\"$stage\"")
    fun failed() = event("failed", "\"reason\":\"Export failed\"")
    fun archiveReady() = event(
        "ready",
        """"manifest":$MANIFEST,"archiveSizeBytes":2,"archiveSha256":"sha","maxChunkBytes":1,"totalChunks":2"""
    )

    private fun event(type: String, fields: String) {
        listener.onMessage(
            JsonRpcMessage.Notification(
                "events.event",
                WireJson.parseToJsonElement(
                    """{"subscriptionId":"transfer","event":{"type":"workspace:transfer:$type","data":{"workspaceId":"ws","exportId":"export",$fields}}}"""
                )
            )
        )
    }
}

private const val MANIFEST = """{"workspaceId":"ws","creatingIntentdVersion":"1"}"""
