package dev.intent.app.workspaces

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferProgressTest {
    @Test fun cancelBeforeStartDoesNotContactEitherHost() {
        val control = TransferControl()
        assertTrue(control.cancel())
        assertFalse(control.cancel())
        val fixture = TransferFixture()
        assertThrows(CancellationException::class.java) { fixture.relay(control).run(true) }
        assertTrue(fixture.calls.isEmpty())
        assertEquals(TransferPhase.CANCELLED, control.state.value.phase)
    }

    @Test fun cancellationWaitsForStagingIdsThenAbortsOwnedSessions() {
        listOf("workspace.export.start", "workspace.import.begin", "workspace.import.chunk")
            .forEach { boundary ->
                val control = TransferControl()
                val fixture = TransferFixture(beforeReply = { method ->
                    if (method == boundary) {
                        assertTrue(control.cancel())
                        assertEquals(TransferPhase.CANCELLING, control.state.value.phase)
                    }
                })
                assertThrows(CancellationException::class.java) { fixture.relay(control).run(true) }
                assertEquals(TransferPhase.CANCELLED, control.state.value.phase)
                assertTrue(fixture.calls.contains("workspace.export.abort"))
                assertEquals(
                    boundary != "workspace.export.start",
                    fixture.calls.contains("workspace.import.abort")
                )
                assertFalse(fixture.calls.contains("workspace.import.commit"))
                assertFalse(fixture.calls.contains("workspace.export.finalize"))
                assertTrue(fixture.closed)
            }
    }

    @Test fun cancellationAfterReadNeverUploadsTheChunk() {
        val control = TransferControl()
        val fixture = TransferFixture(beforeReply = { method ->
            if (method == "workspace.export.read") control.cancel()
        })
        assertThrows(CancellationException::class.java) { fixture.relay(control).run(true) }
        assertFalse(fixture.calls.contains("workspace.import.chunk"))
        assertTrue(fixture.calls.contains("workspace.import.abort"))
    }

    @Test fun commitAndFinalizationRefuseCancellationAndCompleteOnce() {
        val control = TransferControl()
        val fixture = TransferFixture(beforeReply = { method ->
            when (method) {
                "workspace.import.commit" -> {
                    assertEquals(TransferPhase.COMMITTING, control.state.value.phase)
                    assertEquals(1f, control.state.value.fraction)
                    assertFalse(control.cancel())
                }
                "workspace.export.finalize" -> {
                    assertEquals(TransferPhase.FINALIZING, control.state.value.phase)
                    assertFalse(control.cancel())
                }
            }
        })
        val relay = fixture.relay(control)
        relay.run(true)
        assertEquals(TransferPhase.DONE, control.state.value.phase)
        assertFalse(control.cancel())
        assertThrows(IllegalStateException::class.java) { relay.run(true) }
        assertEquals(1, fixture.calls.count { it == "workspace.import.commit" })
    }

    @Test fun cancellationCannotBeOverwrittenByLateProgressOrCommit() {
        val control = TransferControl()
        control.update(TransferProgress(TransferPhase.IMPORTING, "Transferring", 1, 2))
        assertTrue(control.cancel())
        control.update(TransferProgress(TransferPhase.EXPORTING, "Late event"))
        assertEquals(TransferPhase.CANCELLING, control.state.value.phase)
        assertThrows(CancellationException::class.java) { control.beginCommit() }
    }

    @Test fun lostCommitIsUncertainAndNeverAbortedOrFinalized() {
        val control = TransferControl()
        val fixture = TransferFixture(failCommit = true)
        assertThrows(IllegalStateException::class.java) { fixture.relay(control).run(true) }
        assertEquals(TransferPhase.UNCERTAIN, control.state.value.phase)
        assertFalse(control.cancel())
        assertFalse(fixture.calls.contains("workspace.import.abort"))
        assertFalse(fixture.calls.contains("workspace.export.finalize"))
    }

    @Test fun unconfirmedCleanupIsFailureNotSuccessfulCancellation() {
        val control = TransferControl()
        val fixture = TransferFixture(beforeReply = { method ->
            if (method == "workspace.import.begin") control.cancel()
            if (method == "workspace.import.abort") error("Disconnected")
        })
        assertThrows(CancellationException::class.java) { fixture.relay(control).run(true) }
        assertEquals(TransferPhase.FAILED, control.state.value.phase)
        assertTrue(
            control.state.value.message.contains("cleanup was not confirmed on the destination")
        )
        assertTrue(fixture.calls.contains("workspace.export.abort"))
    }

    @Test fun lostStartReplyReportsUnconfirmedSourceCleanup() {
        val control = TransferControl()
        val fixture = TransferFixture(beforeReply = { method ->
            if (method == "workspace.export.start") error("Disconnected")
        })
        assertThrows(IllegalStateException::class.java) { fixture.relay(control).run(true) }
        assertEquals(TransferPhase.FAILED, control.state.value.phase)
        assertTrue(control.state.value.message.contains("cleanup was not confirmed on the source"))
    }

    @Test fun sourceFinalizationFailureKeepsConfirmedDestinationComplete() {
        val control = TransferControl()
        val fixture = TransferFixture(beforeReply = { method ->
            if (method == "workspace.export.finalize") error("Disconnected")
        })
        fixture.relay(control).run(true)
        assertEquals(TransferPhase.DONE, control.state.value.phase)
        assertTrue(control.state.value.message.contains("do not repeat the transfer"))
    }

    @Test fun progressHasDistinctExportStagesAndByteFraction() {
        assertEquals("Stopping running agents…", exportStageMessage("stopping-agents"))
        assertEquals("Preparing workspace data…", exportStageMessage("exporting-rows"))
        assertEquals("Preparing repository files…", exportStageMessage("bundling-git"))
        assertEquals("Building the transfer archive…", exportStageMessage("writing-archive"))
        assertEquals("Preparing the transfer…", exportStageMessage("unknown"))
        assertEquals(0.5f, TransferProgress(receivedBytes = 5, totalBytes = 10).fraction)
        assertEquals(null, TransferProgress().fraction)
        TransferPhase.entries.forEach { phase ->
            assertEquals(
                phase in
                    listOf(
                        TransferPhase.CHECKING,
                        TransferPhase.EXPORTING,
                        TransferPhase.IMPORTING
                    ),
                phase.cancellable
            )
        }
    }
}
