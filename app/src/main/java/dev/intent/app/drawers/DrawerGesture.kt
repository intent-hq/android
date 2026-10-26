package dev.intent.app.drawers

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange

/** The original down position reserves system edges. Consumed child movement cancels this pan. */
internal suspend fun PointerInputScope.drawerGestures(
    leftInset: Int,
    rightInset: Int,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.position.x <= leftInset ||
            down.position.x >= size.width - rightInset
        ) {
            return@awaitEachGesture
        }
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            onDrag(over)
        }
        if (start != null) {
            val finished = horizontalDrag(start.id) { change ->
                onDrag(change.positionChange().x)
                change.consume()
            }
            if (finished) onEnd() else onCancel()
        }
    }
}
