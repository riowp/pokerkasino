package com.rio.casinoklasik.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.rio.casinoklasik.engine.Card

object CardViews {

    private fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()

    /** Kartu terbuka menampilkan rank + suit. */
    fun faceUp(ctx: Context, card: Card): View {
        val cv = CardView(ctx).apply {
            radius = dp(ctx, 8).toFloat()
            cardElevation = dp(ctx, 3).toFloat()
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 62), dp(ctx, 88)).apply {
                marginEnd = dp(ctx, 6)
            }
        }
        val color = if (card.isRed) Color.parseColor("#D32F2F") else Color.parseColor("#212121")
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val rank = TextView(ctx).apply {
            text = card.label
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val suit = TextView(ctx).apply {
            text = card.symbol
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.CENTER
        }
        container.addView(rank)
        container.addView(suit)
        cv.addView(container)
        return cv
    }

    /** Kartu tertutup (punggung kartu). */
    fun faceDown(ctx: Context): View {
        val cv = CardView(ctx).apply {
            radius = dp(ctx, 8).toFloat()
            cardElevation = dp(ctx, 3).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A237E"))
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 62), dp(ctx, 88)).apply {
                marginEnd = dp(ctx, 6)
            }
        }
        val inner = TextView(ctx).apply {
            text = "\u2663"
            setTextColor(Color.parseColor("#3949AB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        cv.addView(inner)
        return cv
    }
}
