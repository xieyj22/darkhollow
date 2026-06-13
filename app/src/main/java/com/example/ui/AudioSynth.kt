package com.example.ui

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.sin

object AudioSynth {
    private var isMuted = false

    fun setMuted(muted: Boolean) {
        this.isMuted = muted
    }

    fun isMuted() = isMuted

    fun play(type: String) {
        if (isMuted) return
        Thread {
            try {
                val sampleRate = 8000
                val durationMs = when (type) {
                    "hit" -> 100
                    "crit" -> 200
                    "pickup" -> 150
                    "levelup" -> 400
                    "death" -> 800
                    "stairs" -> 300
                    "trap" -> 150
                    "heal" -> 250
                    "dodge" -> 120
                    "spell" -> 300
                    "victory" -> 700
                    "ach" -> 400
                    else -> 100
                }
                val numSamples = durationMs * sampleRate / 1000
                val sample = ByteArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = when (type) {
                        "hit" -> {
                            val currentFreq = 200.0 - (120.0 * (i.toDouble() / numSamples))
                            currentFreq
                        }
                        "crit" -> {
                            400.0 - (300.0 * (i.toDouble() / numSamples))
                        }
                        "pickup" -> {
                            600.0 + (300.0 * (i.toDouble() / numSamples))
                        }
                        "levelup" -> {
                            val pct = i.toDouble() / numSamples
                            when {
                                pct < 0.25 -> 400.0
                                pct < 0.50 -> 500.0
                                else -> 700.0
                            }
                        }
                        "death" -> {
                            300.0 - (270.0 * (i.toDouble() / numSamples))
                        }
                        "stairs" -> {
                            300.0 + (300.0 * (i.toDouble() / numSamples))
                        }
                        "trap" -> {
                            100.0 - (50.0 * (i.toDouble() / numSamples))
                        }
                        "heal" -> {
                            500.0 + (300.0 * (i.toDouble() / numSamples))
                        }
                        "dodge" -> {
                            400.0 - (200.0 * (i.toDouble() / numSamples))
                        }
                        "spell" -> {
                            800.0 - (600.0 * (i.toDouble() / numSamples))
                        }
                        "victory" -> {
                            val pct = i.toDouble() / numSamples
                            when {
                                pct < 0.2 -> 523.0
                                pct < 0.4 -> 659.0
                                pct < 0.6 -> 784.0
                                else -> 1047.0
                            }
                        }
                        "ach" -> {
                            val pct = i.toDouble() / numSamples
                            when {
                                pct < 0.25 -> 600.0
                                pct < 0.5 -> 800.0
                                else -> 1000.0
                            }
                        }
                        else -> 440.0
                    }

                    val angle = 2.0 * Math.PI * freq * t
                    var value = (sin(angle) * 127.0).toInt()

                    val envelope = when (type) {
                        "hit", "crit", "death", "trap", "dodge" -> {
                            Math.exp(-4.0 * (i.toDouble() / numSamples))
                        }
                        else -> {
                            1.0 - (i.toDouble() / numSamples)
                        }
                    }

                    value = (value * envelope).toInt()
                    sample[i] = value.toByte()
                }

                @Suppress("DEPRECATION")
                val track = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_8BIT,
                    numSamples,
                    AudioTrack.MODE_STATIC
                )
                track.write(sample, 0, numSamples)
                track.play()
                Thread.sleep(durationMs.toLong() + 50)
                track.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
