package com.rio.casinoklasik.engine

import android.content.Context
import java.text.NumberFormat
import java.util.Locale

/** Menyimpan dan mengelola saldo chip virtual secara lokal (offline). */
object ChipManager {

    private const val PREF = "casino_prefs"
    private const val KEY_BALANCE = "balance"
    private const val KEY_LAST_BONUS = "last_bonus_day"
    private const val START_BALANCE = 5_000L
    private const val DAILY_BONUS = 1_000L

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getBalance(ctx: Context): Long {
        val p = prefs(ctx)
        if (!p.contains(KEY_BALANCE)) {
            p.edit().putLong(KEY_BALANCE, START_BALANCE).apply()
        }
        return p.getLong(KEY_BALANCE, START_BALANCE)
    }

    fun setBalance(ctx: Context, value: Long) {
        prefs(ctx).edit().putLong(KEY_BALANCE, value.coerceAtLeast(0)).apply()
    }

    /** Tambah (positif) atau kurangi (negatif) saldo. Mengembalikan saldo baru. */
    fun add(ctx: Context, delta: Long): Long {
        val newVal = (getBalance(ctx) + delta).coerceAtLeast(0)
        setBalance(ctx, newVal)
        return newVal
    }

    fun canAfford(ctx: Context, amount: Long): Boolean = getBalance(ctx) >= amount

    fun resetBalance(ctx: Context) = setBalance(ctx, START_BALANCE)

    /** Bonus harian: dapat diklaim sekali per hari kalender. */
    fun claimDailyBonus(ctx: Context): Boolean {
        val p = prefs(ctx)
        val today = System.currentTimeMillis() / 86_400_000L
        val last = p.getLong(KEY_LAST_BONUS, -1L)
        if (last == today) return false
        p.edit().putLong(KEY_LAST_BONUS, today).apply()
        add(ctx, DAILY_BONUS)
        return true
    }

    fun format(value: Long): String =
        NumberFormat.getInstance(Locale("in", "ID")).format(value)
}
