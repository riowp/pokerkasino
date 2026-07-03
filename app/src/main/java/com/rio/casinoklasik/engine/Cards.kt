package com.rio.casinoklasik.engine

enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADES("\u2660", false),
    HEARTS("\u2665", true),
    DIAMONDS("\u2666", true),
    CLUBS("\u2663", false)
}

enum class Rank(val label: String, val value: Int) {
    ACE("A", 11),
    TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5),
    SIX("6", 6), SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9), TEN("10", 10),
    JACK("J", 10), QUEEN("Q", 10), KING("K", 10)
}

data class Card(val rank: Rank, val suit: Suit) {
    val label: String get() = rank.label
    val symbol: String get() = suit.symbol
    val isRed: Boolean get() = suit.isRed
}

/** Dek standar 52 kartu yang bisa dikocok dan dibagikan. */
class Deck {
    private val cards = ArrayDeque<Card>()

    fun reset() {
        cards.clear()
        for (s in Suit.values()) for (r in Rank.values()) cards.add(Card(r, s))
        cards.shuffle()
    }

    fun deal(): Card {
        if (cards.isEmpty()) reset()
        return cards.removeFirst()
    }
}

/** Hitung nilai tangan blackjack, memperlakukan As sebagai 11 lalu 1 bila bust. */
object Blackjack {
    fun value(hand: List<Card>): Int {
        var total = hand.sumOf { it.rank.value }
        var aces = hand.count { it.rank == Rank.ACE }
        while (total > 21 && aces > 0) {
            total -= 10
            aces--
        }
        return total
    }

    fun isBlackjack(hand: List<Card>): Boolean =
        hand.size == 2 && value(hand) == 21
}
