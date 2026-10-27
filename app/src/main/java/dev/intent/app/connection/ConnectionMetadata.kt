package dev.intent.app.connection

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Local display metadata is separate from the immutable pairing credentials. */
data class ConnectionMetadata(val name: String? = null, val lastConnectedAt: Long? = null)

internal fun reportedConnectionName(result: JsonElement?, shared: Boolean = false): String? {
    val fields = result as? JsonObject ?: return null
    val keys = if (shared) {
        listOf("collaborationName", "prettyHostname", "hostname")
    } else {
        listOf("prettyHostname", "hostname")
    }
    return keys.firstNotNullOfOrNull { key ->
        (fields[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
