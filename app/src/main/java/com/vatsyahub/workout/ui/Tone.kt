package com.vatsyahub.workout.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

class Tones {
    private val executor = Executors.newSingleThreadExecutor()
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun ring() = play(beats(3, 0.16, 0.09) { tone(980.0, 0.16, 0.72, listOf(1960.0, 2940.0)) })

    fun whistle() = play(beats(2, 0.13, 0.07) { chirp(2600.0, 1900.0, 0.13, 0.55) })

    fun bell() = play(strike(420.0, 0.95, 0.95, listOf(840.0, 1260.0, 1680.0)))

    fun drum() = play(thump())

    fun horn() = play(sustain(196.0, 1.35, 0.7, listOf(294.0, 392.0)))

    private fun play(samples: ShortArray) {
        executor.execute {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(samples.size * 1000L / RATE + 30)
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

    private fun chirp(from: Double, to: Double, seconds: Double, volume: Double): ShortArray {
        val count = (RATE * seconds).toInt().coerceAtLeast(1)
        return ShortArray(count) { index ->
            val t = index.toDouble() / count
            val hz = from + (to - from) * t
            val env = if (t < 0.08) t / 0.08 else (1.0 - t)
            val sample = sin(2.0 * PI * hz * index / RATE) + 0.25 * sin(2.0 * PI * hz * 2 * index / RATE)
            (sample * 32767.0 * volume * env.coerceAtLeast(0.0)).toInt().coerceIn(-32767, 32767).toShort()
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

    private companion object {
        const val RATE = 44100
    }
}
