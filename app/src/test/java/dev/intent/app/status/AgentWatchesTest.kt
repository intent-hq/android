package dev.intent.app.status

import dev.intent.core.protocol.models.WireJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AgentWatchesTest {
    @Test fun capturedAfterAllPayloadProvidesRowsWithoutParentWaitingFlags() {
        val payload = requireNotNull(javaClass.getResource("/agent-watches-live.json")).readText()
        val result = parseAgentWatches(WireJson.parseToJsonElement(payload).jsonObject)
        assertEquals("Waiting for all (0/3)", result.header)
        assertEquals(3, result.rows.size)
        assertEquals(
            setOf(
                "Plain stop button",
                "Create and delete terminals",
                "Fix: waiting-for-agents status missing on Android"
            ),
            result.rows.map {
                it.agent.name
            }.toSet()
        )
        assertFalse(result.rows.any { it.completed || it.deleted })
        assertEquals(setOf("port-create"), result.rows.map { it.agent.workspaceId }.toSet())
    }

    @Test fun groupedCompletedAndDeletedParticipantsRemainUntilGroupClears() {
        val result =
            parseAgentWatches(
                WireJson.parseToJsonElement(
                    """{
            "subscriptions":[{"filter":{"actorIds":["legacy","running"]}}],
            "delegationGroups":[{"expectedAgentIds":["running","done","deleted"],
                "completedAgentIds":["done","deleted"],"deletedAgentIds":["deleted"]}],
            "agentStatuses":{"running":"active","unrelated":"active"},
            "agents":[{"id":"running","name":"Worker","workspaceId":"ws"},
                {"id":"unrelated","name":"Not watched"}]
        }"""
                ).jsonObject
            )
        assertEquals(
            listOf("deleted", "done", "legacy", "running"),
            result.rows.map {
                it.agent.id
            }
        )
        assertEquals("Waiting for all (2/3)", result.header)
        assertEquals("Deleted", result.rows[0].label)
        assertEquals("Completed", result.rows[1].label)
        assertEquals("Worker", result.rows.last().agent.name)
        assertEquals("Running", result.rows.last().label)
        assertEquals(
            emptyList<WatchedAgent>(),
            parseAgentWatches(
                WireJson.parseToJsonElement(
                    """{"subscriptions":[],"delegationGroups":[]}"""
                ).jsonObject
            ).rows
        )
    }
}
