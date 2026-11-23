package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
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

class MinaAiRepository {

    private val generalModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val fastModel by lazy {
        Firebase.ai.generativeModel("gemini-3.5-flash-lite")
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
            modelName = "gemini-3.8-flash",
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
        memories: List<MemoryItem>
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
        """.trimIndent()
    }

    fun streamChatReply(
        userMessage: String,
        history: List<ChatMessage>,
        profile: UserProfile,
        memories: List<MemoryItem>,
        keepChatting: Boolean
    ): Flow<String> = flow {
        val systemInstruction = buildSystemPrompt(profile, memories)
        val extraPrompt = if (keepChatting) {
            "\nAfter answering, optionally add one brief, natural follow-up question to keep the conversation flowing."
        } else ""

        val recentHistory = mutableListOf<Content>()
        recentHistory.add(content("user") { text("System context for this conversation:\n$systemInstruction$extraPrompt\nPlease acknowledge silently and follow these instructions in all replies.") })
        recentHistory.add(content("model") { text("Samajh gayi! Main MINA AI hoon aur aapke saath hoon.") })

        history.takeLast(12).forEach { msg ->
            val role = if (msg.role == "USER") "user" else "model"
            if (msg.text.isNotBlank()) {
                recentHistory.add(content(role) { text(msg.text) })
            }
        }

        val chat = generalModel.startChat(history = recentHistory)
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(userMessage).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("MINA returned an empty response. Please try again.")
        }
    }

    suspend fun generateSingleReply(
        prompt: String,
        profile: UserProfile,
        memories: List<MemoryItem> = emptyList(),
        useFastModel: Boolean = false
    ): String {
        val systemInstruction = buildSystemPrompt(profile, memories)
        val fullPrompt = "$systemInstruction\n\nTask:\n$prompt"
        val targetModel = if (useFastModel) fastModel else generalModel
        val response = withTimeout(60_000L) {
            targetModel.generateContent(fullPrompt)
        }
        return response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Empty response from AI.")
    }

    suspend fun analyzeVision(
        bitmap: Bitmap,
        taskPrompt: String,
        profile: UserProfile
    ): String {
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
        return response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Could not extract visual insight from the image.")
    }

    suspend fun generateDocument(
        docType: DocumentType,
        details: String,
        profile: UserProfile
    ): String {
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
        return response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Document generation returned empty output.")
    }

    suspend fun generateCodeAssistant(
        mode: String,
        languageOrStack: String,
        userPrompt: String
    ): String {
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
        return response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Code generation returned empty output.")
    }

    suspend fun generateStudyQuiz(
        topic: String,
        language: MinaLanguage
    ): List<QuizQuestion> {
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
        return list
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
    }
}
