package com.vatsyahub.workout.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.vatsyahub.workout.R
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

class Tones(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun ring() = play(beats(3, 0.16, 0.09) { tone(980.0, 0.16, 0.72, listOf(1960.0, 2940.0)) })

    fun whistle() {
        val clip = try {
            peaBlast()
        } catch (_: Exception) {
            return
        }
        play(clip.samples, clip.rate)
    }

    fun bell() = play(strike(420.0, 0.95, 0.95, listOf(840.0, 1260.0, 1680.0)))

    fun drum() = play(thump())

    fun horn() = play(sustain(196.0, 1.35, 0.7, listOf(294.0, 392.0)))

    private fun play(samples: ShortArray, rate: Int = RATE) {
        if (samples.isEmpty()) return
        executor.execute {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(samples.size * 1000L / rate + 30)
                track.release()
            } catch (_: Exception) {
                // A missing audio device should not stop the countdown.
            }
        }
    }

    private fun beats(count: Int, seconds: Double, gap: Double, one: () -> ShortArray): ShortArray {
        val piece = one()
        val silence = ShortArray((RATE * gap).toInt())
        val out = ArrayList<Short>(count * (piece.size + silence.size))
        repeat(count) { index ->
            out.addAll(piece.asList())
            if (index != count - 1) out.addAll(silence.asList())
        }
        return out.toShortArray()
    }

    private fun tone(hz: Double, seconds: Double, volume: Double, harmonics: List<Double>): ShortArray {
        val count = (RATE * seconds).toInt().coerceAtLeast(1)
        return ShortArray(count) { index ->
            val t = index.toDouble() / count
            val env = (1.0 - t).let { it * it }
            var sample = sin(2.0 * PI * hz * index / RATE)
            harmonics.forEachIndexed { nth, over ->
                sample += sin(2.0 * PI * over * index / RATE) * (0.35 / (nth + 2))
            }
            (sample * 32767.0 * volume * env).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun strike(hz: Double, seconds: Double, volume: Double, harmonics: List<Double>): ShortArray {
        val count = (RATE * seconds).toInt().coerceAtLeast(1)
        return ShortArray(count) { index ->
            val t = index.toDouble() / RATE
            val env = kotlin.math.exp(-3.2 * t)
            var sample = sin(2.0 * PI * hz * t)
            harmonics.forEachIndexed { nth, over ->
                sample += sin(2.0 * PI * over * t) * (0.45 / (nth + 1))
            }
            (sample * 32767.0 * volume * env).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun thump(): ShortArray {
        val count = (RATE * 0.28).toInt()
        return ShortArray(count) { index ->
            val t = index.toDouble() / RATE
            val env = kotlin.math.exp(-14.0 * t)
            val body = sin(2.0 * PI * 78.0 * t) + 0.4 * sin(2.0 * PI * 140.0 * t)
            (body * 32767.0 * 0.9 * env).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun sustain(hz: Double, seconds: Double, volume: Double, harmonics: List<Double>): ShortArray {
        val count = (RATE * seconds).toInt().coerceAtLeast(1)
        return ShortArray(count) { index ->
            val t = index.toDouble() / count
            val env = when {
                t < 0.12 -> t / 0.12
                t > 0.72 -> (1.0 - t) / 0.28
                else -> 1.0
            }
            var sample = sin(2.0 * PI * hz * index / RATE)
            harmonics.forEach { over -> sample += 0.35 * sin(2.0 * PI * over * index / RATE) }
            (sample * 32767.0 * volume * env.coerceAtLeast(0.0)).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private class Clip(val samples: ShortArray, val rate: Int)

    @Volatile
    private var pea: Clip? = null

    private fun peaBlast(): Clip {
        pea?.let { return it }
        val bytes = context.resources.openRawResource(R.raw.whistle).use { it.readBytes() }
        val clip = pcmWav(bytes)
        pea = clip
        return clip
    }

    private fun pcmWav(bytes: ByteArray): Clip {
        fun four(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
        fun u16(at: Int) = (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)
        fun u32(at: Int) = u16(at) or (u16(at + 2) shl 16)
        check(bytes.size > 44 && four(0) == "RIFF" && four(8) == "WAVE")
        var at = 12
        var rate = RATE
        var channels = 1
        var bits = 16
        var dataAt = -1
        var dataBytes = 0
        while (at + 8 <= bytes.size) {
            val id = four(at)
            val size = u32(at + 4)
            val body = at + 8
            when (id) {
                "fmt " -> {
                    check(u16(body) == 1)
                    channels = u16(body + 2)
                    rate = u32(body + 4)
                    bits = u16(body + 14)
                }
                "data" -> {
                    dataAt = body
                    dataBytes = size
                    break
                }
            }
            at = body + size + (size and 1)
        }
        check(dataAt >= 0 && channels == 1 && bits == 16 && rate > 0)
        val count = dataBytes / 2
        check(dataAt + count * 2 <= bytes.size)
        val samples = ShortArray(count) { index ->
            val pos = dataAt + index * 2
            ((bytes[pos].toInt() and 0xff) or ((bytes[pos + 1].toInt() and 0xff) shl 8)).toShort()
        }
        return Clip(samples, rate)
    }

    private companion object {
        const val RATE = 44100
    }
}
