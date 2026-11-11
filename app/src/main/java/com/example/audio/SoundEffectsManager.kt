package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SoundEffectsManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var isEnabled: Boolean = true

    fun playCardTap() {
        if (!isEnabled) return
        playNotes(listOf(Tone(520.0, 45), Tone(660.0, 35)))
    }

    fun playDrawCard() {
        if (!isEnabled) return
        playNotes(listOf(Tone(340.0, 50)))
    }

    fun playAttackTwo() {
        if (!isEnabled) return
        playNotes(listOf(Tone(440.0, 65), Tone(370.0, 80)))
    }

    fun playJumpSkip() {
        if (!isEnabled) return
        playNotes(listOf(Tone(587.3, 60), Tone(880.0, 90)))
    }

    fun playSevenCut() {
        if (!isEnabled) return
        playNotes(listOf(Tone(523.25, 80), Tone(659.25, 80), Tone(783.99, 120), Tone(1046.5, 160)))
    }

    fun playWinFanfare() {
        if (!isEnabled) return
        playNotes(listOf(Tone(523.25, 90), Tone(659.25, 90), Tone(783.99, 90), Tone(1046.5, 220)))
    }

    fun playCoinChime() {
        if (!isEnabled) return
        playNotes(listOf(Tone(987.77, 75), Tone(1318.51, 140)))
    }

    fun playErrorTone() {
        if (!isEnabled) return
        playNotes(listOf(Tone(220.0, 120)))
    }

    private data class Tone(val freqHz: Double, val durationMs: Int)

    private fun playNotes(notes: List<Tone>) {
        scope.launch {
            runCatching {
                val sampleRate = 22050
                val samplesList = mutableListOf<Short>()
                for (note in notes) {
                    val count = (sampleRate * note.durationMs) / 1000
                    for (i in 0 until count) {
                        val t = i.toDouble() / sampleRate
                        val envelope = exp(-4.5 * i.toDouble() / count)
                        val wave = sin(2.0 * PI * note.freqHz * t) * envelope
                        samplesList.add((wave * Short.MAX_VALUE * 0.25).toInt().toShort())
                    }
                }
                val pcm = samplesList.toShortArray()
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
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
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, pcm.size * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                val totalMs = notes.sumOf { it.durationMs } + 60L
                Thread.sleep(totalMs)
                track.stop()
                track.release()
            }
        }
    }
}
