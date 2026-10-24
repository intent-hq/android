package dev.intent.app.connection

import dev.intent.core.protocol.CertificateFingerprint
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import dev.intent.core.protocol.session.SessionTransport
import dev.intent.core.protocol.session.pinnedSessionTransport
import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun connectionEndpoint(payload: PairingPayload): String = URI(
    "wss",
    null,
    payload.hosts.first().removeSurrounding("[", "]"),
    payload.port,
    "/ws",
    null,
    null
).toASCIIString()

/** Certificate and authenticated principal form the cache boundary; bearer tokens never enter it. */
fun connectionCacheScope(payload: PairingPayload, principalId: String): String {
    require(principalId.isNotBlank())
    val identity =
        CertificateFingerprint.normalize(requireNotNull(payload.certFingerprint)) + "\n" +
            principalId
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

fun createConnectionSession(
    connection: SavedConnection,
    onState: (SessionState) -> Unit,
    transport: (WssTransport.Listener) -> SessionTransport = pinnedSessionTransport(
        connectionEndpoint(connection.payload),
        connection.payload.token,
        requireNotNull(connection.payload.certFingerprint)
    )
): RpcSession = RpcSession(
    transport,
    buildJsonObject {
        put("clientId", "android-${connection.id}")
        put("name", "Intent Android")
        put("deviceKind", "phone")
    },
    onState = onState,
    verifyPrincipal = true
)
