package com.example.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.AssistantStatus
import com.example.data.model.MinaLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class VoiceEngine(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _status = MutableStateFlow(AssistantStatus.READY)
    val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<String>>(emptyList())
    val availableVoices: StateFlow<List<String>> = _availableVoices.asStateFlow()

    private var currentLanguage: MinaLanguage = MinaLanguage.HINGLISH
    private var currentSpeechRate: Float = 1.0f
    private var currentPitch: Float = 1.08f
    private var preferredVoiceHint: String = "female"

    init {
        try {
            tts = TextToSpeech(appContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "TTS init failed", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            applyTtsConfig()
            try {
                val voices = tts?.voices
                    ?.filter { it.locale.language in listOf("hi", "en") }
                    ?.map { it.name }
                    ?.distinct()
                    ?.take(12)
                    ?: emptyList()
                _availableVoices.value = listOf("Mina Warm Female") + voices
            } catch (e: Exception) {
                Log.w(TAG, "Unable to list TTS voices", e)
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _status.value = AssistantStatus.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    _status.value = AssistantStatus.READY
                    _amplitude.value = 0f
                    onSpeakFinishedCallback?.invoke()
                    onSpeakFinishedCallback = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _status.value = AssistantStatus.READY
                    _amplitude.value = 0f
                }
            })
        } else {
            Log.w(TAG, "TTS initialization returned status $status")
        }
    }

    private var onSpeakFinishedCallback: (() -> Unit)? = null

    fun configure(
        language: MinaLanguage,
        speechRate: Float,
        pitch: Float,
        voiceName: String
    ) {
        currentLanguage = language
        currentSpeechRate = speechRate.coerceIn(0.6f, 1.8f)
        currentPitch = pitch.coerceIn(0.75f, 1.4f)
        preferredVoiceHint = voiceName
        applyTtsConfig()
    }

    private fun applyTtsConfig() {
        val engine = tts ?: return
        if (!ttsReady) return
        val locale = when (currentLanguage) {
            MinaLanguage.HINDI -> Locale("hi", "IN")
            MinaLanguage.HINGLISH -> Locale("en", "IN")
            MinaLanguage.ENGLISH -> Locale.US
        }
        val res = engine.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        engine.setSpeechRate(currentSpeechRate)
        engine.setPitch(currentPitch)

        try {
            val voices = engine.voices ?: return
            val exact = voices.firstOrNull { it.name == preferredVoiceHint }
            val femaleMatch = voices.firstOrNull { voice ->
                voice.locale.language == locale.language &&
                    (voice.name.contains("female", ignoreCase = true) ||
                        voice.name.contains("-f-", ignoreCase = true) ||
                        voice.name.contains("in-x-", ignoreCase = true))
            }
            (exact ?: femaleMatch)?.let { engine.voice = it }
        } catch (e: Exception) {
            Log.w(TAG, "Voice selection skipped", e)
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        val clean = text
            .replace(Regex("[*#`_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isBlank()) return
        if (!ttsReady || tts == null) {
            onComplete?.invoke()
            return
        }
        applyTtsConfig()
        onSpeakFinishedCallback = onComplete
        _status.value = AssistantStatus.SPEAKING
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        onSpeakFinishedCallback = null
        if (_status.value == AssistantStatus.SPEAKING) {
            _status.value = AssistantStatus.READY
        }
        _amplitude.value = 0f
    }

    fun setThinking() {
        _status.value = AssistantStatus.THINKING
    }

    fun setReady() {
        _status.value = AssistantStatus.READY
        _amplitude.value = 0f
    }

    fun startListening(
        language: MinaLanguage,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Interrupt TTS immediately when user taps mic
        stopSpeaking()

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Speech recognition service is not available on this device.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _status.value = AssistantStatus.LISTENING
                        _partialTranscript.value = ""
                    }

                    override fun onBeginningOfSpeech() {
                        _status.value = AssistantStatus.LISTENING
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _amplitude.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _amplitude.value = 0f
                    }

                    override fun onError(error: Int) {
                        _status.value = AssistantStatus.READY
                        _amplitude.value = 0f
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Sun nahi paayi, please dobara boliye."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Awaaz detect nahi hui, tap mic to try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                            SpeechRecognizer.ERROR_NETWORK -> "Network error during voice recognition."
                            else -> "Voice input stopped (code $error)."
                        }
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        _amplitude.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull()?.trim().orEmpty()
                        _partialTranscript.value = spoken
                        if (spoken.isNotBlank()) {
                            _status.value = AssistantStatus.THINKING
                            onResult(spoken)
                        } else {
                            _status.value = AssistantStatus.READY
                            onError("Kuch samajh nahi aaya, please try again.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            _status.value = AssistantStatus.LISTENING
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer", e)
            _status.value = AssistantStatus.READY
            onError(e.localizedMessage ?: "Unable to start microphone.")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        _amplitude.value = 0f
        if (_status.value == AssistantStatus.LISTENING) {
            _status.value = AssistantStatus.READY
        }
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {
        }
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "VoiceEngine"
    }
}
