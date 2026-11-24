package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.PdfAndExportHelper
import com.example.data.model.AppSettingsState
import com.example.data.model.ChatMessage
import com.example.data.model.MinaLanguage
import com.example.data.model.MinaPersonality
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.GlassCard
import com.example.ui.components.MinaBackdrop
import com.example.ui.components.MinaTopBar
import com.example.ui.theme.LocalMinaPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    profile: UserProfile,
    settings: AppSettingsState,
    chatsState: UiState<List<ChatMessage>>,
    streamingReply: String?,
    isBusy: Boolean,
    chatError: String?,
    canRetry: Boolean,
    onSendMessage: (String, Boolean) -> Unit,
    onRetryLastMessage: () -> Unit,
    onTestReply: () -> Unit,
    onVoiceInput: ((String) -> Unit) -> Unit,
    onSpeakText: (String) -> Unit,
    onClearChat: () -> Unit,
    onUpdateTone: (MinaPersonality, Int, MinaLanguage) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMinaPalette.current
    var inputText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showPersonalitySheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onVoiceInput { spoken -> onSendMessage(spoken, true) }
        } else {
            onToast("Microphone permission required for voice chat.")
        }
    }

    val messages = (chatsState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size, streamingReply) {
        val total = messages.size + if (streamingReply != null) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    MinaBackdrop(
        particlesEnabled = settings.particlesEnabled,
        glowIntensity = settings.glowIntensity
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(bottom = 86.dp)
        ) {
            MinaTopBar(
                title = "Chat with MINA",
                subtitle = "${MinaPersonality.fromRaw(profile.personality).displayName} • ${MinaLanguage.fromRaw(profile.language).displayName}",
                trailing = {
                    Row {
                        IconButton(
                            onClick = { showPersonalitySheet = true },
                            modifier = Modifier.testTag("chat_personality_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "Customize personality",
                                tint = palette.goldHighlight
                            )
                        }
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("chat_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )

            // Quick conversation starter chips + real Gemini Test Reply
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassCard(
                    modifier = Modifier.testTag("chat_test_reply_button"),
                    cornerRadius = 50.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    onClick = { onTestReply() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = "Test Reply",
                            tint = palette.goldHighlight,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isBusy) "Testing..." else "Test Reply",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.goldHighlight
                        )
                    }
                }
                listOf(
                    "Kaise ho Mina?",
                    "Aaj ka plan banayein?",
                    "Motivate me today!",
                    "Explain Quantum Computing simply",
                    "Write a sweet Shayari in Hinglish"
                ).forEach { prompt ->
                    GlassCard(
                        cornerRadius = 50.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                        onClick = { onSendMessage(prompt, false) }
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.goldHighlight
                        )
                    }
                }
            }

            if (chatError != null) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = chatError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        if (canRetry) {
                            TextButton(
                                onClick = onRetryLastMessage,
                                enabled = !isBusy,
                                colors = ButtonDefaults.textButtonColors(contentColor = palette.goldHighlight),
                                modifier = Modifier.testTag("chat_retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Retry message",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Retry", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty() && streamingReply == null) {
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            contentPadding = PaddingValues(20.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = palette.goldHighlight,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Namaste ${profile.displayName}! Main MINA hoon.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Aap mujhse Hinglish, Hindi ya English mein kuch bhi pooch sakte ho — feelings share karo, code likhwao, ya bas baatein karo!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id.ifBlank { "${it.role}_${it.text.hashCode()}_${it.createdAt?.seconds ?: 0}" } }) { msg ->
                    ChatBubble(
                        message = msg,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MINA Chat", msg.text))
                            onToast("Copied to clipboard")
                        },
                        onShare = { PdfAndExportHelper.shareText(context, msg.text, "MINA AI Conversation") },
                        onSpeak = { onSpeakText(msg.text) }
                    )
                }

                if (streamingReply != null) {
                    item(key = "streaming_bubble") {
                        ChatBubble(
                            message = ChatMessage(
                                role = "MINA",
                                text = streamingReply.ifBlank { "MINA is typing..." },
                                personality = profile.personality
                            ),
                            isStreaming = true,
                            onCopy = {},
                            onShare = {},
                            onSpeak = {}
                        )
                    }
                }
            }

            // Input composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask MINA anything in Hinglish / Hindi / English...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    shape = RoundedCornerShape(26.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.goldHighlight,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = palette.glassFill,
                        unfocusedContainerColor = palette.glassFill
                    )
                )
                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(palette.glassFill)
                        .border(1.dp, palette.goldHighlight.copy(alpha = 0.7f), CircleShape)
                        .clickable {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                onVoiceInput { spoken -> onSendMessage(spoken, true) }
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        .testTag("chat_mic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.cd_mic),
                        tint = palette.goldHighlight
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(palette.gradientStart, palette.gradientEnd))
                        )
                        .clickable(enabled = inputText.isNotBlank() && !isBusy) {
                            val toSend = inputText
                            inputText = ""
                            onSendMessage(toSend, false)
                        }
                        .testTag("chat_send_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.2.dp,
                            color = Color(0xFF1A0E07)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.cd_send),
                            tint = Color(0xFF1A0E07)
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Chat History?") },
            text = { Text("This deletes saved conversation messages from your account.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearChat()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPersonalitySheet) {
        var selectedP by remember { mutableStateOf(MinaPersonality.fromRaw(profile.personality)) }
        var selectedL by remember { mutableStateOf(MinaLanguage.fromRaw(profile.language)) }
        var emotion by remember { mutableIntStateOf(profile.emotionLevel.toInt().coerceIn(1, 5)) }

        ModalBottomSheet(onDismissRequest = { showPersonalitySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("Customize MINA Personality", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinaPersonality.entries.forEach { p ->
                        FilterChip(
                            selected = selectedP == p,
                            onClick = { selectedP = p },
                            label = { Text(p.displayName) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Emotion Level: $emotion/5", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = emotion.toFloat(),
                    onValueChange = { emotion = it.toInt().coerceIn(1, 5) },
                    valueRange = 1f..5f,
                    steps = 3
                )
                Spacer(Modifier.height(8.dp))
                Text("Language", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MinaLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedL == lang,
                            onClick = { selectedL = lang },
                            label = { Text(lang.displayName) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        onUpdateTone(selectedP, emotion, selectedL)
                        showPersonalitySheet = false
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Tone", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSpeak: () -> Unit
) {
    val palette = LocalMinaPalette.current
    val isUser = message.role == "USER"
    val timeText = remember(message.createdAt) {
        val date = message.createdAt?.toDate() ?: Date()
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp
                    )
                )
                .background(
                    if (isUser) {
                        Brush.linearGradient(listOf(palette.gradientStart, palette.gradientEnd))
                    } else {
                        Brush.linearGradient(listOf(palette.glassFill, palette.glassFill))
                    }
                )
                .border(
                    1.dp,
                    if (isUser) Color.Transparent else palette.goldHighlight.copy(alpha = 0.45f),
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) Color(0xFF1A0E07) else MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isStreaming) "Streaming..." else timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) Color(0xFF3E2210) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isStreaming) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isUser) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Speak response",
                                    tint = palette.goldHighlight,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable(onClick = onSpeak)
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy message",
                                tint = if (isUser) Color(0xFF3E2210) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(17.dp)
                                    .clickable(onClick = onCopy)
                            )
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share message",
                                tint = if (isUser) Color(0xFF3E2210) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(17.dp)
                                    .clickable(onClick = onShare)
                            )
                        }
                    }
                }
            }
        }
    }
}
