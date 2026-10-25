package dev.intent.core.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcResultTest {
    @Test
    fun bareArrayDiffResultRoundTrips() {
        val fixture = """{"jsonrpc":"2.0","id":"diff","result":[{"path":"a.kt","hunks":[]}]}"""
        val message = JsonRpc.decode(fixture) as JsonRpcMessage.Success
        assertTrue(message.result is JsonArray)
        assertEquals(
            Json.parseToJsonElement(fixture),
            Json.parseToJsonElement(JsonRpc.encode(message))
        )
    }

    @Test
    fun scalarAndNullResultsRemainValidJsonRpc() {
        listOf("null", "true", "42", "\"ok\"").forEach { result ->
            val fixture = """{"jsonrpc":"2.0","id":1,"result":$result}"""
            assertEquals(fixture, JsonRpc.encode(JsonRpc.decode(fixture)))
        }
    }
}
