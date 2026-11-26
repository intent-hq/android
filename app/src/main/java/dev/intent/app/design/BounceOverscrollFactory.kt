package dev.intent.app.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign

/** iOS-style rubber band: content follows a drag past the edge with resistance, then springs back. */
internal object BounceOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = BounceOverscrollEffect()

    override fun hashCode(): Int = javaClass.hashCode()

    override fun equals(other: Any?): Boolean = other === this
}

private class BounceOverscrollEffect : OverscrollEffect {
    private var offset by mutableStateOf(Offset.Zero)
    private var limit = DEFAULT_LIMIT

    override val isInProgress: Boolean get() = offset != Offset.Zero

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        val release = Offset(release(delta.x, offset.x), release(delta.y, offset.y))
        offset += release
        val remaining = delta - release
        val consumed = performScroll(remaining)
        val leftover = remaining - consumed
        if (source != NestedScrollSource.UserInput ||
            leftover == Offset.Zero
        ) {
            return release + consumed
        }
        offset += Offset(resisted(leftover.x, offset.x), resisted(leftover.y, offset.y))
        return delta
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) {
        val remaining = if (offset == Offset.Zero) performFling(velocity) else Velocity.Zero
        val animation = Animatable(offset, Offset.VectorConverter)
        animation.animateTo(
            Offset.Zero,
            spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
            Offset(remaining.x, remaining.y) * FLING_SHARE
        ) { offset = value }
    }

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            limit =
                maxOf(placeable.width, placeable.height).toFloat().takeIf { it > 0f }
                    ?: DEFAULT_LIMIT
            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(0, 0) {
                    translationX = offset.x
                    translationY = offset.y
                }
            }
        }
    }

    /** The part of a drag that moves an overscrolled list back toward rest, without crossing it. */
    private fun release(delta: Float, current: Float): Float = if (current != 0f &&
        sign(delta) == -sign(current)
    ) {
        sign(delta) * minOf(abs(delta), abs(current))
    } else {
        0f
    }

    private fun resisted(delta: Float, current: Float): Float =
        delta * RESISTANCE * (1f - minOf(abs(current) / (limit * STRETCH_LIMIT), 1f))
}

private const val RESISTANCE = 0.55f
private const val STRETCH_LIMIT = 0.5f
private const val FLING_SHARE = 0.05f
private const val DEFAULT_LIMIT = 2_000f
