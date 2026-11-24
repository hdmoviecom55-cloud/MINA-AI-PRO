package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.core.AppCheckManager
import com.example.core.AppCheckProviderMode
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentType
import com.example.data.model.MemoryItem
import com.example.data.model.MinaLanguage
import com.example.data.model.MinaPersonality
import com.example.data.model.QuizQuestion
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class NormalizedChatTurn(
    val role: String,
    val text: String
)

object MinaChatHistoryFormatter {

    /**
     * Converts persisted [ChatMessage] history into valid, strictly alternating
     * ("user", "model") pairs ending on a "model" turn so `startChat(history)` +
     * `sendMessageStream(currentUserMessage)` never violates Gemini turn ordering.
     */
    fun normalizeChatHistory(
        history: List<ChatMessage>,
        currentUserMessage: String = "",
        maxTurns: Int = 12
    ): List<NormalizedChatTurn> {
        val merged = mutableListOf<NormalizedChatTurn>()

        for (msg in history.takeLast(maxTurns + 6)) {
            val cleanText = msg.text.trim()
            if (cleanText.isEmpty()) continue
            val role = if (msg.role.equals("USER", ignoreCase = true)) "user" else "model"

            val last = merged.lastOrNull()
            if (last != null && last.role == role) {
                if (last.text != cleanText) {
                    merged[merged.lastIndex] = last.copy(text = "${last.text}\n$cleanText")
                }
            } else {
                merged.add(NormalizedChatTurn(role = role, text = cleanText))
            }
        }

        // Gemini chat history must start with a user turn.
        while (merged.firstOrNull()?.role == "model") {
            merged.removeAt(0)
        }

        // Drop a trailing user turn whether it duplicates the just-sent message (Firestore race)
        // or came from an unanswered prior message, so history ends cleanly on a "model" turn.
        if (merged.lastOrNull()?.role == "user") {
            merged.removeAt(merged.lastIndex)
        }

        val capped = if (merged.size > maxTurns) {
            merged.takeLast(maxTurns).toMutableList()
        } else {
            merged
        }

        while (capped.firstOrNull()?.role == "model") {
            capped.removeAt(0)
        }
        if (capped.lastOrNull()?.role == "user") {
            capped.removeAt(capped.lastIndex)
        }

        return capped
    }
}

object MinaAiErrorClassifier {

    fun isAppCheckRelated(error: Throwable): Boolean {
        val combined = collectErrorChainText(error).lowercase()
        return combined.contains("app check") ||
            combined.contains("appcheck") ||
            combined.contains("attestation failed") ||
            combined.contains("403")
    }

    fun classify(
        error: Throwable,
        providerMode: AppCheckProviderMode = AppCheckManager.diagnostics.value.providerMode
    ): String {
        if (error is TimeoutCancellationException) {
            return "MINA response timed out after 60 seconds. Check your internet connection and retry."
        }
        val combined = collectErrorChainText(error)
        val lower = combined.lowercase()

        return when {
            isAppCheckRelated(error) -> {
                AppCheckManager.classifyAppCheckError(error, providerMode)
            }
            lower.contains("429") || lower.contains("quota") || lower.contains("resource_exhausted") || lower.contains("rate limit") ->
                "Gemini rate limit or quota reached temporarily. Please wait a moment and retry."
            lower.contains("unable to resolve host") ||
                lower.contains("failed to connect") ||
                lower.contains("network") ||
                lower.contains("unknownhost") ||
                lower.contains("sockettimeout") ||
                lower.contains("timed out") ->
                "Network connection failed while reaching MINA's Gemini backend. Check internet and tap Retry."
            lower.contains("404") || lower.contains("not found") && lower.contains("model") ->
                "Configured Gemini model is unavailable on Firebase AI Logic backend."
            lower.contains("permission_denied") || lower.contains("api has not been used") ->
                "Firebase AI Logic API permission denied for this project. Verify Firebase AI Logic provisioning."
            else -> error.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Unable to reach MINA AI right now. Please try again."
        }
    }

