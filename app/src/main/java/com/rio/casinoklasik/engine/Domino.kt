package com.rio.casinoklasik.engine

/** Model kartu domino (double-six) untuk permainan Gaple. */
object Domino {

    data class Tile(val a: Int, val b: Int) {
        val isDouble: Boolean get() = a == b
        val pips: Int get() = a + b
        fun matches(v: Int): Boolean = a == v || b == v
        /** Nilai sisi lain dari [v]. Untuk double mengembalikan nilai yang sama. */
        fun other(v: Int): Int = if (a == v) b else a
    }

    /** Set lengkap 28 kartu domino (0-0 sampai 6-6). */
    fun fullSet(): MutableList<Tile> {
        val list = mutableListOf<Tile>()
        for (i in 0..6) for (j in i..6) list.add(Tile(i, j))
        return list
    }

    val DOUBLE_SIX = Tile(6, 6)
}
