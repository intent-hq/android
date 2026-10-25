package dev.intent.core.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class RpcError(val code: Int, val message: String, val data: JsonElement? = null) {
    /** Only the exact data discriminator indicates a missing entity. */
    val dataCode: String?
        get() = ((data as? JsonObject)?.get("code") as? JsonPrimitive)?.takeIf {
            it.isString
        }?.content
}

object RpcErrorCode {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
    const val CONFLICT = -32005
    const val UNAUTHORIZED = -32001
    const val FORBIDDEN = -32003
    const val OVERSIZED_RESPONSE = -32010
    const val SERVER_OVERLOADED = -32011

    /** Reserved by the prepared startup readiness contract. */
    const val DAEMON_NOT_READY = -32012
}
