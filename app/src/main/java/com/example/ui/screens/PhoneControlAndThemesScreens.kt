package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.PdfAndExportHelper
import com.example.core.PhoneControlHelper
import com.example.data.model.AppSettingsState
import com.example.data.model.CodeSyntaxStyle
import com.example.data.model.MinaThemeStyle
import com.example.data.model.UiState
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.GlassCard
import com.example.ui.components.MinaBackdrop
import com.example.ui.components.MinaTopBar
import com.example.ui.theme.LocalMinaPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneControlScreen(
    settings: AppSettingsState,
    smartReplyState: UiState<String>?,
    onBack: () -> Unit,
    onVoiceCommand: ((String) -> Unit) -> Unit,
    onGenerateSmartReply: (String) -> Unit,
    onSpeakText: (String) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMinaPalette.current

    var torchOn by remember { mutableStateOf(false) }
    var mediaVolume by remember { mutableIntStateOf(PhoneControlHelper.getVolumePercent(context, AudioManager.STREAM_MUSIC)) }
    var ringVolume by remember { mutableIntStateOf(PhoneControlHelper.getVolumePercent(context, AudioManager.STREAM_RING)) }
    var alarmHour by remember { mutableIntStateOf(7) }
    var alarmMinute by remember { mutableIntStateOf(30) }
    var appCommand by remember { mutableStateOf("") }
    var incomingMsgText by remember { mutableStateOf("") }

    val cameraPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            PhoneControlHelper.toggleFlashlight(context, !torchOn)
                .onSuccess { torchOn = it }
                .onFailure { onToast(it.localizedMessage ?: "Flashlight unavailable") }
        } else {
            onToast("Camera permission needed for flashlight.")
        }
    }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            onVoiceCommand { spoken ->
                appCommand = spoken
                PhoneControlHelper.openAppByKeyword(context, spoken)
                    .onSuccess { onToast(it) }
                    .onFailure { onToast("Could not execute command.") }
            }
        }
    }

    MinaBackdrop(
        particlesEnabled = settings.particlesEnabled,
        glowIntensity = settings.glowIntensity
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            MinaTopBar(
                title = "Phone Control",
                subtitle = "Hardware, Apps, Alarms & Consent Controls",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Voice / Text App Launcher
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Open Apps & Voice Commands", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = appCommand,
                                onValueChange = { appCommand = it },
                                label = { Text("Command (e.g. Open YouTube)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        onVoiceCommand { spoken ->
                                            appCommand = spoken
                                            PhoneControlHelper.openAppByKeyword(context, spoken).onSuccess(onToast)
                                        }
                                    } else {
                                        micPerm.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(Icons.Filled.Mic, contentDescription = "Voice command")
                            }
                        }

                        AmberGradientButton(
                            text = "Run Command",
                            icon = Icons.Filled.Apps,
                            enabled = appCommand.isNotBlank(),
                            onClick = {
                                PhoneControlHelper.openAppByKeyword(context, appCommand)
                                    .onSuccess(onToast)
                                    .onFailure { onToast("Unable to launch") }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            height = 46.dp
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("YouTube", "Instagram", "Spotify", "WhatsApp", "Maps", "Settings").forEach { app ->
                                FilterChip(
                                    selected = false,
                                    onClick = { PhoneControlHelper.openAppByKeyword(context, app).onSuccess(onToast) },
                                    label = { Text(app) }
                                )
                            }
                        }
                    }
                }

                // Flashlight & Volume Controls
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Hardware & Volume", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)

                        AmberGradientButton(
                            text = if (torchOn) "Turn Flashlight OFF" else "Turn Flashlight ON",
                            icon = if (torchOn) Icons.Filled.FlashlightOff else Icons.Filled.FlashlightOn,
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    PhoneControlHelper.toggleFlashlight(context, !torchOn)
                                        .onSuccess { torchOn = it }
                                        .onFailure { onToast(it.localizedMessage ?: "No flash hardware") }
                                } else {
                                    cameraPerm.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("phone_flashlight_button")
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = palette.goldHighlight)
                            Spacer(Modifier.width(8.dp))
                            Text("Media Volume: $mediaVolume%", style = MaterialTheme.typography.bodyLarge)
                        }
                        Slider(
                            value = mediaVolume.toFloat(),
                            onValueChange = {
                                mediaVolume = it.toInt()
                                PhoneControlHelper.setVolumePercent(context, AudioManager.STREAM_MUSIC, mediaVolume)
                            },
                            valueRange = 0f..100f
                        )

                        Text("Ring Volume: $ringVolume%", style = MaterialTheme.typography.bodyLarge)
                        Slider(
                            value = ringVolume.toFloat(),
                            onValueChange = {
                                ringVolume = it.toInt()
                                PhoneControlHelper.setVolumePercent(context, AudioManager.STREAM_RING, ringVolume)
                            },
                            valueRange = 0f..100f
                        )
                    }
                }

                // Alarms & System Settings Shortcuts
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Alarms, Timers & Android Settings", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AmberGradientButton(
                                text = "Set ${String.format("%02d:%02d", alarmHour, alarmMinute)} Alarm",
                                icon = Icons.Filled.Alarm,
                                onClick = {
                                    PhoneControlHelper.setAlarm(context, alarmHour, alarmMinute, "MINA AI Reminder")
                                        .onFailure { onToast("Clock app unavailable") }
                                },
                                modifier = Modifier.weight(1f),
                                height = 46.dp
                            )
                            OutlinedButton(
                                onClick = {
                                    PhoneControlHelper.setTimer(context, 300, "MINA 5m Focus")
                                        .onFailure { onToast("Timer app unavailable") }
                                },
                                modifier = Modifier.height(46.dp)
                            ) {
                                Text("5m Timer")
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { PhoneControlHelper.openSystemSettings(context, Settings.ACTION_WIFI_SETTINGS) }) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Wi-Fi")
                            }
                            OutlinedButton(onClick = { PhoneControlHelper.openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) }) {
                                Text("Bluetooth")
                            }
                            OutlinedButton(onClick = { PhoneControlHelper.openSystemSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Notification Access")
                            }
                            OutlinedButton(onClick = { PhoneControlHelper.openSystemSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) }) {
                                Icon(Icons.Filled.AccessibilityNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Accessibility Consent")
                            }
                        }
                    }
                }

                // Message Reading & Suggested AI Reply (Explicit User Confirmation)
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Message Reader & AI Reply Drafter", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        Text(
                            text = "Paste an incoming message to have MINA read it aloud or draft safe replies. Messages are never sent automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = incomingMsgText,
                            onValueChange = { incomingMsgText = it },
                            label = { Text("Incoming message text") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                enabled = incomingMsgText.isNotBlank(),
                                onClick = { onSpeakText(incomingMsgText) },
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Text("Read Aloud")
                            }
                            AmberGradientButton(
                                text = "Draft AI Replies",
                                enabled = incomingMsgText.isNotBlank(),
                                onClick = { onGenerateSmartReply(incomingMsgText) },
                                modifier = Modifier.weight(1f),
                                height = 46.dp
                            )
                        }

                        when (smartReplyState) {
                            UiState.Loading -> CircularProgressIndicator(color = palette.goldHighlight, modifier = Modifier.size(26.dp))
                            is UiState.Success -> {
                                Text(smartReplyState.data, style = MaterialTheme.typography.bodyLarge)
                                OutlinedButton(
                                    onClick = { PdfAndExportHelper.shareText(context, smartReplyState.data, "Suggested Reply") }
                                ) {
                                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share Chosen Reply")
                                }
                            }
                            is UiState.Error -> Text(smartReplyState.message, color = MaterialTheme.colorScheme.error)
                            null -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemesScreen(
    settings: AppSettingsState,
    onBack: () -> Unit,
    onUpdateSettings: ((AppSettingsState) -> AppSettingsState) -> Unit
) {
    val palette = LocalMinaPalette.current

    MinaBackdrop(
        particlesEnabled = settings.particlesEnabled,
        glowIntensity = settings.glowIntensity
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            MinaTopBar(
                title = "Themes & Visuals",
                subtitle = "Customize Dark/Light, Amber Glow & Code Colours",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Theme Presets", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)

                        MinaThemeStyle.entries.forEach { style ->
                            val selected = settings.themeStyle == style
                            val swatchColors = when (style) {
                                MinaThemeStyle.MIDNIGHT_AMBER -> Color(0xFF140D09) to Color(0xFFFFA000)
                                MinaThemeStyle.OBSIDIAN_EMBER -> Color(0xFF0E0B0B) to Color(0xFFFF5722)
                                MinaThemeStyle.ROYAL_BRONZE -> Color(0xFF1A110A) to Color(0xFFFFE082)
                                MinaThemeStyle.SOLAR_LIGHT -> Color(0xFFFFF8F0) to Color(0xFFFF6D00)
                            }
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                borderWidth = if (selected) 2.dp else 1.dp,
                                contentPadding = PaddingValues(14.dp),
                                onClick = {
                                    onUpdateSettings {
                                        it.copy(
                                            themeStyle = style,
                                            darkMode = style != MinaThemeStyle.SOLAR_LIGHT
                                        )
                                    }
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(swatchColors.first)
                                            .border(3.dp, swatchColors.second, CircleShape)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(style.label, style = MaterialTheme.typography.titleMedium)
                                        Text(style.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (selected) {
                                        Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = palette.goldHighlight)
                                    }
                                }
                            }
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Atmosphere & Code Colours", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = settings.darkMode,
                                onCheckedChange = { c ->
                                    onUpdateSettings {
                                        it.copy(
                                            darkMode = c,
                                            themeStyle = if (!c) MinaThemeStyle.SOLAR_LIGHT else if (it.themeStyle == MinaThemeStyle.SOLAR_LIGHT) MinaThemeStyle.MIDNIGHT_AMBER else it.themeStyle
                                        )
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Floating Particles", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = settings.particlesEnabled,
                                onCheckedChange = { c -> onUpdateSettings { it.copy(particlesEnabled = c) } }
                            )
                        }

                        Text("Ambient Glow Intensity: ${(settings.glowIntensity * 100).toInt()}%")
                        Slider(
                            value = settings.glowIntensity,
                            onValueChange = { g -> onUpdateSettings { it.copy(glowIntensity = g) } },
                            valueRange = 0.2f..1.2f
                        )

                        Text("Code Syntax Palette", style = MaterialTheme.typography.bodyLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CodeSyntaxStyle.entries.forEach { codeStyle ->
                                FilterChip(
                                    selected = settings.codeSyntaxStyle == codeStyle,
                                    onClick = { onUpdateSettings { it.copy(codeSyntaxStyle = codeStyle) } },
                                    label = { Text(codeStyle.label) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
