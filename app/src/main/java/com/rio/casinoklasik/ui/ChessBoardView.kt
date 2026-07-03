package com.rio.casinoklasik.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.rio.casinoklasik.engine.Chess
import kotlin.math.abs

/** Papan catur 8x8 yang dapat digambar dan disentuh. */
class ChessBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var flipped = false
        set(value) { field = value; invalidate() }

    var board: IntArray = IntArray(64)
        set(value) { field = value; invalidate() }

    var selected = -1
        set(value) { field = value; invalidate() }

    var legalTargets: Set<Int> = emptySet()
        set(value) { field = value; invalidate() }

    var lastFrom = -1
    var lastTo = -1
    var checkSq = -1
    var hintFrom = -1
    var hintTo = -1

    var onTap: ((Int) -> Unit)? = null

    private val light = Color.parseColor("#EED9B5")
    private val dark = Color.parseColor("#B58863")
    private val selCol = Color.parseColor("#88F2C94C")
    private val lastCol = Color.parseColor("#66F7EC9B")
    private val hintCol = Color.parseColor("#8864B5FF")
    private val checkCol = Color.parseColor("#99E53935")
    private val dotCol = Color.parseColor("#66145A32")

    private val sq = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hi = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotCol }
    private val pieceFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val pieceStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; isFakeBoldText = true
    }
    private val rect = RectF()

    private fun glyph(type: Int): String = when (type) {
        Chess.K -> "\u265A"; Chess.Q -> "\u265B"; Chess.R -> "\u265C"
        Chess.B -> "\u265D"; Chess.N -> "\u265E"; Chess.P -> "\u265F"; else -> ""
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    private fun squareAt(row: Int, col: Int): Int {
        val rank = if (flipped) row else 7 - row
        val file = if (flipped) 7 - col else col
        return rank * 8 + file
    }

    override fun onDraw(canvas: Canvas) {
        val cell = width / 8f
        pieceFill.textSize = cell * 0.74f
        pieceStroke.textSize = cell * 0.74f
        pieceStroke.strokeWidth = cell * 0.03f
        val fm = pieceFill.fontMetrics
        val textDy = (fm.ascent + fm.descent) / 2f

        for (row in 0..7) for (col in 0..7) {
            val x = col * cell; val y = row * cell
            val square = squareAt(row, col)
            sq.color = if ((row + col) % 2 == 0) light else dark
            canvas.drawRect(x, y, x + cell, y + cell, sq)

            if (square == lastFrom || square == lastTo) { hi.color = lastCol; canvas.drawRect(x, y, x + cell, y + cell, hi) }
            if (square == hintFrom || square == hintTo) { hi.color = hintCol; canvas.drawRect(x, y, x + cell, y + cell, hi) }
            if (square == selected) { hi.color = selCol; canvas.drawRect(x, y, x + cell, y + cell, hi) }
            if (square == checkSq) { hi.color = checkCol; canvas.drawRect(x, y, x + cell, y + cell, hi) }

            if (legalTargets.contains(square)) {
                val cx = x + cell / 2f; val cy = y + cell / 2f
                if (board[square] != 0) {
                    dot.style = Paint.Style.STROKE; dot.strokeWidth = cell * 0.08f
                    canvas.drawCircle(cx, cy, cell * 0.42f, dot)
                    dot.style = Paint.Style.FILL
                } else {
                    canvas.drawCircle(cx, cy, cell * 0.16f, dot)
                }
            }

            val pc = board[square]
            if (pc != 0) {
                val g = glyph(abs(pc))
                val cx = x + cell / 2f
                val cy = y + cell / 2f - textDy
                if (pc > 0) {
                    pieceFill.color = Color.WHITE
                    pieceStroke.color = Color.parseColor("#33270A")
                } else {
                    pieceFill.color = Color.parseColor("#1A1A1A")
                    pieceStroke.color = Color.parseColor("#E8E8E8")
                }
                canvas.drawText(g, cx, cy, pieceStroke)
                canvas.drawText(g, cx, cy, pieceFill)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val cell = width / 8f
            if (cell <= 0f) return true
            val col = (event.x / cell).toInt().coerceIn(0, 7)
            val row = (event.y / cell).toInt().coerceIn(0, 7)
            onTap?.invoke(squareAt(row, col))
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
