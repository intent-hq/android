package dev.intent.app.agents

import dev.intent.app.workspaces.WorkspaceOperations
import dev.intent.core.protocol.models.Agent
import java.io.Closeable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentControlsTest {
    private val operations = ControlOperations()
    private val controls = AgentControls(operations, "workspace")
    private val agent =
        Agent("agent", "Original", provider = "provider", model = "old", reasoningEffort = "low")

    @Test fun mutationsMatchIosAndKeepWorkspaceScope() {
        assertEquals("Renamed", controls.rename(agent, " Renamed ").name)
        assertTrue(controls.mute(agent).notificationsMuted)
        controls.model(agent, "other", "default")
        controls.reasoning(agent, null)
        controls.delete(agent.id)
        assertEquals(
            listOf(
                "agent.rename",
                "agent.update",
                "agent.setModel",
                "agent.update",
                "agent.delete"
            ),
            operations.calls.map {
                it.first
            }
        )
        operations.calls.forEach { (_, params) ->
            assertEquals("workspace", params["workspaceId"]?.jsonPrimitive?.content)
            assertEquals("agent", params["agentId"]?.jsonPrimitive?.content)
        }
        assertEquals("Renamed", operations.calls[0].second["name"]?.jsonPrimitive?.content)
        assertEquals(
            "true",
            operations.calls[1].second["changes"]?.jsonObject?.get(
                "notificationsMuted"
            )?.jsonPrimitive?.content
        )
        assertEquals("other", operations.calls[2].second["providerId"]?.jsonPrimitive?.content)
        assertEquals("default", operations.calls[2].second["modelId"]?.jsonPrimitive?.content)
        assertEquals(
            JsonNull,
            operations.calls[3].second["changes"]?.jsonObject?.get("reasoningEffort")
        )
    }

    @Test fun partialModelSaveRetriesOnlyReasoning() = runBlocking {
        val changed = mutableListOf<Agent>()
        val selection = AgentModelSelection(agent, operations, controls, changed::add)
        selection.load()
        selection.loadModels()
        selection.selectModel("new")
        selection.reasoning = "high"
        operations.failReasoning = true
        assertFalse(selection.save())
        assertEquals("new", changed.single().model)
        assertTrue(selection.error.orEmpty().contains("Provider and model were saved"))
        operations.failReasoning = false
        assertTrue(selection.save())
        assertEquals(1, operations.calls.count { it.first == "agent.setModel" })
        assertEquals("high", changed.last().reasoningEffort)
    }

    @Test fun changingProviderClearsModelAndEffort() = runBlocking {
        val selection = AgentModelSelection(agent, operations, controls) {}
        selection.load()
        selection.loadModels()
        assertFalse(selection.canSave)
        selection.selectProvider("other")
        assertEquals("default", selection.model)
        assertEquals(null, selection.reasoning)
        assertFalse(selection.canSave)
        selection.loadModels()
        assertTrue(selection.canSave)
    }
}

private class ControlOperations : WorkspaceOperations {
    val calls = mutableListOf<Pair<String, JsonObject>>()
    var failReasoning = false
    override fun request(method: String, params: JsonObject): JsonObject {
        calls.add(method to params)
        if (method == "agent.update" && failReasoning) error("Reasoning failed")
        val result = when (method) {
            "providers.catalog" ->
                """{"providers":[{"id":"other","visible":true,"canBeDisabled":false}]}"""
            "settings.get" -> """{"path":"providers.enabled","value":null}"""
            "host.providerAuthStatus" -> """{"providers":[{"id":"other","authenticated":true}]}"""
            "models.list" ->
                """{"models":[{"id":"new","provider":"provider","effortLevels":["low","high"]}]}"""
            else -> "{}"
        }
        return Json.parseToJsonElement(result).jsonObject
    }
    override fun events(workspaceId: String, receive: (JsonObject) -> Unit): Closeable =
        Closeable {}
}
