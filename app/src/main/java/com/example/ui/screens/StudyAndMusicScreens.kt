package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.AmbientTrack
import com.example.core.PhoneControlHelper
import com.example.data.model.AppSettingsState
import com.example.data.model.QuizQuestion
import com.example.data.model.UiState
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.GlassCard
import com.example.ui.components.MinaBackdrop
import com.example.ui.components.MinaTopBar
import com.example.ui.theme.LocalMinaPalette
import com.example.ui.theme.StatusReadyGreen

@Composable
fun StudyScreen(
    settings: AppSettingsState,
    studyAnswerState: UiState<String>?,
    quizState: UiState<List<QuizQuestion>>?,
    onBack: () -> Unit,
    onAskStudy: (String, String) -> Unit,
    onGenerateQuiz: (String) -> Unit,
    onSpeakText: (String) -> Unit
) {
    val palette = LocalMinaPalette.current
    var mode by remember { mutableStateOf("Explain Topic") }
    var topicInput by remember { mutableStateOf("") }
    val selectedAnswers = remember { mutableStateMapOf<Int, Int>() }

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
                title = "MINA Study Tutor",
                subtitle = "Concepts, Notes, Homework & AI Quizzes",
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Explain Topic", "Create Notes", "Homework Help", "Interactive Quiz").forEach { m ->
                                FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) })
                            }
                        }

                        OutlinedTextField(
                            value = topicInput,
                            onValueChange = { topicInput = it },
                            label = { Text("Enter subject, question, or homework problem") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AmberGradientButton(
                                text = if (mode == "Interactive Quiz") "Generate Quiz" else "Ask MINA Tutor",
                                icon = if (mode == "Interactive Quiz") Icons.Filled.Quiz else Icons.Filled.MenuBook,
                                enabled = topicInput.isNotBlank(),
                                onClick = {
                                    if (mode == "Interactive Quiz") {
                                        selectedAnswers.clear()
                                        onGenerateQuiz(topicInput)
                                    } else {
                                        onAskStudy(mode, topicInput)
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("study_submit_button")
                            )
                            if (mode != "Interactive Quiz") {
                                OutlinedButton(
                                    enabled = topicInput.isNotBlank(),
                                    onClick = {
                                        selectedAnswers.clear()
                                        onGenerateQuiz(topicInput)
                                    },
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    Text("Quiz Me")
                                }
                            }
                        }
                    }
                }

                // Study text output
                when (studyAnswerState) {
                    UiState.Loading -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = palette.goldHighlight, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Preparing study guide...")
                            }
                        }
                    }
                    is UiState.Success -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Study Guide", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                                    IconButton(onClick = { onSpeakText(studyAnswerState.data) }) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read notes aloud", tint = palette.goldHighlight)
                                    }
                                }
                                Text(studyAnswerState.data, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    is UiState.Error -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(studyAnswerState.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    null -> {}
                }

                // Quiz section
                when (quizState) {
                    UiState.Loading -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = palette.goldHighlight, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Crafting 4 quiz questions...")
                            }
                        }
                    }
                    is UiState.Success -> {
                        val score = quizState.data.indices.count { idx ->
                            selectedAnswers[idx] == quizState.data[idx].correctIndex
                        }
                        Text(
                            text = "Practice Quiz • Score: $score / ${quizState.data.size}",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.goldHighlight
                        )
                        quizState.data.forEachIndexed { qIdx, q ->
                            val chosen = selectedAnswers[qIdx]
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Q${qIdx + 1}. ${q.question}", style = MaterialTheme.typography.titleMedium)
                                    q.options.forEachIndexed { optIdx, optText ->
                                        val isPicked = chosen == optIdx
                                        val isCorrect = optIdx == q.correctIndex
                                        val borderColor = when {
                                            chosen == null -> MaterialTheme.colorScheme.outline
                                            isCorrect -> StatusReadyGreen
                                            isPicked -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                                                .clickable(enabled = chosen == null) {
                                                    selectedAnswers[qIdx] = optIdx
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Text(optText, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    if (chosen != null && q.explanation.isNotBlank()) {
                                        Text(
                                            text = q.explanation,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.goldHighlight
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is UiState.Error -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(quizState.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    null -> {}
                }
            }
        }
    }
}

@Composable
fun MusicScreen(
    settings: AppSettingsState,
    tracks: List<AmbientTrack>,
    playingTrackId: String?,
    musicAiState: UiState<String>?,
    onBack: () -> Unit,
    onToggleTrack: (AmbientTrack) -> Unit,
    onRecommendMusic: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMinaPalette.current
    var query by remember { mutableStateOf("") }

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
                title = "MINA Music & Vibes",
                subtitle = "Ambient Synth Player, Search & AI Mood Mixes",
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
                // In-App Ambient Soundscape Player
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Built-in Focus & Relaxation Audio", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        tracks.forEach { track ->
                            val isPlaying = playingTrackId == track.id
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp,
                                borderWidth = if (isPlaying) 2.dp else 1.dp,
                                contentPadding = PaddingValues(12.dp),
                                onClick = { onToggleTrack(track) }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(if (isPlaying) palette.gradientEnd else palette.gradientStart.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = if (isPlaying) Color(0xFF1A0E07) else palette.goldHighlight
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title, style = MaterialTheme.typography.titleMedium)
                                        Text(track.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isPlaying) {
                                        Text("PLAYING", style = MaterialTheme.typography.labelSmall, color = palette.goldHighlight)
                                    }
                                }
                            }
                        }
                    }
                }

                // AI Music Discovery & Streaming Search
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("AI Music Recommendations & Search", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Mood, artist, or song (e.g. Arijit Singh rain lofi)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Romantic Hindi Acoustic", "Late Night Coding Lo-Fi", "Gym Workout Punjabi", "Peaceful Sufi Morning").forEach { mood ->
                                FilterChip(
                                    selected = query == mood,
                                    onClick = {
                                        query = mood
                                        onRecommendMusic(mood)
                                    },
                                    label = { Text(mood) }
                                )
                            }
                        }

                        AmberGradientButton(
                            text = "Get MINA Recommendations",
                            icon = Icons.Filled.AutoAwesome,
                            enabled = query.isNotBlank(),
                            onClick = { onRecommendMusic(query) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val q = android.net.Uri.encode(query.ifBlank { "Hindi chill songs" })
                                    PhoneControlHelper.openExternalUrlOrApp(context, "https://www.youtube.com/results?search_query=$q", "com.google.android.youtube")
                                },
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("YouTube")
                            }
                            OutlinedButton(
                                onClick = {
                                    val q = android.net.Uri.encode(query.ifBlank { "Bollywood Lo-Fi" })
                                    PhoneControlHelper.openExternalUrlOrApp(context, "https://open.spotify.com/search/$q", "com.spotify.music")
                                },
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Spotify")
                            }
                        }
                    }
                }

                when (musicAiState) {
                    UiState.Loading -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = palette.goldHighlight, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Curating tracks for your vibe...")
                            }
                        }
                    }
                    is UiState.Success -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("MINA's Playlist Picks", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                                Text(musicAiState.data, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    is UiState.Error -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(musicAiState.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    null -> {}
                }
            }
        }
    }
}