    private fun collectErrorChainText(error: Throwable): String {
        val sb = StringBuilder()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 8) {
            sb.append(current::class.java.simpleName).append(' ')
            current.message?.let { sb.append(it).append(' ') }
            current = current.cause
            depth++
        }
        return sb.toString()
    }
}

class MinaAiRepository {

    private val generalModel by lazy {
        Firebase.ai.generativeModel(DEFAULT_MODEL)
    }

    private val fastModel by lazy {
        Firebase.ai.generativeModel(FAST_MODEL)
    }

    private val quizModel by lazy {
        val quizSchema = Schema.obj(
            properties = mapOf(
                "questions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "question" to Schema.string(description = "Quiz question text"),
                            "options" to Schema.array(
                                items = Schema.string(),
                                description = "Four multiple-choice options"
                            ),
                            "correctIndex" to Schema.integer(description = "Zero-based index of correct option (0..3)"),
                            "explanation" to Schema.string(description = "Short friendly explanation")
                        )
                    ),
                    description = "List of 4 quiz questions"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quizSchema
            }
        )
    }

    private fun emotionLabel(level: Int): String = when (level.coerceIn(1, 5)) {
        1 -> "1/5 Calm (serene, grounded, gentle)"
        2 -> "2/5 Friendly (warm, polite, pleasant)"
        3 -> "3/5 Normal (balanced, conversational, helpful)"
        4 -> "4/5 Caring (deeply supportive, warm, attentive)"
        else -> "5/5 Highly Expressive (vibrant, enthusiastic, deeply warm)"
    }

    private fun buildSystemPrompt(
        profile: UserProfile,
        memories: List<MemoryItem>,
        extraInstruction: String = ""
    ): String {
        val personality = MinaPersonality.fromRaw(profile.personality)
        val language = MinaLanguage.fromRaw(profile.language)
        val memoryBlock = if (memories.isNotEmpty()) {
            memories.take(8).joinToString("\n") { "- ${it.title}: ${it.content}" }
        } else {
            "No saved personal preferences yet."
        }

        return """
            You are MINA AI, a warm, intelligent, emotionally supportive personal AI companion.
            User's name: ${profile.displayName}.
            ${if (profile.bio.isNotBlank()) "User bio: ${profile.bio}." else ""}
            Selected Personality: ${personality.displayName} — ${personality.promptGuidance}
            Emotion Level: ${emotionLabel(profile.emotionLevel.toInt())}.
            Language Rule: ${language.promptGuidance}
            Safety & Honesty Boundary: Always maintain respectful boundaries and never claim to be a real human. Be transparent if asked about being an AI, while staying deeply caring and natural.
            User Memories for helpful context:
            $memoryBlock
            Keep responses concise, natural, well-structured, and conversational unless asked for detailed output.
            ${if (extraInstruction.isNotBlank()) extraInstruction else ""}
        """.trimIndent()
    }

    private suspend fun ensureValidAppCheck(forceRefresh: Boolean): Unit {
        val result = AppCheckManager.verifyAppCheckToken(forceRefresh = forceRefresh)
        if (result.isFailure) {
            val err = result.exceptionOrNull()
                ?: IllegalStateException("Firebase App Check token verification failed.")
            throw err
        }
    }

    private suspend fun <T> executeWithAppCheck(
        forceRefreshInitially: Boolean = false,
        block: suspend () -> T
    ): T {
        ensureValidAppCheck(forceRefresh = forceRefreshInitially)
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!forceRefreshInitially && MinaAiErrorClassifier.isAppCheckRelated(e)) {
                Log.w(TAG, "AI call hit App Check error; refreshing App Check token and retrying once.", e)
                try {
                    ensureValidAppCheck(forceRefresh = true)
                    return block()
                } catch (retryError: CancellationException) {
                    throw retryError
                } catch (retryError: Exception) {
                    Log.e(TAG, "AI retry after App Check refresh failed", retryError)
                    throw IllegalStateException(MinaAiErrorClassifier.classify(retryError), retryError)
                }
            }
            Log.e(TAG, "AI request failed", e)
            throw IllegalStateException(MinaAiErrorClassifier.classify(e), e)
        }
    }

    fun streamChatReply(
        userMessage: String,
        history: List<ChatMessage>,
        profile: UserProfile,
        memories: List<MemoryItem>,
        keepChatting: Boolean
    ): Flow<String> = flow {
        val extraPrompt = if (keepChatting) {
            "After answering, optionally add one brief, natural follow-up question to keep the conversation flowing."
        } else ""

        val systemText = buildSystemPrompt(profile, memories, extraPrompt)
        val systemInstructionContent = content { text(systemText) }
        val normalizedHistory: List<Content> = MinaChatHistoryFormatter
            .normalizeChatHistory(history, userMessage)
            .map { turn -> content(turn.role) { text(turn.text) } }

        val chatModel = Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            systemInstruction = systemInstructionContent
        )

        var emittedAnyChunk = false
        suspend fun runStreamAttempt(forceRefreshToken: Boolean) {
            ensureValidAppCheck(forceRefresh = forceRefreshToken)
            val chat = chatModel.startChat(history = normalizedHistory)
            val accumulated = StringBuilder()
            withTimeout(60_000L) {
                chat.sendMessageStream(userMessage.trim()).collect { chunk ->
                    val part = chunk.text.orEmpty()
                    if (part.isNotEmpty()) {
                        emittedAnyChunk = true
                        accumulated.append(part)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("MINA returned an empty response. Please try again.")
            }
        }

        try {
            runStreamAttempt(forceRefreshToken = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!emittedAnyChunk && MinaAiErrorClassifier.isAppCheckRelated(e)) {
                Log.w(TAG, "Chat stream hit App Check error; forcing token refresh and retrying once.", e)
                try {
                    runStreamAttempt(forceRefreshToken = true)
                    return@flow
                } catch (retryError: CancellationException) {
                    throw retryError
                } catch (retryError: Exception) {
                    Log.e(TAG, "Chat stream retry after App Check refresh failed", retryError)
                    throw IllegalStateException(MinaAiErrorClassifier.classify(retryError), retryError)
                }
            }
            Log.e(TAG, "Chat stream failed", e)
            throw IllegalStateException(MinaAiErrorClassifier.classify(e), e)
        }
    }

    suspend fun runTestReply(profile: UserProfile): Result<String> {
        return try {
            val reply = executeWithAppCheck(forceRefreshInitially = true) {
                val systemContent = content { text(buildSystemPrompt(profile, emptyList())) }
                val model = Firebase.ai.generativeModel(
                    modelName = FAST_MODEL,
                    systemInstruction = systemContent
                )
                val prompt = "Connection test for ${profile.displayName}: please reply with one brief friendly sentence confirming MINA AI is connected and ready."
                val response = withTimeout(60_000L) {
                    model.generateContent(prompt)
                }
                response.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Gemini backend returned an empty test response.")
            }
            Result.success(reply)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Test reply failed", e)
            Result.failure(IllegalStateException(MinaAiErrorClassifier.classify(e), e))
        }
    }

    suspend fun generateSingleReply(
        prompt: String,
        profile: UserProfile,
        memories: List<MemoryItem> = emptyList(),
        useFastModel: Boolean = false
    ): String = executeWithAppCheck {
        val systemContent = content { text(buildSystemPrompt(profile, memories)) }
        val targetModel = Firebase.ai.generativeModel(
            modelName = if (useFastModel) FAST_MODEL else DEFAULT_MODEL,
            systemInstruction = systemContent
        )
        val response = withTimeout(60_000L) {
            targetModel.generateContent(prompt)
        }
        response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Empty response from AI.")
    }

    suspend fun analyzeVision(
        bitmap: Bitmap,
        taskPrompt: String,
        profile: UserProfile
    ): String = executeWithAppCheck {
        val scaled = scaleBitmapSafely(bitmap, 1280)
        val language = MinaLanguage.fromRaw(profile.language)
        val prompt = """
            You are MINA AI's Live Vision assistant speaking to ${profile.displayName}.
            ${language.promptGuidance}
            User request: $taskPrompt
        """.trimIndent()

        val response = withTimeout(60_000L) {
            generalModel.generateContent(
                content {
                    image(scaled)
                    text(prompt)
                }
            )
        }
        response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Could not extract visual insight from the image.")
    }

    suspend fun generateDocument(
        docType: DocumentType,
        details: String,
        profile: UserProfile
    ): String = executeWithAppCheck {
        val language = MinaLanguage.fromRaw(profile.language)
        val prompt = """
            Create a polished, ready-to-use ${docType.label} for ${profile.displayName}.
            User details & instructions:
            $details
            Format cleanly with clear headings and bullet points (plain text / markdown-free clean layout suitable for PDF export).
            Language preference: ${if (docType == DocumentType.RESUME || docType == DocumentType.BIODATA) "Professional English (unless requested otherwise)" else language.displayName}.
        """.trimIndent()
        val response = withTimeout(60_000L) {
            generalModel.generateContent(prompt)
        }
        response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Document generation returned empty output.")
    }

    suspend fun generateCodeAssistant(
        mode: String,
        languageOrStack: String,
        userPrompt: String
    ): String = executeWithAppCheck {
        val prompt = """
            You are MINA Coding Studio.
            Mode: $mode
            Target Stack/Language: $languageOrStack
            Request: $userPrompt
            If generating HTML/CSS/JS for a website or prototype, provide a complete single-file self-contained HTML document (with embedded <style> and <script>) inside a ```html code block so it can be previewed live.
            If explaining or debugging, provide clear step-by-step analysis and corrected code.
        """.trimIndent()
        val response = withTimeout(60_000L) {
            generalModel.generateContent(prompt)
        }
        response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Code generation returned empty output.")
    }

    suspend fun generateStudyQuiz(
        topic: String,
        language: MinaLanguage
    ): List<QuizQuestion> = executeWithAppCheck {
        val prompt = """
            Generate 4 high-quality multiple-choice quiz questions on the topic: "$topic".
            Language style: ${language.displayName}.
            Each question must have 4 concise options, correctIndex (0 to 3), and a short helpful explanation.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            quizModel.generateContent(prompt)
        }
        val rawJson = response.text ?: throw IllegalStateException("Quiz model returned empty JSON.")
        val root = JSONObject(rawJson)
        val arr = root.getJSONArray("questions")
        val list = mutableListOf<QuizQuestion>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val optsJson = obj.getJSONArray("options")
            val opts = (0 until optsJson.length()).map { optsJson.getString(it) }
            list.add(
                QuizQuestion(
                    question = obj.getString("question"),
                    options = opts,
                    correctIndex = obj.optInt("correctIndex", 0).coerceIn(0, (opts.size - 1).coerceAtLeast(0)),
                    explanation = obj.optString("explanation", "")
                )
            )
        }
        list
    }

    suspend fun generateJournalReflection(
        title: String,
        content: String,
        mood: String,
        profile: UserProfile
    ): String {
        return try {
            val prompt = """
                The user (${profile.displayName}) wrote a journal entry titled "$title" with mood "$mood":
                "$content"
                Write a short, warm, caring 2-3 sentence personal reflection from MINA AI in ${MinaLanguage.fromRaw(profile.language).displayName}.
            """.trimIndent()
            generateSingleReply(prompt, profile, useFastModel = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Reflection timed out", e)
            ""
        } catch (e: Exception) {
            Log.w(TAG, "Reflection skipped", e)
            ""
        }
    }

    private fun scaleBitmapSafely(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDimension && h <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxOf(w, h)
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    companion object {
        private const val TAG = "MinaAiRepository"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val FAST_MODEL = "gemini-3.5-flash-lite"
    }
}
