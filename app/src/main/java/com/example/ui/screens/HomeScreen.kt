package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.PhoneControlHelper
import com.example.data.model.AppSettingsState
import com.example.data.model.AssistantStatus
import com.example.data.model.MinaPersonality
import com.example.data.model.UserProfile
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.AssistantStatusPill
import com.example.ui.components.GlassCard
import com.example.ui.components.MinaAnimatedOrb
import com.example.ui.components.MinaBackdrop
import com.example.ui.theme.LocalMinaPalette
import java.util.Calendar

private data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String
)

private data class ShortcutChipItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    profile: UserProfile,
    settings: AppSettingsState,
    assistantStatus: AssistantStatus,
    voiceAmplitude: Float,
    onTapToTalk: () -> Unit,
    onStopVoice: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMinaPalette.current

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Shubh Raatri"
        }
    }

    val quickActions = remember {
        listOf(
            QuickActionItem("Documents", "Resume, Biodata & PDF", Icons.Filled.Description, "documents"),
            QuickActionItem("Coding", "HTML, Kotlin & Debug", Icons.Filled.Code, "coding"),
            QuickActionItem("Live Vision", "Camera & OCR AI", Icons.Filled.CameraAlt, "vision"),
            QuickActionItem("Phone Control", "Torch, Volume & Apps", Icons.Filled.PhoneAndroid, "phone_control"),
            QuickActionItem("Themes", "Dark, Amber & Code", Icons.Filled.Palette, "themes"),
            QuickActionItem("Creator Hub", "Profile, Links & Memory", Icons.Filled.AutoAwesome, "creator_hub")
        )
    }

    val shortcuts = remember(profile) {
        listOf(
            ShortcutChipItem("Music", Icons.Filled.MusicNote) { onNavigate("music") },
            ShortcutChipItem("Study", Icons.Filled.MenuBook) { onNavigate("study") },
            ShortcutChipItem("Journal", Icons.Filled.Book) { onNavigate("journal") },
            ShortcutChipItem("YouTube", Icons.Filled.PlayCircle) {
                val url = profile.youtubeUrl.ifBlank { "https://www.youtube.com" }
                PhoneControlHelper.openExternalUrlOrApp(context, url, "com.google.android.youtube")
            },
            ShortcutChipItem("Instagram", Icons.Filled.Public) {
                val url = profile.instagramUrl.ifBlank { "https://www.instagram.com" }
                PhoneControlHelper.openExternalUrlOrApp(context, url, "com.instagram.android")
            },
            ShortcutChipItem("Song", Icons.Filled.MusicNote) {
                val url = profile.musicUrl.ifBlank { "https://open.spotify.com" }
                if (profile.musicUrl.isNotBlank()) {
                    PhoneControlHelper.openExternalUrlOrApp(context, url)
                } else {
                    onNavigate("music")
                }
            },
            ShortcutChipItem("Live Location", Icons.Filled.LocationOn) { onNavigate("location") },
            ShortcutChipItem("Where Am I?", Icons.Filled.MyLocation) { onNavigate("location") }
        )
    }

    MinaBackdrop(
        particlesEnabled = settings.particlesEnabled,
        glowIntensity = settings.glowIntensity,
        showHeroArt = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp)) {
                // Header Row: Logo + MINA AI + Profile Avatar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.mina_ai_icon_1795466489832),
                            contentDescription = stringResource(R.string.cd_mina_logo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, palette.goldHighlight, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MINA AI",
                                style = MaterialTheme.typography.titleLarge,
                                color = palette.goldHighlight,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.tagline),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(palette.glassFill)
                            .border(1.5.dp, palette.goldHighlight.copy(alpha = 0.7f), CircleShape)
                            .clickable { onNavigate("personal_details") }
                            .testTag("home_profile_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profile.avatarUri.isNotBlank()) {
                            AsyncImage(
                                model = profile.avatarUri,
                                contentDescription = "User profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "User profile",
                                tint = palette.goldHighlight
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Greeting & Companion Hero Card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 28.dp,
                    contentPadding = PaddingValues(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$greeting,",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = profile.displayName.ifBlank { "Friend" },
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            AssistantStatusPill(status = assistantStatus)
                        }

                        Spacer(Modifier.height(18.dp))

                        MinaAnimatedOrb(
                            status = assistantStatus,
                            amplitude = voiceAmplitude,
                            size = 160.dp,
                            showAvatar = true,
                            onClick = onTapToTalk
                        )

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = "${MinaPersonality.fromRaw(profile.personality).displayName} • Emotion ${profile.emotionLevel}/5",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.goldHighlight
                        )
                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AmberGradientButton(
                                text = if (assistantStatus == AssistantStatus.LISTENING) "Listening..." else stringResource(R.string.tap_to_talk),
                                icon = Icons.Filled.Mic,
                                onClick = onTapToTalk,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tap_to_talk_button")
                            )
                            if (assistantStatus == AssistantStatus.SPEAKING || assistantStatus == AssistantStatus.LISTENING) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                                        .border(1.dp, MaterialTheme.colorScheme.error, CircleShape)
                                        .clickable { onStopVoice() }
                                        .testTag("home_stop_voice_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Stop,
                                        contentDescription = "Stop voice",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))

                // 2-column grid of Quick Actions
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    quickActions.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { item ->
                                GlassCard(
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_action_${item.route}"),
                                    cornerRadius = 20.dp,
                                    contentPadding = PaddingValues(14.dp),
                                    onClick = { onNavigate(item.route) }
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            palette.gradientStart.copy(alpha = 0.26f),
                                                            palette.gradientEnd.copy(alpha = 0.14f)
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.title,
                                                tint = palette.goldHighlight,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                Text(
                    text = "More Shortcuts",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    shortcuts.forEach { chip ->
                        GlassCard(
                            cornerRadius = 50.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            onClick = chip.onClick,
                            modifier = Modifier.testTag("shortcut_${chip.label.lowercase().replace(" ", "_")}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = chip.icon,
                                    contentDescription = chip.label,
                                    tint = palette.goldHighlight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = chip.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
