package dev.intent.app.git

import androidx.compose.ui.graphics.luminance
import dev.intent.app.design.IntentDarkColors
import dev.intent.app.design.IntentLightColors
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffContrastTest {
    @Test
    fun diffTextMeetsNormalTextContrastOnBothThemeSurfaces() {
        listOf(false to IntentLightColors, true to IntentDarkColors).forEach { (dark, scheme) ->
            listOf("Addition", "Deletion", "Context").forEach { type ->
                listOf(scheme.surface, scheme.background).forEach { background ->
                    val foreground = diffLineColor(type, dark).luminance()
                    val backdrop = background.luminance()
                    val ratio = (maxOf(foreground, backdrop) + 0.05f) /
                        (minOf(foreground, backdrop) + 0.05f)
                    assertTrue("$type dark=$dark contrast=$ratio", ratio >= 4.5f)
                }
            }
        }
    }
}
