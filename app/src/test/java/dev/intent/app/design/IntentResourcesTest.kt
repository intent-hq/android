package dev.intent.app.design

import android.graphics.Bitmap
import android.graphics.Canvas
import dev.intent.app.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IntentResourcesTest {
    @Test
    fun `all avatar vectors render visible artwork`() {
        val context = RuntimeEnvironment.getApplication()
        AgentAvatarDesign.entries.forEach { design ->
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val drawable = requireNotNull(context.getDrawable(design.drawable))
            drawable.setBounds(0, 0, 96, 96)
            drawable.draw(Canvas(bitmap))
            val pixels = IntArray(96 * 96)
            bitmap.getPixels(pixels, 0, 96, 0, 0, 96, 96)
            assertTrue(design.wireName, pixels.count { it != 0 } > 50)
        }
    }

    @Test
    fun `bundled fonts and launcher icon load offline`() {
        val resources = RuntimeEnvironment.getApplication().resources
        listOf(
            R.font.inter_regular,
            R.font.inter_medium,
            R.font.inter_semi_bold,
            R.font.inter_italic
        ).forEach {
            assertNotNull(resources.getFont(it))
        }
        assertNotNull(resources.getDrawable(R.mipmap.ic_launcher, null))
        assertTrue(
            resources.openRawResource(R.raw.inter_license).bufferedReader().use {
                it.readText()
            }.contains("SIL")
        )
    }
}
