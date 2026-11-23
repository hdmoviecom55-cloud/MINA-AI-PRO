package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettingsState
import com.example.data.model.CodeSyntaxStyle
import com.example.data.model.MinaThemeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.minaSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mina_ai_settings"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val CODE_STYLE = stringPreferencesKey("code_style")
        val PARTICLES = booleanPreferencesKey("particles_enabled")
        val GLOW = floatPreferencesKey("glow_intensity")
        val HEY_MINA = booleanPreferencesKey("hey_mina_enabled")
        val AUTO_LISTEN = booleanPreferencesKey("auto_listen_enabled")
        val KEEP_CHATTING = booleanPreferencesKey("keep_chatting_enabled")
        val REMEMBER_CHATS = booleanPreferencesKey("remember_conversations")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val VOICE_NAME = stringPreferencesKey("selected_voice_name")
        val PHONE_CONTROL = booleanPreferencesKey("phone_control_enabled")
        val SCREEN_LOCK = booleanPreferencesKey("screen_lock_automation")
        val MSG_READING = booleanPreferencesKey("message_reading_enabled")
        val AI_AUTO_REPLY = booleanPreferencesKey("ai_auto_reply_drafts")
        val ACCESS_KEY = stringPreferencesKey("access_key")
    }

    val settingsFlow: Flow<AppSettingsState> = context.minaSettingsDataStore.data.map { prefs ->
        val accessKey = prefs[Keys.ACCESS_KEY] ?: ""
        val darkMode = prefs[Keys.DARK_MODE] ?: true
        val themeRaw = prefs[Keys.THEME_STYLE]
        val themeStyle = MinaThemeStyle.fromRaw(themeRaw)
        AppSettingsState(
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            darkMode = darkMode,
            themeStyle = themeStyle,
            codeSyntaxStyle = CodeSyntaxStyle.fromRaw(prefs[Keys.CODE_STYLE]),
            particlesEnabled = prefs[Keys.PARTICLES] ?: true,
            glowIntensity = prefs[Keys.GLOW] ?: 0.85f,
            heyMinaEnabled = prefs[Keys.HEY_MINA] ?: true,
            autoListenEnabled = prefs[Keys.AUTO_LISTEN] ?: false,
            keepChattingEnabled = prefs[Keys.KEEP_CHATTING] ?: true,
            rememberConversations = prefs[Keys.REMEMBER_CHATS] ?: true,
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.08f,
            selectedVoiceName = prefs[Keys.VOICE_NAME] ?: "Mina Warm Female",
            phoneControlEnabled = prefs[Keys.PHONE_CONTROL] ?: true,
            screenLockAutomation = prefs[Keys.SCREEN_LOCK] ?: false,
            messageReadingEnabled = prefs[Keys.MSG_READING] ?: false,
            aiAutoReplyDrafts = prefs[Keys.AI_AUTO_REPLY] ?: true,
            accessKey = accessKey,
            isPremiumUnlocked = accessKey.trim().startsWith("MINA-", ignoreCase = true) && accessKey.trim().length >= 9
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.minaSettingsDataStore.edit { it[Keys.ONBOARDING_DONE] = completed }
    }

    suspend fun updateSettings(transform: (AppSettingsState) -> AppSettingsState) {
        context.minaSettingsDataStore.edit { prefs ->
            val current = AppSettingsState(
                onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
                darkMode = prefs[Keys.DARK_MODE] ?: true,
                themeStyle = MinaThemeStyle.fromRaw(prefs[Keys.THEME_STYLE]),
                codeSyntaxStyle = CodeSyntaxStyle.fromRaw(prefs[Keys.CODE_STYLE]),
                particlesEnabled = prefs[Keys.PARTICLES] ?: true,
                glowIntensity = prefs[Keys.GLOW] ?: 0.85f,
                heyMinaEnabled = prefs[Keys.HEY_MINA] ?: true,
                autoListenEnabled = prefs[Keys.AUTO_LISTEN] ?: false,
                keepChattingEnabled = prefs[Keys.KEEP_CHATTING] ?: true,
                rememberConversations = prefs[Keys.REMEMBER_CHATS] ?: true,
                speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
                voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.08f,
                selectedVoiceName = prefs[Keys.VOICE_NAME] ?: "Mina Warm Female",
                phoneControlEnabled = prefs[Keys.PHONE_CONTROL] ?: true,
                screenLockAutomation = prefs[Keys.SCREEN_LOCK] ?: false,
                messageReadingEnabled = prefs[Keys.MSG_READING] ?: false,
                aiAutoReplyDrafts = prefs[Keys.AI_AUTO_REPLY] ?: true,
                accessKey = prefs[Keys.ACCESS_KEY] ?: ""
            )
            val updated = transform(current)
            prefs[Keys.ONBOARDING_DONE] = updated.onboardingCompleted
            prefs[Keys.DARK_MODE] = updated.darkMode
            prefs[Keys.THEME_STYLE] = updated.themeStyle.name
            prefs[Keys.CODE_STYLE] = updated.codeSyntaxStyle.name
            prefs[Keys.PARTICLES] = updated.particlesEnabled
            prefs[Keys.GLOW] = updated.glowIntensity
            prefs[Keys.HEY_MINA] = updated.heyMinaEnabled
            prefs[Keys.AUTO_LISTEN] = updated.autoListenEnabled
            prefs[Keys.KEEP_CHATTING] = updated.keepChattingEnabled
            prefs[Keys.REMEMBER_CHATS] = updated.rememberConversations
            prefs[Keys.SPEECH_RATE] = updated.speechRate
            prefs[Keys.VOICE_PITCH] = updated.voicePitch
            prefs[Keys.VOICE_NAME] = updated.selectedVoiceName
            prefs[Keys.PHONE_CONTROL] = updated.phoneControlEnabled
            prefs[Keys.SCREEN_LOCK] = updated.screenLockAutomation
            prefs[Keys.MSG_READING] = updated.messageReadingEnabled
            prefs[Keys.AI_AUTO_REPLY] = updated.aiAutoReplyDrafts
            prefs[Keys.ACCESS_KEY] = updated.accessKey
        }
    }
}
