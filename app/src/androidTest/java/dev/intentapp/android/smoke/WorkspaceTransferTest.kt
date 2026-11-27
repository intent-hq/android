package dev.intentapp.android.smoke

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import dev.intent.core.protocol.session.SessionTransport
import dev.intentapp.android.connection.ConnectionMetadata
import dev.intentapp.android.connection.SavedConnection
import dev.intentapp.android.design.IntentTheme
import dev.intentapp.android.testing.createIntentComposeRule
import dev.intentapp.android.workspaces.WorkspaceTransferScreen
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Matches iOS `WorkspaceTransferSheet` copy and states; each state is captured in both themes. */
@RunWith(AndroidJUnit4::class)
class WorkspaceTransferTest {
    @get:Rule val compose = createIntentComposeRule()
    private var dark by mutableStateOf(true)
    private var done = false

    @Test fun previewDestinationProgressCommitAndDone() {
        withTransfer { source, target ->
            waitText("Choose a machine")
            waitText("Estimated size: 2 B")
            waitText("2 agents are running and will be stopped.")
            screenshot("transfer-preview")
            compose.onNodeWithTag(
                "workspace-transfer.submit"
            ).performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("workspace-transfer.destination").performClick()
            waitText("destination")
            screenshot("transfer-destination-menu")
            compose.onNodeWithText("destination").performClick()
            compose.onNodeWithTag("workspace-transfer.archive-source").performClick()
            submitButton().performScrollTo().assertIsEnabled()
            screenshot("transfer-ready")
            submit(source)
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
            waitText("Workspace transferred")
            screenshot("transfer-done")
            assertEquals(1, target.calls.count { it == "workspace.import.commit" })
            assertEquals(1, source.calls.count { it == "workspace.export.finalize" })
            assertTrue(source.archived)
            compose.onNodeWithText("Done").performScrollTo().performClick()
            compose.waitUntil(10_000) { done }
        }
    }

    @Test fun cancelDuringExportCleansSourceWithoutImport() {
        withTransfer { source, target ->
            start(source)
            source.progress("bundling-git")
            waitText("Preparing repository files…")
            compose.onNodeWithText("Cancel transfer").performClick()
            waitText(CANCELLED)
            screenshot("transfer-cancelled")
            assertEquals(1, source.calls.count { it == "workspace.export.abort" })
            assertFalse(target.calls.contains("workspace.import.begin"))
            assertFalse(source.calls.contains("workspace.export.finalize"))
            submitButton().performScrollTo().assertIsEnabled()
        }
    }

    @Test fun cancelDuringUploadWaitsForReplyAndAbortsBothHosts() {
        withTransfer { source, target ->
            start(source)
            source.archiveReady()
            waitHeld(target, "workspace.import.chunk")
            compose.onNodeWithText("Cancel transfer").performClick()
            waitText("Cancelling the transfer…")
            compose.onNodeWithText("Cancel transfer").assertIsNotEnabled()
            screenshot("transfer-cancelling")
            assertFalse(target.calls.contains("workspace.import.abort"))
            target.release("workspace.import.chunk")
            waitText(CANCELLED)
            assertEquals(1, target.calls.count { it == "workspace.import.abort" })
            assertEquals(1, source.calls.count { it == "workspace.export.abort" })
            assertFalse(target.calls.contains("workspace.import.commit"))
        }
    }

    @Test fun exportFailureReturnsToFormAndRetries() {
        withTransfer { source, target ->
            start(source)
            source.failed()
            waitText("The transfer could not finish.")
            waitText("Export failed")
            screenshot("transfer-failed")
            assertFalse(target.calls.contains("workspace.import.begin"))
            submitButton().performScrollTo().performClick()
            compose.waitUntil(10_000) {
                source.calls.count { it == "workspace.export.start" } == 2
            }
            waitText("Preparing the transfer…")
            assertFalse(target.calls.contains("workspace.import.commit"))
        }
    }

    @Test fun uncertainCommitNeverArchivesOrAborts() {
        withTransfer { source, target ->
            start(source)
            source.archiveReady()
            repeat(2) {
                waitHeld(target, "workspace.import.chunk")
                target.release("workspace.import.chunk")
            }
            waitHeld(target, "workspace.import.commit")
            target.release("workspace.import.commit", "{}")
            waitText("The transfer could not finish.")
            waitText("The destination did not confirm the transfer.", substring = true)
            screenshot("transfer-uncertain")
            assertFalse(source.calls.contains("workspace.export.finalize"))
            assertFalse(target.calls.contains("workspace.import.abort"))
            assertEquals(1, target.calls.count { it == "workspace.import.commit" })
        }
    }

    @Test fun previewCanBeCancelledAndRetried() {
        withTransfer(source = TransferServer(hold = setOf("workspace.transfer.plan"))) {
                source,
                _
            ->
            waitText("Loading transfer preview…")
            screenshot("transfer-preview-loading")
            compose.onNodeWithText("Cancel").performClick()
            waitText("Transfer preview cancelled.")
            compose.onNodeWithTag(
                "workspace-transfer.submit"
            ).performScrollTo().assertIsNotEnabled()
            source.release("workspace.transfer.plan")
            source.hold = emptySet()
            compose.onNodeWithText("Retry").performScrollTo().performClick()
            waitText("Estimated size: 2 B")
            assertFalse(source.calls.contains("workspace.export.start"))
        }
    }

