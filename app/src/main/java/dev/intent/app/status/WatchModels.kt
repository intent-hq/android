package dev.intent.app.status

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.number(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull
internal fun JsonObject.objectValue(key: String): JsonObject? = get(key) as? JsonObject

enum class WatchFamily(val wire: String, val listKey: String, val idKey: String) {
    SCRIPT("scriptMonitor", "monitors", "monitorId"),
    HOOK("hook", "hooks", "hookId"),
    PR("prMonitor", "monitors", "monitorId")
}

data class WatchPage(val rows: List<JsonObject> = emptyList(), val nextCursor: String? = null)
data class WatchBucket(
    val page: WatchPage = WatchPage(),
    val loading: Boolean = false,
    val error: String? = null
)
data class WatchDetail(
    val summary: JsonObject,
    val detail: JsonObject? = null,
    val loading: Boolean = true,
    val unavailable: Boolean = false,
    val error: String? = null
)
data class WatchState(
    val buckets: Map<WatchFamily, WatchBucket> = emptyMap(),
    val selected: WatchDetail? = null
) {
    fun rows(family: WatchFamily, agentId: String): List<JsonObject> =
        buckets[family]?.page?.rows.orEmpty().filter {
            it.text("agentId") == agentId &&
                it.active(family)
        }

    fun isWaiting(agentId: String): Boolean = WatchFamily.entries.any {
        rows(it, agentId).isNotEmpty()
    }
}

internal fun JsonObject.active(family: WatchFamily): Boolean = when (family) {
    WatchFamily.HOOK -> text("state") in setOf("scheduled", "running")
    else -> text("state") == "active"
}

object WatchTime {
    private const val MINUTE = 60L
    private const val HOUR = 3600L
    const val DEADLINE = "Watch deadline reached; awaiting confirmation"
    private fun date(value: String?): Instant? =
        value?.let { runCatching { Instant.parse(it) }.getOrNull() }
    fun duration(seconds: Long): String = when {
        seconds >= HOUR -> "${seconds / HOUR}h ${seconds % HOUR / MINUTE}m"
        seconds >= MINUTE -> "${seconds / MINUTE}m ${seconds % MINUTE}s"
        else -> "${seconds.coerceAtLeast(0)}s"
    }
    fun expiry(value: String?, now: Instant): String {
        val end = date(value) ?: return "Expiry unavailable"
        return if (end <=
            now
        ) {
            DEADLINE
        } else {
            "Watch expires in ${duration(end.epochSecond - now.epochSecond)}"
        }
    }
    fun absolute(value: String?): String = date(value)?.let {
        DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM
        ).withZone(ZoneId.systemDefault()).format(it)
    } ?: "Time unavailable"

    fun elapsed(value: String?, now: Instant): String = date(value)?.let {
        "Watching for ${duration(now.epochSecond - it.epochSecond)}"
    } ?: "Elapsed time unavailable"
    fun next(value: String?, now: Instant): String = date(value)?.let {
        if (it <=
            now
        ) {
            "Next run due"
        } else {
            "Next run in ${duration(it.epochSecond - now.epochSecond)}"
        }
    } ?: "Next run unavailable"
    fun fraction(value: String?, delayMs: Long, now: Instant): Float {
        val end = date(value)?.takeIf { delayMs > 0 } ?: return 0f
        return ((end.toEpochMilli() - now.toEpochMilli()).toDouble() / delayMs).coerceIn(
            0.0,
            1.0
        ).toFloat()
    }
}

internal fun prStatus(row: JsonObject): String {
    val snapshot = row.objectValue("lastSnapshot") ?: return "Merge status unavailable"
    val reason = snapshot.text("mergeBlockedReason")
    return when {
        !reason.isNullOrBlank() -> "Merge blocked: $reason"
        snapshot.text(
            "state"
        ) in setOf("merged", "closed", "MERGED", "CLOSED") -> snapshot.text("state")!!.lowercase()
        snapshot.text("mergeable") == "true" -> "Mergeable"
        snapshot.text("mergeable") == "false" -> "Not mergeable"
        else -> "Merge status unavailable"
    }
}
