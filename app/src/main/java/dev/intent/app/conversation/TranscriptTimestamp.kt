package dev.intent.app.conversation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/** Use the reader's local date and time; never fall back to raw wire metadata. */
internal fun transcriptTimestamp(
    value: String,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val instant = try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        return "Time unavailable"
    }
    val local = instant.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val time = local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    return when (local.toLocalDate()) {
        today -> time
        today.minusDays(1) -> "Yesterday · $time"
        else -> local.format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
        )
    }
}
