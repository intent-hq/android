package dev.intent.core.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One JSON-RPC object per frame. A null ID is a request, not a notification. */
@Serializable(with = JsonRpcMessageSerializer::class)
sealed interface JsonRpcMessage {
    data class Request(val id: JsonElement, val method: String, val params: JsonElement? = null) :
        JsonRpcMessage {
        init {
            validateId(id)
            validateCall(method, params)
        }
    }

    data class Notification(val method: String, val params: JsonElement? = null) : JsonRpcMessage {
        init {
            validateCall(method, params)
        }
    }

    data class Success(val id: JsonElement, val result: JsonObject) : JsonRpcMessage {
        init {
            validateId(id)
        }
    }

    data class Failure(val id: JsonElement, val error: RpcError) : JsonRpcMessage {
        init {
            validateId(id)
        }
    }
}

internal fun validateId(id: JsonElement) {
    require(id is JsonPrimitive && (id == JsonNull || id.isString || NUMBER.matches(id.content))) {
        "JSON-RPC id must be a string, number, or null"
    }
}

private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

private fun validateCall(method: String, params: JsonElement?) {
    require(method.isNotEmpty()) { "JSON-RPC method must not be empty" }
    require(params == null || params is JsonObject || params is JsonArray) {
        "JSON-RPC params must be an object or array"
    }
}
