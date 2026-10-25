package dev.intent.app.connection

/** Display identity uses hosts only; pairing credentials never enter UI labels. */
internal val SavedConnection.displayName: String
    get() {
        val host = payload.hosts.firstOrNull { candidate ->
            ':' !in candidate && candidate.any(Char::isLetter)
        } ?: return "Saved computer"
        return host.substringBefore('.').replace('-', ' ').replace('_', ' ')
            .replaceFirstChar { it.titlecase() }
    }

internal val SavedConnection.displayAddress: String
    get() {
        val host = payload.hosts.firstOrNull() ?: return "Tunnel connection"
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
