package com.rio.casinoklasik.engine

import kotlin.math.abs
import kotlin.random.Random

/**
 * Engine catur minimal namun lengkap: pembuatan langkah legal (termasuk rokade,
 * en passant, promosi), deteksi skak/skakmat/remis, evaluasi posisi, dan
 * pencarian alpha-beta (negamax) untuk AI bot maupun fitur rekomendasi langkah.
 * Papan direpresentasikan IntArray(64): 0 kosong, positif = putih, negatif = hitam.
 * Indeks 0 = a1, 63 = h8 (rank = sq/8, file = sq%8). Putih bergerak ke rank lebih tinggi.
 */
object Chess {

    const val P = 1; const val N = 2; const val B = 3; const val R = 4; const val Q = 5; const val K = 6

    const val F_NORMAL = 0; const val F_DOUBLE = 1; const val F_EP = 2; const val F_CASTLE_K = 3; const val F_CASTLE_Q = 4

    const val ONGOING = 0; const val CHECKMATE = 1; const val STALEMATE = 2; const val DRAW_MATERIAL = 3; const val DRAW_50 = 4

    data class Move(val from: Int, val to: Int, val promo: Int = 0, val flag: Int = 0)

    class State {
        val board = IntArray(64)
        var whiteToMove = true
        val castle = booleanArrayOf(true, true, true, true) // wK, wQ, bK, bQ
        var ep = -1
        var halfmove = 0
        fun copy(): State {
            val s = State()
            System.arraycopy(board, 0, s.board, 0, 64)
            s.whiteToMove = whiteToMove
            s.castle[0] = castle[0]; s.castle[1] = castle[1]; s.castle[2] = castle[2]; s.castle[3] = castle[3]
            s.ep = ep; s.halfmove = halfmove
            return s
        }
    }

    fun initial(): State {
        val s = State()
        val back = intArrayOf(R, N, B, Q, K, B, N, R)
        for (f in 0..7) {
            s.board[f] = back[f]
            s.board[8 + f] = P
            s.board[48 + f] = -P
            s.board[56 + f] = -back[f]
        }
        return s
    }

    private fun onBoard(f: Int, r: Int) = f in 0..7 && r in 0..7

