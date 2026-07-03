package com.rio.casinoklasik.games

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rio.casinoklasik.R
import com.rio.casinoklasik.databinding.ActivityBlackjackBinding
import com.rio.casinoklasik.engine.Blackjack
import com.rio.casinoklasik.engine.Card
import com.rio.casinoklasik.engine.ChipManager
import com.rio.casinoklasik.engine.Deck
import com.rio.casinoklasik.util.Anim
import com.rio.casinoklasik.util.CardViews
import com.rio.casinoklasik.util.SoundEngine

class BlackjackActivity : AppCompatActivity() {

    private lateinit var b: ActivityBlackjackBinding
    private val deck = Deck()
    private val handler = Handler(Looper.getMainLooper())

    private val player = mutableListOf<Card>()
    private val dealer = mutableListOf<Card>()

    private var bet = 0L
    private var inRound = false
    private var doubled = false
    private var displayed = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBlackjackBinding.inflate(layoutInflater)
        setContentView(b.root)
        SoundEngine.init(this)

        b.sliderBet.setPercentMode(ChipManager.getBalance(this))
        b.sliderBet.onChange = { SoundEngine.tick() }
        b.btnDeal.setOnClickListener { Anim.pop(it); startRound() }

        b.btnHit.setOnClickListener { Anim.pop(it); hit() }
        b.btnStand.setOnClickListener { Anim.pop(it); stand() }
        b.btnDouble.setOnClickListener { Anim.pop(it); doubleDown() }

