package dev.intent.core.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolModuleTest {
    @Test
    fun serializationDependencyIsAvailable() {
        val placeholder = buildJsonObject { put("module", "protocol") }
        assertEquals(placeholder, Json.parseToJsonElement(placeholder.toString()))
    }
}
