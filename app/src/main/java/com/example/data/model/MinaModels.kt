package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MinaPersonality(
    val displayName: String,
    val subtitle: String,
    val promptGuidance: String
) {
    GIRLFRIEND(
        "Romantic Companion",
        "Sweet, affectionate & caring AI",
        "Speak with warm affection, playful sweetness, and genuine emotional attentiveness like a devoted romantic companion, while always staying respectful and transparent that you are MINA AI."
    ),
    BEST_FRIEND(
        "Best Friend",
        "Fun, casual, loyal buddy",
        "Talk like a loyal, witty best friend—relaxed, supportive, uplifting, and ready to banter or listen without judgement."
    ),
    PROFESSIONAL(
        "Professional Assistant",
        "Crisp, organized & executive",
        "Be sharp, structured, reliable, and productivity-focused like an executive chief of staff."
    ),
    CARING(
        "Caring Companion",
        "Gentle, empathetic & supportive",
        "Be deeply empathetic, soothing, patient, and emotionally supportive—like a calm, caring confidant."
    ),
    TEACHER(
        "Teacher",
        "Clear, patient & insightful",
        "Explain concepts step-by-step with relatable examples, patience, and encouraging feedback."
    ),
    COACH(
        "Motivational Coach",
        "Energetic, focused & inspiring",
        "Be high-energy, action-oriented, encouraging, and focused on building momentum and confidence."
    );

    companion object {
        fun fromRaw(raw: String?): MinaPersonality =
            entries.firstOrNull { it.name == raw } ?: CARING
    }
}

enum class MinaLanguage(
    val displayName: String,
    val localeTag: String,
    val promptGuidance: String
) {
    HINGLISH(
        "Hinglish",
        "en-IN",
        "Respond naturally in warm conversational Hinglish (a natural mix of Hindi written in Roman script and everyday English, e.g., 'Haan bilkul! Chalo saath mein solve karte hain')."
    ),
    HINDI(
        "Hindi (हिन्दी)",
        "hi-IN",
        "Respond naturally in warm, clear Hindi using Devanagari script with friendly everyday phrasing."
    ),
    ENGLISH(
        "English",
        "en-US",
        "Respond naturally in clear, expressive, warm English."
    );

    companion object {
        fun fromRaw(raw: String?): MinaLanguage =
            entries.firstOrNull { it.name == raw } ?: HINGLISH
    }
}

enum class AssistantStatus(val label: String) {
    READY("Ready"),
    LISTENING("Listening..."),
    THINKING("Thinking..."),
    SPEAKING("Speaking...")
}

enum class MemoryCategory(val label: String) {
    PREFERENCE("Preference"),
    CONVERSATION("Conversation"),
    PERSONAL("Personal"),
    GOAL("Goal");

    companion object {
        fun fromRaw(raw: String?): MemoryCategory =
            entries.firstOrNull { it.name == raw } ?: PREFERENCE
    }
}

enum class JournalMood(val label: String, val emoji: String) {
    HAPPY("Happy", "😊"),
    CALM("Calm", "😌"),
    EXCITED("Excited", "✨"),
    TIRED("Tired", "😴"),
    STRESSED("Stressed", "🌧️"),
    GRATEFUL("Grateful", "🙏");

    companion object {
        fun fromRaw(raw: String?): JournalMood =
            entries.firstOrNull { it.name == raw } ?: CALM
    }
}

enum class DocumentType(val label: String) {
    RESUME("Resume"),
    BIODATA("Biodata"),
    LETTER("Letter"),
    CODE("Code"),
    NOTE("Note");

    companion object {
        fun fromRaw(raw: String?): DocumentType =
            entries.firstOrNull { it.name == raw } ?: RESUME
    }
}

enum class MinaThemeStyle(val label: String, val subtitle: String) {
    MIDNIGHT_AMBER("Midnight Espresso", "Deep brown & warm amber gold"),
    OBSIDIAN_EMBER("Obsidian Ember", "Charcoal glass & fiery orange"),
    ROYAL_BRONZE("Royal Bronze", "Warm bronze & champagne gold"),
    SOLAR_LIGHT("Warm Ivory", "Clean daylight parchment & amber");

    companion object {
        fun fromRaw(raw: String?): MinaThemeStyle =
            entries.firstOrNull { it.name == raw } ?: MIDNIGHT_AMBER
    }
}

enum class CodeSyntaxStyle(val label: String) {
    AMBER_DARK("Amber Terminal"),
    EMERALD_MATRIX("Emerald Matrix"),
    CYAN_CYBER("Cyber Cyan"),
    MONOCHROME_INK("Clean Ink");

    companion object {
        fun fromRaw(raw: String?): CodeSyntaxStyle =
            entries.firstOrNull { it.name == raw } ?: AMBER_DARK
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Friend",
    val bio: String = "",
    val privateNote: String = "",
    val avatarUri: String = "",
    val youtubeUrl: String = "",
    val instagramUrl: String = "",
    val socialUrl: String = "",
    val musicUrl: String = "",
    val personality: String = MinaPersonality.CARING.name,
    val emotionLevel: Long = 3L,
    val language: String = MinaLanguage.HINGLISH.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MemoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = MemoryCategory.PREFERENCE.name,
    val encrypted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "USER",
    val text: String = "",
    val personality: String = MinaPersonality.CARING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class JournalEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val mood: String = JournalMood.CALM.name,
    val encrypted: Boolean = false,
    val aiReflection: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SavedDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val docType: String = DocumentType.RESUME.name,
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FavoritePlace(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class AppSettingsState(
    val onboardingCompleted: Boolean = false,
    val darkMode: Boolean = true,
    val themeStyle: MinaThemeStyle = MinaThemeStyle.MIDNIGHT_AMBER,
    val codeSyntaxStyle: CodeSyntaxStyle = CodeSyntaxStyle.AMBER_DARK,
    val particlesEnabled: Boolean = true,
    val glowIntensity: Float = 0.85f,
    val heyMinaEnabled: Boolean = true,
    val autoListenEnabled: Boolean = false,
    val keepChattingEnabled: Boolean = true,
    val rememberConversations: Boolean = true,
    val speechRate: Float = 1.0f,
    val voicePitch: Float = 1.08f,
    val selectedVoiceName: String = "Mina Warm Female",
    val phoneControlEnabled: Boolean = true,
    val screenLockAutomation: Boolean = false,
    val messageReadingEnabled: Boolean = false,
    val aiAutoReplyDrafts: Boolean = true,
    val accessKey: String = "",
    val isPremiumUnlocked: Boolean = false
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
