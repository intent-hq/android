package dev.intent.app.agents

import dev.intent.core.protocol.models.Agent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentGroupsTest {
    @Test fun groupsKeepDescendantsWithTheirRootAndRetiredAgentsSeparate() {
        val groups = agentGroups(
            listOf(
                Agent("root", "Coordinator"),
                Agent("worker", "Worker", createdByAgentId = "root", status = "active"),
                Agent(
                    "background",
                    "Background",
                    metadata = buildJsonObject {
                        put("isBackground", true)
                    }
                ),
                Agent("background-child", "Child", parentAgentId = "background"),
                Agent("orphan", "Orphan", parentAgentId = "missing"),
                Agent("old", "Retired", retiredAt = "now")
            )
        ).associateBy { it.group }
        assertEquals(
            listOf("root", "worker"),
            groups.getValue(AgentGroup.FOREGROUND).agents.map {
                it.id
            }
        )
        assertEquals(1, groups.getValue(AgentGroup.FOREGROUND).running)
        assertEquals(
            listOf("background", "background-child"),
            groups.getValue(AgentGroup.BACKGROUND).agents.map { it.id }
        )
        assertEquals("orphan", groups.getValue(AgentGroup.ORPHANED).agents.single().id)
        assertEquals("old", groups.getValue(AgentGroup.RETIRED).agents.single().id)
    }

    @Test fun searchRetainsLegacyParentBranchAndCyclesRemainFinite() {
        val rows = listOf(
            Agent("root", "Root"),
            Agent("child", "Find me", parentAgentId = "", createdByAgentId = "root")
        )
        assertEquals(listOf("root", "child"), filterAgents(rows, "Find", false).map { it.id })
        assertEquals(listOf(0, 1), AgentTree(rows).rows().map { it.depth })
        val cycle = listOf(
            Agent("a", "A", parentAgentId = "b"),
            Agent("b", "B", parentAgentId = "a")
        )
        assertEquals(
            setOf("a", "b"),
            agentGroups(cycle).flatMap {
                it.agents
            }.map { it.id }.toSet()
        )
    }
}
