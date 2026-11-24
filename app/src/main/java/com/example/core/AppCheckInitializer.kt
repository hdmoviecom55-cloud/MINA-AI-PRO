package com.example.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val META_PREFS_NAME = "mina_appcheck_meta"
private const val META_INTENT_TOKEN_REGISTERED = "intent_debug_token_registered"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"
private const val ERROR_TOKEN_PREFIX = "eyJlcnJvciI"

enum class AppCheckProviderMode(val label: String) {
    UNINITIALIZED("Not initialized"),
    DEBUG_LAUNCH_TOKEN("Debug Provider (Verified Token)"),
    DEBUG_LOCAL("Debug Provider (Local)"),
    RECAPTCHA_ENTERPRISE("reCAPTCHA Enterprise")
}

data class AppCheckDiagnostics(
    val providerMode: AppCheckProviderMode = AppCheckProviderMode.UNINITIALIZED,
    val isTokenVerified: Boolean = false,
    val lastError: String? = null,
    val lastCheckedAtMillis: Long? = null
)

object AppCheckManager {
    private val _diagnostics = MutableStateFlow(AppCheckDiagnostics())
    val diagnostics: StateFlow<AppCheckDiagnostics> = _diagnostics.asStateFlow()

    internal fun updateDiagnostics(transform: (AppCheckDiagnostics) -> AppCheckDiagnostics) {
        _diagnostics.value = transform(_diagnostics.value)
    }

