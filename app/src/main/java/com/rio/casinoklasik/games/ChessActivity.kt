package com.rio.casinoklasik.games

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rio.casinoklasik.R
import com.rio.casinoklasik.databinding.ActivityChessBinding
import com.rio.casinoklasik.engine.ChipManager
import com.rio.casinoklasik.engine.Chess
import com.rio.casinoklasik.util.Anim
import com.rio.casinoklasik.util.SoundEngine
import java.util.concurrent.Executors

class ChessActivity : AppCompatActivity() {

    private lateinit var b: ActivityChessBinding
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private enum class Diff { EASY, MEDIUM, HARD }
    private var diff = Diff.MEDIUM

    private var state = Chess.initial()
    private val humanWhite = true
    private var selected = -1
    private var bet = 0L
    private var gameOver = true
    private var thinking = false
    private var displayed = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChessBinding.inflate(layoutInflater)
        setContentView(b.root)
        SoundEngine.init(this)

        b.btnEasy.setOnClickListener { Anim.pop(it); setDiff(Diff.EASY) }
        b.btnMedium.setOnClickListener { Anim.pop(it); setDiff(Diff.MEDIUM) }
        b.btnHard.setOnClickListener { Anim.pop(it); setDiff(Diff.HARD) }
        setDiff(Diff.MEDIUM)

        b.sliderBet.setPercentMode(ChipManager.getBalance(this))
        b.sliderBet.onChange = { updatePayInfo() }
        updatePayInfo()

        b.btnPlay.setOnClickListener { Anim.pop(it); startGame() }
        b.btnHint.setOnClickListener { Anim.pop(it); requestHint() }
        b.btnResign.setOnClickListener { Anim.pop(it); resign() }
        b.btnNewGame.setOnClickListener { Anim.pop(it); backToSetup() }

        b.boardView.onTap = { sq -> onSquareTap(sq) }

