package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.CryptoVault
import com.example.core.PhoneControlHelper
import com.example.data.model.AppSettingsState
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
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
fun CreatorHubScreen(
    profile: UserProfile,
    settings: AppSettingsState,
    memoriesState: UiState<List<MemoryItem>>,
    onBack: () -> Unit,
    onOpenPersonalDetails: () -> Unit,
    onSaveAvatarUri: (String) -> Unit,
    onToggleRememberConversations: (Boolean) -> Unit,
    onSaveMemory: (String?, String, String, MemoryCategory, Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onExportData: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMinaPalette.current

    var editingMemory by remember { mutableStateOf<MemoryItem?>(null) }
    var showMemoryDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSaveAvatarUri(uri.toString())
        }
    }

    val memories = (memoriesState as? UiState.Success)?.data.orEmpty()

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
                title = "Creator Hub",
                subtitle = "Profile, Social Presence & Encrypted Memory",
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
                // Profile Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(palette.bgTop)
                                .border(2.dp, palette.goldHighlight, CircleShape)
                                .clickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("creator_change_photo"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (profile.avatarUri.isNotBlank()) {
                                AsyncImage(
                                    model = profile.avatarUri,
                                    contentDescription = "Profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = palette.goldHighlight, modifier = Modifier.size(34.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(palette.gradientEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = "Change photo", tint = palette.bgTop, modifier = Modifier.size(14.dp))
                            }
                        }

                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.displayName.ifBlank { "Friend" }, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = profile.bio.ifBlank { "Tap Edit Personal Details to customize your bio & private vault." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = palette.goldHighlight, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "AES-256-GCM Local Vault Active • ${memories.size} Memories",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.goldHighlight
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    AmberGradientButton(
                        text = "Edit Personal Details & Social Links",
                        icon = Icons.Filled.Edit,
                        onClick = onOpenPersonalDetails,
                        modifier = Modifier.fillMaxWidth().testTag("open_personal_details_button"),
                        height = 46.dp
                    )
                }

                // Social Presence Quick Launcher
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Social Presence", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        SocialLinkRow("YouTube Channel", profile.youtubeUrl.ifBlank { "Not configured" }) {
                            if (profile.youtubeUrl.isNotBlank()) PhoneControlHelper.openExternalUrlOrApp(context, profile.youtubeUrl, "com.google.android.youtube")
                            else onOpenPersonalDetails()
                        }
                        SocialLinkRow("Instagram Profile", profile.instagramUrl.ifBlank { "Not configured" }) {
                            if (profile.instagramUrl.isNotBlank()) PhoneControlHelper.openExternalUrlOrApp(context, profile.instagramUrl, "com.instagram.android")
                            else onOpenPersonalDetails()
                        }
                        SocialLinkRow("Music / Artist Link", profile.musicUrl.ifBlank { "Not configured" }) {
                            if (profile.musicUrl.isNotBlank()) PhoneControlHelper.openExternalUrlOrApp(context, profile.musicUrl)
                            else onOpenPersonalDetails()
                        }
                        SocialLinkRow("Other Social Link", profile.socialUrl.ifBlank { "Not configured" }) {
                            if (profile.socialUrl.isNotBlank()) PhoneControlHelper.openExternalUrlOrApp(context, profile.socialUrl)
                            else onOpenPersonalDetails()
                        }
                    }
                }

                // Memory & Privacy Controls
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Memory Bank", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                                Text("Save preferences & let MINA remember context", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                onClick = {
                                    editingMemory = null
                                    showMemoryDialog = true
                                },
                                modifier = Modifier.testTag("add_memory_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add memory", tint = palette.goldHighlight)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Remember Conversations", style = MaterialTheme.typography.bodyLarge)
                                Text("Sync chat history with permission", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.rememberConversations,
                                onCheckedChange = onToggleRememberConversations
                            )
                        }

                        if (memories.isEmpty()) {
                            Text(
                                text = "No saved memories yet. Tap + to add a preference, goal, or private encrypted note.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            memories.forEach { mem ->
                                val decoded = if (mem.encrypted) CryptoVault.decrypt(mem.content) else mem.content
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 16.dp,
                                    contentPadding = PaddingValues(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (mem.encrypted) Icons.Filled.Lock else Icons.Filled.Psychology,
                                            contentDescription = null,
                                            tint = palette.goldHighlight,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(mem.title, style = MaterialTheme.typography.titleMedium)
                                            Text(decoded, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "${MemoryCategory.fromRaw(mem.category).label}${if (mem.encrypted) " • Encrypted" else ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = palette.goldHighlight
                                            )
                                        }
                                        IconButton(onClick = {
                                            editingMemory = mem
                                            showMemoryDialog = true
                                        }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit memory", tint = palette.goldHighlight)
                                        }
                                        IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete memory", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onExportData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("export_personal_data_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Export Personal Data (JSON)")
                        }
                    }
                }
            }
        }
    }

    if (showMemoryDialog) {
        val existing = editingMemory
        var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
        var content by remember(existing) {
            mutableStateOf(
                if (existing == null) "" else if (existing.encrypted) CryptoVault.decrypt(existing.content) else existing.content
            )
        }
        var category by remember(existing) { mutableStateOf(MemoryCategory.fromRaw(existing?.category)) }
        var encrypt by remember(existing) { mutableStateOf(existing?.encrypted ?: true) }

        AlertDialog(
            onDismissRequest = { showMemoryDialog = false },
            title = { Text(if (existing == null) "Save Memory" else "Edit Memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Detail for MINA to remember") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MemoryCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Encrypt with Keystore AES-256")
                        Switch(checked = encrypt, onCheckedChange = { encrypt = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = content.isNotBlank(),
                    onClick = {
                        onSaveMemory(existing?.id, title, content, category, encrypt)
                        showMemoryDialog = false
                    }
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showMemoryDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SocialLinkRow(label: String, value: String, onClick: () -> Unit) {
    val palette = LocalMinaPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(Icons.Filled.OpenInNew, contentDescription = label, tint = palette.goldHighlight, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PersonalDetailsScreen(
    profile: UserProfile,
    settings: AppSettingsState,
    onBack: () -> Unit,
    onSaveProfile: (
        displayName: String,
        bio: String,
        privateNotePlain: String,
        avatarUri: String,
        youtubeUrl: String,
        instagramUrl: String,
        socialUrl: String,
        musicUrl: String,
        personality: MinaPersonality,
        emotionLevel: Int,
        language: MinaLanguage
    ) -> Unit
) {
    val palette = LocalMinaPalette.current
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var privateNote by remember(profile) { mutableStateOf(CryptoVault.decrypt(profile.privateNote)) }
    var avatarUri by remember(profile) { mutableStateOf(profile.avatarUri) }
    var youtubeUrl by remember(profile) { mutableStateOf(profile.youtubeUrl) }
    var instagramUrl by remember(profile) { mutableStateOf(profile.instagramUrl) }
    var socialUrl by remember(profile) { mutableStateOf(profile.socialUrl) }
    var musicUrl by remember(profile) { mutableStateOf(profile.musicUrl) }
    var personality by remember(profile) { mutableStateOf(MinaPersonality.fromRaw(profile.personality)) }
    var emotionLevel by remember(profile) { mutableIntStateOf(profile.emotionLevel.toInt().coerceIn(1, 5)) }
    var language by remember(profile) { mutableStateOf(MinaLanguage.fromRaw(profile.language)) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) avatarUri = uri.toString()
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
                title = "Personal Details",
                subtitle = "Encrypted Profile & Companion Tuning",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, palette.goldHighlight, CircleShape)
                                    .clickable {
                                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUri.isNotBlank()) {
                                    AsyncImage(
                                        model = avatarUri,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = palette.goldHighlight)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton(onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                Text("Choose Photo")
                            }
                        }

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Your Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            label = { Text("Bio / About You") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        OutlinedTextField(
                            value = privateNote,
                            onValueChange = { privateNote = it },
                            label = { Text("Private Info (AES-256 Encrypted)") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Social & Creator Links", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        OutlinedTextField(
                            value = youtubeUrl,
                            onValueChange = { youtubeUrl = it },
                            label = { Text("YouTube Channel URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        OutlinedTextField(
                            value = instagramUrl,
                            onValueChange = { instagramUrl = it },
                            label = { Text("Instagram Profile URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        OutlinedTextField(
                            value = musicUrl,
                            onValueChange = { musicUrl = it },
                            label = { Text("Music / Playlist URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        OutlinedTextField(
                            value = socialUrl,
                            onValueChange = { socialUrl = it },
                            label = { Text("Other Social / Portfolio URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("MINA Personality & Emotion", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MinaPersonality.entries.forEach { p ->
                                FilterChip(
                                    selected = personality == p,
                                    onClick = { personality = p },
                                    label = { Text(p.displayName) }
                                )
                            }
                        }
                        Text("Emotion Level: $emotionLevel/5")
                        Slider(
                            value = emotionLevel.toFloat(),
                            onValueChange = { emotionLevel = it.toInt().coerceIn(1, 5) },
                            valueRange = 1f..5f,
                            steps = 3
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MinaLanguage.entries.forEach { l ->
                                FilterChip(
                                    selected = language == l,
                                    onClick = { language = l },
                                    label = { Text(l.displayName) }
                                )
                            }
                        }
                    }
                }

                AmberGradientButton(
                    text = "Save Personal Details",
                    onClick = {
                        onSaveProfile(
                            displayName, bio, privateNote, avatarUri,
                            youtubeUrl, instagramUrl, socialUrl, musicUrl,
                            personality, emotionLevel, language
                        )
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_personal_details_button")
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