    suspend fun verifyAppCheckToken(forceRefresh: Boolean = false): Result<Unit> {
        return try {
            if (_diagnostics.value.providerMode == AppCheckProviderMode.UNINITIALIZED) {
                val msg = _diagnostics.value.lastError
                    ?: "Firebase App Check provider is not initialized."
                updateDiagnostics {
                    it.copy(
                        isTokenVerified = false,
                        lastError = msg,
                        lastCheckedAtMillis = System.currentTimeMillis()
                    )
                }
                return Result.failure(IllegalStateException(msg))
            }

            val appCheck = FirebaseAppCheck.getInstance()
            val tokenResult = appCheck.getAppCheckToken(forceRefresh).await()
            val token = tokenResult.token
            if (token.isBlank() || token.startsWith(ERROR_TOKEN_PREFIX)) {
                val msg = "Firebase App Check returned an invalid or empty attestation token."
                updateDiagnostics {
                    it.copy(
                        isTokenVerified = false,
                        lastError = msg,
                        lastCheckedAtMillis = System.currentTimeMillis()
                    )
                }
                return Result.failure(IllegalStateException(msg))
            }

            updateDiagnostics {
                it.copy(
                    isTokenVerified = true,
                    lastError = null,
                    lastCheckedAtMillis = System.currentTimeMillis()
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "App Check token verification failed (forceRefresh=$forceRefresh)", e)
            val friendly = classifyAppCheckError(e)
            updateDiagnostics {
                it.copy(
                    isTokenVerified = false,
                    lastError = friendly,
                    lastCheckedAtMillis = System.currentTimeMillis()
                )
            }
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    fun classifyAppCheckError(
        error: Throwable,
        providerMode: AppCheckProviderMode = _diagnostics.value.providerMode
    ): String {
        val raw = buildString {
            var cur: Throwable? = error
            while (cur != null) {
                cur.message?.let { append(it).append(' ') }
                cur = cur.cause
            }
        }
        val lower = raw.lowercase()
        return when {
            lower.contains("no appcheckprovider installed") ->
                "Firebase App Check provider is not installed. Restart the app and ensure reCAPTCHA or debug attestation is configured."
            lower.contains("403") ||
                lower.contains("attestation failed") ||
                lower.contains("app check token is invalid") ||
                lower.contains("invalid app check") ||
                lower.contains("app check") && lower.contains("invalid") -> {
                when (providerMode) {
                    AppCheckProviderMode.DEBUG_LOCAL ->
                        "Firebase App Check rejected this device's unregistered debug token. Install a build configured with reCAPTCHA Enterprise or register the debug token in Firebase Console."
                    AppCheckProviderMode.DEBUG_LAUNCH_TOKEN ->
                        "Firebase App Check rejected the launch debug token (403). Verify the token is registered for this Firebase project and App ID."
                    else ->
                        "Firebase App Check attestation failed (403). Verify Google Play Services / reCAPTCHA Enterprise connectivity and try again."
                }
            }
            lower.contains("recaptcha") ->
                "reCAPTCHA Enterprise verification could not complete. Check internet & Google Play Services, then retry."
            lower.contains("network") || lower.contains("unable to resolve host") || lower.contains("timeout") ->
                "Network error while obtaining Firebase App Check token. Check internet connection and retry."
            else -> error.localizedMessage ?: "Firebase App Check verification failed."
        }
    }
}

private const val TOKEN_STORE_PREFIX = "com.google.firebase.appcheck.store."

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val app = FirebaseApp.getInstance()
        val persistenceKey = app.persistenceKey
        val debugPrefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        val tokenCachePrefs = context.getSharedPreferences(
            "$TOKEN_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        val metaPrefs = context.getSharedPreferences(META_PREFS_NAME, Context.MODE_PRIVATE)
        val siteKey = resolveRecaptchaSiteKey(context)

        if (BuildConfig.DEBUG) {
            val intentDebugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()?.takeIf { it.isNotEmpty() }

            if (intentDebugToken != null) {
                val previousToken = debugPrefs.getString(DEBUG_SECRET_KEY, null)
                // Synchronously commit debug secret and verified flag before FirebaseAppCheck/Factory reads them.
                debugPrefs.edit()
                    .putString(DEBUG_SECRET_KEY, intentDebugToken)
                    .commit()
                metaPrefs.edit()
                    .putBoolean(META_INTENT_TOKEN_REGISTERED, true)
                    .commit()

                if (previousToken != intentDebugToken) {
                    tokenCachePrefs.edit().clear().commit()
                }

                val appCheck = FirebaseAppCheck.getInstance()
                appCheck.setTokenAutoRefreshEnabled(true)
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance(),
                    true
                )
                AppCheckManager.updateDiagnostics {
                    it.copy(providerMode = AppCheckProviderMode.DEBUG_LAUNCH_TOKEN, lastError = null)
                }
                Log.d(TAG, "DebugAppCheckProviderFactory installed with launch Intent token.")
            } else if (metaPrefs.getBoolean(META_INTENT_TOKEN_REGISTERED, false) &&
                !debugPrefs.getString(DEBUG_SECRET_KEY, null).isNullOrBlank()
            ) {
                val appCheck = FirebaseAppCheck.getInstance()
                appCheck.setTokenAutoRefreshEnabled(true)
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance(),
                    true
                )
                AppCheckManager.updateDiagnostics {
                    it.copy(providerMode = AppCheckProviderMode.DEBUG_LAUNCH_TOKEN, lastError = null)
                }
                Log.d(TAG, "DebugAppCheckProviderFactory installed with persisted launch token.")
            } else if (!siteKey.isNullOrBlank()) {
                // On physical devices or launcher starts without a registered Intent debug token,
                // avoid generating an unregistered random debug UUID (which fails with 403).
                // Use the provisioned reCAPTCHA Enterprise site key instead.
                if (debugPrefs.contains(DEBUG_SECRET_KEY)) {
                    debugPrefs.edit().remove(DEBUG_SECRET_KEY).commit()
                    tokenCachePrefs.edit().clear().commit()
                }
                val appCheck = FirebaseAppCheck.getInstance()
                appCheck.setTokenAutoRefreshEnabled(true)
                appCheck.installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey),
                    true
                )
                AppCheckManager.updateDiagnostics {
                    it.copy(providerMode = AppCheckProviderMode.RECAPTCHA_ENTERPRISE, lastError = null)
                }
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed in debug build (no Intent debug token).")
            } else {
                val appCheck = FirebaseAppCheck.getInstance()
                appCheck.setTokenAutoRefreshEnabled(true)
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance(),
                    true
                )
                AppCheckManager.updateDiagnostics {
                    it.copy(providerMode = AppCheckProviderMode.DEBUG_LOCAL, lastError = null)
                }
                Log.w(TAG, "DebugAppCheckProviderFactory installed without Intent token or reCAPTCHA site key.")
            }
        } else {
            if (!siteKey.isNullOrBlank()) {
                val appCheck = FirebaseAppCheck.getInstance()
                appCheck.setTokenAutoRefreshEnabled(true)
                appCheck.installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey),
                    true
                )
                AppCheckManager.updateDiagnostics {
                    it.copy(providerMode = AppCheckProviderMode.RECAPTCHA_ENTERPRISE, lastError = null)
                }
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed for release build.")
            } else {
                val msg = "recaptcha_site_key string resource is missing or blank."
                Log.e(TAG, msg)
                AppCheckManager.updateDiagnostics {
                    it.copy(
                        providerMode = AppCheckProviderMode.UNINITIALIZED,
                        isTokenVerified = false,
                        lastError = msg
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        AppCheckManager.updateDiagnostics {
            it.copy(
                isTokenVerified = false,
                lastError = e.localizedMessage ?: "Failed to initialize Firebase App Check"
            )
        }
    }
}

private fun resolveRecaptchaSiteKey(context: Context): String? {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) return null
    return context.getString(resId).trim().takeIf { it.isNotEmpty() }
}
