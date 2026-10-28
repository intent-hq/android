package dev.intent.app.terminals

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.InputType
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.KeyHandler
import com.termux.view.TerminalRenderer

/** Native Canvas and IME adapter. Touch gestures scroll history; mouse reporting stays off. */
@Suppress("TooManyFunctions", "MagicNumber")
class TerminalCanvas(context: Context) : View(context) {
    var terminal = TerminalEngine()
    var send: (String) -> Unit = {}
    var resize: (Int, Int) -> Unit = { _, _ -> }
    var inputEnabled = false
    var control = false
    var controlChanged: () -> Unit = {}
    private var topRow = 0
    private var scrollRemainder = 0f
    private var renderer = TerminalRenderer(fontPixels(12), Typeface.MONOSPACE)
    private val gestures =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent) = true
                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    performClick()
                    showKeyboard()
                    return true
                }
                override fun onScroll(
                    first: MotionEvent?,
                    last: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    terminal.read {
                        scrollRemainder += distanceY / renderer.fontLineSpacing
                        val delta = scrollRemainder.toInt()
                        scrollRemainder -= delta
                        topRow = (topRow + delta).coerceIn(-it.screen.activeTranscriptRows, 0)
                    }
                    invalidate()
                    return true
                }
                override fun onLongPress(event: MotionEvent) {
                    android.widget.PopupMenu(context, this@TerminalCanvas).apply {
                        menu.add("Copy output").setOnMenuItemClickListener {
                            copyOutput()
                            true
                        }
                        menu.add("Paste").setOnMenuItemClickListener {
                            paste()
                            true
                        }
                        show()
                    }
                }
            }
        )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = "Workspace terminal"
        setBackgroundColor(android.graphics.Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        terminal.read {
            if (topRow < 0) topRow -= it.scrollCounter
            it.clearScrollCounter()
            topRow =
                if (it.isAlternateBufferActive) {
                    0
                } else {
                    topRow.coerceIn(
                        -it.screen.activeTranscriptRows,
                        0
                    )
                }
            renderer.render(it, canvas, topRow, -1, -1, -1, -1)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateSize()
    }

    fun fontSize(size: Int) {
        renderer = TerminalRenderer(fontPixels(size), Typeface.MONOSPACE)
        updateSize()
        invalidate()
    }

    private fun fontPixels(size: Int) = (size * resources.displayMetrics.scaledDensity).toInt()

    private fun updateSize() {
        if (width > 0 && height > 0) {
            val (columns, rows) = TerminalKeys.size(
                width,
                height,
                renderer.fontWidth,
                renderer.fontLineSpacing
            )
            resize(columns, rows)
        }
    }

    fun input(text: String) {
        if (!inputEnabled) return
        topRow = 0
        send(TerminalKeys.text(text, control))
        control = false
        controlChanged()
    }

    fun arrow(final: Char) = sequence(
        terminal.read {
            TerminalKeys.arrow(final, it.isCursorKeysApplicationMode)
        }
    )

    private fun sequence(text: String) {
        if (inputEnabled) {
            topRow = 0
            send(text)
        }
    }

    fun toggleKeyboard() {
        val manager = context.getSystemService(InputMethodManager::class.java)
        if (androidx.core.view.ViewCompat.getRootWindowInsets(this)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        ) {
            manager.hideSoftInputFromWindow(windowToken, 0)
        } else {
            showKeyboard()
        }
    }

    fun showKeyboard() {
        if (inputEnabled) {
            requestFocus()
            context.getSystemService(
                InputMethodManager::class.java
            ).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun copyOutput() {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Terminal output", terminal.text()))
    }

    fun paste() {
        if (!inputEnabled) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let(terminal::paste)
    }

    override fun performClick(): Boolean = super.performClick()
    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)
    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, false) {
            private var composing = ""
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                composing = text?.toString().orEmpty()
                return true
            }
            override fun finishComposingText(): Boolean {
                if (composing.isNotEmpty()) input(composing)
                composing = ""
                return true
            }
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                composing = ""
                input(text?.toString().orEmpty())
                return true
            }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (composing.isNotEmpty()) {
                    composing = composing.dropLast(beforeLength.coerceAtLeast(0))
                    return true
                }
                repeat(beforeLength.coerceIn(0, 128)) { input("\u007f") }
                repeat(afterLength.coerceIn(0, 128)) { input("\u001b[3~") }
                return true
            }
            override fun sendKeyEvent(event: KeyEvent): Boolean = dispatchKeyEvent(event)
            override fun performEditorAction(actionCode: Int): Boolean {
                input("\r")
                return true
            }
        }
    }

    @Suppress("ReturnCount")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!inputEnabled ||
            keyCode == KeyEvent.KEYCODE_BACK
        ) {
            return super.onKeyDown(keyCode, event)
        }
        val code = terminal.read {
            KeyHandler.getCode(
                keyCode,
                keyModifiers(event),
                it.isCursorKeysApplicationMode,
                it.isKeypadApplicationMode
            )
        }
        if (code != null) {
            sequence(code)
            return true
        }
        val unicode = event.getUnicodeChar(event.metaState and KeyEvent.META_CTRL_MASK.inv())
        if (unicode != 0) {
            control = control || event.isCtrlPressed
            input(String(Character.toChars(unicode)))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun keyModifiers(event: KeyEvent): Int =
        (if (event.isCtrlPressed) KeyHandler.KEYMOD_CTRL else 0) or
            (if (event.isAltPressed) KeyHandler.KEYMOD_ALT else 0) or
            (if (event.isShiftPressed) KeyHandler.KEYMOD_SHIFT else 0)
}
