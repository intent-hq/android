package dev.intent.app.notifications

import dev.intent.core.protocol.models.Agent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTriggerTest {
    private fun alert(type: String, data: String = "") = notificationTrigger(
        Json.parseToJsonElement(
            """{"event":{"type":"$type","workspaceId":"ws","data":{"agentId":"agent"$data}}}"""
        ) as JsonObject
    )

    @Test fun mapsAttentionQuestionAndCompletion() {
        assertEquals("attention", alert("agent:attention-requested")?.channel)
        assertEquals(
            "attention",
            alert("agent:updated", """, "pendingQuestionsMessageId":"message"""")?.channel
        )
        assertEquals(
            "completion",
            alert("task:status-changed", """, "noteId":"task", "newStatus":"complete"""")?.channel
        )
        assertEquals("completion", alert("agent:idle")?.channel)
    }

    @Test fun suppressesClearsMutedChildrenAndWaitingAgents() {
        assertNull(alert("agent:updated", """, "pendingQuestionsMessageId":"""""))
        assertNull(alert("agent:idle", """, "notificationsMuted":true"""))
        assertNull(alert("agent:idle", """, "isBackground":true"""))
        assertNull(alert("agent:idle", """, "waitingForAgentIds":["child"]"""))
        assertNull(alert("agent:attention-requested", """, "parentAgentId":"parent""""))
        assertNull(alert("task:status-changed", """, "noteId":"task", "newStatus":"in_progress""""))
        assertNull(alert("agent:stream:activity"))
    }

    @Test fun mutedAgentCannotPostAttentionQuestionOrCompletion() {
        val muted = listOf(Agent("agent", "Muted", notificationsMuted = true))
        for (type in listOf("agent:attention-requested", "agent:updated", "agent:idle")) {
            val candidate = alert(type, """, "pendingQuestionsMessageId":"question"""")!!
            assertTrue(suppressAgentAlert(candidate.subjectId, muted, type == "agent:idle"))
            assertFalse(
                suppressAgentAlert(
                    candidate.subjectId,
                    muted.map {
                        it.copy(notificationsMuted = false)
                    },
                    type == "agent:idle"
                )
            )
        }
    }

    @Test fun freshAgentListSuppressesPrematureCompletion() {
        assertTrue(suppressAgentAlert("a", listOf(Agent("b", "Other", status = "active")), true))
        assertTrue(
            suppressAgentAlert("a", listOf(Agent("a", "Agent", notificationsMuted = true)), false)
        )
        assertFalse(suppressAgentAlert("a", listOf(Agent("b", "Other", status = "active")), false))
        assertFalse(suppressAgentAlert("a", null, true))
    }
}
