package dev.intent.app.connection

import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.tailcat.TunnelClientFactory
import dev.intent.tailcat.TunnelUnavailableException

private const val PING_TIMEOUT_MILLIS = 5000
private const val MAX_PORT = 65535

internal const val TUNNEL_UNAVAILABLE_MESSAGE =
    "Tailcat is unavailable in this build. Use a host name or IP address from your desktop instead."

/** Fetches only the certificate: no bearer credentials are sent before the user confirms trust. */
internal fun probePairingCertificate(
    payload: PairingPayload,
    tunnels: TunnelClientFactory,
    probe: (String, Int) -> String
): String {
    val host = payload.hosts.firstOrNull()
    if (host != null) return probe(host.removeSurrounding("[", "]"), payload.port)
    if (!tunnels.available) throw TunnelUnavailableException()
    return tunnels.createClient(requireNotNull(payload.tcAddress)).use { client ->
        client.ping(PING_TIMEOUT_MILLIS)
        client.forward(payload.port).use { forwarder ->
            require(forwarder.localPort in 1..MAX_PORT)
            probe("127.0.0.1", forwarder.localPort)
        }
    }
}
