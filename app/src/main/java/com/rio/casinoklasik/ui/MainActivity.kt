package com.rio.casinoklasik.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rio.casinoklasik.R
import com.rio.casinoklasik.databinding.ActivityMainBinding
import com.rio.casinoklasik.engine.ChipManager
import com.rio.casinoklasik.games.BlackjackActivity
import com.rio.casinoklasik.games.ChessActivity
import com.rio.casinoklasik.games.DiceActivity
import com.rio.casinoklasik.games.DominoActivity
import com.rio.casinoklasik.games.PokerActivity
import com.rio.casinoklasik.games.RouletteActivity
import com.rio.casinoklasik.games.SlotActivity
import com.rio.casinoklasik.util.Anim
import com.rio.casinoklasik.util.SoundEngine

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var lastBalance = 0L

    private data class GameItem(
        val icon: String,
        val title: String,
        val desc: String,
        val target: Class<*>
    )

    private val games by lazy {
        listOf(
            GameItem("\uD83C\uDCA1", getString(R.string.game_blackjack), getString(R.string.desc_blackjack), BlackjackActivity::class.java),
            GameItem("\uD83C\uDFA1", getString(R.string.game_roulette), getString(R.string.desc_roulette), RouletteActivity::class.java),
            GameItem("\uD83C\uDFB0", getString(R.string.game_slot), getString(R.string.desc_slot), SlotActivity::class.java),
            GameItem("\uD83C\uDFB2", getString(R.string.game_dice), getString(R.string.desc_dice), DiceActivity::class.java),
            GameItem("\u2660\uFE0F", getString(R.string.game_poker), getString(R.string.desc_poker), PokerActivity::class.java),
            GameItem("\uD83C\uDC00", getString(R.string.game_domino), getString(R.string.desc_domino), DominoActivity::class.java),
            GameItem("\u265A", getString(R.string.game_chess), getString(R.string.desc_chess), ChessActivity::class.java)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        SoundEngine.init(this)

        b.rvGames.layoutManager = GridLayoutManager(this, 2)
        b.rvGames.adapter = GameAdapter()
        b.rvGames.isNestedScrollingEnabled = false

        updateSoundIcon()
        b.btnSound.setOnClickListener {
            val on = SoundEngine.toggle(this)
            updateSoundIcon()
            Anim.pop(b.btnSound)
            if (on) SoundEngine.chime()
        }

        b.btnBonus.setOnClickListener {
            Anim.pop(b.btnBonus)
            if (ChipManager.claimDailyBonus(this)) {
                SoundEngine.win()
                Toast.makeText(this, "Bonus harian +1.000 chip!", Toast.LENGTH_SHORT).show()
                animateBalance()
            } else {
                SoundEngine.push()
                Toast.makeText(this, "Bonus hari ini sudah diklaim.", Toast.LENGTH_SHORT).show()
            }
        }

        b.btnReset.setOnClickListener {
            Anim.pop(b.btnReset)
            AlertDialog.Builder(this)
                .setTitle("Reset Chip")
                .setMessage("Kembalikan saldo ke 5.000 chip?")
                .setPositiveButton("Reset") { _, _ ->
                    ChipManager.resetBalance(this)
                    SoundEngine.bet()
                    animateBalance()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        lastBalance = ChipManager.getBalance(this)
        b.txtBalance.text = ChipManager.format(lastBalance)
    }

    override fun onResume() {
        super.onResume()
        animateBalance()
    }

    private fun updateSoundIcon() {
        b.btnSound.text = if (SoundEngine.enabled) "\uD83D\uDD0A" else "\uD83D\uDD07"
    }

    private fun animateBalance() {
        val now = ChipManager.getBalance(this)
        if (now != lastBalance) {
            Anim.countTo(b.txtBalance, lastBalance, now)
            lastBalance = now
        } else {
            b.txtBalance.text = ChipManager.format(now)
        }
    }

    inner class GameAdapter : RecyclerView.Adapter<GameAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: TextView = v.findViewById(R.id.txtIcon)
            val title: TextView = v.findViewById(R.id.txtTitle)
            val desc: TextView = v.findViewById(R.id.txtDesc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_game, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val g = games[position]
            holder.icon.text = g.icon
            holder.title.text = g.title
            holder.desc.text = g.desc

            // Animasi masuk bertahap
            holder.itemView.alpha = 0f
            holder.itemView.scaleX = 0.8f
            holder.itemView.scaleY = 0.8f
            holder.itemView.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(position * 70L)
                .setInterpolator(OvershootInterpolator(1.5f))
                .setDuration(360).start()

            holder.itemView.setOnClickListener {
                SoundEngine.bet()
                Anim.pop(holder.itemView)
                holder.itemView.postDelayed({
                    startActivity(Intent(this@MainActivity, g.target))
                }, 120)
            }
        }

        override fun getItemCount() = games.size
    }
}
