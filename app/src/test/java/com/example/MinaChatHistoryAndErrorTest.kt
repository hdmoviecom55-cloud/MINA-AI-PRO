package com.example

import com.example.core.AppCheckManager
import com.example.core.AppCheckProviderMode
import com.example.data.model.ChatMessage
import com.example.data.repository.MinaAiErrorClassifier
import com.example.data.repository.MinaChatHistoryFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinaChatHistoryAndErrorTest {

    @Test
    fun normalizeChatHistory_enforcesAlternatingUserModelPairsEndingInModel() {
        val raw = listOf(
            ChatMessage(id = "1", role = "MINA", text = "Welcome back!"),
            ChatMessage(id = "2", role = "USER", text = "   "),
            ChatMessage(id = "3", role = "USER", text = "Hi Mina"),
            ChatMessage(id = "4", role = "USER", text = "Kaise ho?"),
            ChatMessage(id = "5", role = "MINA", text = "Main bilkul theek hoon!"),
            ChatMessage(id = "6", role = "USER", text = "Aaj ka plan?")
        )

        val normalized = MinaChatHistoryFormatter.normalizeChatHistory(raw, "Aaj ka plan?")

        assertEquals(2, normalized.size)
        assertEquals("user", normalized[0].role)
        assertEquals("Hi Mina\nKaise ho?", normalized[0].text)
        assertEquals("model", normalized[1].role)
        assertEquals("Main bilkul theek hoon!", normalized[1].text)
    }

    @Test
    fun normalizeChatHistory_capsAtMaxTurnsWithCleanBoundaries() {
        val raw = (1..10).flatMap { idx ->
            listOf(
                ChatMessage(id = "u$idx", role = "USER", text = "User $idx"),
                ChatMessage(id = "m$idx", role = "MINA", text = "Model $idx")
            )
        }

        val normalized = MinaChatHistoryFormatter.normalizeChatHistory(raw, "New question", maxTurns = 6)

        assertEquals(6, normalized.size)
        assertEquals("user", normalized.first().role)
        assertEquals("model", normalized.last().role)
        for (i in 0 until normalized.size step 2) {
            assertEquals("user", normalized[i].role)
            assertEquals("model", normalized[i + 1].role)
        }
    }

    @Test
    fun errorClassifier_detectsAppCheckErrorsInCauseChain() {
        val root = IllegalStateException("Server returned HTTP 403: Firebase App Check token is invalid.")
        val wrapped = RuntimeException("Generative request rejected", root)

        assertTrue(MinaAiErrorClassifier.isAppCheckRelated(wrapped))
        val msg = MinaAiErrorClassifier.classify(wrapped, AppCheckProviderMode.RECAPTCHA_ENTERPRISE)
        assertTrue(msg.contains("App Check", ignoreCase = true) || msg.contains("reCAPTCHA", ignoreCase = true))
    }

    @Test
    fun errorClassifier_detectsTimeoutAndRateLimitAndNetwork() {
        var timeoutMsg = ""
        try {
            runBlocking {
                withTimeout(1L) {
                    delay(100L)
                }
            }
        } catch (e: Exception) {
            timeoutMsg = MinaAiErrorClassifier.classify(e)
        }
        assertTrue(timeoutMsg.contains("timed out", ignoreCase = true))

        val rateLimit = MinaAiErrorClassifier.classify(IllegalStateException("HTTP 429 RESOURCE_EXHAUSTED"))
        assertTrue(rateLimit.contains("rate limit", ignoreCase = true) || rateLimit.contains("quota", ignoreCase = true))

        val network = MinaAiErrorClassifier.classify(IllegalStateException("Unable to resolve host generativelanguage.googleapis.com"))
        assertTrue(network.contains("network", ignoreCase = true))
        assertFalse(MinaAiErrorClassifier.isAppCheckRelated(IllegalStateException("Unable to resolve host")))
    }

    @Test
    fun appCheckManager_classifiesProviderAndRecaptchaErrors() {
        val missingProvider = AppCheckManager.classifyAppCheckError(
            IllegalStateException("No AppCheckProvider installed.")
        )
        assertTrue(missingProvider.contains("provider is not installed", ignoreCase = true))

        val recaptcha = AppCheckManager.classifyAppCheckError(
            IllegalStateException("reCAPTCHA token fetch failed")
        )
        assertTrue(recaptcha.contains("reCAPTCHA", ignoreCase = true))
    }
}
