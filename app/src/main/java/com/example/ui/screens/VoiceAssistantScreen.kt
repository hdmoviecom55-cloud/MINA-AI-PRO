package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.AppSettingsState
import com.example.data.model.AssistantStatus
import com.example.data.model.MinaLanguage
import com.example.data.model.MinaPersonality
import com.example.data.model.UserProfile
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.AssistantStatusPill
import com.example.ui.components.GlassCard
import com.example.ui.components.MinaAnimatedOrb
import com.example.ui.components.MinaBackdrop
import com.example.ui.components.MinaTopBar
import com.example.ui.theme.LocalMinaPalette
import kotlin.math.sin

@Composable
fun VoiceAssistantScreen(
    profile: UserProfile,
    settings: AppSettingsState,
    status: AssistantStatus,
    amplitude: Float,
    partialTranscript: String,
    lastResponse: String,
    availableVoices: List<String>,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onUpdateSettings: ((AppSettingsState) -> AppSettingsState) -> Unit,
    onUpdateLanguage: (MinaLanguage) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMinaPalette.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartListening() else onToast("Microphone permission is needed to talk to MINA.")
    }

    fun triggerVoice() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (status == AssistantStatus.LISTENING) onStopListening() else onStartListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-listen loop when enabled and assistant becomes READY after speaking
    LaunchedEffect(settings.autoListenEnabled, status, lastResponse) {
        if (settings.autoListenEnabled && status == AssistantStatus.READY && lastResponse.isNotBlank()) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                kotlinx.coroutines.delay(900)
                onStartListening()
            }
        }
    }

    MinaBackdrop(
        particlesEnabled = settings.particlesEnabled,
        glowIntensity = settings.glowIntensity,
        showHeroArt = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MinaTopBar(
                title = "MINA Voice Mode",
                subtitle = if (settings.heyMinaEnabled) "Wake Phrase: \"Hey Mina\" Active" else "Tap mic to talk",
                trailing = { AssistantStatusPill(status = status) }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MinaAnimatedOrb(
                    status = status,
                    amplitude = amplitude,
                    size = 186.dp,
                    showAvatar = true,
                    onClick = { triggerVoice() }
                )

                Spacer(Modifier.height(12.dp))

                // Live Waveform Visualizer
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    val bars = 28
                    val step = size.width / bars
                    val centerY = size.height / 2f
                    val activeAmp = when (status) {
                        AssistantStatus.LISTENING -> (0.28f + amplitude * 0.72f)
                        AssistantStatus.SPEAKING -> 0.58f
                        AssistantStatus.THINKING -> 0.25f
                        AssistantStatus.READY -> 0.08f
                    }
                    for (i in 0 until bars) {
                        val x = step * i + step / 2f
                        val h = (size.height * 0.85f) * activeAmp * (0.4f + 0.6f * kotlin.math.abs(sin(i * 0.65f + amplitude * 6f)))
                        drawLine(
                            color = palette.goldHighlight,
                            start = Offset(x, centerY - h / 2f),
                            end = Offset(x, centerY + h / 2f),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Transcript / Response Box
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (partialTranscript.isNotBlank()) {
                            Text(
                                text = "You: \"$partialTranscript\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.goldHighlight,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            text = lastResponse.ifBlank {
                                "Bol ke dekho! Say \"Hey Mina, aaj ka din kaise plan karein?\" or tap below."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AmberGradientButton(
                        text = if (status == AssistantStatus.LISTENING) "Stop Listening" else "Speak to MINA",
                        icon = Icons.Filled.Mic,
                        onClick = { triggerVoice() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_mic_button")
                    )

                    if (status == AssistantStatus.SPEAKING) {
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                    RoundedCornerShape(26.dp)
                                )
                                .clickable { onStopSpeaking() }
                                .padding(horizontal = 18.dp)
                                .testTag("voice_stop_speaking_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop speaking", tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(6.dp))
                                Text("Interrupt", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Instant Spoken Prompts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Hey Mina, motivate me!",
                        "Ek short Hindi kahani sunao",
                        "Teach me 1 productivity habit",
                        "Aaj mood fresh karne ki tip do"
                    ).forEach { chip ->
                        GlassCard(
                            cornerRadius = 50.dp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            onClick = { onSendPrompt(chip) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = palette.goldHighlight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(chip, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Voice Controls Card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Voice & Language Controls", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("\"Hey Mina\" Wake Prefix", style = MaterialTheme.typography.bodyLarge)
                                Text("Strips wake word automatically", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.heyMinaEnabled,
                                onCheckedChange = { checked -> onUpdateSettings { it.copy(heyMinaEnabled = checked) } }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Listen Mode", style = MaterialTheme.typography.bodyLarge)
                                Text("Listen again after MINA finishes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.autoListenEnabled,
                                onCheckedChange = { checked -> onUpdateSettings { it.copy(autoListenEnabled = checked) } }
                            )
                        }

                        Text("Voice Language", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val currentLang = MinaLanguage.fromRaw(profile.language)
                            MinaLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = currentLang == lang,
                                    onClick = { onUpdateLanguage(lang) },
                                    label = { Text(lang.displayName) }
                                )
                            }
                        }

                        Text(
                            text = "Speaking Speed: ${String.format("%.2fx", settings.speechRate)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Slider(
                            value = settings.speechRate,
                            onValueChange = { rate -> onUpdateSettings { it.copy(speechRate = rate) } },
                            valueRange = 0.6f..1.6f
                        )

                        Text(
                            text = "Voice Pitch: ${String.format("%.2f", settings.voicePitch)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Slider(
                            value = settings.voicePitch,
                            onValueChange = { pitch -> onUpdateSettings { it.copy(voicePitch = pitch) } },
                            valueRange = 0.75f..1.35f
                        )

                        if (availableVoices.isNotEmpty()) {
                            Text("Voice Profile", style = MaterialTheme.typography.bodyLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableVoices.forEach { voiceName ->
                                    FilterChip(
                                        selected = settings.selectedVoiceName == voiceName,
                                        onClick = { onUpdateSettings { it.copy(selectedVoiceName = voiceName) } },
                                        label = { Text(voiceName.take(22)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
