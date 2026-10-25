package dev.intent.core.protocol.session

import dev.intent.core.protocol.reconnectDelayMillis

/** Short-lived connections retain backoff. A stable connection starts a new retry sequence. */
internal fun recoveryAttempt(lifetimeMillis: Long?, previous: Int): Int = when {
    lifetimeMillis == null -> previous
    lifetimeMillis >= STABLE_MILLIS -> 0
    else -> maxOf(previous, 1)
}

internal fun recoveryDelayMillis(attempt: Int): Long =
    if (attempt == 0) 0 else reconnectDelayMillis(attempt - 1)

private const val STABLE_MILLIS = 10_000L
