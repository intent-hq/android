package dev.intent.core.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRpcMessageTest {
    @Test
    fun contractFixturesRoundTrip() {
        // Extracted without payload changes from monorepo docs/protocol/fixtures at
        // the Android bootstrap baseline. Prepared startup examples test wire compatibility only.
        val fixtures =
            listOf(
                "checkout-repo-config.json",
                "event-stream-recovery-contract.json",
                "startup-status.json"
            )
        fixtures.forEach { name ->
            val source = checkNotNull(javaClass.getResource("/envelope/$name")).readText()
            val frames = Json.parseToJsonElement(source) as JsonArray
            assertTrue(frames.isNotEmpty())
            frames.forEach { frame ->
                assertEquals(
                    name,
                    frame,
                    Json.parseToJsonElement(JsonRpc.encode(JsonRpc.decode(frame.toString())))
                )
            }
        }
    }

    @Test
    fun nullIdIsARequestAndMissingIdIsANotification() {
        val request = JsonRpc.decode("""{"jsonrpc":"2.0","id":null,"method":"system.ping"}""")
        assertEquals(JsonRpcMessage.Request(JsonNull, "system.ping"), request)
        assertTrue(JsonRpc.encode(request).contains("\"id\":null"))
        val notification = JsonRpc.decode("""{"jsonrpc":"2.0","method":"system.ping"}""")
        assertEquals(JsonRpcMessage.Notification("system.ping"), notification)
        assertFalse(JsonRpc.encode(notification).contains("\"id\""))
    }

    @Test
    fun idTokensRetainTheirTypeAndPrecision() {
        listOf(
            "0",
            "-1",
            "1.25",
            "1e100",
            "18446744073709551616",
            "\"001\"",
            "null"
        ).forEach { id ->
            val frame = """{"jsonrpc":"2.0","id":$id,"result":{}}"""
            assertEquals(frame, JsonRpc.encode(JsonRpc.decode(frame)))
        }
    }

    @Test
    fun paramsAreOptionalAndPositionalArraysArePreserved() {
        listOf("{}", "[]", "[1,\"value\"]").forEach { params ->
            val frame = """{"jsonrpc":"2.0","id":1,"method":"test","params":$params}"""
            assertEquals(frame, JsonRpc.encode(JsonRpc.decode(frame)))
        }
    }

    @Test
    fun errorDataRetainsArbitraryJsonAndUnknownCodes() {
        listOf(
            null,
            "null",
            "\"detail\"",
            "[1]",
            "{\"code\":\"not-found\",\"future\":true}"
        ).forEach { data ->
            val suffix = data?.let { ",\"data\":$it" } ?: ""
            val frame = """{"jsonrpc":"2.0","id":"a",""" +
                """"error":{"code":-31999,"message":"failure"$suffix}}"""
            assertEquals(frame, JsonRpc.encode(JsonRpc.decode(frame)))
        }
        val missing =
            RpcError(-32602, "missing", Json.parseToJsonElement("""{"code":"not-found"}"""))
        assertEquals("not-found", missing.dataCode)
        assertNull(RpcError(-32602, "not found").dataCode)
        assertNull(RpcError(-32602, "missing", JsonPrimitive("not-found")).dataCode)
    }

    @Test
    fun additiveEnvelopeAndErrorFieldsAreIgnored() {
        val frame = """{"jsonrpc":"2.0","id":1,""" +
            """"future":true,"error":{"code":-32603,"message":"oops","future":true}}"""
        assertEquals(
            JsonRpcMessage.Failure(JsonPrimitive(1), RpcError(-32603, "oops")),
            JsonRpc.decode(frame)
        )
        assertEquals(
            JsonRpcMessage.Request(JsonPrimitive(1), "test"),
            JsonRpc.decode("""{"jsonrpc":"2.0","id":1,"method":"test","future":{}}""")
        )
    }

    @Test
    fun malformedAndAmbiguousFramesAreRejected() {
        val invalid = listOf(
            "{",
            "[]",
            "null",
            """{"jsonrpc":2.0,"id":1,"method":"test"}""",
            """{"jsonrpc":"1.0","id":1,"method":"test"}""",
            """{"id":1,"method":"test"}""",
            """{"jsonrpc":"2.0","id":true,"method":"test"}""",
            """{"jsonrpc":"2.0","id":{},"method":"test"}""",
            """{"jsonrpc":"2.0","id":[],"method":"test"}""",
            """{"jsonrpc":"2.0","method":""}""",
            """{"jsonrpc":"2.0","method":1}""",
            """{"jsonrpc":"2.0","method":"test","params":null}""",
            """{"jsonrpc":"2.0","method":"test","params":1}""",
            """{"jsonrpc":"2.0","method":"test","result":{}}""",
            """{"jsonrpc":"2.0","result":{}}""",
            """{"jsonrpc":"2.0","id":1}""",
            """{"jsonrpc":"2.0","id":1,"result":[]}""",
            """{"jsonrpc":"2.0","id":1,"result":null}""",
            """{"jsonrpc":"2.0","id":1,"result":{},"error":{}}""",
            """{"jsonrpc":"2.0","id":1,"error":{"code":"-32603","message":"oops"}}""",
            """{"jsonrpc":"2.0","id":1,"error":{"code":-32603.5,"message":"oops"}}""",
            """{"jsonrpc":"2.0","id":1,"error":{"code":-32603,"message":1}}"""
        )
        invalid.forEach { frame ->
            assertThrows(frame, SerializationException::class.java) { JsonRpc.decode(frame) }
        }
    }

    @Test
    fun invalidMessagesCannotBeConstructed() {
        assertThrows(IllegalArgumentException::class.java) {
            JsonRpcMessage.Request(JsonPrimitive(true), "test")
        }
        assertThrows(IllegalArgumentException::class.java) { JsonRpcMessage.Notification("") }
        assertThrows(IllegalArgumentException::class.java) {
            JsonRpcMessage.Notification("test", JsonNull)
        }
        assertThrows(IllegalArgumentException::class.java) {
            JsonRpcMessage.Success(JsonArray(emptyList()), JsonObject(emptyMap()))
        }
    }

    @Test
    fun kotlinxSerializerUsesTheWireEnvelope() {
        val message: JsonRpcMessage = JsonRpcMessage.Success(
            JsonPrimitive("id"),
            JsonObject(emptyMap())
        )
        val frame = Json.encodeToString(JsonRpcMessage.serializer(), message)
        assertEquals(message, Json.decodeFromString(JsonRpcMessage.serializer(), frame))
        assertFalse(frame.contains("type"))
    }
}
