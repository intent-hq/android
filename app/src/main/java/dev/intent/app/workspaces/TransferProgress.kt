package dev.intent.app.workspaces

import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TransferPhase(val busy: Boolean, val cancellable: Boolean = false) {
    CHECKING(true, true),
    EXPORTING(true, true),
    IMPORTING(true, true),
    COMMITTING(true),
    FINALIZING(true),
    CANCELLING(true),
    CANCELLED(false),
    FAILED(false),
    UNCERTAIN(false),
    DONE(false)
}

data class TransferProgress(
    val phase: TransferPhase = TransferPhase.CHECKING,
    val message: String = "Checking the destination…",
    val receivedBytes: Long = 0,
    val totalBytes: Long? = null
) {
    val fraction: Float? get() = totalBytes?.takeIf { it > 0 }?.let {
        (receivedBytes.toDouble() / it).toFloat().coerceIn(0f, 1f)
    }
}

/** Cancellation and the commit boundary share one lock. RPCs are never interrupted. */
class TransferControl {
    private val mutable = MutableStateFlow(TransferProgress())
    val state = mutable.asStateFlow()
    private var cancelled = false

    @Synchronized fun cancel(): Boolean {
        if (!mutable.value.phase.cancellable) return false
        cancelled = true
        mutable.value = mutable.value.copy(
            phase = TransferPhase.CANCELLING,
            message = "Cancelling the transfer… Waiting for the current request and cleanup."
        )
        return true
    }

    @Synchronized internal fun checkCancellation() {
        if (cancelled) throw CancellationException("Transfer cancelled")
    }

    @Synchronized internal fun update(progress: TransferProgress) {
        if (!cancelled && mutable.value.phase.busy) mutable.value = progress
    }

    @Synchronized internal fun beginCommit() {
        checkCancellation()
        check(mutable.value.phase == TransferPhase.IMPORTING)
        mutable.value = mutable.value.copy(
            phase = TransferPhase.COMMITTING,
            message = "Finishing the transfer on the destination…"
        )
    }

    @Synchronized internal fun finish(phase: TransferPhase, message: String) {
        check(!phase.busy)
        mutable.value = mutable.value.copy(phase = phase, message = message)
    }
}

internal fun exportStageMessage(stage: String?): String = when (stage) {
    "stopping-agents" -> "Stopping running agents…"
    "exporting-rows" -> "Preparing workspace data…"
    "bundling-git" -> "Preparing repository files…"
    "writing-archive" -> "Building the transfer archive…"
    else -> "Preparing the transfer…"
}
