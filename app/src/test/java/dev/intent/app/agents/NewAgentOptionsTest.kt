package dev.intent.app.agents

import dev.intent.app.workspaces.WorkspaceOperations
import java.io.Closeable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewAgentOptionsTest {
    @Test
    fun loadsOnlyVisibleEnabledProvidersAndPickableSpecialists() {
        val store = NewAgentOptionsStore(OptionsHost())
        val options = store.load()
        assertEquals(listOf("worker", "coordinator"), options.specialists.map { it.id })
        assertEquals("coordinator", options.defaultSpecialist?.id)
        assertEquals(listOf("auggie", "codex", "unknown"), options.providers.map { it.id })
        assertFalse(options.providers.first().selectable)
        assertTrue(options.providers.last().selectable)
        assertEquals("codex", options.defaultProvider?.id)
        val models = store.models("codex")
        assertEquals(listOf("model"), models.map { it.id })
        assertEquals(listOf("low", "high"), models.single().effortLevels)
    }

    @Test
    fun hostDefaultsAreOmittedAndStaleSelectionsCannotLeak() {
        val store = NewAgentOptionsStore(OptionsHost())
        val options = store.load()
        val draft = NewAgentDraft("worker", "Worker", "codex", "model", "high")
        val params = draft.params("workspace", options, store.models("codex"))
        assertEquals(JsonPrimitive("high"), params["reasoningEffort"])
        assertEquals(JsonPrimitive("model"), params["model"])
        assertEquals(JsonPrimitive("codex"), params["provider"])
        assertEquals(JsonPrimitive("worker"), params["specialistId"])
        assertEquals(JsonPrimitive("workspace"), params["workspaceId"])
        val default = draft.copy(
            model = "",
            reasoning = ""
        ).params("workspace", options, store.models("codex"))
        assertFalse(default.containsKey("model"))
        assertFalse(default.containsKey("reasoningEffort"))
        val stale = draft.copy(
            provider = "unknown"
        ).params("workspace", options, store.models("codex"))
        assertFalse(stale.containsKey("model"))
        assertFalse(stale.containsKey("reasoningEffort"))
        val invalidEffort = draft.copy(
            reasoning = "max"
        ).params("workspace", options, store.models("codex"))
        assertFalse(invalidEffort.containsKey("reasoningEffort"))
    }

    @Test
    fun auggieAndEmptyNameUseDaemonDefaults() {
        val options =
            NewAgentOptions(
                listOf(AgentSpecialist("s", "S")),
                listOf(dev.intent.app.workspaces.CreationChoice("auggie", "Auggie"))
            )
        assertEquals(
            Json.parseToJsonElement("""{"workspaceId":"w","specialistId":"s"}"""),
            NewAgentDraft(specialist = "s", provider = "auggie").params("w", options, emptyList())
        )
    }

    @Test
    fun createUsesAgentCreateAndReturnsNewId() {
        val host = OptionsHost()
        val store = NewAgentOptionsStore(host)
        assertEquals(
            "new-agent",
            store.create(
                "w",
                NewAgentDraft("worker", provider = "codex"),
                store.load(),
                emptyList()
            )
        )
        assertEquals("agent.create", host.lastMethod)
    }

    @Test(expected = IllegalArgumentException::class)
    fun signedOutProviderCannotSubmit() {
        val store = NewAgentOptionsStore(OptionsHost())
        NewAgentDraft("worker", provider = "auggie").params("w", store.load(), emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun hiddenSpecialistCannotSubmit() {
        val store = NewAgentOptionsStore(OptionsHost())
        NewAgentDraft("hidden", provider = "codex").params("w", store.load(), emptyList())
    }

    @Test(expected = IllegalStateException::class)
    fun failedProviderPolicyHasNoFallback() {
        NewAgentOptionsStore(OptionsHost(failSettings = true)).load()
    }
}

private class OptionsHost(private val failSettings: Boolean = false) : WorkspaceOperations {
    var lastMethod = ""
    override fun request(method: String, params: JsonObject): JsonObject {
        lastMethod = method
        return Json.parseToJsonElement(
            when (method) {
                "specialist.list" ->
                    """{"specialists":[
                    {"id":"worker","name":"Worker"},
                    {"id":"hidden","name":"Hidden","hidden":true},
                    {"id":"internal","name":"Internal","role":"internal"},
                    {"id":"coordinator","name":"Coordinator","role":"orchestrator"}]}"""
                "providers.catalog" ->
                    """{"providers":[
                    {"id":"auggie","visible":true,"canBeDisabled":false},
                    {"id":"codex","visible":true,"canBeDisabled":true},
                    {"id":"unknown","visible":true,"canBeDisabled":false},
                    {"id":"disabled","visible":true,"canBeDisabled":true},
                    {"id":"hidden","visible":false,"canBeDisabled":false}]}"""
                "settings.get" -> {
                    check(!failSettings)
                    """{"path":"providers.enabled","value":{"codex":true}}"""
                }
                "host.providerAuthStatus" ->
                    """{"providers":[{"id":"auggie","authenticated":false},{"id":"codex","authenticated":true}]}"""
                "models.list" -> {
                    assertEquals(JsonPrimitive("codex"), params["providerId"])
                    """{"models":[{"id":"model","name":"Model","provider":"codex","effortLevels":["low","high","high",""]},
                        {"id":"stale","provider":"auggie"}]}"""
                }
                "agent.create" -> """{"agent":{"id":"new-agent"}}"""
                else -> error(method)
            }
        ) as JsonObject
    }
    override fun events(workspaceId: String, receive: (JsonObject) -> Unit): Closeable =
        Closeable {}
}
