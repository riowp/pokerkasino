package com.rio.casinoklasik.games

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.rio.casinoklasik.R
import com.rio.casinoklasik.databinding.ActivityDominoBinding
import com.rio.casinoklasik.engine.ChipManager
import com.rio.casinoklasik.engine.Domino
import com.rio.casinoklasik.engine.Domino.Tile
import com.rio.casinoklasik.ui.DominoTileView
import com.rio.casinoklasik.util.Anim
import com.rio.casinoklasik.util.SoundEngine

class DominoActivity : AppCompatActivity() {

    private lateinit var b: ActivityDominoBinding
    private val handler = Handler(Looper.getMainLooper())

    private val seats = 4
    private val names = arrayOf("Anda", "Bot A", "Bot B", "Bot C")

    private lateinit var hands: Array<MutableList<Tile>>
    private val board = mutableListOf<Tile>()
    private var leftEnd = -1
    private var rightEnd = -1
    private var current = 0
    private var passes = 0
    private var handActive = false
    private var awaitingHuman = false
    private var displayed = 0L

    private var ante = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDominoBinding.inflate(layoutInflater)
        setContentView(b.root)
        SoundEngine.init(this)

        b.sliderBet.setPercentMode(ChipManager.getBalance(this))
        b.sliderBet.onChange = { SoundEngine.tick() }
        b.btnDeal.setOnClickListener { Anim.pop(it); startRound() }
        b.btnPass.setOnClickListener { humanPass() }

        displayed = ChipManager.getBalance(this)
        b.txtBalance.text = ChipManager.format(displayed)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------------- Mulai ronde ----------------

    private fun startRound() {
        ante = b.sliderBet.value
        if (!ChipManager.canAfford(this, ante)) {
            Toast.makeText(this, "Saldo tidak cukup untuk ante", Toast.LENGTH_SHORT).show(); return
        }
        SoundEngine.bet()
        ChipManager.add(this, -ante)
        refreshBalance(true)

        val set = Domino.fullSet().apply { shuffle() }
        hands = Array(seats) { mutableListOf() }
        for (i in 0 until 28) hands[i % seats].add(set[i])

        board.clear(); passes = 0; leftEnd = -1; rightEnd = -1; handActive = true

        b.betPanel.visibility = View.GONE
        b.btnDeal.visibility = View.GONE
        b.btnPass.visibility = View.GONE

        current = (0 until seats).first { s -> hands[s].any { it == Domino.DOUBLE_SIX } }
        val six = hands[current].first { it == Domino.DOUBLE_SIX }

        renderOpponents()
        renderHand(emptyList())

        placeTile(current, six, true)
        b.txtMessage.setTextColor(resources.getColor(R.color.white, theme))
        b.txtMessage.text = "${names[current]} mulai dengan 6-6"

        current = (current + 1) % seats
        handler.postDelayed({ advance() }, 800)
    }

    private fun placeTile(seat: Int, tile: Tile, onLeft: Boolean) {
        hands[seat].remove(tile)
        when {
            board.isEmpty() -> { board.add(tile); leftEnd = tile.a; rightEnd = tile.b }
            onLeft -> { val o = tile.other(leftEnd); board.add(0, Tile(o, leftEnd)); leftEnd = o }
            else -> { val o = tile.other(rightEnd); board.add(Tile(rightEnd, o)); rightEnd = o }
        }
        passes = 0
        SoundEngine.reelStop()
        renderBoard(); renderOpponents(); updateEnds()
        if (seat == 0) renderHand(emptyList())
    }

    // ---------------- Loop giliran ----------------

