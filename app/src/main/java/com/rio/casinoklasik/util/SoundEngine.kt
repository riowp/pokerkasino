package com.rio.casinoklasik.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Efek suara yang disintesis sepenuhnya di kode (tanpa file audio).
 * Mono 16-bit PCM diputar lewat AudioTrack. Ringan dan offline.
 */
object SoundEngine {

    private const val SR = 44100
    private const val PREF = "casino_prefs"
    private const val KEY_SOUND = "sound_on"

    @Volatile var enabled = true
        private set

    private val exec = Executors.newFixedThreadPool(6)
    private val cache = HashMap<String, ShortArray>()

    fun init(ctx: Context) {
        enabled = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_SOUND, true)
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        enabled = on
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SOUND, on).apply()
    }

    fun toggle(ctx: Context): Boolean {
        setEnabled(ctx, !enabled)
        return enabled
    }

    // ---------- Bentuk gelombang ----------
    private enum class Wave { SINE, SQUARE, TRI, NOISE }

    private class Note(
        val freq: Double,
        val dur: Double,
        val startAt: Double = 0.0,
        val vol: Double = 0.5,
        val wave: Wave = Wave.SINE,
        val decay: Double = 4.0   // makin besar makin cepat meredup (pluck)
    )

    private fun render(notes: List<Note>): ShortArray {
        val total = notes.maxOf { it.startAt + it.dur }
        val n = (total * SR).toInt().coerceAtLeast(1)
        val buf = DoubleArray(n)
        val rnd = Random(7)
        for (note in notes) {
            val start = (note.startAt * SR).toInt()
            val len = (note.dur * SR).toInt().coerceAtLeast(1)
            for (i in 0 until len) {
                val t = i.toDouble() / SR
                val phase = 2 * PI * note.freq * t
                val raw = when (note.wave) {
                    Wave.SINE -> sin(phase)
                    Wave.SQUARE -> if (sin(phase) >= 0) 1.0 else -1.0
                    Wave.TRI -> 2.0 / PI * kotlin.math.asin(sin(phase))
                    Wave.NOISE -> rnd.nextDouble(-1.0, 1.0)
                }
                // Envelope: attack cepat lalu decay eksponensial
                val prog = i.toDouble() / len
                val attack = (i / (SR * 0.004)).coerceAtMost(1.0)   // ~4 ms
                val env = attack * exp(-note.decay * prog)
                val idx = start + i
                if (idx < n) buf[idx] += raw * note.vol * env
            }
        }
        return ShortArray(n) {
            (buf[it].coerceIn(-1.0, 1.0) * 30000).toInt().toShort()
        }
    }

    private fun play(key: String, builder: () -> List<Note>) {
        if (!enabled) return
        val samples = synchronized(cache) { cache.getOrPut(key) { render(builder()) } }
        playSamples(samples)
    }

    /** Untuk suara yang berubah tiap kali (mis. tick dengan pitch berbeda) tanpa cache. */
    private fun playOnce(notes: List<Note>) {
        if (!enabled) return
        playSamples(render(notes))
    }

    private fun playSamples(samples: ShortArray) {
        exec.execute {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                val ms = samples.size * 1000L / SR + 60
                Thread.sleep(ms)
                track.release()
            } catch (_: Exception) {
                // Abaikan bila audio tidak tersedia
            }
        }
    }

    // ---------- Efek per aksi ----------

    fun bet() = play("bet") {
        listOf(
            Note(1500.0, 0.05, vol = 0.35, wave = Wave.NOISE, decay = 12.0),
            Note(900.0, 0.06, vol = 0.4, wave = Wave.TRI, decay = 9.0)
        )
    }

    fun deal() = play("deal") {
        listOf(Note(2600.0, 0.13, vol = 0.3, wave = Wave.NOISE, decay = 7.0))
    }

    fun flip() = play("flip") {
        listOf(
            Note(2000.0, 0.04, vol = 0.3, wave = Wave.NOISE, decay = 14.0),
            Note(1400.0, 0.05, startAt = 0.05, vol = 0.3, wave = Wave.NOISE, decay = 12.0)
        )
    }

    fun chime() = play("chime") {
        listOf(
            Note(1046.5, 0.35, vol = 0.4, wave = Wave.SINE, decay = 3.0),
            Note(2093.0, 0.35, vol = 0.15, wave = Wave.SINE, decay = 3.5)
        )
    }

    fun win() = play("win") {
        val d = 0.12
        listOf(
            Note(523.25, 0.18, startAt = 0 * d, vol = 0.45, wave = Wave.TRI, decay = 2.5),
            Note(659.25, 0.18, startAt = 1 * d, vol = 0.45, wave = Wave.TRI, decay = 2.5),
            Note(783.99, 0.18, startAt = 2 * d, vol = 0.45, wave = Wave.TRI, decay = 2.5),
            Note(1046.5, 0.35, startAt = 3 * d, vol = 0.5, wave = Wave.TRI, decay = 2.0)
        )
    }

    fun bigWin() = play("bigwin") {
        val d = 0.10
        val notes = mutableListOf<Note>()
        val seq = listOf(523.25, 659.25, 783.99, 1046.5, 1318.5, 1046.5, 1318.5, 1567.98)
        seq.forEachIndexed { i, f ->
            notes += Note(f, 0.16, startAt = i * d, vol = 0.45, wave = Wave.TRI, decay = 2.2)
            notes += Note(f * 2, 0.16, startAt = i * d, vol = 0.12, wave = Wave.SINE, decay = 2.5)
        }
        notes += Note(2093.0, 0.5, startAt = seq.size * d, vol = 0.4, wave = Wave.SINE, decay = 1.6)
        notes
    }

    fun lose() = play("lose") {
        listOf(
            Note(392.0, 0.18, startAt = 0.0, vol = 0.4, wave = Wave.TRI, decay = 2.0),
            Note(329.63, 0.18, startAt = 0.16, vol = 0.4, wave = Wave.TRI, decay = 2.0),
            Note(261.63, 0.32, startAt = 0.32, vol = 0.4, wave = Wave.TRI, decay = 1.8)
        )
    }

    fun push() = play("push") {
        listOf(Note(440.0, 0.2, vol = 0.35, wave = Wave.TRI, decay = 2.5))
    }

    /** Tick roulette: pitch sedikit acak agar hidup. Tidak di-cache. */
    fun tick() = playOnce(
        listOf(
            Note(2200.0 + Random.nextInt(-150, 150), 0.03, vol = 0.3, wave = Wave.NOISE, decay = 16.0),
            Note(1600.0, 0.03, vol = 0.25, wave = Wave.SQUARE, decay = 14.0)
        )
    )

    /** Bunyi gulungan slot berputar (deretan klik mekanis ~2,3 detik). */
    fun slotSpin() = play("slotspin") {
        val notes = mutableListOf<Note>()
        var t = 0.0
        while (t < 2.3) {
            notes += Note(2400.0, 0.02, startAt = t, vol = 0.28, wave = Wave.NOISE, decay = 18.0)
            notes += Note(520.0, 0.03, startAt = t, vol = 0.20, wave = Wave.SQUARE, decay = 12.0)
            t += 0.055
        }
        notes
    }

    fun reelStop() = play("reelstop") {
        listOf(
            Note(180.0, 0.09, vol = 0.5, wave = Wave.SQUARE, decay = 8.0),
            Note(90.0, 0.09, vol = 0.4, wave = Wave.SINE, decay = 7.0),
            Note(2000.0, 0.03, vol = 0.2, wave = Wave.NOISE, decay = 14.0)
        )
    }

    fun diceRoll() = play("dice") {
        val notes = mutableListOf<Note>()
        var t = 0.0
        repeat(7) {
            notes += Note(
                600.0 + Random.nextInt(-200, 200), 0.05,
                startAt = t, vol = 0.35, wave = Wave.NOISE, decay = 10.0
            )
            t += 0.06 + Random.nextDouble() * 0.02
        }
        notes
    }
}
