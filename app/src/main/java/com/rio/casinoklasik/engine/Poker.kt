package com.rio.casinoklasik.engine

import kotlin.math.max
import kotlin.math.min

/**
 * Evaluasi tangan Texas Hold'em. Mengambil 5 kartu terbaik dari 5-7 kartu
 * dan mengembalikan skor Long yang bisa langsung dibandingkan.
 */
object Poker {

    private const val SLOT = 1_048_576L // 16^5, pengali kategori

    private fun rv(r: Rank): Int = when (r) {
        Rank.ACE -> 14
        Rank.KING -> 13
        Rank.QUEEN -> 12
        Rank.JACK -> 11
        else -> r.value // TWO..TEN
    }

    private val names = arrayOf(
        "Kartu Tinggi", "Satu Pair", "Dua Pair", "Three of a Kind",
        "Straight", "Flush", "Full House", "Four of a Kind", "Straight Flush"
    )

    private fun encode(category: Int, tb: List<Int>): Long {
        var v = category.toLong()
        for (i in 0 until 5) v = v * 16 + (tb.getOrElse(i) { 0 })
        return v
    }

    private fun score5(cards: List<Card>): Long {
        val ranks = cards.map { rv(it.rank) }.sortedDescending()
        val flush = cards.map { it.suit }.toSet().size == 1

        val distinct = ranks.distinct().sortedDescending()
        var straightHigh = 0
        if (distinct.size == 5) {
            if (distinct[0] - distinct[4] == 4) straightHigh = distinct[0]
            else if (distinct == listOf(14, 5, 4, 3, 2)) straightHigh = 5 // roda A-2-3-4-5
        }

        val counts = ranks.groupingBy { it }.eachCount()
        val ordered = counts.entries.sortedWith(
            compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key }
        )
        val pattern = ordered.map { it.value }
        val byRank = ordered.map { it.key }

        return when {
            straightHigh > 0 && flush -> encode(8, listOf(straightHigh))
            pattern[0] == 4 -> encode(7, byRank)
            pattern[0] == 3 && pattern.getOrElse(1) { 0 } == 2 -> encode(6, byRank)
            flush -> encode(5, ranks)
            straightHigh > 0 -> encode(4, listOf(straightHigh))
            pattern[0] == 3 -> encode(3, byRank)
            pattern[0] == 2 && pattern.getOrElse(1) { 0 } == 2 -> encode(2, byRank)
            pattern[0] == 2 -> encode(1, byRank)
            else -> encode(0, ranks)
        }
    }

    private fun combinations(n: Int, k: Int): List<IntArray> {
        val result = ArrayList<IntArray>()
        val combo = IntArray(k)
        fun build(start: Int, depth: Int) {
            if (depth == k) { result.add(combo.clone()); return }
            for (i in start until n) {
                combo[depth] = i
                build(i + 1, depth + 1)
            }
        }
        build(0, 0)
        return result
    }

    /** Skor terbaik dari 5-7 kartu (makin besar makin kuat). */
    fun evaluate(cards: List<Card>): Long {
        if (cards.size == 5) return score5(cards)
        var best = Long.MIN_VALUE
        for (c in combinations(cards.size, 5)) {
            val hand = listOf(cards[c[0]], cards[c[1]], cards[c[2]], cards[c[3]], cards[c[4]])
            val s = score5(hand)
            if (s > best) best = s
        }
        return best
    }

    fun categoryOf(score: Long): Int = (score / SLOT).toInt()

    fun handName(hole: List<Card>, community: List<Card>): String {
        val all = hole + community
        if (all.size < 5) return "-"
        return names[categoryOf(evaluate(all))]
    }

    fun nameOfScore(score: Long): String = names[categoryOf(score)]

    /** Perkiraan kekuatan tangan 0..1 untuk keputusan bot. */
    fun strength(hole: List<Card>, community: List<Card>): Double {
        if (community.isEmpty()) return preflop(hole)
        val cat = categoryOf(evaluate(hole + community))
        val high = rv(hole.maxByOrNull { rv(it.rank) }!!.rank)
        return (cat / 8.0 + high / 14.0 * 0.06).coerceIn(0.0, 1.0)
    }

    private fun preflop(hole: List<Card>): Double {
        val a = rv(hole[0].rank); val b = rv(hole[1].rank)
        val hi = max(a, b); val lo = min(a, b)
        val pair = a == b
        val suited = hole[0].suit == hole[1].suit
        var s = (hi + lo) / 28.0 * 0.5
        if (pair) s += 0.35 + hi / 14.0 * 0.15
        if (suited) s += 0.08
        if (!pair && hi - lo <= 2) s += 0.05
        return s.coerceIn(0.0, 1.0)
    }
}