        displayed = ChipManager.getBalance(this)
        b.txtBalance.text = ChipManager.format(displayed)
    }

    private fun startRound() {
        bet = b.sliderBet.value
        if (bet <= 0) { Toast.makeText(this, "Pasang taruhan dulu", Toast.LENGTH_SHORT).show(); return }
        if (!ChipManager.canAfford(this, bet)) { Toast.makeText(this, "Saldo tidak cukup", Toast.LENGTH_SHORT).show(); return }

        SoundEngine.bet()
        ChipManager.add(this, -bet)
        refreshBalance(true)

        deck.reset()
        player.clear(); dealer.clear()
        doubled = false
        inRound = true

        player.add(deck.deal()); player.add(deck.deal())
        dealer.add(deck.deal()); dealer.add(deck.deal())

        b.betPanel.visibility = View.GONE
        b.actionPanel.visibility = View.VISIBLE
        setActionsEnabled(true)
        b.btnDouble.isEnabled = ChipManager.canAfford(this, bet)
        b.txtMessage.text = ""

        // Bagikan berurutan dengan animasi
        b.playerCards.removeAllViews()
        b.dealerCards.removeAllViews()
        b.txtPlayerScore.text = "-"
        b.txtDealerScore.text = "-"

        dealCard(b.playerCards, CardViews.faceUp(this, player[0]), 0) { updatePlayerScore() }
        dealCard(b.dealerCards, CardViews.faceUp(this, dealer[0]), 180) {
            b.txtDealerScore.text = "${dealer[0].rank.value} + ?"
        }
        dealCard(b.playerCards, CardViews.faceUp(this, player[1]), 360) { updatePlayerScore() }
        dealCard(b.dealerCards, CardViews.faceDown(this), 540) {
            if (Blackjack.isBlackjack(player)) {
                b.txtMessage.text = "Blackjack!"
                handler.postDelayed({ stand() }, 500)
            }
        }
    }

    private fun dealCard(container: android.widget.LinearLayout, view: View, delay: Long, onShown: () -> Unit) {
        container.addView(view)
        Anim.dealIn(view, delay)
        handler.postDelayed({ SoundEngine.deal(); onShown() }, delay)
    }

    private fun hit() {
        if (!inRound) return
        val c = deck.deal(); player.add(c)
        val v = CardViews.faceUp(this, c)
        b.playerCards.addView(v)
        Anim.dealIn(v)
        SoundEngine.deal()
        updatePlayerScore()
        b.btnDouble.isEnabled = false
        if (Blackjack.value(player) > 21) handler.postDelayed({ endRound() }, 400)
    }

    private fun doubleDown() {
        if (!inRound || doubled || player.size != 2) return
        if (!ChipManager.canAfford(this, bet)) { Toast.makeText(this, "Saldo tidak cukup untuk double", Toast.LENGTH_SHORT).show(); return }
        ChipManager.add(this, -bet)
        bet *= 2
        doubled = true
        refreshBalance(true)
        updateBet()

        val c = deck.deal(); player.add(c)
        val v = CardViews.faceUp(this, c)
        b.playerCards.addView(v)
        Anim.dealIn(v)
        SoundEngine.deal()
        updatePlayerScore()
        setActionsEnabled(false)
        handler.postDelayed({
            if (Blackjack.value(player) > 21) endRound() else stand()
        }, 500)
    }

    private fun stand() {
        if (!inRound) return
        setActionsEnabled(false)
        revealHole()
        handler.postDelayed({ dealerStep() }, 700)
    }

    private fun revealHole() {
        val old = b.dealerCards.getChildAt(1) ?: return
        old.animate().scaleX(0f).setDuration(150).withEndAction {
            b.dealerCards.removeViewAt(1)
            val nv = CardViews.faceUp(this, dealer[1])
            nv.scaleX = 0f
            b.dealerCards.addView(nv, 1)
            nv.animate().scaleX(1f).setDuration(150).start()
            SoundEngine.flip()
            b.txtDealerScore.text = Blackjack.value(dealer).toString()
        }.start()
    }

    private fun dealerStep() {
        if (Blackjack.value(dealer) < 17) {
            val c = deck.deal(); dealer.add(c)
            val v = CardViews.faceUp(this, c)
            b.dealerCards.addView(v)
            Anim.dealIn(v)
            SoundEngine.deal()
            b.txtDealerScore.text = Blackjack.value(dealer).toString()
            handler.postDelayed({ dealerStep() }, 600)
        } else {
            handler.postDelayed({ endRound() }, 350)
        }
    }

    private fun endRound() {
        inRound = false

        val pv = Blackjack.value(player)
        val dv = Blackjack.value(dealer)
        val pBj = Blackjack.isBlackjack(player)
        val dBj = Blackjack.isBlackjack(dealer)

        val (msg, payout) = when {
            pv > 21 -> "BUST! Kamu kalah" to 0L
            pBj && !dBj -> "BLACKJACK! Menang 3:2" to (bet + bet * 3 / 2)
            dv > 21 -> "Bandar bust! Kamu menang" to bet * 2
            pv > dv -> "Kamu menang!" to bet * 2
            pv < dv -> "Bandar menang" to 0L
            else -> "Seri (push)" to bet
        }

        if (payout > 0) ChipManager.add(this, payout)
        val net = payout - bet

        b.txtMessage.text = msg
        b.txtMessage.setTextColor(
            resources.getColor(
                when {
                    net > 0 -> R.color.gold
                    net < 0 -> R.color.lose_red
                    else -> R.color.white
                }, theme
            )
        )
        Anim.pulse(b.txtMessage)

        when {
            pBj && net > 0 -> SoundEngine.bigWin()
            net > 0 -> SoundEngine.win()
            net < 0 -> { SoundEngine.lose(); Anim.shake(b.txtMessage) }
            else -> SoundEngine.push()
        }

        refreshBalance(true)

        bet = 0
        b.actionPanel.visibility = View.GONE
        b.betPanel.visibility = View.VISIBLE
        updateBet()
    }

    private fun updatePlayerScore() { b.txtPlayerScore.text = Blackjack.value(player).toString() }

    private fun setActionsEnabled(enabled: Boolean) {
        b.btnHit.isEnabled = enabled
        b.btnStand.isEnabled = enabled
        b.btnDouble.isEnabled = enabled && !doubled && player.size == 2
    }

    private fun updateBet() {
        b.txtBet.text = "Taruhan (% dari total chip)"
        b.sliderBet.setBalanceCap(ChipManager.getBalance(this))
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
