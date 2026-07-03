package com.rio.casinoklasik.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.rio.casinoklasik.engine.ChipManager

/**
 * Slider taruhan yang dipakai ulang di semua game.
 * Batas maksimum keras 5.000, dan otomatis dibatasi saldo pemain.
 */
class BetSliderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val seek = SeekBar(context)
    private val valueLabel = TextView(context)

    private var minBet = 10L
    private var step = 10L
    private var hardMax = 5000L
    private var effMax = 5000L

    private var percentMode = false
    private var pctBalance = 0L
    private var minPct = 5
    private var stepPct = 5
    private var currentPct = 5

    var value = minBet
        private set

    var onChange: ((Long) -> Unit)? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val gold = Color.parseColor("#F2C94C")

        fun stepBtn(t: String) = TextView(context).apply {
            text = t
            setTextColor(gold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            width = dp(40); height = dp(40)
            setBackgroundResource(com.rio.casinoklasik.R.drawable.btn_dark)
        }
        val mv = stepBtn("\u2212"); val pv = stepBtn("+")

        seek.apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8); marginEnd = dp(8)
            }
            progressTintList = ColorStateList.valueOf(gold)
            thumbTintList = ColorStateList.valueOf(gold)
        }
        valueLabel.apply {
            setTextColor(gold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            minWidth = dp(104)
        }

        addView(mv)
        addView(seek)
        addView(pv)
        addView(valueLabel)

        mv.setOnClickListener { nudge(-1) }
        pv.setOnClickListener { nudge(+1) }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (percentMode) {
                    currentPct = (minPct + progress * stepPct).coerceIn(minPct, 100)
                    value = (pctBalance * currentPct / 100).coerceAtLeast(if (pctBalance > 0) 1L else 0L)
                    valueLabel.text = "$currentPct% \u2022 ${ChipManager.format(value)}"
                } else {
                    value = (minBet + progress.toLong() * step).coerceIn(minBet, effMax)
                    valueLabel.text = ChipManager.format(value)
                }
                onChange?.invoke(value)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun nudge(dir: Int) {
        seek.progress = (seek.progress + dir).coerceIn(0, seek.max)
    }

    fun setRange(min: Long, step: Long, hardMax: Long = 5000L) {
        percentMode = false
        this.minBet = min
        this.step = step.coerceAtLeast(1)
        this.hardMax = hardMax
        applyBalance(hardMax)
    }

    /** Mode persentase: taruhan = persen dari total chip (maks 100% = seluruh chip). */
    fun setPercentMode(balance: Long, minPercent: Int = 5, stepPercent: Int = 5) {
        percentMode = true
        pctBalance = balance
        minPct = minPercent.coerceIn(1, 100)
        stepPct = stepPercent.coerceAtLeast(1)
        val steps = ((100 - minPct) / stepPct).coerceAtLeast(0)
        seek.max = steps
        val keepPct = currentPct.coerceIn(minPct, 100)
        seek.progress = ((keepPct - minPct) / stepPct).coerceIn(0, steps)
        currentPct = (minPct + seek.progress * stepPct).coerceIn(minPct, 100)
        value = (pctBalance * currentPct / 100).coerceAtLeast(if (pctBalance > 0) 1L else 0L)
        valueLabel.text = "$currentPct% \u2022 ${ChipManager.format(value)}"
    }

    /** Absolut: batasi maks berdasarkan saldo. Persen: perbarui basis total chip. */
    fun setBalanceCap(balance: Long) {
        if (percentMode) {
            pctBalance = balance
            value = (pctBalance * currentPct / 100).coerceAtLeast(if (pctBalance > 0) 1L else 0L)
            valueLabel.text = "$currentPct% \u2022 ${ChipManager.format(value)}"
        } else applyBalance(balance)
    }

    private fun applyBalance(balance: Long) {
        effMax = minOf(hardMax, balance).coerceAtLeast(minBet)
        val steps = ((effMax - minBet) / step).toInt().coerceAtLeast(0)
        val keep = value.coerceIn(minBet, effMax)
        seek.max = steps
        seek.progress = (((keep - minBet) / step).toInt()).coerceIn(0, steps)
        value = (minBet + seek.progress.toLong() * step).coerceIn(minBet, effMax)
        valueLabel.text = ChipManager.format(value)
    }

    fun setValue(v: Long) {
        val steps = ((effMax - minBet) / step).toInt().coerceAtLeast(0)
        seek.progress = (((v - minBet) / step).toInt()).coerceIn(0, steps)
    }

    fun isAffordable(balance: Long): Boolean = balance >= minBet
}
