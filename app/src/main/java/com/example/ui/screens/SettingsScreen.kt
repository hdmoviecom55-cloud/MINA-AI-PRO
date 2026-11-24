package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.example.core.AppCheckDiagnostics
import com.example.data.model.AppSettingsState
import com.example.data.model.CodeSyntaxStyle
import com.example.data.model.MinaLanguage
import com.example.data.model.MinaPersonality
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.GlassCard
import com.example.ui.components.MinaBackdrop
import com.example.ui.components.MinaTopBar
import com.example.ui.theme.LocalMinaPalette

@Composable
fun SettingsScreen(
    profile: UserProfile,
    settings: AppSettingsState,
    availableVoices: List<String>,
    appCheckDiagnostics: AppCheckDiagnostics,
    backendTestState: UiState<String>?,
    onTestBackendConnection: () -> Unit,
    onUpdateSettings: ((AppSettingsState) -> AppSettingsState) -> Unit,
    onUpdateTone: (MinaPersonality, Int, MinaLanguage) -> Unit,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
    onToast: (String) -> Unit
) {
    val palette = LocalMinaPalette.current
    var accessKeyInput by remember(settings.accessKey) { mutableStateOf(settings.accessKey) }

    val emotionLabel = when (profile.emotionLevel.toInt().coerceIn(1, 5)) {
        1 -> "1/5 – Calm"
        2 -> "2/5 – Friendly"
        3 -> "3/5 – Normal"
        4 -> "4/5 – Caring"
        else -> "5/5 – Highly Expressive"
    }

    MinaBackdrop(
        particlesEnabled = settings.particlesEnabled,
        glowIntensity = settings.glowIntensity
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp)
        ) {
            MinaTopBar(
                title = "Settings",
                subtitle = "Premium, AI Voice, Appearance & Controls"
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Premium Section
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (settings.isPremiumUnlocked) Icons.Filled.Verified else Icons.Filled.Key,
                                contentDescription = null,
                                tint = palette.goldHighlight
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("MINA Premium & Access Key", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                                Text(
                                    text = if (settings.isPremiumUnlocked) "Status: MINA Pro Active" else "Status: Standard Companion Tier",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedTextField(
                            value = accessKeyInput,
                            onValueChange = { accessKeyInput = it },
                            label = { Text("Access Key (e.g. MINA-2026-PRO)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        AmberGradientButton(
                            text = "Activate Access Key",
                            onClick = {
                                onUpdateSettings { it.copy(accessKey = accessKeyInput.trim()) }
                                if (accessKeyInput.trim().startsWith("MINA-", ignoreCase = true) && accessKeyInput.trim().length >= 9) {
                                    onToast("MINA Pro Access Key activated!")
                                } else {
                                    onToast("Key saved. Use format MINA-XXXX-XXX for Pro status.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            height = 46.dp
                        )
                    }
                }

                // 2. AI & Voice Section
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("AI & Voice Configuration", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)

                        Text(
                            text = "AI Backend: Firebase AI Logic (Gemini 3.8 Flash • App Check Secured)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val tokenStatusText = when {
                            appCheckDiagnostics.isTokenVerified -> "Verified"
                            appCheckDiagnostics.lastError != null -> "Attention Needed"
                            else -> "Ready (On-Demand)"
                        }
                        Text(
                            text = "App Check: ${appCheckDiagnostics.providerMode.label} • $tokenStatusText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (appCheckDiagnostics.lastError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                palette.goldHighlight
                            }
                        )
                        appCheckDiagnostics.lastError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedButton(
                            onClick = onTestBackendConnection,
                            enabled = backendTestState !is UiState.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_test_connection_button")
                        ) {
                            if (backendTestState is UiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Testing App Check & Gemini...")
                            } else {
                                Text("Test Gemini & App Check")
                            }
                        }

                        when (backendTestState) {
                            is UiState.Success -> {
                                Text(
                                    text = "Live Reply: ${backendTestState.data}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.goldHighlight
                                )
                            }
                            is UiState.Error -> {
                                Text(
                                    text = backendTestState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> Unit
                        }

                        SettingsToggleRow(
                            title = "Hey Mina Wake Word",
                            subtitle = "Respond to 'Hey Mina' voice prefix",
                            checked = settings.heyMinaEnabled,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(heyMinaEnabled = c) } }
                        )

                        SettingsToggleRow(
                            title = "Keep Chatting",
                            subtitle = "MINA asks natural follow-up questions",
                            checked = settings.keepChattingEnabled,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(keepChattingEnabled = c) } }
                        )

                        Text("Emotion Style", style = MaterialTheme.typography.bodyLarge)
                        val currentPersonality = MinaPersonality.fromRaw(profile.personality)
                        val currentLanguage = MinaLanguage.fromRaw(profile.language)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MinaPersonality.entries.forEach { p ->
                                FilterChip(
                                    selected = currentPersonality == p,
                                    onClick = { onUpdateTone(p, profile.emotionLevel.toInt(), currentLanguage) },
                                    label = { Text(p.displayName) }
                                )
                            }
                        }

                        Text("Emotion Level: $emotionLabel", style = MaterialTheme.typography.bodyLarge)
                        Slider(
                            value = profile.emotionLevel.toFloat().coerceIn(1f, 5f),
                            onValueChange = { v ->
                                onUpdateTone(currentPersonality, v.toInt().coerceIn(1, 5), currentLanguage)
                            },
                            valueRange = 1f..5f,
                            steps = 3
                        )

                        Text("Language", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MinaLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = currentLanguage == lang,
                                    onClick = { onUpdateTone(currentPersonality, profile.emotionLevel.toInt(), lang) },
                                    label = { Text(lang.displayName) }
                                )
                            }
                        }

                        if (availableVoices.isNotEmpty()) {
                            Text("Voice Selection", style = MaterialTheme.typography.bodyLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableVoices.forEach { v ->
                                    FilterChip(
                                        selected = settings.selectedVoiceName == v,
                                        onClick = { onUpdateSettings { it.copy(selectedVoiceName = v) } },
                                        label = { Text(v.take(22)) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Appearance Section
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Appearance & Studio", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)

                        SettingsToggleRow(
                            title = "Dark Mode",
                            subtitle = "Toggle dark futuristic vs warm ivory light theme",
                            checked = settings.darkMode,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(darkMode = c) } }
                        )

                        SettingsToggleRow(
                            title = "Floating Gold Particles",
                            subtitle = "Custom animated background stardust",
                            checked = settings.particlesEnabled,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(particlesEnabled = c) } }
                        )

                        Text("Code Syntax Colour", style = MaterialTheme.typography.bodyLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CodeSyntaxStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = settings.codeSyntaxStyle == style,
                                    onClick = { onUpdateSettings { it.copy(codeSyntaxStyle = style) } },
                                    label = { Text(style.label) }
                                )
                            }
                        }

                        SettingsNavRow(
                            icon = Icons.Filled.Palette,
                            title = "Open Themes Studio",
                            subtitle = settings.themeStyle.label,
                            onClick = { onNavigate("themes") }
                        )
                    }
                }

                // 4. Phone Control & Privacy Section
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Phone Control & Automation", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)

                        SettingsToggleRow(
                            title = "Phone Control Module",
                            subtitle = "Enable flashlight, volume, alarms & app launch",
                            checked = settings.phoneControlEnabled,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(phoneControlEnabled = c) } }
                        )
                        SettingsToggleRow(
                            title = "Screen Lock Automation (Consent Required)",
                            subtitle = "Guides you to Android Accessibility settings when needed",
                            checked = settings.screenLockAutomation,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(screenLockAutomation = c) } }
                        )
                        SettingsToggleRow(
                            title = "Message Reading Helper",
                            subtitle = "Read pasted/shared messages aloud with TTS",
                            checked = settings.messageReadingEnabled,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(messageReadingEnabled = c) } }
                        )
                        SettingsToggleRow(
                            title = "AI Smart Reply Drafts",
                            subtitle = "Prepare reply drafts (never sends without confirmation)",
                            checked = settings.aiAutoReplyDrafts,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(aiAutoReplyDrafts = c) } }
                        )

                        SettingsNavRow(
                            icon = Icons.Filled.PhoneAndroid,
                            title = "Open Phone Control Center",
                            subtitle = "Hardware controls & Android permissions",
                            onClick = { onNavigate("phone_control") }
                        )
                        SettingsNavRow(
                            icon = Icons.Filled.Person,
                            title = "Creator Hub & Personal Details",
                            subtitle = "Profile, social links, memories & data export",
                            onClick = { onNavigate("creator_hub") }
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val palette = LocalMinaPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.goldHighlight)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
