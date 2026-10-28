package dev.intent.app.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** CPU-rendered preview evidence; no network, device, or daemon is required. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IntentPreviewTest {
    @Test
    fun `theme renders distinct light and dark surfaces`() {
        listOf(false, true).forEach { dark ->
            val expected = if (dark) 0xFF161616.toInt() else 0xFFF0F0F0.toInt()
            val bitmap = render {
                IntentTheme(darkTheme = dark) {
                    assertEquals(expected, MaterialTheme.colorScheme.background.toArgb())
                    assertEquals(Inter, MaterialTheme.typography.bodyLarge.fontFamily)
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                }
            }
            assertEquals(expected, bitmap.getPixel(100, 100))
        }
    }

    @Test
    fun `key screen previews render in both system modes`() {
        listOf("notnight", "night").forEach { mode ->
            org.robolectric.RuntimeEnvironment.setQualifiers("w390dp-h800dp-$mode-mdpi")
            listOf<Pair<String, @Composable () -> Unit>>(
                "avatars" to { IntentAvatarPreview() },
                "workspaces" to { IntentWorkspacePreview() },
                "agents" to { IntentAgentListPreview() },
                "components" to { IntentComponentsPreview() },
                "pairing" to { IntentPairingPreview() }
            ).forEach { (name, content) ->
                capturePreview(name, mode, content)
            }
        }
    }

    private fun capturePreview(name: String, mode: String, content: @Composable () -> Unit) {
        val bitmap = render(content = content)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("$name $mode has text and artwork", pixels.toSet().size > 20)
        val output = File("build/outputs/design-previews/$name-$mode.png")
        output.parentFile.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (name in setOf("components", "workspaces")) {
            val narrow = render(width = 320) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) { content() }
            }
            File(output.parentFile, "$name-$mode-narrow.png").outputStream().use {
                narrow.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private fun render(width: Int = 390, content: @Composable () -> Unit): Bitmap {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.setContent(content = content)
            shadowOf(Looper.getMainLooper()).idle()
            val view = activity.window.decorView
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, 800)
            shadowOf(Looper.getMainLooper()).idle()
            return Bitmap.createBitmap(width, 800, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
