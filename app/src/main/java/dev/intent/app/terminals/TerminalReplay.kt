package dev.intent.app.terminals

import java.util.Base64

/** Byte offsets belong to a single daemon boot and terminal. Never compare output text for overlap. */
class TerminalReplay {
    var next: ULong? = null
        private set
    var boot: String? = null
        private set

    fun reset(bootId: String) {
        boot = bootId
        next = null
    }

    @Suppress("ReturnCount")
    fun accept(range: TerminalRange, snapshot: Boolean): ReplayResult {
        if (range.boot != null && range.boot != boot) return ReplayResult()
        val bytes = Base64.getDecoder().decode(range.encoded)
        val start = range.start?.toULongOrNull()
        val end = range.end?.toULongOrNull()
        if (start == null || end == null || range.boot == null) {
            require(range.start == null && range.end == null && range.boot == null)
            next = null
            return ReplayResult(bytes, reset = snapshot, legacy = true)
        }
        require(end >= start && end - start == bytes.size.toULong())
        val position = next
        if (position == null) {
            if (!snapshot) return ReplayResult(gap = true)
            next = end
            return ReplayResult(bytes, reset = true, truncated = start > 0u)
        }
        if (end <= position) return ReplayResult()
        if (start > position) {
            if (!snapshot) return ReplayResult(gap = true)
            next = end
            return ReplayResult(bytes, reset = true, truncated = true)
        }
        next = end
        return ReplayResult(bytes.copyOfRange((position - start).toInt(), bytes.size))
    }
}

data class TerminalRange(
    val encoded: String,
    val boot: String?,
    val start: String?,
    val end: String?
)
data class ReplayResult(
    val bytes: ByteArray = byteArrayOf(),
    val reset: Boolean = false,
    val gap: Boolean = false,
    val truncated: Boolean = false,
    val legacy: Boolean = false
)
