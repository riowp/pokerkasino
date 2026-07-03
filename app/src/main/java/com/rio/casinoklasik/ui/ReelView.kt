package com.rio.casinoklasik.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.floor

/**
 * Menampilkan satu simbol di tengah dan bisa "diputar" menggulir ke simbol target
 * dengan deselerasi mulus. Simbol digambar sebagai teks (emoji) di canvas.
 */
class ReelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var symbols: List<String> = listOf("\uD83C\uDF52", "\uD83C\uDF4B", "\uD83D\uDD14")
        set(value) { field = value; invalidate() }

    private var offset = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#212121")
    }
    private var animator: ValueAnimator? = null

    private fun wrap(i: Int): Int {
        val s = symbols.size
        return ((i % s) + s) % s
    }

    /** Indeks simbol yang saat ini di tengah. */
    val centerIndex: Int get() = wrap(Math.round(offset))

    /**
     * Putar hingga simbol [target] di tengah.
     * [loops] jumlah putaran penuh sebelum berhenti, [duration] ms.
     */
    fun spinTo(target: Int, loops: Int, duration: Long, onEnd: () -> Unit) {
        animator?.cancel()
        val start = offset
        val baseInt = floor(start).toInt()
        // Cari offset akhir (bilangan bulat) yang center-nya = target dan cukup jauh
        var finalOffset = baseInt + loops * symbols.size
        val delta = ((target - wrap(finalOffset)) + symbols.size) % symbols.size
        finalOffset += delta
        if (finalOffset <= start + 2) finalOffset += symbols.size

        animator = ValueAnimator.ofFloat(start, finalOffset.toFloat()).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener { offset = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    offset = finalOffset.toFloat()
                    invalidate()
                    onEnd()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        paint.textSize = h * 0.6f

        val base = floor(offset).toInt()
        val frac = offset - base
        val fm = paint.fontMetrics
        val textOffset = (fm.ascent + fm.descent) / 2f

        // Gambar baris tetangga agar terasa menggulir
        for (k in -1..1) {
            val idx = wrap(base + k)
            val cy = h / 2f - frac * h + k * h
            canvas.drawText(symbols[idx], w / 2f, cy - textOffset, paint)
        }
    }
}
