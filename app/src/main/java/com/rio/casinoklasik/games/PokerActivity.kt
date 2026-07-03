package com.rio.casinoklasik.games

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rio.casinoklasik.R
import com.rio.casinoklasik.databinding.ActivityPokerBinding
import com.rio.casinoklasik.engine.Card
import com.rio.casinoklasik.engine.ChipManager
import com.rio.casinoklasik.engine.Deck
import com.rio.casinoklasik.engine.Poker
import com.rio.casinoklasik.ui.SeatView
import com.rio.casinoklasik.util.Anim
import com.rio.casinoklasik.util.CardViews
import com.rio.casinoklasik.util.SoundEngine
import kotlin.random.Random

class PokerActivity : AppCompatActivity() {

    private val N = 5
    private val BUYIN_BB = 20L

    private lateinit var b: ActivityPokerBinding
    private val deck = Deck()
    private val handler = Handler(Looper.getMainLooper())

    private enum class Diff { EASY, MEDIUM, HARD }
    private var diff = Diff.MEDIUM

    private class P(val index: Int, val name: String, val emoji: String, val isHuman: Boolean) {
        val hole = mutableListOf<Card>()
        var stack = 0L
        var folded = false
        var allIn = false
        var acted = false
        var eliminated = false
        var roundContribution = 0L
        var totalContribution = 0L
    }

    private lateinit var players: List<P>
    private val community = mutableListOf<Card>()
    private var communityShown = 0

    private var currentBet = 0L
    private var lastRaiseSize = 0L
    private var bigBlind = 100L
    private var smallBlind = 50L
    private var dealer = 0
    private var stage = 0
    private var currentActor = 0
    private var handActive = false
    private var awaitingHuman = false
    private var tableActive = false
    private var returnToSetup = false
    private var buyIn = 0L
    private var displayed = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPokerBinding.inflate(layoutInflater)
        setContentView(b.root)
        SoundEngine.init(this)

        b.btnEasy.setOnClickListener { Anim.pop(it); setDiff(Diff.EASY) }
        b.btnMedium.setOnClickListener { Anim.pop(it); setDiff(Diff.MEDIUM) }
        b.btnHard.setOnClickListener { Anim.pop(it); setDiff(Diff.HARD) }
        setDiff(Diff.MEDIUM)

        b.sliderBlind.setRange(20, 20, 1_000_000)
        b.sliderBlind.setBalanceCap(ChipManager.getBalance(this) / BUYIN_BB)
        b.sliderBlind.onChange = { updateBuyin() }
        updateBuyin()

        b.btnSit.setOnClickListener { Anim.pop(it); sitDown() }
        b.btnLeave.setOnClickListener { Anim.pop(it); leaveTable() }

        b.btnFold.setOnClickListener { if (awaitingHuman) humanFold() }
        b.btnCall.setOnClickListener { if (awaitingHuman) humanCall() }
        b.btnRaise.setOnClickListener { if (awaitingHuman) openRaise() }
        b.btnAllIn.setOnClickListener { if (awaitingHuman) humanAllIn() }
        b.btnCancelRaise.setOnClickListener { closeRaise() }
        b.btnConfirmRaise.setOnClickListener { if (awaitingHuman) confirmRaise() }
        b.btnNextHand.setOnClickListener { Anim.pop(it); onNextHand() }

