package dev.intent.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun AgentAvatarState.surface(dark: Boolean): Color = with(IntentColors) {
    when (this@surface) {
        AgentAvatarState.Idle ->
            if (dark) agentAvatarSurfaceDark else agentAvatarSurfaceLight
        AgentAvatarState.Running ->
            if (dark) agentAvatarSurfaceActiveDark else agentAvatarSurfaceActiveLight
        AgentAvatarState.Waiting ->
            if (dark) agentAvatarSurfaceWaitingDark else agentAvatarSurfaceWaitingLight
        AgentAvatarState.Failed ->
            if (dark) agentAvatarSurfaceFailedDark else agentAvatarSurfaceFailedLight
        AgentAvatarState.Completed ->
            if (dark) agentAvatarSurfaceCompletedDark else agentAvatarSurfaceCompletedLight
        else ->
            if (dark) agentAvatarSurfaceAttentionDark else agentAvatarSurfaceAttentionLight
    }
}

/** Reusable brand tile. Callers supply state; the glyph is decorative beside its state label. */
@Suppress("MagicNumber", "LongParameterList")
@Composable
fun AgentAvatar(
    agentId: String,
    modifier: Modifier = Modifier,
    specialist: String? = null,
    icon: String? = null,
    state: AgentAvatarState = AgentAvatarState.Idle,
    size: Dp = 32.dp
) {
    val dark = LocalIntentDarkTheme.current
    val design = AgentAvatarDesign.resolve(agentId, specialist, icon)
    val shape = RoundedCornerShape(size * 0.3f)
    val glyph = if (state == AgentAvatarState.Completed) {
        if (dark) {
            IntentColors.agentAvatarGlyphCompletedDark
        } else {
            IntentColors.agentAvatarGlyphCompletedLight
        }
    } else {
        IntentColors.agentAvatarGlyphLight
    }
    val ring = if (dark) {
        IntentColors.agentAvatarActiveStrokeStrongDark
    } else {
        IntentColors.agentAvatarActiveStrokeStrongLight
    }
    Box(
        modifier.size(size).background(state.surface(dark), shape)
            .then(
                if (state ==
                    AgentAvatarState.Running
                ) {
                    Modifier.border(1.dp, ring, shape)
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = state.label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(design.drawable),
            contentDescription = null,
            Modifier.size(size * 0.8f),
            tint = glyph
        )
    }
}
