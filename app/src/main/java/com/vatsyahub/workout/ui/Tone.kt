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

    fun high() = beep(880.0)

    fun low() = beep(220.0)

    private fun beep(hz: Double) {
        executor.execute {
            try {
                val sampleRate = 44100
                val count = (sampleRate * 0.14).toInt()
                val data = ShortArray(count) { index ->
                    val attack = (index / (sampleRate * 0.01)).coerceAtMost(1.0)
                    val release = ((count - index) / (sampleRate * 0.04)).coerceAtMost(1.0)
                    val sample = sin(2.0 * PI * hz * index / sampleRate)
                    (sample * 32767.0 * 0.45 * attack * release).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(data, 0, data.size)
                track.play()
                Thread.sleep(200)
                track.release()
            } catch (_: Exception) {
                // A missing audio device should not stop the countdown.
            }
        }
    }
}