    private val knightDeltas = arrayOf(
        intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(-1, 2), intArrayOf(-2, 1),
        intArrayOf(1, -2), intArrayOf(2, -1), intArrayOf(-1, -2), intArrayOf(-2, -1)
    )
    private val diagDirs = arrayOf(intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1))
    private val orthoDirs = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

    /** Apakah kotak [sq] diserang oleh pihak [byWhite]? */
    fun attacked(board: IntArray, sq: Int, byWhite: Boolean): Boolean {
        val f = sq % 8; val r = sq / 8
        val pr = if (byWhite) r - 1 else r + 1
        if (pr in 0..7) {
            for (df in intArrayOf(-1, 1)) {
                val nf = f + df
                if (nf in 0..7) {
                    val pc = board[pr * 8 + nf]
                    if (byWhite && pc == P) return true
                    if (!byWhite && pc == -P) return true
                }
            }
        }
        val kn = if (byWhite) N else -N
        for (d in knightDeltas) {
            val nf = f + d[0]; val nr = r + d[1]
            if (onBoard(nf, nr) && board[nr * 8 + nf] == kn) return true
        }
        val kk = if (byWhite) K else -K
        for (dr in -1..1) for (df in -1..1) {
            if (df == 0 && dr == 0) continue
            val nf = f + df; val nr = r + dr
            if (onBoard(nf, nr) && board[nr * 8 + nf] == kk) return true
        }
        val bishop = if (byWhite) B else -B
        val queen = if (byWhite) Q else -Q
        for (d in diagDirs) {
            var nf = f + d[0]; var nr = r + d[1]
            while (onBoard(nf, nr)) {
                val pc = board[nr * 8 + nf]
                if (pc != 0) { if (pc == bishop || pc == queen) return true; break }
                nf += d[0]; nr += d[1]
            }
        }
        val rook = if (byWhite) R else -R
        for (d in orthoDirs) {
            var nf = f + d[0]; var nr = r + d[1]
            while (onBoard(nf, nr)) {
                val pc = board[nr * 8 + nf]
                if (pc != 0) { if (pc == rook || pc == queen) return true; break }
                nf += d[0]; nr += d[1]
            }
        }
        return false
    }

    fun findKing(board: IntArray, white: Boolean): Int {
        val k = if (white) K else -K
        for (i in 0..63) if (board[i] == k) return i
        return -1
    }

    fun inCheck(s: State, white: Boolean): Boolean {
        val ks = findKing(s.board, white)
        return ks >= 0 && attacked(s.board, ks, !white)
    }

    private fun pseudoMoves(s: State): ArrayList<Move> {
        val moves = ArrayList<Move>(48)
        val white = s.whiteToMove
        val board = s.board
        for (sq in 0..63) {
            val pc = board[sq]
            if (pc == 0 || (pc > 0) != white) continue
            val type = abs(pc)
            val f = sq % 8; val r = sq / 8
            when (type) {
                P -> genPawn(s, sq, f, r, white, moves)
                N -> for (d in knightDeltas) {
                    val nf = f + d[0]; val nr = r + d[1]
                    if (onBoard(nf, nr)) { val t = board[nr * 8 + nf]; if (t == 0 || (t > 0) != white) moves.add(Move(sq, nr * 8 + nf)) }
                }
                B -> slide(board, sq, f, r, white, diagDirs, moves)
                R -> slide(board, sq, f, r, white, orthoDirs, moves)
                Q -> { slide(board, sq, f, r, white, diagDirs, moves); slide(board, sq, f, r, white, orthoDirs, moves) }
                K -> genKing(s, sq, f, r, white, moves)
            }
        }
        return moves
    }

    private fun slide(board: IntArray, sq: Int, f: Int, r: Int, white: Boolean, dirs: Array<IntArray>, moves: ArrayList<Move>) {
        for (d in dirs) {
            var nf = f + d[0]; var nr = r + d[1]
            while (onBoard(nf, nr)) {
                val t = board[nr * 8 + nf]
                if (t == 0) moves.add(Move(sq, nr * 8 + nf))
                else { if ((t > 0) != white) moves.add(Move(sq, nr * 8 + nf)); break }
                nf += d[0]; nr += d[1]
            }
        }
    }

    private fun genPawn(s: State, sq: Int, f: Int, r: Int, white: Boolean, moves: ArrayList<Move>) {
        val board = s.board
        val dir = if (white) 1 else -1
        val startRank = if (white) 1 else 6
        val promoRank = if (white) 7 else 0
        val one = r + dir
        if (one in 0..7 && board[one * 8 + f] == 0) {
            if (one == promoRank) addPromos(sq, one * 8 + f, moves) else moves.add(Move(sq, one * 8 + f))
            if (r == startRank) {
                val two = r + 2 * dir
                if (board[two * 8 + f] == 0) moves.add(Move(sq, two * 8 + f, 0, F_DOUBLE))
            }
        }
        for (df in intArrayOf(-1, 1)) {
            val nf = f + df; val nr = r + dir
            if (onBoard(nf, nr)) {
                val target = nr * 8 + nf
                val t = board[target]
                if (t != 0 && (t > 0) != white) {
                    if (nr == promoRank) addPromos(sq, target, moves) else moves.add(Move(sq, target))
                } else if (target == s.ep && s.ep >= 0) {
                    moves.add(Move(sq, target, 0, F_EP))
                }
            }
        }
    }

    private fun addPromos(from: Int, to: Int, moves: ArrayList<Move>) {
        moves.add(Move(from, to, Q)); moves.add(Move(from, to, R)); moves.add(Move(from, to, B)); moves.add(Move(from, to, N))
    }

    private fun genKing(s: State, sq: Int, f: Int, r: Int, white: Boolean, moves: ArrayList<Move>) {
        val board = s.board
        for (dr in -1..1) for (df in -1..1) {
            if (df == 0 && dr == 0) continue
            val nf = f + df; val nr = r + dr
            if (onBoard(nf, nr)) { val t = board[nr * 8 + nf]; if (t == 0 || (t > 0) != white) moves.add(Move(sq, nr * 8 + nf)) }
        }
        val rights = s.castle
        if (white && sq == 4) {
            if (rights[0] && board[5] == 0 && board[6] == 0 && board[7] == R &&
                !attacked(board, 4, false) && !attacked(board, 5, false) && !attacked(board, 6, false)
            ) moves.add(Move(4, 6, 0, F_CASTLE_K))
            if (rights[1] && board[3] == 0 && board[2] == 0 && board[1] == 0 && board[0] == R &&
                !attacked(board, 4, false) && !attacked(board, 3, false) && !attacked(board, 2, false)
            ) moves.add(Move(4, 2, 0, F_CASTLE_Q))
        } else if (!white && sq == 60) {
            if (rights[2] && board[61] == 0 && board[62] == 0 && board[63] == -R &&
                !attacked(board, 60, true) && !attacked(board, 61, true) && !attacked(board, 62, true)
            ) moves.add(Move(60, 62, 0, F_CASTLE_K))
            if (rights[3] && board[59] == 0 && board[58] == 0 && board[57] == 0 && board[56] == -R &&
                !attacked(board, 60, true) && !attacked(board, 59, true) && !attacked(board, 58, true)
            ) moves.add(Move(60, 58, 0, F_CASTLE_Q))
        }
    }

    fun makeMove(s: State, m: Move): State {
        val n = s.copy()
        val b = n.board
        val pc = b[m.from]
        val white = pc > 0
        val type = abs(pc)
        val captured = b[m.to]
        n.ep = -1
        n.halfmove = if (type == P || captured != 0) 0 else n.halfmove + 1
        b[m.to] = pc
        b[m.from] = 0
        when (m.flag) {
            F_DOUBLE -> n.ep = m.from + if (white) 8 else -8
            F_EP -> { val capSq = m.to + if (white) -8 else 8; b[capSq] = 0 }
            F_CASTLE_K -> if (white) { b[5] = R; b[7] = 0 } else { b[61] = -R; b[63] = 0 }
            F_CASTLE_Q -> if (white) { b[3] = R; b[0] = 0 } else { b[59] = -R; b[56] = 0 }
        }
        if (m.promo != 0) b[m.to] = if (white) m.promo else -m.promo
        if (type == K) { if (white) { n.castle[0] = false; n.castle[1] = false } else { n.castle[2] = false; n.castle[3] = false } }
        if (m.from == 0 || m.to == 0) n.castle[1] = false
        if (m.from == 7 || m.to == 7) n.castle[0] = false
        if (m.from == 56 || m.to == 56) n.castle[3] = false
        if (m.from == 63 || m.to == 63) n.castle[2] = false
        n.whiteToMove = !white
        return n
    }

    fun legalMoves(s: State): List<Move> {
        val pseudo = pseudoMoves(s)
        val res = ArrayList<Move>(pseudo.size)
        val white = s.whiteToMove
        for (m in pseudo) {
            val n = makeMove(s, m)
            val kingSq = findKing(n.board, white)
            if (kingSq >= 0 && !attacked(n.board, kingSq, !white)) res.add(m)
        }
        return res
    }

    fun status(s: State): Int {
        val legal = legalMoves(s)
        if (legal.isEmpty()) return if (inCheck(s, s.whiteToMove)) CHECKMATE else STALEMATE
        if (insufficientMaterial(s.board)) return DRAW_MATERIAL
        if (s.halfmove >= 100) return DRAW_50
        return ONGOING
    }

    private fun insufficientMaterial(board: IntArray): Boolean {
        var minor = 0
        for (pc in board) {
            if (pc == 0) continue
            when (abs(pc)) {
                P, R, Q -> return false
                N, B -> minor++
            }
        }
        return minor <= 1
    }

    // ---------------- Evaluasi & pencarian ----------------

    private val VAL = intArrayOf(0, 100, 320, 330, 500, 900, 0)

    private val PST_P = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, -20, -20, 10, 10, 5,
        5, -5, -10, 0, 0, -10, -5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, 5, 10, 25, 25, 10, 5, 5,
        10, 10, 20, 30, 30, 20, 10, 10,
        50, 50, 50, 50, 50, 50, 50, 50,
        0, 0, 0, 0, 0, 0, 0, 0
    )
    private val PST_N = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50
    )
    private val PST_B = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -20, -10, -10, -10, -10, -10, -10, -20
    )
    private val PST_R = intArrayOf(
        0, 0, 0, 5, 5, 0, 0, 0,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        5, 10, 10, 10, 10, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0
    )
    private val PST_Q = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20
    )
    private val PST_K = intArrayOf(
        20, 30, 10, 0, 0, 10, 30, 20,
        20, 20, 0, 0, 0, 0, 20, 20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30
    )

    private fun pst(type: Int, sq: Int, white: Boolean): Int {
        val i = if (white) sq else sq xor 56
        return when (type) {
            P -> PST_P[i]; N -> PST_N[i]; B -> PST_B[i]; R -> PST_R[i]; Q -> PST_Q[i]; K -> PST_K[i]
            else -> 0
        }
    }

    /** Skor dari sudut pandang putih (positif = putih unggul). */
    fun eval(s: State): Int {
        var score = 0
        for (sq in 0..63) {
            val pc = s.board[sq]
            if (pc == 0) continue
            val t = abs(pc)
            val v = VAL[t] + pst(t, sq, pc > 0)
            if (pc > 0) score += v else score -= v
        }
        return score
    }

    private const val INF = 1_000_000
    private const val MATE = 100_000

    private fun evalStm(s: State): Int { val e = eval(s); return if (s.whiteToMove) e else -e }

    private fun orderedMoves(s: State): List<Move> {
        val moves = legalMoves(s)
        return moves.sortedByDescending { m -> val v = s.board[m.to]; if (v != 0) VAL[abs(v)] else 0 }
    }

    private fun negamax(s: State, depth: Int, alphaIn: Int, beta: Int): Int {
        if (depth == 0) return evalStm(s)
        val moves = orderedMoves(s)
        if (moves.isEmpty()) return if (inCheck(s, s.whiteToMove)) -MATE - depth else 0
        if (s.halfmove >= 100 || insufficientMaterial(s.board)) return 0
        var alpha = alphaIn
        var best = -INF
        for (m in moves) {
            val sc = -negamax(makeMove(s, m), depth - 1, -beta, -alpha)
            if (sc > best) best = sc
            if (best > alpha) alpha = best
            if (alpha >= beta) break
        }
        return best
    }

    /**
     * Cari langkah terbaik. [randomnessCp] = lebar jendela (centipawn) untuk memilih
     * secara acak di antara langkah yang mendekati terbaik (untuk level mudah).
     */
    fun bestMove(s: State, depth: Int, randomnessCp: Int): Move? {
        val moves = orderedMoves(s)
        if (moves.isEmpty()) return null
        val scored = ArrayList<Pair<Move, Int>>(moves.size)
        var bestScore = -INF
        var alpha = -INF
        val beta = INF
        for (m in moves) {
            val sc = -negamax(makeMove(s, m), depth - 1, -beta, -alpha)
            scored.add(Pair(m, sc))
            if (sc > bestScore) bestScore = sc
            if (sc > alpha) alpha = sc
        }
        if (randomnessCp <= 0) return scored.maxByOrNull { it.second }!!.first
        val pool = scored.filter { it.second >= bestScore - randomnessCp }
        return if (pool.isEmpty()) scored.maxByOrNull { it.second }!!.first
        else pool[Random.nextInt(pool.size)].first
    }

    fun squareName(sq: Int): String {
        val f = sq % 8; val r = sq / 8
        return "${('a' + f)}${r + 1}"
    }
}
