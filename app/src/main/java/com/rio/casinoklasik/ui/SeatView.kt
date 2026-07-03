package com.rio.casinoklasik.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.rio.casinoklasik.R
import com.rio.casinoklasik.engine.Card
import com.rio.casinoklasik.engine.ChipManager

/** Satu kursi pemain poker: avatar beranimasi, nama, stack chip, taruhan, dan kartu mini. */
class SeatView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val bubble = TextView(context)
    private val avatar = TextView(context)
    private val nameTv = TextView(context)
    private val stackTv = TextView(context)
    private val betTv = TextView(context)
    private val cardRow = LinearLayout(context)
    private val mc1 = TextView(context)
    private val mc2 = TextView(context)

    private var baseEmoji = "\uD83E\uDD16"
    private var pulse: ObjectAnimator? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(4), dp(4), dp(4), dp(4))

        val gold = Color.parseColor("#F2C94C")
        val cream = Color.parseColor("#FFF8E1")

        bubble.apply {
            setTextColor(Color.parseColor("#212121"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_action_bubble)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            visibility = INVISIBLE
        }
        avatar.apply {
            text = baseEmoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
            width = dp(52); height = dp(52)
            setBackgroundResource(R.drawable.bg_avatar)
        }
        nameTv.apply {
            setTextColor(cream)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        stackTv.apply {
            setTextColor(gold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        }
        cardRow.apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        for (mc in listOf(mc1, mc2)) mc.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            width = dp(20); height = dp(28)
            setBackgroundResource(R.drawable.bg_minicard)
            val lp = LayoutParams(dp(20), dp(28)); lp.marginEnd = dp(2); layoutParams = lp
        }
        cardRow.addView(mc1); cardRow.addView(mc2)
        betTv.apply {
            setTextColor(Color.parseColor("#7CF29A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_chip_tag)
            setPadding(dp(8), dp(1), dp(8), dp(1))
            visibility = INVISIBLE
        }

        addView(bubble, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(2) })
        addView(cardRow)
        addView(avatar, LayoutParams(dp(52), dp(52)).apply { topMargin = dp(2) })
        addView(nameTv)
        addView(stackTv)
        addView(betTv, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
    }

    fun bind(name: String, emoji: String) {
        baseEmoji = emoji
        avatar.text = emoji
        nameTv.text = name
    }

    fun setName(s: String) { nameTv.text = s }

    fun setStack(v: Long) { stackTv.text = ChipManager.format(v) }

    fun setBet(v: Long) {
        if (v > 0) { betTv.text = "\uD83E\uDE99 ${ChipManager.format(v)}"; betTv.visibility = VISIBLE }
        else betTv.visibility = INVISIBLE
    }

    fun setCards(reveal: Boolean, c1: Card?, c2: Card?, folded: Boolean) {
        renderMini(mc1, if (reveal) c1 else null, folded)
        renderMini(mc2, if (reveal) c2 else null, folded)
    }

    private fun renderMini(mc: TextView, card: Card?, folded: Boolean) {
        if (folded) { mc.visibility = INVISIBLE; return }
        mc.visibility = VISIBLE
        if (card == null) {
            mc.text = ""
            mc.setBackgroundResource(R.drawable.bg_minicard)
        } else {
            mc.text = card.label
            mc.setTextColor(if (card.isRed) Color.parseColor("#D32F2F") else Color.parseColor("#212121"))
            mc.setBackgroundResource(R.drawable.bg_minicard_up)
        }
    }

    fun setActive(active: Boolean) {
        if (active) {
            avatar.setBackgroundResource(R.drawable.bg_avatar_active)
            pulse?.cancel()
            pulse = ObjectAnimator.ofPropertyValuesHolder(
                avatar,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.14f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.14f)
            ).apply {
                duration = 520
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
        } else {
            pulse?.cancel(); pulse = null
            avatar.scaleX = 1f; avatar.scaleY = 1f
            avatar.setBackgroundResource(R.drawable.bg_avatar)
        }
    }

    fun setFolded(folded: Boolean) { alpha = if (folded) 0.45f else 1f }

    fun setEliminated() {
        setActive(false)
        alpha = 0.35f
        avatar.text = "\uD83D\uDC80"
        stackTv.text = "OUT"
        betTv.visibility = INVISIBLE
        mc1.visibility = INVISIBLE; mc2.visibility = INVISIBLE
    }

    fun reset() {
        alpha = 1f
        avatar.text = baseEmoji
        setActive(false)
        betTv.visibility = INVISIBLE
        renderMini(mc1, null, false); renderMini(mc2, null, false)
    }

    fun showAction(text: String) {
        bubble.text = text
        bubble.visibility = VISIBLE
        bubble.alpha = 0f; bubble.scaleX = 0.6f; bubble.scaleY = 0.6f
        bubble.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(180).setInterpolator(OvershootInterpolator())
            .withEndAction { bubble.postDelayed({ bubble.visibility = INVISIBLE }, 950) }
            .start()
    }

    fun setWinner() {
        nameTv.setTextColor(Color.parseColor("#F2C94C"))
        ObjectAnimator.ofPropertyValuesHolder(
            this,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f, 1f)
        ).apply { duration = 600; start() }
    }

    fun clearWinner() { nameTv.setTextColor(Color.parseColor("#FFF8E1")) }
}
