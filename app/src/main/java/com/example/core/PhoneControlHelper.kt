package com.example.core

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log

object PhoneControlHelper {
    private const val TAG = "PhoneControl"

    fun toggleFlashlight(context: Context, enable: Boolean): Result<Boolean> {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return Result.failure(IllegalStateException("Flashlight hardware unavailable."))
            cameraManager.setTorchMode(cameraId, enable)
            Result.success(enable)
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight toggle failed", e)
            Result.failure(e)
        }
    }

    fun getVolumePercent(context: Context, streamType: Int): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(streamType)
        return ((cur.toFloat() / max) * 100f).toInt().coerceIn(0, 100)
    }

    fun setVolumePercent(context: Context, streamType: Int, percent: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audioManager.getStreamMaxVolume(streamType)
            val target = ((percent.coerceIn(0, 100) / 100f) * max).toInt()
            audioManager.setStreamVolume(streamType, target, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Volume adjustment failed", e)
        }
    }

    fun openExternalUrlOrApp(context: Context, url: String, fallbackPackage: String? = null): Result<Unit> {
        return try {
            if (!fallbackPackage.isNullOrBlank()) {
                val launch = context.packageManager.getLaunchIntentForPackage(fallbackPackage)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    return Result.success(Unit)
                }
            }
            val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app/url", e)
            Result.failure(e)
        }
    }

    fun openAppByKeyword(context: Context, query: String): Result<String> {
        val q = query.lowercase()
        return when {
            "youtube" in q -> {
                openExternalUrlOrApp(context, "https://www.youtube.com", "com.google.android.youtube")
                    .map { "Opening YouTube" }
            }
            "instagram" in q -> {
                openExternalUrlOrApp(context, "https://www.instagram.com", "com.instagram.android")
                    .map { "Opening Instagram" }
            }
            "spotify" in q || "music" in q -> {
                openExternalUrlOrApp(context, "https://open.spotify.com", "com.spotify.music")
                    .map { "Opening Spotify" }
            }
            "whatsapp" in q -> {
                openExternalUrlOrApp(context, "https://wa.me", "com.whatsapp")
                    .map { "Opening WhatsApp" }
            }
            "map" in q || "navigation" in q -> {
                openExternalUrlOrApp(context, "https://maps.google.com", "com.google.android.apps.maps")
                    .map { "Opening Maps" }
            }
            "setting" in q -> {
                openSystemSettings(context, Settings.ACTION_SETTINGS).map { "Opening Phone Settings" }
            }
            else -> {
                openExternalUrlOrApp(context, "https://www.google.com/search?q=${Uri.encode(query)}")
                    .map { "Searching for $query" }
            }
        }
    }

    fun setAlarm(context: Context, hour: Int, minute: Int, message: String): Result<Unit> {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Set alarm failed", e)
            Result.failure(e)
        }
    }

    fun setTimer(context: Context, seconds: Int, message: String): Result<Unit> {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(5))
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Set timer failed", e)
            Result.failure(e)
        }
    }

    fun openSystemSettings(context: Context, action: String): Result<Unit> {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                Result.success(Unit)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    fun openMapNavigation(context: Context, lat: Double, lng: Double, label: String): Result<Unit> {
        return try {
            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            openExternalUrlOrApp(context, "https://maps.google.com/?q=$lat,$lng")
        }
    }
}
