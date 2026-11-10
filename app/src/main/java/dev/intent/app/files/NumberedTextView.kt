package dev.intent.app.files

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.TextView

/** Draw only visible logical line numbers. The gutter is not part of copied text. */
internal class NumberedTextView(context: Context) : TextView(context) {
    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var starts = listOf(0)
    private val inset = (TEXT_INSET_DP * resources.displayMetrics.density).toInt()
    var gutterColor: Int = currentTextColor
    var gutterBackground: Int = android.graphics.Color.TRANSPARENT

    init {
        typeface = Typeface.MONOSPACE
        textSize = TEXT_SIZE_SP
        setTextIsSelectable(true)
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true
    }

    fun setLineStarts(lineStarts: List<Int>) {
        starts = lineStarts
        val gutter = paint.measureText(starts.size.toString()).toInt() + inset * 2
        setPadding(gutter + inset, inset, inset, inset)
    }

    override fun onDraw(canvas: Canvas) {
        val gutterWidth = paddingLeft - inset
        val saved = canvas.save()
        canvas.clipRect(scrollX + gutterWidth, scrollY, scrollX + width, scrollY + height)
        super.onDraw(canvas)
        canvas.restoreToCount(saved)
        val textLayout = layout ?: return
        gutterPaint.color = gutterBackground
        canvas.drawRect(
            scrollX.toFloat(),
            scrollY.toFloat(),
            (scrollX + gutterWidth).toFloat(),
            (scrollY + height).toFloat(),
            gutterPaint
        )
        gutterPaint.color = gutterColor
        gutterPaint.typeface = typeface
        gutterPaint.textSize = textSize
        gutterPaint.textAlign = Paint.Align.RIGHT
        val first = textLayout.getLineForVertical((scrollY - totalPaddingTop).coerceAtLeast(0))
        val last = textLayout.getLineForVertical(scrollY + height - totalPaddingTop)
        for (line in first..last) {
            val logical = starts.binarySearch(textLayout.getLineStart(line))
            if (logical >= 0) {
                canvas.drawText(
                    (logical + 1).toString(),
                    (scrollX + gutterWidth - inset).toFloat(),
                    (totalPaddingTop + textLayout.getLineBaseline(line)).toFloat(),
                    gutterPaint
                )
            }
        }
    }
}

private const val TEXT_SIZE_SP = 16f
private const val TEXT_INSET_DP = 8