    @Test fun previewFailureShowsErrorAndRetryRecovers() {
        withTransfer(source = TransferServer(failPreview = true)) { source, _ ->
            waitText("Could not load the transfer preview.")
            waitText("Transfer preview unavailable. The host may need an update.", substring = true)
            screenshot("transfer-preview-failed")
            compose.onNodeWithTag(
                "workspace-transfer.submit"
            ).performScrollTo().assertIsNotEnabled()
            source.failPreview = false
            compose.onNodeWithText("Retry").performScrollTo().performClick()
            waitText("Estimated size: 2 B")
            assertEquals(0, compose.onAllNodesWithText("Retry").fetchSemanticsNodes().size)
            assertFalse(source.calls.contains("workspace.export.start"))
        }
    }

    @Test fun nonOwnerCannotPreviewOrStartTransfer() {
        withTransfer(source = TransferServer(owner = false)) { source, _ ->
            waitText("Connect as the workspace owner to transfer this workspace.", substring = true)
            selectDestination()
            compose.onNodeWithTag(
                "workspace-transfer.submit"
            ).performScrollTo().assertIsNotEnabled()
            assertFalse(source.calls.contains("workspace.transfer.plan"))
            assertFalse(source.calls.contains("workspace.export.start"))
        }
    }

    @Test fun disconnectedSourceCannotPreviewOrStartTransfer() {
        withTransfer(sourceState = SessionState.DISCONNECTED) { source, _ ->
            waitText("Connect as the workspace owner to transfer this workspace.", substring = true)
            selectDestination()
            compose.onNodeWithTag(
                "workspace-transfer.submit"
            ).performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Retry").performScrollTo().assertIsNotEnabled()
            assertFalse(source.calls.contains("workspace.transfer.plan"))
            assertFalse(source.calls.contains("workspace.export.start"))
        }
    }

    @Test fun noDestinationsExplainsPairing() {
        withTransfer(destinations = emptyList()) { _, _ ->
            waitText(
                "Pair another machine from Settings to transfer this workspace. " +
                    "You must be an owner on both machines."
            )
            screenshot("transfer-no-destinations")
            compose.onNodeWithTag(
                "workspace-transfer.submit"
            ).performScrollTo().assertIsNotEnabled()
        }
    }

    private fun submitButton() = compose.onNodeWithTag("workspace-transfer.submit")

    private fun selectDestination() {
        compose.onNodeWithTag("workspace-transfer.destination").performScrollTo().performClick()
        waitText("destination")
        compose.onNodeWithText("destination").performClick()
    }

    private fun start(source: TransferServer) {
        waitText("Estimated size: 2 B")
        submitButton().performScrollTo().assertIsNotEnabled()
        selectDestination()
        assertFalse(source.calls.contains("workspace.export.start"))
        submit(source)
    }

    private fun submit(source: TransferServer) {
        submitButton().performScrollTo().performClick()
        compose.waitUntil(10_000) { source.calls.contains("workspace.export.start") }
    }

    private fun withTransfer(
        source: TransferServer = TransferServer(),
        sourceState: SessionState = SessionState.READY,
        destinations: List<SavedConnection> = listOf(
            SavedConnection(
                "target",
                PairingPayload(listOf("destination"), 443, "test"),
                ConnectionMetadata(name = "destination")
            )
        ),
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
                IntentTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        WorkspaceTransferScreen(
                            session,
                            "ws",
                            destinations,
                            onBusy = {},
                            onDone = { done = true },
                            workspaceTitle = "Android workspace",
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

    private fun waitText(text: String, substring: Boolean = false) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText(text, substring = substring)[0].assertIsDisplayed()
    }

    /** Captures the current state in dark, then light, then restores dark. */
    private fun screenshot(name: String) {
        capture("$name-dark")
        dark = false
        capture("$name-light")
        dark = true
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Let the window draw and press ripples settle before the device screenshot.
        SystemClock.sleep(SETTLE_MS)
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

private const val SETTLE_MS = 600L

private const val CANCELLED =
    "Transfer cancelled. The source workspace is still available; " +
        "stopped agents can be restarted there."

private class TransferServer(
    var hold: Set<String> = emptySet(),
    private val owner: Boolean = true,
    var failPreview: Boolean = false
) {
    val calls = CopyOnWriteArrayList<String>()
    val pending = ConcurrentHashMap<String, JsonRpcMessage.Request>()
    private lateinit var listener: WssTransport.Listener
    private var received = 0
    var archived = false

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
                """{"plan":{"manifest":$MANIFEST,"totalSizeBytes":2,"warnings":$WARNINGS}}"""
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
            "workspace.export.finalize" -> {
                archived = (request.params as JsonObject)["archiveSource"].toString() == "true"
                """{"exportId":"export","finalized":true}"""
            }
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
private const val WARNINGS =
    """[{"code":"agents-running","message":"2 agent(s) are running and will be stopped."}]"""
