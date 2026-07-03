package com.rio.casinoklasik.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.floor
import kotlin.math.min

/**
 * Roda roulette Eropa (0-36) yang dapat berputar dan berhenti tepat pada
 * angka target. Pointer tetap di puncak (atas).
 */
class RouletteWheelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val order = intArrayOf(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
        10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    )
    private val red = setOf(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36)

    private val sector = 360f / order.size
    private var rotationDeg = 0f
    private var highlightIndex = -1

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#C9A227")
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A3B2E")
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2C94C")
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 6f
    }

    private val arc = RectF()
    private var animator: ValueAnimator? = null
    private var lastPocket = -1
    private var lastTickAt = 0L

    private fun colorFor(n: Int): Int = when {
        n == 0 -> Color.parseColor("#0B6E4F")
        red.contains(n) -> Color.parseColor("#C62828")
        else -> Color.parseColor("#212121")
    }

    /** Kantong yang saat ini di bawah pointer (puncak). */
    private fun pocketAtPointer(): Int {
        val pointerInWheel = ((270f - rotationDeg) % 360f + 360f) % 360f
        return (floor(pointerInWheel / sector).toInt()) % order.size
    }

    fun spinTo(
        resultNumber: Int,
        loops: Int,
        duration: Long,
        onTick: () -> Unit,
        onEnd: () -> Unit
    ) {
        animator?.cancel()
        highlightIndex = -1
        val idx = order.indexOf(resultNumber)
        val pocketCenter = idx * sector + sector / 2f
        val targetMod = ((270f - pocketCenter) % 360f + 360f) % 360f

        var finalRot = rotationDeg + loops * 360f
        val delta = ((targetMod - (finalRot % 360f)) + 360f) % 360f
        finalRot += delta

        lastPocket = pocketAtPointer()
        animator = ValueAnimator.ofFloat(rotationDeg, finalRot).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                rotationDeg = it.animatedValue as Float
                val p = pocketAtPointer()
                if (p != lastPocket) {
                    lastPocket = p
                    val now = System.currentTimeMillis()
                    if (now - lastTickAt >= 45L) {
                        lastTickAt = now
                        onTick()
                    }
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    rotationDeg = finalRot % 360f
                    highlightIndex = idx
                    invalidate()
                    onEnd()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - 8f
        textPaint.textSize = radius * 0.11f

        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)

        for (i in order.indices) {
            val startAngle = i * sector + rotationDeg
            fill.color = colorFor(order[i])
            canvas.drawArc(arc, startAngle, sector, true, fill)
            canvas.drawArc(arc, startAngle, sector, true, stroke)

            // Nomor
            val mid = Math.toRadians((startAngle + sector / 2f).toDouble())
            val tr = radius * 0.82f
            val tx = cx + (tr * Math.cos(mid)).toFloat()
            val ty = cy + (tr * Math.sin(mid)).toFloat()
            canvas.save()
            canvas.rotate(startAngle + sector / 2f + 90f, tx, ty)
            val fm = textPaint.fontMetrics
            canvas.drawText(order[i].toString(), tx, ty - (fm.ascent + fm.descent) / 2f, textPaint)
            canvas.restore()
        }

        // Sorot kantong pemenang
        if (highlightIndex >= 0) {
            val startAngle = highlightIndex * sector + rotationDeg
            canvas.drawArc(arc, startAngle, sector, true, highlightPaint)
        }

        // Hub tengah
        canvas.drawCircle(cx, cy, radius * 0.16f, hubPaint)
        canvas.drawCircle(cx, cy, radius * 0.16f, stroke)

        // Pointer tetap di puncak
        val path = Path()
        val py = cy - radius - 2f
        path.moveTo(cx, py + 26f)
        path.lineTo(cx - 16f, py - 6f)
        path.lineTo(cx + 16f, py - 6f)
        path.close()
        canvas.drawPath(path, pointerPaint)
    }
}
