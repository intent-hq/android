package dev.intent.app.workspaces

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationParityTest {
    @Test fun sourceAndAgentSelectionsSurviveSerialization() {
        val draft = WorkspaceDraft(
            repository = CreationRepository("intent-hq", "android", "main"),
            baseRef = "feature",
            prompt = "Build it",
            provider = "codex",
            model = "gpt",
            specialist = "implementor",
            reasoning = "high",
            step = CreationStep.AGENT
        )
        assertEquals(draft, Json.decodeFromString<WorkspaceDraft>(Json.encodeToString(draft)))
        val params = draft.params()
        assertEquals("https://github.com/intent-hq/android", params.string("githubUrl"))
        assertEquals("feature", params.string("baseRef"))
        assertEquals("high", params.obj("initialAgent").string("reasoningEffort"))
        assertEquals(draft.requestKey, params.string("idempotencyKey"))
        assertEquals(CreationStep.BRANCH, draft.back().step)
        assertEquals("", draft.select(CreationRepository("o", "r")).baseRef)
    }

    @Test fun newRepositoryValidationAndWireShape() {
        assertEquals("/Developer/project", newRepositoryPath(" project ", "/Developer//"))
        assertEquals("~/Developer/project", newRepositoryPath("project", "~/Developer"))
        listOf("..", "a/b", "a\\b", "a\nb", "*").forEach {
            assertNull(newRepositoryPath(it, "/tmp"))
        }
        listOf("relative", "/tmp/../x", "/tmp/./x").forEach {
            assertNull(newRepositoryPath("project", it))
        }
        val draft = WorkspaceDraft(
            newRepository = true,
            repositoryName = "project",
            parentPath = "/Developer",
            repositoryPath = "/Developer/project",
            provider = "auggie",
            specialist = "coordinator",
            step = CreationStep.AGENT
        )
        val params = draft.params()
        assertEquals(JsonPrimitive(true), params["isNewRepo"])
        assertFalse(params.containsKey("githubUrl"))
        assertEquals("auggie", params.obj("initialAgent").string("provider"))
        assertEquals(CreationStep.NEW_REPOSITORY, draft.back().step)
    }

    @Test fun lostCreationReplyReusesPersistedRequestAcrossRestart() {
        var persisted = WorkspaceDraft(prompt = "Original", provider = "codex")
        val sent = mutableListOf<kotlinx.serialization.json.JsonObject>()
        val operations = CreationFakeOperations { method, params ->
            assertEquals("workspace.create", method)
            sent += params
            if (sent.size == 1) error("Lost reply")
            json("""{"workspace":{"id":"same-workspace"}}""")
        }
        val submission = CreationSubmission(operations, { error("No media") }) { persisted = it }
        assertThrows(IllegalStateException::class.java) { submission.submit(persisted) }
        val restored = Json.decodeFromString<WorkspaceDraft>(Json.encodeToString(persisted))
        assertEquals("same-workspace", submission.submit(restored))
        assertEquals(sent[0], sent[1])
    }

    @Test fun uncertainMediaMessageOnlyChecksHistory() {
        val calls = mutableListOf<String>()
        val draft = WorkspaceDraft(
            submitted = json("{}"),
            workspaceId = "ws",
            agentId = "agent",
            messageId = "msg",
            media = listOf(CreationMedia("key", "/unused", "clip.mp4", "video/mp4", false))
        )
        val operations = CreationFakeOperations { method, _ ->
            calls += method
            when (method) {
                "agent.getConversation" -> json("""{"messages":[]}""")
                "agent.getQueue" -> json("""{"queue":[]}""")
                else -> error("Unexpected mutation")
            }
        }
        assertThrows(IllegalStateException::class.java) {
            CreationSubmission(operations, { error("No upload on status check") }, {}).submit(draft)
        }
        assertEquals(listOf("agent.getConversation", "agent.getQueue"), calls)
    }

    @Test fun onlyTransportProofAllowsRetryOfFirstMessage() {
        val notSent = dev.intent.core.protocol.session.SessionDisconnectedException(true)
        assertTrue(creationRequestNotSent(java.util.concurrent.ExecutionException(notSent)))
        assertFalse(
            creationRequestNotSent(dev.intent.core.protocol.session.SessionDisconnectedException())
        )
        assertFalse(creationRequestNotSent(java.util.concurrent.TimeoutException()))
    }

    @Test fun directoryFailureDoesNotCreateAnythingAndSearchUsesIosQuery() {
        val store = CreationRepositoryStore(
            CreationFakeOperations { method, params ->
                assertEquals("host.directoryStatus", method)
                assertEquals("/tmp/project", params.string("path"))
                json("""{"exists":true}""")
            }
        )
        assertThrows(IllegalStateException::class.java) { store.checkDestination("/tmp/project") }
        assertEquals("android user:intent-hq", repositoryQuery("intent-hq/android"))
        assertEquals("user:intent-hq", repositoryQuery("intent-hq/"))
        assertTrue(WorkspaceDraft(provider = "codex").params().containsKey("initialAgent"))
    }
}

private fun json(value: String): kotlinx.serialization.json.JsonObject =
    Json.parseToJsonElement(value) as kotlinx.serialization.json.JsonObject
private class CreationFakeOperations(
    private val respond: (
        String,
        kotlinx.serialization.json.JsonObject
    ) -> kotlinx.serialization.json.JsonObject
) : WorkspaceOperations {
    override fun request(method: String, params: kotlinx.serialization.json.JsonObject) =
        respond(method, params)
    override fun events(
        workspaceId: String,
        receive: (kotlinx.serialization.json.JsonObject) -> Unit
    ) = java.io.Closeable {}
}
