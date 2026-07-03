package com.rio.casinoklasik.util

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.rio.casinoklasik.engine.ChipManager

object Anim {

    /** Efek tekan tombol yang halus. */
    fun pop(v: View) {
        v.animate().cancel()
        v.scaleX = 0.92f; v.scaleY = 0.92f
        v.animate().scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(3f))
            .setDuration(180).start()
    }

    /** Kartu meluncur masuk dari atas + fade, dengan sedikit overshoot. */
    fun dealIn(v: View, delay: Long = 0L) {
        v.alpha = 0f
        v.translationY = -120f
        v.translationX = 60f
        v.rotation = -12f
        v.animate()
            .alpha(1f).translationY(0f).translationX(0f).rotation(0f)
            .setStartDelay(delay)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .setDuration(320).start()
    }

    /** Pulse membesar lalu kembali (untuk pesan menang). */
    fun pulse(v: View) {
        v.animate().cancel()
        v.scaleX = 1f; v.scaleY = 1f
        v.animate().scaleX(1.25f).scaleY(1.25f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(220)
            .withEndAction {
                v.animate().scaleX(1f).scaleY(1f)
                    .setInterpolator(OvershootInterpolator(2f))
                    .setDuration(260).start()
            }.start()
    }

    /** Goyang kecil (untuk kalah / error). */
    fun shake(v: View) {
        v.animate().cancel()
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            addUpdateListener {
                val f = it.animatedValue as Float
                v.translationX = Math.sin(f * Math.PI * 4).toFloat() * 14f * (1 - f)
            }
        }
        a.start()
    }

    /** Menghitung saldo dari nilai lama ke baru secara mulus. */
    fun countTo(tv: TextView, from: Long, to: Long, duration: Long = 600) {
        val a = ValueAnimator.ofFloat(from.toFloat(), to.toFloat())
        a.duration = duration
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            val v = (it.animatedValue as Float).toLong()
            tv.text = ChipManager.format(v)
        }
        a.start()
    }
}
