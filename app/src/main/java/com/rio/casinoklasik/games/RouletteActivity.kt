package com.rio.casinoklasik.games

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rio.casinoklasik.R
import com.rio.casinoklasik.databinding.ActivityRouletteBinding
import com.rio.casinoklasik.engine.ChipManager
import com.rio.casinoklasik.util.Anim
import com.rio.casinoklasik.util.SoundEngine
import kotlin.random.Random

class RouletteActivity : AppCompatActivity() {

    private lateinit var b: ActivityRouletteBinding
    private val redNumbers = setOf(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36)

    private sealed class Bet(val label: String, val payout: Int) {
        object Red : Bet("Merah", 1)
        object Black : Bet("Hitam", 1)
        object Odd : Bet("Ganjil", 1)
        object Even : Bet("Genap", 1)
        object Low : Bet("1-18", 1)
        object High : Bet("19-36", 1)
        class Dozen(val d: Int) : Bet("Dozen $d", 2)
        class Straight(val n: Int) : Bet("Angka $n", 35)
    }

    private var selectedBet: Bet? = null
    private var chips = 0L
    private var spinning = false
    private var displayed = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRouletteBinding.inflate(layoutInflater)
        setContentView(b.root)
        SoundEngine.init(this)

        b.betRed.setOnClickListener { select(Bet.Red, it) }
        b.betBlack.setOnClickListener { select(Bet.Black, it) }
        b.betOdd.setOnClickListener { select(Bet.Odd, it) }
        b.betEven.setOnClickListener { select(Bet.Even, it) }
        b.betLow.setOnClickListener { select(Bet.Low, it) }
        b.betHigh.setOnClickListener { select(Bet.High, it) }
        b.betD1.setOnClickListener { select(Bet.Dozen(1), it) }
        b.betD2.setOnClickListener { select(Bet.Dozen(2), it) }
        b.betD3.setOnClickListener { select(Bet.Dozen(3), it) }
        b.betNumber.setOnClickListener {
            val n = b.inputNumber.text.toString().toIntOrNull()
            if (n == null || n < 0 || n > 36) Toast.makeText(this, "Masukkan angka 0-36", Toast.LENGTH_SHORT).show()
            else select(Bet.Straight(n), it)
        }

        b.sliderBet.setPercentMode(ChipManager.getBalance(this))
        b.sliderBet.onChange = { SoundEngine.tick() }
        b.btnSpin.setOnClickListener { Anim.pop(it); spin() }

        displayed = ChipManager.getBalance(this)
        b.txtBalance.text = ChipManager.format(displayed)
    }

    private fun select(bet: Bet, view: android.view.View) {
        if (spinning) return
        SoundEngine.bet()
        Anim.pop(view)
        selectedBet = bet
        b.txtSelectedBet.text = "Taruhan: ${bet.label}  (bayar ${bet.payout}:1)"
        Anim.pulse(b.txtSelectedBet)
    }

    private fun spin() {
        val bet = selectedBet
        if (bet == null) { Toast.makeText(this, "Pilih taruhan dulu", Toast.LENGTH_SHORT).show(); return }
        chips = b.sliderBet.value
        if (chips <= 0) { Toast.makeText(this, "Pasang chip dulu", Toast.LENGTH_SHORT).show(); return }
        if (!ChipManager.canAfford(this, chips)) { Toast.makeText(this, "Saldo tidak cukup", Toast.LENGTH_SHORT).show(); return }

        SoundEngine.bet()
        ChipManager.add(this, -chips)
        refreshBalance(true)
        spinning = true
        setButtonsEnabled(false)
        b.txtMessage.text = "Memutar..."
        b.txtMessage.setTextColor(resources.getColor(R.color.cream, theme))

        val result = Random.nextInt(0, 37)
        val loops = 4 + Random.nextInt(0, 3)
        b.wheel.spinTo(
            resultNumber = result,
            loops = loops,
            duration = 4200,
            onTick = { SoundEngine.tick() },
            onEnd = { settle(bet, result) }
        )
    }

    private fun settle(bet: Bet, result: Int) {
        val isRed = redNumbers.contains(result)
        val won = when (bet) {
            Bet.Red -> result != 0 && isRed
            Bet.Black -> result != 0 && !isRed
            Bet.Odd -> result != 0 && result % 2 == 1
            Bet.Even -> result != 0 && result % 2 == 0
            Bet.Low -> result in 1..18
            Bet.High -> result in 19..36
            is Bet.Dozen -> when (bet.d) { 1 -> result in 1..12; 2 -> result in 13..24; else -> result in 25..36 }
            is Bet.Straight -> result == bet.n
        }

        if (won) {
            val payout = chips + chips * bet.payout
            ChipManager.add(this, payout)
            b.txtMessage.text = "Keluar $result — MENANG +${ChipManager.format(payout - chips)}!"
            b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))
            if (bet is Bet.Straight) SoundEngine.bigWin() else SoundEngine.win()
            Anim.pulse(b.txtMessage)
        } else {
            b.txtMessage.text = "Keluar $result — kalah"
            b.txtMessage.setTextColor(resources.getColor(R.color.lose_red, theme))
            SoundEngine.lose()
            Anim.shake(b.txtMessage)
        }
        refreshBalance(true)

        spinning = false
        b.sliderBet.setBalanceCap(ChipManager.getBalance(this))
        setButtonsEnabled(true)
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        listOf(
            b.betRed, b.betBlack, b.betOdd, b.betEven, b.betLow, b.betHigh,
            b.betD1, b.betD2, b.betD3, b.betNumber, b.btnSpin
        ).forEach { (it as Button).isEnabled = enabled }
    }


    private fun refreshBalance(animated: Boolean) {
        val now = ChipManager.getBalance(this)
        if (animated && now != displayed) Anim.countTo(b.txtBalance, displayed, now)
        else b.txtBalance.text = ChipManager.format(now)
        displayed = now
    }
}
