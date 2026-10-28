package dev.intent.app.status

import dev.intent.core.protocol.models.WireJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrMonitorPresentationTest {
    @Test fun urlsUseWireValueOrGithubFallbackAndRejectUnsafeSchemes() {
        assertEquals("https://github.com/intent-hq/android/pull/42", prUrl(row("")))
        assertEquals(
            "https://example.org/pr/42",
            prUrl(row(",\"url\":\"https://example.org/pr/42\""))
        )
        for (url in listOf(
            "javascript:alert(1)",
            "file:///tmp/secret",
            "intent://local",
            "https://",
            "https://user:secret@example.com"
        )) {
            assertNull(prUrl(row(",\"url\":\"$url\"")))
        }
    }

    @Test fun detailsIncludeSnapshotAndPendingCountsWithoutInventingMergeability() {
        val lines =
            prDetailLines(
                row(
                    """, "title":"Fix preview", "hasPendingChanges":true,
            "pendingChanges":["checks","reviews"], "lastSnapshot":{"state":"open",
            "checks":{"total":4,"passed":2,"failed":1,"pending":1},
            "approvals":{"have":1,"needed":2,"decision":"review_required"},
            "threads":{"unresolved":3},"mergeBlockedReason":"Review required"}"""
                )
            )
        assertEquals("Fix preview — open", lines.first())
        assertTrue(
            lines.any {
                "1/4 failing" in it &&
                    "1/2 approvals" in it &&
                    "3 unresolved threads" in it
            }
        )
        assertTrue("Merge blocked: Review required" in lines)
        assertEquals("2 pending changes", lines.last())
        assertEquals(
            listOf("intent-hq/android#42", "Merge status unavailable", "No pending changes"),
            prDetailLines(row(""))
        )
    }

    private fun row(extra: String) = WireJson.parseToJsonElement(
        """{"repo":"intent-hq/android","prNumber":42$extra}"""
    ).jsonObject
}
