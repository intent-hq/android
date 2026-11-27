@file:Suppress("MagicNumber") // iOS LaunchBackground gradient stops.

package dev.intentapp.android.connection

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen

/** iOS SplashArtView: the Intent mark twists once, then pulses on the launch gradient. */
@Composable
internal fun LaunchSplash(modifier: Modifier = Modifier) {
    val dark = IntentScreen.canvas.luminance() < DARK_LUMINANCE
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) ==
            0f
    }
    val twist by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.intent_mark_twist_256)
    )
    val pulse by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.intent_mark_pulse_256)
    )
    var twisted by remember { mutableStateOf(reduceMotion) }
    val twistProgress by animateLottieCompositionAsState(twist, isPlaying = !twisted)
    val pulseProgress by animateLottieCompositionAsState(
        pulse,
        isPlaying = twisted && !reduceMotion,
        iterations = LottieConstants.IterateForever
    )
    if (!twisted && twist != null && twistProgress == 1f) twisted = true
    val foreground = if (dark) Color.White else IntentScreen.text
    val tint = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, foreground.toArgb(), "**"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, foreground.toArgb(), "**")
    )
    Box(
        modifier
            .fillMaxSize()
            .background(if (dark) DarkBackground else LightBackground)
            .testTag("launch-splash")
            .semantics { contentDescription = "Connecting" },
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = if (twisted) pulse else twist,
            progress = { if (twisted) pulseProgress else twistProgress },
            dynamicProperties = tint,
            modifier = Modifier.size(LOGO_SIZE)
        )
    }
}

private val LOGO_SIZE = 100.dp
private const val DARK_LUMINANCE = 0.5f

// iOS Assets.xcassets/LaunchBackground.imageset gradients.
private val LightBackground = Brush.verticalGradient(
    0.029f to Color(0xFFF3F2F1),
    0.619f to Color(0xFFEBE0D0),
    1f to Color(0xFFE7F573)
)
private val DarkBackground = Brush.verticalGradient(
    0f to Color(0xFF515152),
    0.494f to Color(0xFFB5B49F),
    0.742f to Color(0xFFD3D99E),
    0.943f to Color(0xFFDDEC61)
)
