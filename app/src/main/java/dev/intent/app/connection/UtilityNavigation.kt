package dev.intent.app.connection

/** Keep each caller; selecting an existing destination returns to that stack entry. */
internal fun List<String>.openUtility(destination: String): List<String> {
    val existing = indexOf(destination)
    return if (existing >= 0) take(existing + 1) else this + destination
}