        displayed = ChipManager.getBalance(this)
        b.txtBalance.text = ChipManager.format(displayed)
    }

    // ---------------- Setup ----------------

    private fun setDiff(d: Diff) {
        diff = d
        fun style(v: TextView, on: Boolean) {
            v.setBackgroundResource(if (on) R.drawable.bg_diff_selected else R.drawable.bg_diff_normal)
            v.setTextColor(resources.getColor(if (on) R.color.chip_black else R.color.cream, theme))
        }
        style(b.btnEasy, d == Diff.EASY)
        style(b.btnMedium, d == Diff.MEDIUM)
        style(b.btnHard, d == Diff.HARD)
        updatePayInfo()
    }

    private fun depthFor() = when (diff) { Diff.EASY -> 1; Diff.MEDIUM -> 2; Diff.HARD -> 3 }
    private fun randomnessFor() = when (diff) { Diff.EASY -> 150; Diff.MEDIUM -> 40; Diff.HARD -> 0 }
    private fun multiplierFor() = when (diff) { Diff.EASY -> 1.8; Diff.MEDIUM -> 2.3; Diff.HARD -> 3.0 }

    private fun updatePayInfo() {
        val v = b.sliderBet.value
        val ret = (v * multiplierFor()).toLong()
        b.txtPayInfo.text = "Menang bayar ${"%.1f".format(multiplierFor())}x → +${ChipManager.format(ret - v)} • Seri: taruhan kembali"
    }

    private fun startGame() {
        bet = b.sliderBet.value
        if (bet <= 0 || !ChipManager.canAfford(this, bet)) {
            Toast.makeText(this, "Saldo tidak cukup", Toast.LENGTH_SHORT).show(); return
        }
        ChipManager.add(this, -bet)
        refreshBalance(true)

        state = Chess.initial()
        selected = -1
        gameOver = false
        thinking = false

        b.boardView.flipped = !humanWhite
        b.boardView.lastFrom = -1; b.boardView.lastTo = -1
        b.boardView.hintFrom = -1; b.boardView.hintTo = -1
        b.boardView.checkSq = -1
        b.boardView.selected = -1
        b.boardView.legalTargets = emptySet()
        b.boardView.board = state.board.copyOf()

        b.setupPanel.visibility = View.GONE
        b.gamePanel.visibility = View.VISIBLE
        b.btnNewGame.visibility = View.GONE
        setControlsEnabled(true)
        b.txtMessage.text = ""
        updateStatus("Giliran Anda (Putih)")
    }

    private fun backToSetup() {
        b.gamePanel.visibility = View.GONE
        b.setupPanel.visibility = View.VISIBLE
        b.sliderBet.setPercentMode(ChipManager.getBalance(this))
        updatePayInfo()
    }

    // ---------------- Interaksi pemain ----------------

    private fun onSquareTap(sq: Int) {
        if (gameOver || thinking) return
        if (state.whiteToMove != humanWhite) return
        val pc = state.board[sq]
        val mine = if (humanWhite) pc > 0 else pc < 0

        if (selected == -1) {
            if (mine) selectSquare(sq)
            return
        }
        if (sq == selected) { clearSelection(); return }
        if (mine) { selectSquare(sq); return }

        if (b.boardView.legalTargets.contains(sq)) {
            commitHuman(selected, sq)
        } else {
            clearSelection()
        }
    }

    private fun selectSquare(sq: Int) {
        selected = sq
        val targets = Chess.legalMoves(state).filter { it.from == sq }.map { it.to }.toSet()
        b.boardView.selected = sq
        b.boardView.legalTargets = targets
    }

    private fun clearSelection() {
        selected = -1
        b.boardView.selected = -1
        b.boardView.legalTargets = emptySet()
    }

    private fun commitHuman(from: Int, to: Int) {
        val opts = Chess.legalMoves(state).filter { it.from == from && it.to == to }
        if (opts.isEmpty()) { clearSelection(); return }
        val mv = opts.firstOrNull { it.promo == Chess.Q } ?: opts.first()
        clearSelection()
        applyMove(mv)
    }

    // ---------------- Terapkan langkah ----------------

    private fun applyMove(mv: Chess.Move) {
        val capture = state.board[mv.to] != 0 || mv.flag == Chess.F_EP
        state = Chess.makeMove(state, mv)

        b.boardView.board = state.board.copyOf()
        b.boardView.lastFrom = mv.from
        b.boardView.lastTo = mv.to
        b.boardView.hintFrom = -1
        b.boardView.hintTo = -1
        b.boardView.selected = -1
        b.boardView.legalTargets = emptySet()

        val checkNow = Chess.inCheck(state, state.whiteToMove)
        b.boardView.checkSq = if (checkNow) Chess.findKing(state.board, state.whiteToMove) else -1
        b.boardView.invalidate()

        if (capture) SoundEngine.reelStop() else SoundEngine.deal()
        if (checkNow) SoundEngine.tick()

        val st = Chess.status(state)
        if (st != Chess.ONGOING) { handleEnd(st); return }

        if (state.whiteToMove == humanWhite) {
            updateStatus(if (checkNow) "Giliran Anda — Skak!" else "Giliran Anda")
        } else {
            triggerBot()
        }
    }

    private fun triggerBot() {
        thinking = true
        b.btnHint.isEnabled = false
        updateStatus("Bot berpikir\u2026")
        val snapshot = state.copy()
        val depth = depthFor()
        val rnd = randomnessFor()
        worker.execute {
            val mv = Chess.bestMove(snapshot, depth, rnd)
            handler.post {
                thinking = false
                b.btnHint.isEnabled = !gameOver
                if (!gameOver && mv != null) applyMove(mv)
            }
        }
    }

    private fun requestHint() {
        if (gameOver || thinking) return
        if (state.whiteToMove != humanWhite) { Toast.makeText(this, "Tunggu giliran Anda", Toast.LENGTH_SHORT).show(); return }
        thinking = true
        b.btnHint.isEnabled = false
        b.txtMessage.text = "Menghitung saran\u2026"
        val snapshot = state.copy()
        worker.execute {
            val mv = Chess.bestMove(snapshot, 3, 0)
            handler.post {
                thinking = false
                b.btnHint.isEnabled = !gameOver
                if (mv != null) {
                    b.boardView.hintFrom = mv.from
                    b.boardView.hintTo = mv.to
                    b.boardView.invalidate()
                    b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))
                    b.txtMessage.text = "Saran: ${Chess.squareName(mv.from)} \u2192 ${Chess.squareName(mv.to)}"
                }
            }
        }
    }

    private fun resign() {
        if (gameOver) return
        gameOver = true
        updateStatus("Anda menyerah — kalah")
        b.txtMessage.setTextColor(resources.getColor(R.color.lose_red, theme))
        b.txtMessage.text = "Taruhan ${ChipManager.format(bet)} hangus"
        SoundEngine.lose()
        finishGameUi()
    }

    // ---------------- Akhir permainan ----------------

    private fun handleEnd(status: Int) {
        gameOver = true
        when (status) {
            Chess.CHECKMATE -> {
                val humanIsLoser = (state.whiteToMove == humanWhite)
                b.boardView.checkSq = Chess.findKing(state.board, state.whiteToMove)
                b.boardView.invalidate()
                if (humanIsLoser) {
                    updateStatus("Skakmat — Anda kalah")
                    b.txtMessage.setTextColor(resources.getColor(R.color.lose_red, theme))
                    b.txtMessage.text = "Taruhan ${ChipManager.format(bet)} hangus"
                    SoundEngine.lose()
                } else {
                    val ret = (bet * multiplierFor()).toLong()
                    ChipManager.add(this, ret)
                    updateStatus("Skakmat — Anda MENANG!")
                    b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))
                    b.txtMessage.text = "Bayaran ${ChipManager.format(ret)} (bersih +${ChipManager.format(ret - bet)})"
                    SoundEngine.bigWin()
                }
            }
            else -> {
                ChipManager.add(this, bet)
                updateStatus("Remis")
                b.txtMessage.setTextColor(resources.getColor(R.color.cream, theme))
                b.txtMessage.text = "Taruhan ${ChipManager.format(bet)} dikembalikan"
                SoundEngine.push()
            }
        }
        refreshBalance(true)
        finishGameUi()
    }

    private fun finishGameUi() {
        setControlsEnabled(false)
        b.btnNewGame.visibility = View.VISIBLE
    }

    private fun setControlsEnabled(on: Boolean) {
        b.btnHint.isEnabled = on
        b.btnResign.isEnabled = on
        b.btnHint.alpha = if (on) 1f else 0.4f
        b.btnResign.alpha = if (on) 1f else 0.4f
    }

    private fun updateStatus(text: String) { b.txtStatus.text = text }

    private fun refreshBalance(animated: Boolean) {
        val now = ChipManager.getBalance(this)
        if (animated && now != displayed) Anim.countTo(b.txtBalance, displayed, now)
        else b.txtBalance.text = ChipManager.format(now)
        displayed = now
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
    }
}