    private fun advance() {
        if (!handActive) return
        renderOpponents()
        val hand = hands[current]
        val playable = hand.filter { it.matches(leftEnd) || it.matches(rightEnd) }

        if (current == 0) {
            awaitingHuman = true
            if (playable.isEmpty()) {
                b.btnPass.visibility = View.VISIBLE
                b.txtMessage.text = "Tidak ada langkah — tekan Lewati"
            } else {
                b.btnPass.visibility = View.GONE
                b.txtMessage.text = "Giliran Anda — pilih kartu"
            }
            b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))
            renderHand(playable)
            return
        }

        awaitingHuman = false
        b.btnPass.visibility = View.GONE
        handler.postDelayed({
            if (!handActive) return@postDelayed
            if (playable.isEmpty()) {
                passes++
                b.txtMessage.setTextColor(resources.getColor(R.color.cream, theme))
                b.txtMessage.text = "${names[current]} lewat"
                SoundEngine.tick()
                afterTurn(false)
            } else {
                val tile = playable.maxByOrNull { it.pips }!!
                val onLeft = tile.matches(leftEnd)
                placeTile(current, tile, onLeft)
                b.txtMessage.setTextColor(resources.getColor(R.color.white, theme))
                b.txtMessage.text = "${names[current]} pasang ${tile.a}-${tile.b}"
                afterTurn(true)
            }
        }, 850)
    }

    private fun afterTurn(moved: Boolean) {
        if (moved && hands[current].isEmpty()) { endRound(current, false); return }
        if (passes >= seats) { endRound(-1, true); return }
        current = (current + 1) % seats
        advance()
    }

    // ---------------- Aksi pemain ----------------

    private fun onHandTileClicked(tile: Tile) {
        if (!awaitingHuman) return
        val ml = tile.matches(leftEnd)
        val mr = tile.matches(rightEnd)
        if (!ml && !mr) { Toast.makeText(this, "Kartu itu tidak cocok", Toast.LENGTH_SHORT).show(); return }

        if (ml && mr && leftEnd != rightEnd) {
            AlertDialog.Builder(this)
                .setTitle("Pasang di ujung mana?")
                .setPositiveButton("Kiri ($leftEnd)") { _, _ -> commitHuman(tile, true) }
                .setNegativeButton("Kanan ($rightEnd)") { _, _ -> commitHuman(tile, false) }
                .show()
        } else {
            commitHuman(tile, ml)
        }
    }

    private fun commitHuman(tile: Tile, onLeft: Boolean) {
        if (!awaitingHuman) return
        awaitingHuman = false
        placeTile(0, tile, onLeft)
        b.txtMessage.setTextColor(resources.getColor(R.color.white, theme))
        b.txtMessage.text = "Anda pasang ${tile.a}-${tile.b}"
        afterTurn(true)
    }

    private fun humanPass() {
        if (!awaitingHuman) return
        if (hands[0].any { it.matches(leftEnd) || it.matches(rightEnd) }) {
            Toast.makeText(this, "Masih ada langkah yang bisa dimainkan", Toast.LENGTH_SHORT).show(); return
        }
        awaitingHuman = false
        passes++
        b.btnPass.visibility = View.GONE
        b.txtMessage.text = "Anda lewat"
        SoundEngine.tick()
        afterTurn(false)
    }

    // ---------------- Akhir ronde ----------------

    private fun endRound(winner: Int, blocked: Boolean) {
        handActive = false
        awaitingHuman = false
        b.btnPass.visibility = View.GONE

        val winners: List<Int> = if (!blocked) listOf(winner) else {
            val totals = (0 until seats).map { s -> hands[s].sumOf { it.pips } }
            val mn = totals.minOrNull()!!
            (0 until seats).filter { totals[it] == mn }
        }

        val pot = ante * seats
        val share = pot / winners.size
        val humanWon = winners.contains(0)
        if (humanWon) ChipManager.add(this, share)
        refreshBalance(true)
        renderOpponents()

        val who = winners.joinToString(" & ") { names[it] }
        val prefix = if (blocked) "Buntu! " else ""
        b.txtMessage.text = "$prefix$who menang • Pot ${ChipManager.format(pot)}"
        if (humanWon) {
            b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))
            if (winners.size == 1) SoundEngine.bigWin() else SoundEngine.win()
            Anim.pulse(b.txtMessage)
        } else {
            b.txtMessage.setTextColor(resources.getColor(R.color.lose_red, theme))
            SoundEngine.lose()
        }

        b.betPanel.visibility = View.VISIBLE
        b.btnDeal.visibility = View.VISIBLE
    }

    // ---------------- Render ----------------

    private fun renderBoard() {
        b.boardWrap.removeAllViews()
        if (board.isEmpty()) return

        val tileW = dp(50); val tileH = dp(26); val m = dp(3)
        val screenW = resources.displayMetrics.widthPixels
        val avail = (screenW - dp(48)).coerceAtLeast(tileW + m)
        val perRow = (avail / (tileW + m)).coerceAtLeast(1)

        var idx = 0
        var rowIndex = 0
        while (idx < board.size) {
            val end = minOf(idx + perRow, board.size)
            var slice = board.subList(idx, end).toList()
            val reversed = rowIndex % 2 == 1
            if (reversed) slice = slice.reversed()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (reversed) android.view.Gravity.END else android.view.Gravity.START
            }
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = if (rowIndex == 0) 0 else dp(4) }

            for (t in slice) {
                val v = DominoTileView(this).apply { set(t.a, t.b, false) }
                v.layoutParams = LinearLayout.LayoutParams(tileW, tileH).apply { marginEnd = m }
                row.addView(v)
            }
            b.boardWrap.addView(row)
            idx = end; rowIndex++
        }
    }

    private fun renderHand(playable: List<Tile>) {
        b.handRow.removeAllViews()
        if (!::hands.isInitialized) return
        val sorted = hands[0].sortedWith(compareBy({ it.a }, { it.b }))
        for (t in sorted) {
            val v = DominoTileView(this).apply {
                set(t.a, t.b, true)
                highlight = playable.contains(t)
            }
            v.layoutParams = LinearLayout.LayoutParams(dp(46), dp(90)).apply { marginEnd = dp(6) }
            v.setOnClickListener { Anim.pop(v); onHandTileClicked(t) }
            b.handRow.addView(v)
        }
    }

    private fun renderOpponents() {
        b.txtBotA.text = oppLine(1)
        b.txtBotB.text = oppLine(2)
        b.txtBotC.text = oppLine(3)
    }

    private fun oppLine(s: Int): String {
        val mark = if (handActive && current == s) "\u25B6 " else ""
        val count = if (::hands.isInitialized) hands[s].size else 0
        return "$mark${names[s]}: $count kartu"
    }

    private fun updateEnds() {
        b.txtEnds.text = "Ujung kiri: $leftEnd     Ujung kanan: $rightEnd"
    }

    private fun refreshBalance(animated: Boolean) {
        b.sliderBet.setBalanceCap(ChipManager.getBalance(this))
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
