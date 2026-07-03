package com.rio.casinoklasik.games

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rio.casinoklasik.R
import com.rio.casinoklasik.databinding.ActivitySlotBinding
import com.rio.casinoklasik.engine.ChipManager
import com.rio.casinoklasik.util.Anim
import com.rio.casinoklasik.util.SoundEngine
import kotlin.random.Random

class SlotActivity : AppCompatActivity() {

    private lateinit var b: ActivitySlotBinding

    private val symbols = listOf("\uD83C\uDF52", "\uD83C\uDF4B", "\uD83D\uDD14", "\u2B50", "\uD83D\uDC8E", "7\uFE0F\u20E3")
    private val names = listOf("Ceri", "Lemon", "Bel", "Bintang", "Berlian", "Angka 7")
    private val tripleMultiplier = intArrayOf(5, 8, 12, 20, 50, 100)

    private var spinning = false
    private var displayed = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var autoActive = false
    private var autoRemaining = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySlotBinding.inflate(layoutInflater)
        setContentView(b.root)
        SoundEngine.init(this)

        b.reel1.symbols = symbols
        b.reel2.symbols = symbols
        b.reel3.symbols = symbols

        b.sliderBet.setPercentMode(ChipManager.getBalance(this))
        b.sliderBet.onChange = { SoundEngine.tick() }
        b.btnSpin.setOnClickListener { Anim.pop(it); spin() }
        b.btnAuto10.setOnClickListener { Anim.pop(it); startAuto(10) }
        b.btnAuto25.setOnClickListener { Anim.pop(it); startAuto(25) }
        b.btnAuto50.setOnClickListener { Anim.pop(it); startAuto(50) }
        b.btnStopAuto.setOnClickListener { Anim.pop(it); finishAuto("Auto spin dihentikan") }

        b.txtPaytable.text = buildPaytable()
        displayed = ChipManager.getBalance(this)
        b.txtBalance.text = ChipManager.format(displayed)
    }

    private fun buildPaytable(): String {
        val sb = StringBuilder()
        for (i in symbols.indices) {
            sb.append("${symbols[i]}${symbols[i]}${symbols[i]}  ${names[i]}  x${tripleMultiplier[i]}\n")
        }
        sb.append("\nDua simbol sama  x2\n")
        sb.append("\u2B50 di posisi mana pun  x1")
        return sb.toString().trim()
    }

    private fun spin() {
        if (spinning) return
        val bet = b.sliderBet.value
        if (!ChipManager.canAfford(this, bet)) { Toast.makeText(this, "Saldo tidak cukup", Toast.LENGTH_SHORT).show(); return }

        ChipManager.add(this, -bet)
        refreshBalance(true)
        spinning = true
        b.btnSpin.isEnabled = false
        SoundEngine.slotSpin()
        b.txtMessage.text = if (autoActive) "Memutar... (Auto sisa $autoRemaining)" else "Memutar..."
        b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))

        val r1 = weightedPick(); val r2 = weightedPick(); val r3 = weightedPick()

        b.reel1.spinTo(r1, loops = 6, duration = 1500) { SoundEngine.reelStop() }
        b.reel2.spinTo(r2, loops = 7, duration = 2000) { SoundEngine.reelStop() }
        b.reel3.spinTo(r3, loops = 8, duration = 2500) {
            SoundEngine.reelStop()
            settle(bet, r1, r2, r3)
        }
    }

    private fun weightedPick(): Int {
        val weights = intArrayOf(30, 26, 20, 12, 8, 4)
        val total = weights.sum()
        var roll = Random.nextInt(total)
        for (i in weights.indices) {
            if (roll < weights[i]) return i
            roll -= weights[i]
        }
        return 0
    }

    private fun settle(bet: Long, a: Int, c: Int, d: Int) {
        val multiplier: Int = when {
            a == c && c == d -> tripleMultiplier[a]
            a == c || c == d || a == d -> 2
            a == 3 || c == 3 || d == 3 -> 1
            else -> 0
        }

        if (multiplier > 0) {
            val win = bet * multiplier
            ChipManager.add(this, win)
            b.txtMessage.text = "MENANG x$multiplier  +${ChipManager.format(win)}!"
            b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))
            if (multiplier >= 20) SoundEngine.bigWin() else SoundEngine.win()
            Anim.pulse(b.txtMessage)
        } else {
            b.txtMessage.text = "Belum beruntung, coba lagi"
            b.txtMessage.setTextColor(resources.getColor(R.color.cream, theme))
            SoundEngine.lose()
        }
        refreshBalance(true)
        spinning = false
        b.btnSpin.isEnabled = !autoActive
        if (autoActive) handler.postDelayed({ runNextAuto() }, 700)
    }

    // ---------------- Auto spin ----------------

    private fun startAuto(count: Int) {
        if (spinning || autoActive) return
        if (!ChipManager.canAfford(this, b.sliderBet.value)) {
            Toast.makeText(this, "Saldo tidak cukup", Toast.LENGTH_SHORT).show(); return
        }
        autoActive = true
        autoRemaining = count
        updateAutoUi()
        runNextAuto()
    }

    private fun runNextAuto() {
        if (!autoActive) return
        if (autoRemaining <= 0) { finishAuto("Auto spin selesai"); return }
        if (!ChipManager.canAfford(this, b.sliderBet.value)) {
            finishAuto("Auto berhenti — saldo tidak cukup"); return
        }
        autoRemaining--
        spin()
    }

    private fun finishAuto(msg: String) {
        autoActive = false
        autoRemaining = 0
        updateAutoUi()
        if (!spinning) {
            b.txtMessage.text = msg
            b.txtMessage.setTextColor(resources.getColor(R.color.cream, theme))
        }
    }

    private fun updateAutoUi() {
        val a = autoActive
        listOf(b.btnAuto10, b.btnAuto25, b.btnAuto50).forEach {
            it.isEnabled = !a; it.alpha = if (a) 0.4f else 1f
        }
        b.btnSpin.isEnabled = !a && !spinning
        b.btnSpin.alpha = if (a) 0.4f else 1f
        b.btnStopAuto.visibility = if (a) View.VISIBLE else View.GONE
    }

    private fun refreshBalance(animated: Boolean) {
        val now = ChipManager.getBalance(this)
        if (animated && now != displayed) Anim.countTo(b.txtBalance, displayed, now)
        else b.txtBalance.text = ChipManager.format(now)
        displayed = now
        b.sliderBet.setBalanceCap(now)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
