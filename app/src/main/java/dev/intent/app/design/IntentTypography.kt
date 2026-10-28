@file:Suppress("MagicNumber") // Figma mobile typography tokens.

package dev.intent.app.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.intent.app.R

// The bundled iOS Regular and Medium faces are static 450 and 550 instances.
val Inter = FontFamily(
    Font(R.font.inter_thin, FontWeight(100)),
    Font(R.font.inter_thin_italic, FontWeight(100), FontStyle.Italic),
    Font(R.font.inter_extra_light, FontWeight(200)),
    Font(R.font.inter_extra_light_italic, FontWeight(200), FontStyle.Italic),
    Font(R.font.inter_light, FontWeight(300)),
    Font(R.font.inter_light_italic, FontWeight(300), FontStyle.Italic),
    Font(R.font.inter_regular, FontWeight(450)),
    Font(R.font.inter_italic, FontWeight(450), FontStyle.Italic),
    Font(R.font.inter_medium, FontWeight(550)),
    Font(R.font.inter_medium_italic, FontWeight(550), FontStyle.Italic),
    Font(R.font.inter_semi_bold, FontWeight(600)),
    Font(R.font.inter_semi_bold_italic, FontWeight(600), FontStyle.Italic),
    Font(R.font.inter_bold, FontWeight(700)),
    Font(R.font.inter_bold_italic, FontWeight(700), FontStyle.Italic),
    Font(R.font.inter_extra_bold, FontWeight(800)),
    Font(R.font.inter_extra_bold_italic, FontWeight(800), FontStyle.Italic),
    Font(R.font.inter_black, FontWeight(900)),
    Font(R.font.inter_black_italic, FontWeight(900), FontStyle.Italic)
)

private fun style(size: Int, weight: Int = 450, tracking: Float = -0.02f, leading: Float = 1.25f) =
    TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        letterSpacing = (size * tracking).sp,
        lineHeight = (size * leading).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

internal val IntentTypography = Typography(
    displayLarge = style(40, 600, -0.03f, 1.1f),
    displayMedium = style(40, 600, -0.03f, 1.1f),
    displaySmall = style(28, 600),
    headlineLarge = style(40, 600, -0.03f, 1.1f),
    headlineMedium = style(28, 600),
    headlineSmall = style(28, 600),
    titleLarge = style(28, 600),
    titleMedium = style(18, 550),
    titleSmall = style(16, 550),
    bodyLarge = style(16, 450, -0.01f, 1.32f),
    bodyMedium = style(14, 450),
    bodySmall = style(12, 450, -0.01f, 1.32f),
    labelLarge = style(16, 550),
    labelMedium = style(14, 550),
    labelSmall = style(12, 450, 0.02f)
)
