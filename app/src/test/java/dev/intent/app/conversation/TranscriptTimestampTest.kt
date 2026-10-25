package dev.intent.app.conversation

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TranscriptTimestampTest {
    private val now = Instant.parse("2026-10-25T19:00:00Z")
    private val zone = ZoneId.of("America/Los_Angeles")

    @Test
    fun todayUsesLocalTimeAndAcceptsOffsetsAndFractions() {
        assertEquals("12:00 PM", format("2026-10-25T19:00:00.123456Z"))
        assertEquals("12:00 PM", format("2026-10-25T21:00:00+02:00"))
    }

    @Test
    fun yesterdayUsesTheLocalCalendarDate() {
        assertEquals("Yesterday · 11:30 PM", format("2026-10-25T06:30:00Z"))
    }

    @Test
    fun olderAndFutureMessagesKeepADate() {
        assertEquals("10/23/26, 12:00 PM", format("2026-10-23T19:00:00Z"))
        assertEquals("10/26/26, 12:00 PM", format("2026-10-26T19:00:00Z"))
    }

    @Test
    fun invalidAndMissingTimesNeverExposeWireText() {
        assertEquals("Time unavailable", format(""))
        assertEquals("Time unavailable", format("2026-10-25Tbad"))
        assertFalse(format("2026-10-25T19:00:00Z").contains("2026-"))
    }

    private fun format(value: String) =
        transcriptTimestamp(value, now, zone, Locale.US).replace('\u202f', ' ')
}
