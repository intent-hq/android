package dev.intent.app.conversation

import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.QueuedMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationNoiseTest {
    @Test
    fun rowAndPersistedBlockMetadataIdentifyAgentMessages() {
        val metadata = metadata("agent_message")
        assertEquals(
            "Agent message",
            ConversationNoise.label(message("hello").copy(metadata = metadata))
        )
        val block = ContentBlock("text", text = "hello", messageMetadata = metadata)
        assertEquals(
            "Agent message",
            ConversationNoise.label(message("hello").copy(contentBlocks = listOf(block)))
        )
    }

    @Test
    fun legacyMachinePrefixesCollapseButQuotedHumanTextStaysVisible() {
        listOf(
            "[MESSAGE FROM AGENT Worker]",
            "[WORKSPACE EVENTS]",
            "[SYSTEM NOTE]",
            "[Role Reminder: Agent]"
        ).forEach {
            org.junit.Assert.assertNotNull(ConversationNoise.label(message(it)))
        }
        assertNull(ConversationNoise.label(message("Please explain [WORKSPACE EVENTS] messages")))
        assertNull(ConversationNoise.label(message("Hello")))
        assertEquals(
            "System detail",
            ConversationNoise.label(message("notice").copy(role = "system"))
        )
        assertNull(
            ConversationNoise.label(message("[SYSTEM NOTE] example").copy(role = "assistant"))
        )
    }

    @Test
    fun queueUsesSameMetadataAndLegacyClassification() {
        assertEquals(
            "Background update",
            ConversationNoise.label(QueuedMessage("q", "wake", "", 0, metadata("hook_wake")))
        )
        assertEquals(
            "Workspace update",
            ConversationNoise.label(QueuedMessage("q", "[WORKSPACE EVENTS] done", "", 0))
        )
        assertNull(ConversationNoise.label(QueuedMessage("q", "My message", "", 0)))
    }

    private fun metadata(type: String) = JsonObject(
        mapOf(
            "type" to JsonPrimitive(type),
            "fromAgentId" to JsonPrimitive("child")
        )
    )
    private fun message(text: String) =
        ConversationMessage("id", "user", listOf(ContentBlock("text", text = text)))
}
