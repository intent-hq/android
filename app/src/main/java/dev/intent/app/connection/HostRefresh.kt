package dev.intent.app.connection

import dev.intent.core.protocol.pairing.PairingPayload
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Only route fields can change. Authentication always comes from the admitted connection. */
fun refreshedPayload(payload: PairingPayload, result: JsonElement): PairingPayload {
    val status = result as? JsonObject ?: return payload
    val hosts = payload.hosts.toMutableList()
    val seen = hosts.map { it.removeSurrounding("[", "]").lowercase(Locale.ROOT) }.toMutableSet()
    val reported = status["localIps"] as? JsonArray
    reported?.forEach { value ->
        val host = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        if (host != null &&
            runCatching { payload.copy(hosts = listOf(host)) }.isSuccess &&
            seen.add(host.removeSurrounding("[", "]").lowercase(Locale.ROOT))
        ) {
            hosts.add(host)
        }
    }
    val tunnel = (status["tcAddress"] as? JsonPrimitive)?.takeIf { it.isString }
        ?.content?.trim()?.takeIf { it.isNotEmpty() }
    return runCatching { payload.copy(hosts = hosts, tcAddress = tunnel ?: payload.tcAddress) }
        .getOrElse { payload.copy(hosts = hosts) }
}
