package dev.intent.app.design

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentAvatarDesignTest {
    @Test
    fun `fallback matches iOS and desktop fixtures including floating point rounding`() {
        val fixtures = mapOf(
            "" to AgentAvatarDesign.FallbackGlasses,
            "a" to AgentAvatarDesign.FallbackDots,
            "agent-123" to AgentAvatarDesign.FallbackCode,
            "agent-456" to AgentAvatarDesign.FallbackDots,
            "AGENT-123" to AgentAvatarDesign.FallbackCrown,
            "0d9c66aa-1c9e-4a09-b52f-1f6f7a10a1de" to AgentAvatarDesign.FallbackDots,
            "agent-0" to AgentAvatarDesign.FallbackGlasses,
            "agent-1" to AgentAvatarDesign.FallbackCrownMouth,
            "agent-5" to AgentAvatarDesign.FallbackCrownInverted,
            "agent-11" to AgentAvatarDesign.FallbackCrown,
            "agent-750158" to AgentAvatarDesign.FallbackCrownMouth,
            "x2191079" to AgentAvatarDesign.FallbackGlasses
        )
        fixtures.forEach { (id, design) -> assertEquals(id, design, AgentAvatarDesign.resolve(id)) }
    }

    @Test
    fun `only builtin icons override specialist mapping`() {
        AgentAvatarDesign.entries.take(7).forEach { design ->
            assertEquals(design, AgentAvatarDesign.resolve("agent", "implementor", design.wireName))
        }
        listOf(null, "", "unknown", "fallback-glasses").forEach { icon ->
            assertEquals(
                AgentAvatarDesign.Implementor,
                AgentAvatarDesign.resolve("agent", "implementor", icon)
            )
        }
        assertEquals(
            AgentAvatarDesign.Coordinator,
            AgentAvatarDesign.resolve("agent", "spec-writer")
        )
        assertEquals(AgentAvatarDesign.Verifier, AgentAvatarDesign.resolve("agent", "developer"))
    }
}