        displayed = ChipManager.getBalance(this)
        b.txtBalance.text = ChipManager.format(displayed)
    }

    // ---------------- Setup ----------------

    private fun setDiff(d: Diff) {
        diff = d
        fun style(v: android.widget.TextView, on: Boolean) {
            v.setBackgroundResource(if (on) R.drawable.bg_diff_selected else R.drawable.bg_diff_normal)
            v.setTextColor(resources.getColor(if (on) R.color.chip_black else R.color.cream, theme))
        }
        style(b.btnEasy, d == Diff.EASY)
        style(b.btnMedium, d == Diff.MEDIUM)
        style(b.btnHard, d == Diff.HARD)
    }

    private fun updateBuyin() {
        buyIn = b.sliderBlind.value * BUYIN_BB
        b.txtBuyin.text = "Buy-in: ${ChipManager.format(buyIn)}  (stack awal tiap pemain)"
    }

    private fun sitDown() {
        bigBlind = b.sliderBlind.value
        smallBlind = maxOf(1L, bigBlind / 2)
        buyIn = bigBlind * BUYIN_BB
        if (!ChipManager.canAfford(this, buyIn)) {
            Toast.makeText(this, "Saldo tidak cukup untuk buy-in ${ChipManager.format(buyIn)}", Toast.LENGTH_SHORT).show()
            return
        }
        ChipManager.add(this, -buyIn)
        refreshBalance(true)

        players = listOf(
            P(0, "Anda", "\uD83E\uDDD1", true),
            P(1, "Bot A", "\uD83E\uDD16", false),
            P(2, "Bot B", "\uD83D\uDC74", false),
            P(3, "Bot C", "\uD83D\uDC69", false),
            P(4, "Bot D", "\uD83D\uDE0E", false)
        )
        players.forEach { it.stack = buyIn }
        for (i in 1..4) seatViewFor(i)!!.bind(players[i].name, players[i].emoji)

        tableActive = true
        returnToSetup = false
        dealer = Random.nextInt(N)
        b.setupPanel.visibility = View.GONE
        b.tablePanel.visibility = View.VISIBLE
        startHand()
    }

    private fun leaveTable() {
        if (!tableActive || handActive) {
            Toast.makeText(this, "Selesaikan hand dulu", Toast.LENGTH_SHORT).show(); return
        }
        cashOutAndReturn("Cash out ${ChipManager.format(players[0].stack)} chip")
    }

    private fun cashOutAndReturn(msg: String) {
        ChipManager.add(this, players[0].stack)
        players[0].stack = 0
        tableActive = false
        refreshBalance(true)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        b.tablePanel.visibility = View.GONE
        b.setupPanel.visibility = View.VISIBLE
        b.sliderBlind.setBalanceCap(ChipManager.getBalance(this) / BUYIN_BB)
        updateBuyin()
    }

    // ---------------- Hand ----------------

    private fun startHand() {
        deck.reset()
        community.clear(); communityShown = 0
        b.communityCards.removeAllViews()
        currentBet = 0; lastRaiseSize = bigBlind; stage = 0; handActive = true
        b.btnNextHand.visibility = View.GONE
        b.btnLeave.visibility = View.GONE
        b.actionPanel.visibility = View.GONE
        b.raisePanel.visibility = View.GONE

        players.forEach {
            if (!it.eliminated) {
                it.hole.clear(); it.folded = false; it.allIn = false; it.acted = false
                it.roundContribution = 0; it.totalContribution = 0
            }
        }

        dealer = nextOccupied(dealer)
        val sb = nextOccupied(dealer)
        val bb = nextOccupied(sb)
        contributeToPot(sb, smallBlind)
        contributeToPot(bb, bigBlind)
        currentBet = bigBlind

        repeat(2) { players.filter { !it.eliminated }.forEach { it.hole.add(deck.deal()) } }

        renderSeatsInitial()
        renderHumanArea()
        renderCommunity()
        updatePot()
        updateHandName()
        SoundEngine.deal()

        seatViewFor(sb)?.showAction("SB")
        seatViewFor(bb)?.showAction("BB")

        b.txtMessage.setTextColor(resources.getColor(R.color.white, theme))
        b.txtMessage.text = "Blind terpasang — Pra-flop"
        currentActor = bb
        handler.postDelayed({ advance() }, 900)
    }

    private fun contributeToPot(i: Int, amount: Long): Long {
        val p = players[i]
        val amt = minOf(amount, p.stack)
        p.stack -= amt
        p.roundContribution += amt
        p.totalContribution += amt
        if (p.stack == 0L) p.allIn = true
        return amt
    }

    private fun nextOccupied(from: Int): Int {
        var i = from
        do { i = (i + 1) % N } while (players[i].eliminated)
        return i
    }

    // ---------------- Loop aksi ----------------

    private fun advance() {
        if (!handActive) return
        val live = players.count { !it.eliminated && !it.folded }
        if (live <= 1) { endHand(); return }
        val canAct = players.count { !it.eliminated && !it.folded && !it.allIn }
        if (canAct == 0) { runOutBoard(); return }
        val next = nextActor(currentActor)
        if (next == null) { advanceStreet(); return }
        currentActor = next
        refreshSeats()
        val p = players[next]
        if (p.isHuman) {
            showHumanActions()
        } else {
            hideHumanControls()
            handler.postDelayed({ botAct(next) }, 850)
        }
    }

    private fun nextActor(from: Int): Int? {
        for (off in 1..N) {
            val i = (from + off) % N
            val p = players[i]
            if (!p.eliminated && !p.folded && !p.allIn && (!p.acted || p.roundContribution < currentBet)) return i
        }
        return null
    }

    private fun doFold(i: Int) { players[i].folded = true; players[i].acted = true }
    private fun doCheck(i: Int) { players[i].acted = true }
    private fun doCall(i: Int) {
        val p = players[i]
        contributeToPot(i, currentBet - p.roundContribution)
        p.acted = true
    }
    private fun applyRaise(i: Int, raiseTo: Long) {
        val p = players[i]
        val target = raiseTo.coerceAtMost(p.roundContribution + p.stack)
        contributeToPot(i, target - p.roundContribution)
        if (p.roundContribution > currentBet) {
            lastRaiseSize = p.roundContribution - currentBet
            currentBet = p.roundContribution
            players.forEach { if (it !== p && !it.eliminated && !it.folded && !it.allIn) it.acted = false }
        }
        p.acted = true
    }

    private fun potTotal(): Long = players.sumOf { it.totalContribution }

    private fun raiseSizeBot(p: P): Long {
        val minRaise = currentBet + maxOf(lastRaiseSize, bigBlind)
        val potRaise = currentBet + maxOf(bigBlind, potTotal() / 2)
        val target = if (diff == Diff.HARD && Random.nextDouble() < 0.5) potRaise else minRaise
        return target.coerceAtMost(p.roundContribution + p.stack)
    }

    private fun botAct(i: Int) {
        if (!handActive) return
        val p = players[i]
        val toCall = currentBet - p.roundContribution
        val strength = Poker.strength(p.hole, community)
        val r = Random.nextDouble()
        val perceived = when (diff) {
            Diff.EASY -> (strength + (r - 0.5) * 0.40).coerceIn(0.0, 1.0)
            Diff.MEDIUM -> (strength + (r - 0.5) * 0.15).coerceIn(0.0, 1.0)
            Diff.HARD -> strength
        }

        var action = "CHECK"
        if (toCall <= 0) {
            val betThresh = when (diff) { Diff.EASY -> 0.72; Diff.MEDIUM -> 0.60; Diff.HARD -> 0.50 }
            val bluff = when (diff) { Diff.EASY -> 0.03; Diff.MEDIUM -> 0.08; Diff.HARD -> 0.16 }
            action = if ((perceived > betThresh && r < 0.7) || r < bluff) "RAISE" else "CHECK"
        } else {
            val potOdds = toCall.toDouble() / (potTotal() + toCall)
            val callThresh = when (diff) { Diff.EASY -> 0.18; Diff.MEDIUM -> 0.30; Diff.HARD -> potOdds * 0.9 + 0.05 }
            val raiseThresh = when (diff) { Diff.EASY -> 0.85; Diff.MEDIUM -> 0.75; Diff.HARD -> 0.66 }
            action = when {
                perceived > raiseThresh && r < (if (diff == Diff.HARD) 0.6 else 0.4) -> "RAISE"
                perceived > callThresh -> "CALL"
                diff == Diff.HARD && r < 0.06 -> "RAISE"
                diff == Diff.EASY && r < 0.25 && toCall <= bigBlind * 3 -> "CALL"
                else -> "FOLD"
            }
        }

        val bubble: String
        when (action) {
            "FOLD" -> { doFold(i); bubble = "Fold"; SoundEngine.deal() }
            "CHECK" -> { doCheck(i); bubble = "Check"; SoundEngine.tick() }
            "CALL" -> { doCall(i); bubble = if (p.allIn) "All-in" else "Call"; SoundEngine.bet() }
            else -> { applyRaise(i, raiseSizeBot(p)); bubble = if (p.allIn) "All-in" else "Raise ${ChipManager.format(p.roundContribution)}"; SoundEngine.bet() }
        }
        seatViewFor(i)?.showAction(bubble)
        b.txtMessage.setTextColor(resources.getColor(R.color.white, theme))
        b.txtMessage.text = "${p.name}: $bubble"
        refreshSeats(); updatePot()
        advance()
    }

    // ---------------- Aksi pemain ----------------

    private fun showHumanActions() {
        awaitingHuman = true
        b.btnLeave.visibility = View.GONE
        b.actionPanel.visibility = View.VISIBLE
        b.raisePanel.visibility = View.GONE
        val p = players[0]
        val toCall = currentBet - p.roundContribution
        b.btnCall.text = if (toCall <= 0) "Check" else "Call ${ChipManager.format(minOf(toCall, p.stack))}"
        val canRaise = p.stack > toCall && players.count { !it.eliminated && !it.folded && !it.allIn } > 1
        b.btnRaise.isEnabled = canRaise
        b.btnRaise.alpha = if (canRaise) 1f else 0.4f
        b.btnAllIn.isEnabled = p.stack > 0
        b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))
        b.txtMessage.text = "Giliran Anda"
        Anim.pulse(b.txtYouInfo)
        updateHandName()
    }

    private fun hideHumanControls() {
        b.actionPanel.visibility = View.GONE
        b.raisePanel.visibility = View.GONE
        awaitingHuman = false
    }

    private fun humanFold() {
        awaitingHuman = false; hideHumanControls()
        doFold(0); SoundEngine.deal(); b.txtMessage.text = "Anda fold"
        refreshSeats(); updatePot(); advance()
    }

    private fun humanCall() {
        awaitingHuman = false; hideHumanControls()
        val toCall = currentBet - players[0].roundContribution
        if (toCall <= 0) { doCheck(0); SoundEngine.tick(); b.txtMessage.text = "Anda cek" }
        else { doCall(0); SoundEngine.bet(); b.txtMessage.text = if (players[0].allIn) "Anda call (all-in)" else "Anda call" }
        refreshSeats(); updatePot(); advance()
    }

    private fun openRaise() {
        val p = players[0]
        val minRaiseTo = (currentBet + maxOf(lastRaiseSize, bigBlind)).coerceAtMost(p.roundContribution + p.stack)
        val maxRaiseTo = p.roundContribution + p.stack
        b.actionPanel.visibility = View.GONE
        b.raisePanel.visibility = View.VISIBLE
        b.sliderRaise.setRange(minRaiseTo, maxOf(bigBlind, 10L), 1_000_000)
        b.sliderRaise.setBalanceCap(maxRaiseTo)
        b.sliderRaise.setValue(minRaiseTo)
    }

    private fun closeRaise() {
        b.raisePanel.visibility = View.GONE
        if (awaitingHuman) b.actionPanel.visibility = View.VISIBLE
    }

    private fun confirmRaise() {
        val target = b.sliderRaise.value
        awaitingHuman = false; hideHumanControls()
        applyRaise(0, target)
        SoundEngine.bet()
        b.txtMessage.text = if (players[0].allIn) "Anda all-in" else "Anda menaikkan ke ${ChipManager.format(players[0].roundContribution)}"
        refreshSeats(); updatePot(); advance()
    }

    private fun humanAllIn() {
        awaitingHuman = false; hideHumanControls()
        val p = players[0]
        applyRaise(0, p.roundContribution + p.stack)
        SoundEngine.bet(); b.txtMessage.text = "Anda all-in"
        refreshSeats(); updatePot(); advance()
    }

    // ---------------- Tahapan ----------------

    private fun advanceStreet() {
        players.forEach {
            if (!it.eliminated) { it.roundContribution = 0; if (!it.folded && !it.allIn) it.acted = false }
        }
        currentBet = 0; lastRaiseSize = bigBlind
        stage++
        when (stage) {
            1 -> { dealCommunity(3); b.txtMessage.text = "Flop" }
            2 -> { dealCommunity(1); b.txtMessage.text = "Turn" }
            3 -> { dealCommunity(1); b.txtMessage.text = "River" }
            else -> { endHand(); return }
        }
        b.txtMessage.setTextColor(resources.getColor(R.color.white, theme))
        renderCommunity(); refreshSeats(); updatePot(); updateHandName()
        SoundEngine.deal()
        currentActor = dealer
        handler.postDelayed({ advance() }, 750)
    }

    private fun dealCommunity(count: Int) { repeat(count) { community.add(deck.deal()) } }

    private fun runOutBoard() {
        while (community.size < 5) community.add(deck.deal())
        renderCommunity()
        stage = 3
        b.txtMessage.text = "Semua all-in — buka kartu"
        handler.postDelayed({ endHand() }, 1000)
    }

    private fun endHand() {
        handActive = false
        awaitingHuman = false
        b.actionPanel.visibility = View.GONE
        b.raisePanel.visibility = View.GONE

        val contenders = players.filter { !it.eliminated && !it.folded }
        val reveal = contenders.size > 1
        if (reveal) while (community.size < 5) community.add(deck.deal())
        renderCommunity()

        val scores = HashMap<Int, Long>()
        if (reveal) for (p in contenders) scores[p.index] = Poker.evaluate(p.hole + community)

        val winnings = distribute(scores, contenders)
        for ((idx, amt) in winnings) players[idx].stack += amt

        revealShowdown(winnings, reveal)

        val winnerIdxs = winnings.filter { it.value > 0 }.keys
        b.txtMessage.text = winnerIdxs.joinToString(" • ") { idx ->
            val hn = scores[idx]?.let { Poker.nameOfScore(it) } ?: ""
            "${players[idx].name}${if (hn.isNotEmpty()) " ($hn)" else ""} +${ChipManager.format(winnings[idx]!!)}"
        }
        if ((winnings[0] ?: 0) > 0) {
            b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))
            SoundEngine.bigWin()
        } else {
            b.txtMessage.setTextColor(resources.getColor(R.color.lose_red, theme))
            SoundEngine.lose()
        }

        players.forEach {
            if (!it.eliminated && it.stack <= 0L) { it.eliminated = true; seatViewFor(it.index)?.setEliminated() }
        }
        updateHumanInfo()
        refreshBalance(false)

        val remaining = players.count { !it.eliminated }
        when {
            players[0].eliminated -> endTable(false)
            remaining <= 1 -> endTable(true)
            else -> { b.btnNextHand.text = "Hand Berikutnya"; b.btnNextHand.visibility = View.VISIBLE; b.btnLeave.visibility = View.VISIBLE }
        }
    }

    private fun distribute(scores: Map<Int, Long>, contenders: List<P>): Map<Int, Long> {
        val win = HashMap<Int, Long>()
        val contrib = LongArray(N) { players[it].totalContribution }
        if (contenders.size == 1) { win[contenders[0].index] = contrib.sum(); return win }
        while (contrib.any { it > 0 }) {
            val minC = contrib.filter { it > 0 }.minOrNull()!!
            var layer = 0L
            val participants = mutableListOf<Int>()
            for (i in 0 until N) if (contrib[i] > 0) { contrib[i] -= minC; layer += minC; participants.add(i) }
            val eligible = participants.filter { !players[it].folded && !players[it].eliminated && scores.containsKey(it) }
            if (eligible.isEmpty()) {
                val share = layer / participants.size
                participants.forEach { win[it] = (win[it] ?: 0) + share }
            } else {
                val best = eligible.maxOf { scores[it]!! }
                val winners = eligible.filter { scores[it] == best }
                val share = layer / winners.size
                winners.forEach { win[it] = (win[it] ?: 0) + share }
                val rem = layer - share * winners.size
                if (rem > 0) win[winners[0]] = (win[winners[0]] ?: 0) + rem
            }
        }
        return win
    }

    private fun endTable(win: Boolean) {
        tableActive = false
        val finalStack = players[0].stack
        if (win) {
            ChipManager.add(this, finalStack)
            b.txtMessage.setTextColor(resources.getColor(R.color.gold, theme))
            b.txtMessage.text = "\uD83C\uDFC6 Anda memenangkan meja! Cash out ${ChipManager.format(finalStack)}"
        } else {
            b.txtMessage.setTextColor(resources.getColor(R.color.lose_red, theme))
            b.txtMessage.text = "Anda tereliminasi. Coba meja baru!"
        }
        players[0].stack = 0
        refreshBalance(true)
        returnToSetup = true
        b.btnLeave.visibility = View.GONE
        b.btnNextHand.text = "Meja Baru"
        b.btnNextHand.visibility = View.VISIBLE
    }

    private fun onNextHand() {
        if (returnToSetup) {
            returnToSetup = false
            b.tablePanel.visibility = View.GONE
            b.setupPanel.visibility = View.VISIBLE
            b.btnNextHand.text = "Hand Berikutnya"
            b.sliderBlind.setBalanceCap(ChipManager.getBalance(this) / BUYIN_BB)
            updateBuyin()
        } else {
            startHand()
        }
    }

    // ---------------- Render ----------------

    private fun seatViewFor(i: Int): SeatView? = when (i) {
        1 -> b.seatB1; 2 -> b.seatB2; 3 -> b.seatB3; 4 -> b.seatB4; else -> null
    }

    private fun botName(i: Int): String =
        players[i].name + if (dealer == i) "  \u24B9" else ""

    private fun renderSeatsInitial() {
        for (i in 1..4) {
            val p = players[i]; val sv = seatViewFor(i)!!
            if (p.eliminated) { sv.setEliminated(); continue }
            sv.reset(); sv.clearWinner()
            sv.setName(botName(i))
            sv.setStack(p.stack)
            sv.setBet(0)
            sv.setCards(false, null, null, false)
            sv.setActive(false)
        }
        updateHumanInfo()
    }

    private fun renderHumanArea() {
        b.playerCards.removeAllViews()
        players[0].hole.forEachIndexed { idx, c ->
            val v = CardViews.faceUp(this, c)
            b.playerCards.addView(v)
            Anim.dealIn(v, idx * 90L)
        }
        updateHumanInfo()
    }

    private fun renderCommunity() {
        while (communityShown < community.size) {
            val v = CardViews.faceUp(this, community[communityShown])
            b.communityCards.addView(v)
            Anim.dealIn(v, (communityShown % 3) * 60L)
            communityShown++
        }
    }

    private fun refreshSeats() {
        for (i in 1..4) {
            val p = players[i]; val sv = seatViewFor(i)!!
            if (p.eliminated) continue
            sv.setName(botName(i))
            sv.setStack(p.stack)
            sv.setBet(p.roundContribution)
            sv.setFolded(p.folded)
            sv.setActive(handActive && currentActor == i && !p.folded && !p.allIn)
        }
        updateHumanInfo()
    }

    private fun revealShowdown(winnings: Map<Int, Long>, reveal: Boolean) {
        for (i in 1..4) {
            val p = players[i]; val sv = seatViewFor(i)!!
            if (p.eliminated) continue
            sv.setActive(false)
            sv.setBet(0)
            sv.setStack(p.stack)
            if (!p.folded && reveal) sv.setCards(true, p.hole.getOrNull(0), p.hole.getOrNull(1), false)
            if ((winnings[i] ?: 0) > 0) sv.setWinner()
        }
        if ((winnings[0] ?: 0) > 0) Anim.pulse(b.txtYouInfo)
        updateHumanInfo()
    }

    private fun updatePot() {
        b.txtPot.text = "Pot: ${ChipManager.format(potTotal())}    Taruhan: ${ChipManager.format(currentBet)}"
    }

    private fun updateHumanInfo() {
        if (!::players.isInitialized) return
        val p = players[0]
        val d = if (dealer == 0) "  \u24B9" else ""
        val st = when { p.eliminated -> " • OUT"; p.folded -> " • Fold"; p.allIn -> " • ALL-IN"; else -> "" }
        b.txtYouInfo.text = "${p.emoji} Anda — Stack: ${ChipManager.format(p.stack)}$d$st"
    }

    private fun updateHandName() {
        if (!::players.isInitialized) return
        val p = players[0]
        b.txtHandName.text = if (community.isNotEmpty() && !p.folded)
            "Tangan Anda: ${Poker.handName(p.hole, community)}" else ""
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
