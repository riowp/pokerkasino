package com.rio.casinoklasik.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Menggambar satu kartu domino dengan titik (pip) seperti dadu. */
class DominoTileView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var a = 0
    private var b = 0
    var vertical = false
    var highlight = false
        set(value) { field = value; invalidate() }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF8E1") }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.parseColor("#3E2723")
    }
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.parseColor("#8D6E63")
    }
    private val pip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#212121") }
    private val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.parseColor("#F2C94C")
    }

    private val rect = RectF()

    fun set(a: Int, b: Int, vertical: Boolean) {
        this.a = a; this.b = b; this.vertical = vertical
        invalidate()
    }

    private fun pattern(n: Int): List<Pair<Int, Int>> = when (n) {
        1 -> listOf(1 to 1)
        2 -> listOf(0 to 0, 2 to 2)
        3 -> listOf(0 to 0, 1 to 1, 2 to 2)
        4 -> listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2)
        5 -> listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2)
        6 -> listOf(0 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 2 to 2)
        else -> emptyList()
    }

    private fun drawHalf(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, value: Int) {
        val r = min(w, h) * 0.09f
        for ((col, row) in pattern(value)) {
            val cx = left + w * (0.25f + 0.25f * col)
            val cy = top + h * (0.25f + 0.25f * row)
            canvas.drawCircle(cx, cy, r, pip)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val pad = 4f
        rect.set(pad, pad, width - pad, height - pad)
        val radius = 10f
        canvas.drawRoundRect(rect, radius, radius, bg)
        canvas.drawRoundRect(rect, radius, radius, if (highlight) hl else border)

        if (vertical) {
            val midY = height / 2f
            canvas.drawLine(pad + 6, midY, width - pad - 6, midY, divider)
            drawHalf(canvas, pad, pad, width - 2 * pad, midY - pad, a)
            drawHalf(canvas, pad, midY, width - 2 * pad, midY - pad, b)
        } else {
            val midX = width / 2f
            canvas.drawLine(midX, pad + 6, midX, height - pad - 6, divider)
            drawHalf(canvas, pad, pad, midX - pad, height - 2 * pad, a)
            drawHalf(canvas, midX, pad, midX - pad, height - 2 * pad, b)
        }
    }
}
