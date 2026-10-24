package dev.intent.app.connection

/** Closed event vocabulary: raw errors, addresses, tokens, fingerprints and IDs cannot enter reports. */
enum class ConnectionEvent {
    CONNECTING,
    HANDSHAKING,
    READY,
    DISCONNECTED,
    CLOSED,
    HOSTS_REFRESHED,
    HOST_REFRESH_FAILED
}

class ConnectionDiagnostics {
    private val events = ArrayDeque<ConnectionEvent>()

    @Synchronized
    fun record(event: ConnectionEvent) {
        if (events.size == MAX_EVENTS) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun report(): String = buildString {
        appendLine("Intent Android connection diagnostics (under development)")
        appendLine("Credentials, addresses, identifiers and message content are excluded.")
        events.forEachIndexed { index, event -> appendLine("${index + 1}: ${event.name}") }
    }

    @Synchronized
    fun clear() = events.clear()

    private companion object {
        const val MAX_EVENTS = 200
    }
}
