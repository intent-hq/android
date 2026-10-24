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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
@Config(sdk = [35], qualifiers = "w390dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IntentPreviewTest {
    @Test
    fun `theme renders distinct light and dark surfaces`() {
        listOf(false, true).forEach { dark ->
            val expected = if (dark) 0xFF1C1C24.toInt() else 0xFFFFFFFF.toInt()
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
            org.robolectric.RuntimeEnvironment.setQualifiers("w390dp-h640dp-$mode-mdpi")
            listOf<Pair<String, @Composable () -> Unit>>(
                "avatars" to { IntentAvatarPreview() },
                "workspaces" to { IntentWorkspacePreview() },
                "agents" to { IntentAgentListPreview() }
            ).forEach { (name, content) ->
                val bitmap = render(content)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("$name $mode has text and artwork", pixels.toSet().size > 20)
                val output = File("build/outputs/design-previews/$name-$mode.png")
                output.parentFile.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun render(content: @Composable () -> Unit): Bitmap {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.setContent(content = content)
            shadowOf(Looper.getMainLooper()).idle()
            val view = activity.window.decorView
            view.measure(
                View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 390, 640)
            shadowOf(Looper.getMainLooper()).idle()
            return Bitmap.createBitmap(390, 640, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
