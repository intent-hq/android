package dev.intent.app.workspaces

import java.io.Closeable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCreationTransferTest {
    @Test fun creationChoicesAndCreateUseWireFields() {
        val fake = FakeOperations { method, params ->
            when (method) {
                "providers.catalog" -> json(
                    """
{"providers":[{"id":"codex",
"visible":true,
"canBeDisabled":true},{"id":"hidden",
"visible":false,
"canBeDisabled":false}]}
"""
                )
                "settings.get" -> json("""{"path":"providers.enabled","value":{"codex":true}}""")
                "host.providerAuthStatus" -> json(
                    """{"providers":[{"id":"codex","authenticated":true}]}"""
                )
                "specialist.list" -> json(
                    """{"specialists":[{"id":"implementor","name":"Implementor"}]}"""
                )
                "models.list" -> {
                    assertEquals("codex", params.string("providerId"))
                    json("""{"models":[{"id":"gpt","name":"GPT"}]}""")
                }
                "workspace.create" -> {
                    assertEquals("Work", params.string("title"))
                    assertEquals("codex", params.obj("initialAgent").string("provider"))
                    assertEquals("gpt", params.obj("initialAgent").string("model"))
                    assertEquals("/repo", params.string("repositoryPath"))
                    json("""{"workspace":{"id":"ws"}}""")
                }
                else -> error(method)
            }
        }
        val store = CreationOptionsStore(fake)
        assertEquals("codex", store.loadProviders().single().id)
        assertEquals(
            "ws",
            CreationSubmission(fake, { error("No media") }, {}).submit(
                WorkspaceDraft(
                    "Work",
                    "/repo",
                    prompt = "Do work",
                    provider = "codex",
                    model = "gpt"
                )
            )
        )
    }

    @Test fun transferCorrelatesEarlyEventAndFinalizesAfterCommit() {
        val fixture = TransferFixture()
        assertEquals("Workspace transferred.", fixture.relay().run(true))
        assertTrue(
            fixture.calls.indexOf("workspace.import.commit") <
                fixture.calls.indexOf("workspace.export.finalize")
        )
        assertTrue(fixture.closed)
    }

    @Test fun uncertainCommitNeverArchivesOrRetries() {
        val fixture = TransferFixture(failCommit = true)
        assertThrows(IllegalStateException::class.java) { fixture.relay().run(true) }
        assertFalse(fixture.calls.contains("workspace.export.finalize"))
        assertEquals(1, fixture.calls.count { it == "workspace.import.commit" })
        assertFalse(fixture.calls.contains("workspace.import.abort"))
        assertTrue(fixture.closed)
    }

    @Test fun corruptChunkAbortsBeforeCommit() {
        val fixture = TransferFixture(corruptChunk = true)
        assertThrows(IllegalArgumentException::class.java) { fixture.relay().run(false) }
        assertFalse(fixture.calls.contains("workspace.import.commit"))
        assertTrue(fixture.calls.contains("workspace.import.abort"))
        assertTrue(fixture.calls.contains("workspace.export.abort"))
    }

    @Test fun versionMismatchNeverStartsExport() {
        val fixture = TransferFixture(version = "other")
        assertThrows(IllegalArgumentException::class.java) { fixture.relay().run(false) }
        assertFalse(fixture.calls.contains("workspace.export.start"))
    }
}

private fun json(value: String): JsonObject = Json.parseToJsonElement(value) as JsonObject
private class FakeOperations(val respond: (String, JsonObject) -> JsonObject) :
    WorkspaceOperations {
    override fun request(method: String, params: JsonObject): JsonObject = respond(method, params)
    override fun events(workspaceId: String, receive: (JsonObject) -> Unit): Closeable =
        Closeable { }
}
internal class TransferFixture(
    val failCommit: Boolean = false,
    val corruptChunk: Boolean = false,
    val version: String = "1",
    val beforeReply: (String) -> Unit = {}
) {
    val calls = mutableListOf<String>()
    var closed = false
    var receive: ((JsonObject) -> Unit)? = null
    private val source = object : WorkspaceOperations {
        override fun request(method: String, params: JsonObject): JsonObject {
            calls += method
            beforeReply(method)
            return when (method) {
                "workspace.transfer.plan" -> json(
                    """{"plan":{"manifest":{"workspaceId":"ws","creatingIntentdVersion":"1"}}}"""
                )
                "principal.me" -> json("""{"id":"owner","isAdministrator":true}""")
                "workspace.export.start" -> {
                    receive!!(
                        json(
                            """
{"event":{"type":"workspace:transfer:ready",
"data":{"workspaceId":"ws",
"exportId":"export",
"manifest":{"workspaceId":"ws",
"creatingIntentdVersion":"1"},
"archiveSizeBytes":1,
"archiveSha256":"sha",
"maxChunkBytes":1,
"totalChunks":1}}}
"""
                        )
                    )
                    json("""{"exportId":"export"}""")
                }
                "workspace.export.read" -> json(
                    """
{"exportId":"export",
"seq":${if (corruptChunk) 1 else 0},
"totalChunks":1,
"data":"YQ=="}
"""
                )
                "workspace.export.finalize" -> {
                    assertTrue(params["archiveSource"].toString().toBoolean())
                    json("""{"exportId":"export","finalized":true}""")
                }
                "workspace.export.abort" -> json("{}")
                else -> error(method)
            }
        }
        override fun events(workspaceId: String, receive: (JsonObject) -> Unit): Closeable {
            this@TransferFixture.receive = receive
            return Closeable { closed = true }
        }
    }
    private val target = FakeOperations { method, _ ->
        calls += method
        beforeReply(method)
        when (method) {
            "principal.me" -> json("""{"id":"owner2","isAdministrator":true}""")
            "system.status" -> json("""{"version":"$version"}""")
            "workspace.list" -> json("""{"workspaces":[]}""")
            "workspace.import.begin" -> json("""{"importId":"import","maxChunkBytes":1}""")
            "workspace.import.chunk" -> json("""{"importId":"import","seq":0,"receivedBytes":1}""")
            "workspace.import.commit" -> {
                if (failCommit) error("Disconnected")
                json("""{"workspace":{"id":"ws"}}""")
            }
            "workspace.import.abort" -> json("{}")
            else -> error(method)
        }
    }
    fun relay(control: TransferControl = TransferControl()): WorkspaceTransfer =
        WorkspaceTransfer(source, target, "ws", control)
}
