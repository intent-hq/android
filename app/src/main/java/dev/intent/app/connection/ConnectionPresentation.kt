package dev.intent.app.connection

/** Only daemon-reported names are names; DNS guesses can misidentify a computer. */
internal val SavedConnection.displayName: String
    get() = metadata.name?.trim()?.takeIf { it.isNotEmpty() } ?: displayAddress

internal val SavedConnection.displayAddress: String
    get() {
        val host = payload.hosts.firstOrNull() ?: return "Tunnel connection ($id)"
        val address = if (':' in host) "[${host.removeSurrounding("[", "]")}]" else host
        return "$address:${payload.port}"
    }

internal fun connectionStatus(id: String, state: ConnectionState): String = when {
    state.activeId != id -> "Disconnected"
    state.status == "Connected" -> "Connected"
    state.status == "Connecting" -> "Connecting"
    state.status == "Connection lost. Retrying." -> "Disconnected · Retrying"
    else -> "Disconnected"
}
