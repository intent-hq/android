package dev.intent.core.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** The shared wire codec tolerates additive fields without adding a type discriminator. */
object JsonRpc {
    val json: Json = Json { ignoreUnknownKeys = true }

    fun decode(frame: String): JsonRpcMessage =
        json.decodeFromString(JsonRpcMessageSerializer, frame)

    fun encode(message: JsonRpcMessage): String =
        json.encodeToString(JsonRpcMessageSerializer, message)
}

object JsonRpcMessageSerializer : KSerializer<JsonRpcMessage> {
    override val descriptor = buildClassSerialDescriptor("JsonRpcMessage")

    override fun deserialize(decoder: Decoder): JsonRpcMessage {
        val input = decoder as? JsonDecoder ?: throw SerializationException("JSON decoder required")
        val obj = input.decodeJsonElement() as? JsonObject
            ?: throw SerializationException("Expected one JSON-RPC object")
        requireWire(obj["jsonrpc"] == JsonPrimitive("2.0"), "Expected jsonrpc 2.0")
        return try {
            if ("method" in obj) decodeCall(obj) else decodeResponse(obj)
        } catch (error: IllegalArgumentException) {
            throw SerializationException("Invalid JSON-RPC envelope", error)
        }
    }

    private fun decodeCall(obj: JsonObject): JsonRpcMessage {
        requireWire("result" !in obj && "error" !in obj, "Call cannot contain a response")
        val method = string(obj["method"], "method")
        return if ("id" in obj) {
            JsonRpcMessage.Request(obj.getValue("id"), method, obj["params"])
        } else {
            JsonRpcMessage.Notification(method, obj["params"])
        }
    }

    private fun decodeResponse(obj: JsonObject): JsonRpcMessage {
        requireWire(
            "id" in obj && (("result" in obj) xor ("error" in obj)),
            "Response requires id and exactly one outcome"
        )
        val id = obj.getValue("id")
        return if ("result" in obj) {
            val result =
                obj["result"] as? JsonObject
                    ?: throw SerializationException("Result must be an object")
            JsonRpcMessage.Success(id, result)
        } else {
            val error =
                obj["error"] as? JsonObject
                    ?: throw SerializationException("Error must be an object")
            val code = error["code"] as? JsonPrimitive
            requireWire(
                code != null && !code.isString && code.intOrNull != null,
                "Error code must be an integer"
            )
            JsonRpcMessage.Failure(
                id,
                RpcError(code!!.intOrNull!!, string(error["message"], "message"), error["data"])
            )
        }
    }

    override fun serialize(encoder: Encoder, value: JsonRpcMessage) {
        val output =
            encoder as? JsonEncoder ?: throw SerializationException("JSON encoder required")
        output.encodeJsonElement(
            buildJsonObject {
                put("jsonrpc", "2.0")
                when (value) {
                    is JsonRpcMessage.Request -> {
                        put("id", wireId(value.id))
                        put("method", value.method)
                        value.params?.let { put("params", it) }
                    }
                    is JsonRpcMessage.Notification -> {
                        put("method", value.method)
                        value.params?.let { put("params", it) }
                    }
                    is JsonRpcMessage.Success -> {
                        put("id", wireId(value.id))
                        put("result", value.result)
                    }
                    is JsonRpcMessage.Failure -> {
                        put("id", wireId(value.id))
                        put(
                            "error",
                            buildJsonObject {
                                put("code", value.error.code)
                                put("message", value.error.message)
                                value.error.data?.let { put("data", it) }
                            }
                        )
                    }
                }
            }
        )
    }

    private fun string(value: JsonElement?, field: String): String {
        requireWire(value is JsonPrimitive && value.isString, "$field must be a string")
        return (value as JsonPrimitive).content
    }

    private fun requireWire(valid: Boolean, message: String) {
        if (!valid) throw SerializationException(message)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun wireId(id: JsonElement): JsonElement = if (id is JsonPrimitive &&
    !id.isString &&
    id != JsonNull
) {
    JsonUnquotedLiteral(id.content)
} else {
    id
}
