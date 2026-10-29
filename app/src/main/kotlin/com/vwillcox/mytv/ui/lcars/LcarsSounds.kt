package com.vwillcox.mytv.ui.lcars

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.vwillcox.mytv.ui.UiSounds
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The panel's chirps, made here rather than recorded: short tones with a little overtone, a quick rise and a soft
 * tail, the way a Starfleet console answers a touch. Each is rendered once into a track of its own, so it plays the
 * instant it's asked for; all the work happens on one background thread.
 */
internal class LcarsSounds : UiSounds {
    private class Note(val hz: Double, val ms: Int, val gain: Double = 1.0)

    private val rate = 44_100
    private val worker = Executors.newSingleThreadExecutor()
    private var tracks = emptyMap<String, AudioTrack>()

    init {
        later {
            tracks = mapOf(
                "select" to track(Note(1480.0, 34), Note(1976.0, 54)),
                "tab" to track(Note(1245.0, 26, 0.5)),
                "close" to track(Note(1976.0, 30), Note(1319.0, 56)),
                "boot" to track(Note(784.0, 50, 0.7), Note(1047.0, 50, 0.8), Note(1319.0, 50, 0.9), Note(1568.0, 56), Note(2093.0, 150)),
            )
        }
    }

    override fun select() = play("select")
    override fun tab() = play("tab")
    override fun close() = play("close")
    override fun boot() = play("boot")

    fun release() = later {
        tracks.values.forEach { it.release() }
        tracks = emptyMap()
        worker.shutdown()
    }

    private fun play(name: String) = later {
        tracks[name]?.let { track -> track.stop(); track.reloadStaticData(); track.play() }
    }

    /** On the worker, where a failure (no audio output, say) costs only the sound: thrown there, it would end the app. */
    private fun later(block: () -> Unit) {
        if (!worker.isShutdown) runCatching { worker.execute { runCatching(block) } }
    }

    private fun track(vararg notes: Note): AudioTrack {
        val pcm = render(notes.toList())
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .apply { write(pcm, 0, pcm.size) }
    }

    /** The notes one after another, a few milliseconds apart, each with a 3 ms rise and a curved fall to silence. */
    private fun render(notes: List<Note>): ShortArray {
        val gap = rate * 6 / 1000
        val out = ShortArray(notes.sumOf { it.ms * rate / 1000 + gap })
        var at = 0
        for (note in notes) {
            val length = note.ms * rate / 1000
            val rise = rate * 3 / 1000
            val fall = min(length / 2, rate * 24 / 1000)
            for (i in 0 until length) {
                val envelope = when {
                    i < rise -> i.toDouble() / rise
                    i > length - fall -> ((length - i).toDouble() / fall).let { it * it }
                    else -> 1.0
                }
                val w = 2 * PI * note.hz * i / rate
                val wave = (sin(w) + 0.22 * sin(2 * w) + 0.08 * sin(3 * w)) / 1.3
                out[at + i] = (wave * envelope * note.gain * VOLUME * Short.MAX_VALUE).toInt().toShort()
            }
            at += length + gap
        }
        return out
    }

    private companion object {
        const val VOLUME = 0.28
    }
}
