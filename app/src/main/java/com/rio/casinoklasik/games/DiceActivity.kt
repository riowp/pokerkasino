package com.rio.casinoklasik.games

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rio.casinoklasik.R
import com.rio.casinoklasik.databinding.ActivityDiceBinding
import com.rio.casinoklasik.engine.ChipManager
import com.rio.casinoklasik.util.Anim
import com.rio.casinoklasik.util.SoundEngine
import kotlin.random.Random

class DiceActivity : AppCompatActivity() {

    private lateinit var b: ActivityDiceBinding
    private val handler = Handler(Looper.getMainLooper())
    private val faces = charArrayOf('\u2680', '\u2681', '\u2682', '\u2683', '\u2684', '\u2685')

    private sealed class Bet(val label: String) {
        object Big : Bet("Besar (11-17)")
        object Small : Bet("Kecil (4-10)")
        class Number(val n: Int) : Bet("Angka $n")
    }

    private var selectedBet: Bet? = null
    private var chips = 0L
    private var rolling = false
    private var displayed = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDiceBinding.inflate(layoutInflater)
        setContentView(b.root)
        SoundEngine.init(this)

        b.betSmall.setOnClickListener { select(Bet.Small, it) }
        b.betBig.setOnClickListener { select(Bet.Big, it) }
        b.num1.setOnClickListener { select(Bet.Number(1), it) }
        b.num2.setOnClickListener { select(Bet.Number(2), it) }
        b.num3.setOnClickListener { select(Bet.Number(3), it) }
        b.num4.setOnClickListener { select(Bet.Number(4), it) }
        b.num5.setOnClickListener { select(Bet.Number(5), it) }
        b.num6.setOnClickListener { select(Bet.Number(6), it) }

        b.sliderBet.setPercentMode(ChipManager.getBalance(this))
        b.sliderBet.onChange = { SoundEngine.tick() }
        b.btnRoll.setOnClickListener { Anim.pop(it); roll() }

        displayed = ChipManager.getBalance(this)
        b.txtBalance.text = ChipManager.format(displayed)
    }

    private fun select(bet: Bet, view: View) {
        if (rolling) return
        SoundEngine.bet()
        Anim.pop(view)
        selectedBet = bet
        b.txtSelectedBet.text = "Taruhan: ${bet.label}"
        Anim.pulse(b.txtSelectedBet)
    }

    private fun roll() {
        val bet = selectedBet
        if (bet == null) { Toast.makeText(this, "Pilih taruhan dulu", Toast.LENGTH_SHORT).show(); return }
        chips = b.sliderBet.value
        if (chips <= 0) { Toast.makeText(this, "Pasang chip dulu", Toast.LENGTH_SHORT).show(); return }
        if (!ChipManager.canAfford(this, chips)) { Toast.makeText(this, "Saldo tidak cukup", Toast.LENGTH_SHORT).show(); return }

        SoundEngine.bet()
        ChipManager.add(this, -chips)
        refreshBalance(true)
        rolling = true
        setEnabled(false)
        b.txtMessage.text = "Melempar..."
        b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))
        SoundEngine.diceRoll()

        val d1 = Random.nextInt(1, 7); val d2 = Random.nextInt(1, 7); val d3 = Random.nextInt(1, 7)
        val dice = listOf(b.dice1, b.dice2, b.dice3)
        val results = listOf(d1, d2, d3)

        val frames = 10
        for (i in 0 until frames) {
            handler.postDelayed({
                dice.forEach {
                    it.text = faces[Random.nextInt(6)].toString()
                    it.rotation = Random.nextInt(-28, 28).toFloat()
                    it.translationY = Random.nextInt(-10, 10).toFloat()
                }
            }, i * 70L)
        }
        handler.postDelayed({
            dice.forEachIndexed { idx, tv ->
                tv.text = faces[results[idx] - 1].toString()
                settleDie(tv)
            }
            handler.postDelayed({ settle(bet, d1, d2, d3) }, 300)
        }, frames * 70L + 80)
    }

    private fun settleDie(tv: TextView) {
        tv.animate().rotation(0f).translationY(0f)
            .scaleX(1.25f).scaleY(1.25f)
            .setInterpolator(OvershootInterpolator(4f))
            .setDuration(280)
            .withEndAction {
                tv.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }.start()
    }

    private fun settle(bet: Bet, d1: Int, d2: Int, d3: Int) {
        val total = d1 + d2 + d3
        val isTriple = d1 == d2 && d2 == d3

        val payout: Long = when (bet) {
            Bet.Big -> if (!isTriple && total in 11..17) chips * 2 else 0
            Bet.Small -> if (!isTriple && total in 4..10) chips * 2 else 0
            is Bet.Number -> {
                val count = listOf(d1, d2, d3).count { it == bet.n }
                if (count > 0) chips + chips * count else 0
            }
        }

        val detail = "Dadu: $d1-$d2-$d3  (total $total)"
        if (payout > 0) {
            ChipManager.add(this, payout)
            b.txtMessage.text = "$detail\nMENANG +${ChipManager.format(payout - chips)}!"
            b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))
            SoundEngine.win()
            Anim.pulse(b.txtMessage)
        } else {
            b.txtMessage.text = "$detail\nKalah" + if (isTriple) " (triple)" else ""
            b.txtMessage.setTextColor(resources.getColor(R.color.lose_red, theme))
            SoundEngine.lose()
            Anim.shake(b.txtMessage)
        }
        refreshBalance(true)

        b.sliderBet.setBalanceCap(ChipManager.getBalance(this))
        rolling = false
        setEnabled(true)
    }

    private fun setEnabled(enabled: Boolean) {
        listOf(
            b.betSmall, b.betBig, b.num1, b.num2, b.num3, b.num4, b.num5, b.num6,
            b.btnRoll
        ).forEach { (it as Button).isEnabled = enabled }
    }


    private fun refreshBalance(animated: Boolean) {
        val now = ChipManager.getBalance(this)
        if (animated && now != displayed) Anim.countTo(b.txtBalance, displayed, now)
        else b.txtBalance.text = ChipManager.format(now)
        displayed = now
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
