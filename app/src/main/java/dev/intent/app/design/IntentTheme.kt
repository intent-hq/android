package dev.intent.app.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

internal val LocalIntentDarkTheme = staticCompositionLocalOf { false }

/** Shared mobile layout spacing. */
object IntentSpacing {
    val small = 4.dp
    val medium = 8.dp
    val row = 12.dp
    val content = 16.dp
    val page = 24.dp
}

internal val IntentLightColors = with(IntentColors) {
    lightColorScheme(
        primary = themePrimaryLight,
        onPrimary = themeForegroundLight,
        primaryContainer = themeMutedLight,
        onPrimaryContainer = themeForegroundLight,
        secondary = themeMutedForegroundLight,
        onSecondary = themeBackgroundLight,
        secondaryContainer = themeSidebarSelectionLight,
        onSecondaryContainer = themeForegroundLight,
        tertiary = themePrimaryLight,
        onTertiary = themeForegroundLight,
        tertiaryContainer = agentAvatarSurfaceCompletedLight,
        onTertiaryContainer = agentAvatarGlyphCompletedLight,
        background = themeBackgroundLight,
        onBackground = themeForegroundLight,
        surface = themeCardLight,
        onSurface = themeForegroundLight,
        surfaceVariant = themeMutedLight,
        onSurfaceVariant = themeMutedForegroundLight,
        surfaceTint = themeCardLight,
        surfaceDim = themeMutedLight,
        surfaceBright = themeBackgroundLight,
        surfaceContainerLowest = themeBackgroundLight,
        surfaceContainerLow = themeSidebarLight,
        surfaceContainer = themeSidebarLight,
        surfaceContainerHigh = themeMutedLight,
        surfaceContainerHighest = themeSidebarSelectionLight,
        outline = themeBorderLight,
        outlineVariant = themeBorderLight,
        error = themeDestructiveLight,
        onError = themeCardLight,
        errorContainer = agentAvatarSurfaceFailedLight,
        onErrorContainer = themeForegroundLight,
        inverseSurface = themeBackgroundDark,
        inverseOnSurface = themeForegroundDark,
        inversePrimary = themePrimaryDark
    )
}

internal val IntentDarkColors = with(IntentColors) {
    darkColorScheme(
        primary = themePrimaryDark,
        onPrimary = agentAvatarGlyphDark,
        primaryContainer = themeSidebarSelectionDark,
        onPrimaryContainer = themeForegroundDark,
        secondary = themeMutedForegroundDark,
        onSecondary = themeBackgroundDark,
        secondaryContainer = themeSidebarSelectionDark,
        onSecondaryContainer = themeForegroundDark,
        tertiary = themePrimaryDark,
        onTertiary = agentAvatarGlyphDark,
        tertiaryContainer = agentAvatarSurfaceCompletedDark,
        onTertiaryContainer = agentAvatarGlyphCompletedDark,
        background = themeBackgroundDark,
        onBackground = themeForegroundDark,
        surface = themeCardDark,
        onSurface = themeForegroundDark,
        surfaceVariant = themeMutedDark,
        onSurfaceVariant = themeMutedForegroundDark,
        surfaceTint = themeCardDark,
        surfaceDim = themeSidebarDark,
        surfaceBright = themeSidebarSelectionDark,
        surfaceContainerLowest = themeMutedDark,
        surfaceContainerLow = themeCardDark,
        surfaceContainer = themeBackgroundDark,
        surfaceContainerHigh = themeSidebarSelectionDark,
        surfaceContainerHighest = themeSidebarSelectionDark,
        outline = themeBorderDark,
        outlineVariant = themeBorderDark,
        error = themeDestructiveDark,
        onError = themeForegroundDark,
        errorContainer = agentAvatarSurfaceFailedDark,
        onErrorContainer = agentAvatarGlyphDark,
        inverseSurface = themeBackgroundLight,
        inverseOnSurface = themeForegroundLight,
        inversePrimary = themePrimaryLight
    )
}

private val IntentShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/** Fixed brand palette; follows system night mode unless a preview supplies an override. */
@Composable
fun IntentTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIntentDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) IntentDarkColors else IntentLightColors,
            typography = IntentTypography,
            shapes = IntentShapes,
            content = content
        )
    }
}
