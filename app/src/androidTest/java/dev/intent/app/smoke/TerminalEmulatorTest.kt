package dev.intent.app.smoke

import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.termux.terminal.TextStyle
import dev.intent.app.terminals.TerminalCanvas
import dev.intent.app.terminals.TerminalEngine
import dev.intent.app.terminals.TerminalRange
import dev.intent.app.terminals.TerminalReplay
import dev.intent.app.testing.createIntentComposeRule
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalEmulatorTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test fun capturedFullScreenAppsRestoreShell() {
        val engine = TerminalEngine()
        var canvas: TerminalCanvas? = null
        compose.setContent {
            AndroidView(
                factory = { context ->
                    TerminalCanvas(context).apply {
                        terminal = engine
                        fontSize(8)
                        canvas = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        for (name in listOf("vim", "less", "htop")) {
            engine.reset()
            engine.feed("shell$ ".toByteArray())
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets
                .open("terminal/$name.ansi").use { it.readBytes() }
            val replay = TerminalReplay().apply { reset("boot") }
            val result = replay.accept(
                TerminalRange(
                    Base64.getEncoder().encodeToString(bytes),
                    "boot",
                    "0",
                    bytes.size.toString()
                ),
                true
            )
            engine.feed(result.bytes)
            engine.read {
                assertTrue("$name entered alternate screen", it.isAlternateBufferActive)
                if (name == "htop") {
                    assertTrue(it.screen.transcriptText.contains("sleep"))
                    assertTrue(it.screen.transcriptText.contains("PID"))
                } else {
                    assertTrue(it.getSelectedText(0, 0, 79, 0).contains("Intent terminal fixture"))
                    assertTrue(it.getSelectedText(0, 10, 79, 10).contains("row 10"))
                }
            }
            compose.runOnIdle { canvas!!.invalidate() }
            screenshot("terminal-$name")
            engine.feed("\u001b[?1049l".toByteArray())
            assertEquals("shell$", engine.text().trim())
        }
    }

    @Test fun nativeKeyboardColorsAndResize() {
        val writes = mutableListOf<String>()
        val engine = TerminalEngine(writes::add)
        lateinit var canvas: TerminalCanvas
        compose.setContent {
            AndroidView(
                factory = { context ->
                    TerminalCanvas(context).apply {
                        terminal = engine
                        send = writes::add
                        inputEnabled = true
                        resize = engine::resize
                        canvas = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        compose.runOnIdle {
            engine.feed(
                (
                    "\u001b[?1049h\u001b[2J\u001b[H\u001b[38;5;196mRED\u001b[0m 256 colors\r\n" +
                        "\u001b[38;2;12;200;56mTRUE COLOR\u001b[0m\r\nUnicode: 色 é"
                    ).toByteArray()
            )
            engine.read {
                assertEquals(196, TextStyle.decodeForeColor(it.screen.getStyleAt(0, 0)))
                assertEquals(
                    0xff0cc838.toInt(),
                    TextStyle.decodeForeColor(it.screen.getStyleAt(1, 0))
                )
                assertTrue(it.mColumns > 2)
                assertTrue(it.mRows > 2)
            }
            val input = canvas.onCreateInputConnection(EditorInfo())
            input.setComposingText("ni", 1)
            assertTrue(writes.isEmpty())
            input.commitText("你", 1)
            input.deleteSurroundingText(1, 0)
            input.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            canvas.control = true
            input.commitText("c", 1)
            engine.feed("\u001b[?1h".toByteArray())
            canvas.arrow('A')
            engine.feed("\u001b[?2004h".toByteArray())
            engine.paste("paste\ntext")
            assertEquals(
                listOf(
                    "你",
                    "\u007f",
                    "\r",
                    "\u0003",
                    "\u001bOA",
                    "\u001b[200~paste\rtext\u001b[201~"
                ),
                writes
            )
            canvas.invalidate()
        }
        screenshot("terminal-colors-input")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name-dark.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
