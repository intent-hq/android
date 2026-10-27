package dev.intent.app.connection

import dev.intent.core.protocol.CertificateFingerprint
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.browser.pinnedTunnelTransport
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import dev.intent.core.protocol.session.SessionTransport
import dev.intent.tailcat.Tailcat
import dev.intent.tailcat.TunnelClientFactory
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun connectionEndpoint(host: String, port: Int): String = URI(
    "wss",
    null,
    host.removeSurrounding("[", "]"),
    port,
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

@Suppress("LongParameterList") // Keep transport injection separate from the public route observer.
fun createConnectionSession(
    connection: SavedConnection,
    onState: (SessionState) -> Unit,
    latestPayload: () -> PairingPayload = { connection.payload },
    tunnels: TunnelClientFactory = Tailcat,
    onRoute: (ConnectionPath) -> Unit = {},
    transport: ((WssTransport.Listener) -> SessionTransport)? = null
): RpcSession {
    val active = AtomicReference<HostRacingTransport>()
    return RpcSession(
        transport ?: { listener ->
            val routes = latestPayload().copy(
                token = connection.payload.token,
                certFingerprint = connection.payload.certFingerprint
            )
            HostRacingTransport(routes, listener, tunnels, onRoute = onRoute).also {
                active.set(it)
            }
        },
        buildJsonObject {
            put("clientId", "android-${connection.id}")
            put("name", "Intent Android")
            put("deviceKind", "phone")
        },
        onState = onState,
        verifyPrincipal = true,
        browserTransportFactory = { listener ->
            val endpoint = active.get()?.endpoint ?: throw IOException("No active connection route")
            pinnedTunnelTransport(
                endpoint.removeSuffix("/ws") + "/tunnel",
                connection.payload.token,
                requireNotNull(connection.payload.certFingerprint)
            )(listener)
        }
    )
}
