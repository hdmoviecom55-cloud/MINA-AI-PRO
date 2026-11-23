package com.example.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

data class AmbientTrack(
    val id: String,
    val title: String,
    val subtitle: String,
    val baseFreqHz: Double,
    val harmonyFreqHz: Double,
    val pulseHz: Double
)

class AmbientAudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val _playingTrackId = MutableStateFlow<String?>(null)
    val playingTrackId: StateFlow<String?> = _playingTrackId.asStateFlow()

    val tracks = listOf(
        AmbientTrack("lofi_amber", "Golden Hour Lo-Fi Drone", "Warm 432 Hz harmonic focus pad", 216.0, 324.0, 0.35),
        AmbientTrack("study_alpha", "Deep Study Alpha Waves", "10 Hz gentle binaural concentration", 240.0, 250.0, 0.25),
        AmbientTrack("night_calm", "Midnight Espresso Calm", "Soothing 174 Hz relaxation chord", 174.0, 261.0, 0.2),
        AmbientTrack("morning_boost", "Surya Energy Flow", "Uplifting 528 Hz bright resonance", 264.0, 396.0, 0.5)
    )

    fun toggleTrack(track: AmbientTrack) {
        if (_playingTrackId.value == track.id) {
            stop()
            return
        }
        stop()
        _playingTrackId.value = track.id

        playJob = scope.launch {
            val sampleRate = 22050
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val trackPlayer = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = trackPlayer
            try {
                trackPlayer.play()
                val chunk = ShortArray(1024)
                var sampleIndex = 0L
                while (isActive) {
                    for (i in chunk.indices) {
                        val t = (sampleIndex + i).toDouble() / sampleRate
                        val env = 0.65 + 0.35 * sin(2.0 * PI * track.pulseHz * t)
                        val wave = 0.5 * sin(2.0 * PI * track.baseFreqHz * t) +
                            0.35 * sin(2.0 * PI * track.harmonyFreqHz * t) +
                            0.15 * sin(2.0 * PI * (track.baseFreqHz * 1.5) * t)
                        chunk[i] = (wave * env * 4800.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    sampleIndex += chunk.size
                    trackPlayer.write(chunk, 0, chunk.size)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    trackPlayer.stop()
                    trackPlayer.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        _playingTrackId.value = null
    }
}
