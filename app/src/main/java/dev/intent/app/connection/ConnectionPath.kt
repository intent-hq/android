package dev.intent.app.connection

import java.security.MessageDigest
import java.util.Locale

/** Public route identity never holds a Tailcat capability or a loopback forwarding port. */
data class ConnectionPath(val address: String, val type: String) {
    val badge: String get() = if (type == "Tunnel") "Tunnel" else "$type · $address"
}

internal fun directPath(host: String, port: Int): ConnectionPath {
    val normalized = host.removeSurrounding("[", "]").lowercase(Locale.ROOT)
    val type = when {
        isVpnAddress(normalized) -> "Direct VPN"
        isLanAddress(normalized) -> "Direct LAN"
        else -> "Direct"
    }
    val address = if (':' in normalized) "[$normalized]:$port" else "$normalized:$port"
    return ConnectionPath(address, type)
}

// These ranges identify address kinds, not the device's physical network interface.
@Suppress("MagicNumber") // Standard CGNAT and Tailscale address ranges.
private fun isVpnAddress(host: String): Boolean {
    val octets = host.split('.').map { it.toIntOrNull() }
    return (octets.size == 4 && octets[0] == 100 && octets[1] in 64..127) ||
        host.startsWith("fd7a:115c:a1e0:") ||
        host.endsWith(".ts.net")
}

@Suppress("MagicNumber") // RFC 1918 private IPv4 ranges.
private fun isLanAddress(host: String): Boolean {
    val octets = host.split('.').map { it.toIntOrNull() }
    val privateIpv4 = octets.size == 4 &&
        (
            octets[0] == 10 ||
                (octets[0] == 192 && octets[1] == 168) ||
                (octets[0] == 172 && octets[1] in 16..31)
            )
    return privateIpv4 || host.startsWith("fe80:") || host.endsWith(".local")
}

private const val ROUTE_DIGEST_BYTES = 6

internal fun tunnelPath(capability: String): ConnectionPath {
    val digest = MessageDigest.getInstance("SHA-256").digest(capability.toByteArray())
        .take(ROUTE_DIGEST_BYTES).joinToString("") { "%02x".format(it) }
    return ConnectionPath("Tailcat $digest (token hidden)", "Tunnel")
}

internal fun SavedConnection.knownPaths(): List<ConnectionPath> =
    payload.hosts.map { directPath(it, payload.port) }.distinct() +
        listOfNotNull(payload.tcAddress?.let(::tunnelPath))

internal fun ConnectionState.pathInUse(id: String): ConnectionPath? =
    activePath.takeIf { activeId == id && status == "Connected" }
