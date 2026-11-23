package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AmbientAudioPlayer
import com.example.core.CryptoVault
import com.example.core.PdfAndExportHelper
import com.example.core.VoiceEngine
import com.example.data.model.AppSettingsState
import com.example.data.model.AssistantStatus
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentType
import com.example.data.model.FavoritePlace
import com.example.data.model.JournalEntry
import com.example.data.model.JournalMood
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.MinaLanguage
import com.example.data.model.MinaPersonality
import com.example.data.model.QuizQuestion
import com.example.data.model.SavedDocument
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.MinaAiRepository
import com.example.data.repository.MinaFirestoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MinaViewModel(
    application: Application,
    private val currentUserId: String,
    private val defaultDisplayName: String,
    private val firestoreRepo: MinaFirestoreRepository,
    private val aiRepo: MinaAiRepository,
    val settingsStore: SettingsDataStore,
    val voiceEngine: VoiceEngine,
    val ambientPlayer: AmbientAudioPlayer
) : AndroidViewModel(application) {

    private fun <T> Flow<T>.toUiStateFlow(errorLabel: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, errorLabel, rootError)
                emit(UiState.Error(rootError.localizedMessage ?: errorLabel))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val settingsState: StateFlow<AppSettingsState> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppSettingsState())

    val profileState: StateFlow<UiState<UserProfile>> = firestoreRepo.observeProfile(currentUserId)
        .map { it ?: UserProfile(userId = currentUserId, displayName = defaultDisplayName) }
        .toUiStateFlow("Failed to load profile")

    val memoriesState: StateFlow<UiState<List<MemoryItem>>> = firestoreRepo.observeMemories(currentUserId)
        .toUiStateFlow("Failed to load memories")

    val chatsState: StateFlow<UiState<List<ChatMessage>>> = firestoreRepo.observeChats(currentUserId)
        .toUiStateFlow("Failed to load chats")

    val journalsState: StateFlow<UiState<List<JournalEntry>>> = firestoreRepo.observeJournals(currentUserId)
        .toUiStateFlow("Failed to load journals")

    val documentsState: StateFlow<UiState<List<SavedDocument>>> = firestoreRepo.observeDocuments(currentUserId)
        .toUiStateFlow("Failed to load documents")

    val placesState: StateFlow<UiState<List<FavoritePlace>>> = firestoreRepo.observePlaces(currentUserId)
        .toUiStateFlow("Failed to load favorite places")

    val assistantStatus: StateFlow<AssistantStatus> = voiceEngine.status
    val voiceAmplitude: StateFlow<Float> = voiceEngine.amplitude
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _isChatBusy = MutableStateFlow(false)
    val isChatBusy: StateFlow<Boolean> = _isChatBusy.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _lastVoiceResponse = MutableStateFlow("")
    val lastVoiceResponse: StateFlow<String> = _lastVoiceResponse.asStateFlow()

    private val _visionState = MutableStateFlow<UiState<String>?>(null)
    val visionState: StateFlow<UiState<String>?> = _visionState.asStateFlow()

    private val _docGenState = MutableStateFlow<UiState<String>?>(null)
    val docGenState: StateFlow<UiState<String>?> = _docGenState.asStateFlow()

    private val _codeState = MutableStateFlow<UiState<String>?>(null)
    val codeState: StateFlow<UiState<String>?> = _codeState.asStateFlow()

    private val _studyAnswerState = MutableStateFlow<UiState<String>?>(null)
    val studyAnswerState: StateFlow<UiState<String>?> = _studyAnswerState.asStateFlow()

    private val _quizState = MutableStateFlow<UiState<List<QuizQuestion>>?>(null)
    val quizState: StateFlow<UiState<List<QuizQuestion>>?> = _quizState.asStateFlow()

    private val _musicAiState = MutableStateFlow<UiState<String>?>(null)
    val musicAiState: StateFlow<UiState<String>?> = _musicAiState.asStateFlow()

    private val _smartReplyState = MutableStateFlow<UiState<String>?>(null)
    val smartReplyState: StateFlow<UiState<String>?> = _smartReplyState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        viewModelScope.launch {
            firestoreRepo.ensureProfileExists(currentUserId, defaultDisplayName)
        }
        viewModelScope.launch {
            combine(profileState, settingsState) { profUi, settings ->
                val profile = (profUi as? UiState.Success)?.data
                    ?: UserProfile(userId = currentUserId, displayName = defaultDisplayName)
                MinaLanguage.fromRaw(profile.language) to settings
            }.collect { (lang, settings) ->
                voiceEngine.configure(
                    language = lang,
                    speechRate = settings.speechRate,
                    pitch = settings.voicePitch,
                    voiceName = settings.selectedVoiceName
                )
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    private fun currentProfile(): UserProfile =
        (profileState.value as? UiState.Success)?.data
            ?: UserProfile(userId = currentUserId, displayName = defaultDisplayName)

    private fun currentMemories(): List<MemoryItem> =
        (memoriesState.value as? UiState.Success)?.data.orEmpty().map { mem ->
            if (mem.encrypted) mem.copy(content = CryptoVault.decrypt(mem.content)) else mem
        }

    private fun currentChats(): List<ChatMessage> =
        (chatsState.value as? UiState.Success)?.data.orEmpty()

    // --- Onboarding & Settings ---

    fun completeOnboarding(language: MinaLanguage, personality: MinaPersonality) {
        viewModelScope.launch {
            settingsStore.setOnboardingCompleted(true)
            val p = currentProfile()
            firestoreRepo.updateCompanionTone(personality, p.emotionLevel.toInt(), language)
        }
    }

    fun updateSettings(transform: (AppSettingsState) -> AppSettingsState) {
        viewModelScope.launch {
            settingsStore.updateSettings(transform)
        }
    }

    fun updateCompanionTone(
        personality: MinaPersonality,
        emotionLevel: Int,
        language: MinaLanguage
    ) {
        viewModelScope.launch {
            val res = firestoreRepo.updateCompanionTone(personality, emotionLevel, language)
            if (res.isFailure) {
                _toastMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update tone"
            }
        }
    }

    // --- AI Chat ---

    fun sendChatMessage(text: String, speakAloud: Boolean = false) {
        val clean = text.trim()
        if (clean.isEmpty() || _isChatBusy.value) return
        viewModelScope.launch {
            _isChatBusy.value = true
            _chatError.value = null
            _streamingReply.value = ""
            voiceEngine.setThinking()

            val profile = currentProfile()
            val settings = settingsState.value
            val historySnapshot = currentChats()
            val memories = if (settings.rememberConversations) currentMemories() else emptyList()

            if (settings.rememberConversations) {
                firestoreRepo.addChatMessage("USER", clean, profile.personality)
            }

            try {
                var finalReply = ""
                aiRepo.streamChatReply(
                    userMessage = clean,
                    history = historySnapshot,
                    profile = profile,
                    memories = memories,
                    keepChatting = settings.keepChattingEnabled
                ).collect { partial ->
                    finalReply = partial
                    _streamingReply.value = partial
                }

                _streamingReply.value = null
                _lastVoiceResponse.value = finalReply
                if (settings.rememberConversations && finalReply.isNotBlank()) {
                    firestoreRepo.addChatMessage("MINA", finalReply, profile.personality)
                }

                if (speakAloud && finalReply.isNotBlank()) {
                    voiceEngine.speak(finalReply) {
                        if (settings.autoListenEnabled) {
                            // Auto-listen handled smoothly in Voice screen if active
                        }
                    }
                } else {
                    voiceEngine.setReady()
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat timed out", e)
                _streamingReply.value = null
                voiceEngine.setReady()
                _chatError.value = "MINA response timed out after 60 seconds. Please try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat AI failed", e)
                _streamingReply.value = null
                voiceEngine.setReady()
                _chatError.value = e.localizedMessage ?: "Failed to reach MINA AI."
            } finally {
                _isChatBusy.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            val res = firestoreRepo.clearChats()
            _toastMessage.value = if (res.isSuccess) "Chat history cleared" else "Could not clear chat"
        }
    }

    // --- Voice Assistant ---

    fun startVoiceListening(onAutoCommand: ((String) -> Unit)? = null) {
        val lang = MinaLanguage.fromRaw(currentProfile().language)
        voiceEngine.startListening(
            language = lang,
            onResult = { spoken ->
                val settings = settingsState.value
                val normalized = spoken.lowercase()
                val stripped = if (settings.heyMinaEnabled && normalized.startsWith("hey mina")) {
                    spoken.substringAfter("hey mina", "").trim().ifBlank { "Haan bolo Mina!" }
                } else {
                    spoken
                }
                if (onAutoCommand != null) {
                    onAutoCommand(stripped)
                } else {
                    sendVoicePrompt(stripped)
                }
            },
            onError = { err ->
                _toastMessage.value = err
            }
        )
    }

    fun sendVoicePrompt(spoken: String) {
        viewModelScope.launch {
            voiceEngine.setThinking()
            val profile = currentProfile()
            val memories = currentMemories()
            val settings = settingsState.value
            try {
                if (settings.rememberConversations) {
                    firestoreRepo.addChatMessage("USER", spoken, profile.personality)
                }
                val reply = aiRepo.generateSingleReply(
                    prompt = spoken,
                    profile = profile,
                    memories = memories,
                    useFastModel = false
                )
                _lastVoiceResponse.value = reply
                if (settings.rememberConversations) {
                    firestoreRepo.addChatMessage("MINA", reply, profile.personality)
                }
                voiceEngine.speak(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice AI error", e)
                voiceEngine.setReady()
                _lastVoiceResponse.value = "Error: ${e.localizedMessage ?: "AI call failed"}"
                _toastMessage.value = e.localizedMessage ?: "Voice AI failed"
            }
        }
    }

    // --- Live Vision ---

    fun analyzeVisionBitmap(bitmap: Bitmap, prompt: String) {
        viewModelScope.launch {
            _visionState.value = UiState.Loading
            try {
                val result = aiRepo.analyzeVision(bitmap, prompt, currentProfile())
                _visionState.value = UiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                _visionState.value = UiState.Error("Vision analysis timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Vision failed", e)
                _visionState.value = UiState.Error(e.localizedMessage ?: "Vision analysis failed.")
            }
        }
    }

    // --- Creator Hub & Memories ---

    fun saveFullProfile(
        displayName: String,
        bio: String,
        privateNotePlain: String,
        avatarUri: String,
        youtubeUrl: String,
        instagramUrl: String,
        socialUrl: String,
        musicUrl: String,
        personality: MinaPersonality,
        emotionLevel: Int,
        language: MinaLanguage
    ) {
        viewModelScope.launch {
            val encryptedPrivateNote = if (privateNotePlain.isNotBlank()) {
                CryptoVault.encrypt(privateNotePlain)
            } else ""
            val res = firestoreRepo.updateProfile(
                displayName = displayName,
                bio = bio,
                privateNote = encryptedPrivateNote,
                avatarUri = avatarUri,
                youtubeUrl = youtubeUrl,
                instagramUrl = instagramUrl,
                socialUrl = socialUrl,
                musicUrl = musicUrl,
                personality = personality,
                emotionLevel = emotionLevel,
                language = language
            )
            _toastMessage.value = if (res.isSuccess) "Profile saved securely" else (res.exceptionOrNull()?.localizedMessage ?: "Failed to save profile")
        }
    }

    fun addOrUpdateMemory(
        existingId: String?,
        title: String,
        contentPlain: String,
        category: MemoryCategory,
        encrypt: Boolean
    ) {
        viewModelScope.launch {
            val payloadContent = if (encrypt) CryptoVault.encrypt(contentPlain) else contentPlain
            val res = if (existingId.isNullOrBlank()) {
                firestoreRepo.addMemory(title, payloadContent, category, encrypt)
            } else {
                firestoreRepo.updateMemory(existingId, title, payloadContent, category, encrypt)
            }
            _toastMessage.value = if (res.isSuccess) "Memory saved" else (res.exceptionOrNull()?.localizedMessage ?: "Memory save failed")
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteMemory(memoryId)
        }
    }

    fun exportPersonalDataAndShare() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val profile = currentProfile()
                val memories = (memoriesState.value as? UiState.Success)?.data.orEmpty()
                val journals = (journalsState.value as? UiState.Success)?.data.orEmpty()
                val docs = (documentsState.value as? UiState.Success)?.data.orEmpty()
                val places = (placesState.value as? UiState.Success)?.data.orEmpty()
                val file = PdfAndExportHelper.exportUserDataJson(
                    context = context,
                    profile = profile,
                    memories = memories,
                    journals = journals,
                    documents = docs,
                    places = places
                )
                PdfAndExportHelper.shareFile(context, file, "application/json", "Export MINA AI Personal Data")
            } catch (e: Exception) {
                Log.e(TAG, "Data export failed", e)
                _toastMessage.value = e.localizedMessage ?: "Export failed"
            }
        }
    }

    // --- Documents ---

    fun generateDocumentDraft(docType: DocumentType, details: String) {
        viewModelScope.launch {
            _docGenState.value = UiState.Loading
            try {
                val content = aiRepo.generateDocument(docType, details, currentProfile())
                _docGenState.value = UiState.Success(content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Document generation failed", e)
                _docGenState.value = UiState.Error(e.localizedMessage ?: "Failed to generate document.")
            }
        }
    }

    fun saveDocument(existingId: String?, title: String, docType: DocumentType, content: String) {
        viewModelScope.launch {
            val res = firestoreRepo.saveDocument(existingId, title, docType, content)
            _toastMessage.value = if (res.isSuccess) "Document saved to cloud" else (res.exceptionOrNull()?.localizedMessage ?: "Save failed")
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteDocument(docId)
        }
    }

    fun exportDocumentToPdf(title: String, docType: DocumentType, content: String): File? {
        return try {
            val context = getApplication<Application>()
            val file = PdfAndExportHelper.generatePdf(context, title, docType.label, content)
            PdfAndExportHelper.shareFile(context, file, "application/pdf", "Share ${docType.label} PDF")
            file
        } catch (e: Exception) {
            Log.e(TAG, "PDF generation failed", e)
            _toastMessage.value = e.localizedMessage ?: "PDF export failed"
            null
        }
    }

    // --- Coding Studio ---

    fun runCodingStudio(mode: String, stack: String, prompt: String) {
        viewModelScope.launch {
            _codeState.value = UiState.Loading
            try {
                val output = aiRepo.generateCodeAssistant(mode, stack, prompt)
                _codeState.value = UiState.Success(output)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Coding Studio failed", e)
                _codeState.value = UiState.Error(e.localizedMessage ?: "Code generation failed.")
            }
        }
    }

    // --- Study ---

    fun askStudyAssistant(mode: String, prompt: String) {
        viewModelScope.launch {
            _studyAnswerState.value = UiState.Loading
            try {
                val fullPrompt = "Study Mode: $mode. Provide clear, student-friendly structured help for:\n$prompt"
                val answer = aiRepo.generateSingleReply(fullPrompt, currentProfile())
                _studyAnswerState.value = UiState.Success(answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Study assistant failed", e)
                _studyAnswerState.value = UiState.Error(e.localizedMessage ?: "Study request failed.")
            }
        }
    }

    fun generateQuiz(topic: String) {
        viewModelScope.launch {
            _quizState.value = UiState.Loading
            try {
                val lang = MinaLanguage.fromRaw(currentProfile().language)
                val questions = aiRepo.generateStudyQuiz(topic, lang)
                _quizState.value = UiState.Success(questions)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Quiz generation failed", e)
                _quizState.value = UiState.Error(e.localizedMessage ?: "Quiz generation failed.")
            }
        }
    }

    // --- Music ---

    fun getMusicRecommendations(moodOrQuery: String) {
        viewModelScope.launch {
            _musicAiState.value = UiState.Loading
            try {
                val prompt = "Suggest 6 curated songs & artists (Hindi, Bollywood, Indie, and English mix) matching: '$moodOrQuery'. Include brief why-it-fits notes."
                val answer = aiRepo.generateSingleReply(prompt, currentProfile(), useFastModel = true)
                _musicAiState.value = UiState.Success(answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _musicAiState.value = UiState.Error(e.localizedMessage ?: "Music lookup failed.")
            }
        }
    }

    // --- Journal ---

    fun saveJournalEntry(title: String, contentPlain: String, mood: JournalMood, encrypt: Boolean) {
        viewModelScope.launch {
            val profile = currentProfile()
            val reflection = aiRepo.generateJournalReflection(title, contentPlain, mood.label, profile)
            val storedContent = if (encrypt) CryptoVault.encrypt(contentPlain) else contentPlain
            val res = firestoreRepo.addJournalEntry(title, storedContent, mood, encrypt, reflection)
            _toastMessage.value = if (res.isSuccess) "Journal entry saved" else (res.exceptionOrNull()?.localizedMessage ?: "Failed to save entry")
        }
    }

    fun deleteJournalEntry(entryId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteJournalEntry(entryId)
        }
    }

    // --- Phone Control Smart Reply ---

    fun generateSmartReply(incomingMessage: String) {
        viewModelScope.launch {
            _smartReplyState.value = UiState.Loading
            try {
                val prompt = "Draft 3 short, polite reply options (1 casual Hinglish, 1 friendly, 1 professional) for this incoming message:\n\"$incomingMessage\""
                val reply = aiRepo.generateSingleReply(prompt, currentProfile(), useFastModel = true)
                _smartReplyState.value = UiState.Success(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _smartReplyState.value = UiState.Error(e.localizedMessage ?: "Failed to draft replies.")
            }
        }
    }

    // --- Location Favorite Places ---

    fun saveFavoritePlace(name: String, address: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            val res = firestoreRepo.addPlace(name, address, lat, lng)
            _toastMessage.value = if (res.isSuccess) "Saved '$name' to favorites" else (res.exceptionOrNull()?.localizedMessage ?: "Failed to save place")
        }
    }

    fun deleteFavoritePlace(placeId: String) {
        viewModelScope.launch {
            firestoreRepo.deletePlace(placeId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
        ambientPlayer.stop()
    }

    companion object {
        private const val TAG = "MinaViewModel"
    }
}
