package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.CryptoVault
import com.example.core.JournalRichText
import com.example.core.PdfAndExportHelper
import com.example.core.PhoneControlHelper
import com.example.core.RichJournalText
import com.example.data.model.AppSettingsState
import com.example.data.model.FavoritePlace
import com.example.data.model.JournalEntry
import com.example.data.model.JournalMood
import com.example.data.model.UiState
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.GlassCard
import com.example.ui.components.MinaBackdrop
import com.example.ui.components.MinaTopBar
import com.example.ui.theme.LocalMinaPalette
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DecryptedJournalItem(
    val entry: JournalEntry,
    val plainContent: String,
    val mood: JournalMood
)

@Composable
fun JournalScreen(
    settings: AppSettingsState,
    journalsState: UiState<List<JournalEntry>>,
    onBack: () -> Unit,
    onSaveJournal: (String, String, JournalMood, Boolean) -> Unit,
    onDeleteJournal: (String) -> Unit
) {
    val palette = LocalMinaPalette.current
    val focusManager = LocalFocusManager.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedMoodFilter by rememberSaveable { mutableStateOf<JournalMood?>(null) }
    var showNewDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteEntry by remember { mutableStateOf<JournalEntry?>(null) }

    val rawEntries = (journalsState as? UiState.Success)?.data.orEmpty()
    val decryptedEntries = remember(rawEntries) {
        rawEntries.map { entry ->
            val plain = if (entry.encrypted) CryptoVault.decrypt(entry.content) else entry.content
            DecryptedJournalItem(
                entry = entry,
                plainContent = plain,
                mood = JournalMood.fromRaw(entry.mood)
            )
        }
    }

    val filteredEntries = remember(decryptedEntries, searchQuery, selectedMoodFilter) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        decryptedEntries.filter { item ->
            val matchesMood = selectedMoodFilter == null || item.mood == selectedMoodFilter
            val effectiveTitle = item.entry.title.ifBlank { "Daily Reflection" }
            val searchableContent = JournalRichText.stripFormattingForSearch(item.plainContent)
            val matchesQuery = q.isEmpty() ||
                effectiveTitle.lowercase(Locale.getDefault()).contains(q) ||
                item.plainContent.lowercase(Locale.getDefault()).contains(q) ||
                searchableContent.lowercase(Locale.getDefault()).contains(q)
            matchesMood && matchesQuery
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()) }

    MinaBackdrop(
        particlesEnabled = settings.particlesEnabled,
        glowIntensity = settings.glowIntensity
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showNewDialog = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.journal_cd_add)
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.journal_new_entry),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(24.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp
                    ),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("journal_fab")
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MinaTopBar(
                    title = stringResource(R.string.journal_title),
                    subtitle = stringResource(R.string.journal_subtitle),
                    onBack = onBack
                )

                // Pinned top search bar & mood filter strip
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = palette.goldHighlight
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        focusManager.clearFocus()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("journal_search_clear")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        placeholder = { Text(stringResource(R.string.journal_search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = palette.glassFill,
                            unfocusedContainerColor = palette.glassFill,
                            focusedBorderColor = palette.goldHighlight,
                            unfocusedBorderColor = palette.glassBorderEnd
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("journal_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedMoodFilter == null,
                            onClick = { selectedMoodFilter = null },
                            label = { Text("All (${decryptedEntries.size})") },
                            modifier = Modifier.testTag("journal_filter_all")
                        )
                        JournalMood.entries.forEach { mood ->
                            FilterChip(
                                selected = selectedMoodFilter == mood,
                                onClick = {
                                    selectedMoodFilter = if (selectedMoodFilter == mood) null else mood
                                },
                                label = { Text("${mood.emoji} ${mood.label}") },
                                modifier = Modifier.testTag("journal_filter_${mood.name.lowercase(Locale.ROOT)}")
                            )
                        }
                    }

                    if (searchQuery.isNotBlank() || selectedMoodFilter != null) {
                        Text(
                            text = "Showing ${filteredEntries.size} of ${decryptedEntries.size} entries",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.goldHighlight,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .testTag("journal_search_count")
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 680.dp)
                            .testTag("journal_entries_list"),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 104.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        when (journalsState) {
                            UiState.Loading -> {
                                item(key = "journal_loading") {
                                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                color = palette.goldHighlight,
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = "Loading your journal entries…",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            is UiState.Error -> {
                                item(key = "journal_error") {
                                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = journalsState.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            is UiState.Success -> {
                                if (decryptedEntries.isEmpty()) {
                                    item(key = "journal_empty") {
                                        GlassCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("journal_empty_card")
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(CircleShape)
                                                        .background(palette.gradientEnd.copy(alpha = 0.16f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.AutoStories,
                                                        contentDescription = null,
                                                        tint = palette.goldHighlight,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                                Text(
                                                    text = stringResource(R.string.journal_empty_title),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = stringResource(R.string.journal_empty_body),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                OutlinedButton(
                                                    onClick = { showNewDialog = true },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Add,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Write First Entry")
                                                }
                                            }
                                        }
                                    }
                                } else if (filteredEntries.isEmpty()) {
                                    item(key = "journal_no_matches") {
                                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text(
                                                    text = stringResource(R.string.journal_no_matches),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                OutlinedButton(
                                                    onClick = {
                                                        searchQuery = ""
                                                        selectedMoodFilter = null
                                                    },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Text("Clear Filters")
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    items(
                                        items = filteredEntries,
                                        key = { item ->
                                            item.entry.id.ifBlank {
                                                "${item.entry.title}_${item.entry.createdAt?.seconds ?: 0L}_${item.plainContent.hashCode()}"
                                            }
                                        }
                                    ) { item ->
                                        JournalEntryCard(
                                            item = item,
                                            formattedDate = dateFormatter.format(
                                                item.entry.createdAt?.toDate() ?: Date()
                                            ),
                                            onRequestDelete = { pendingDeleteEntry = item.entry }
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

    if (showNewDialog) {
        NewJournalEntryDialog(
            onDismiss = { showNewDialog = false },
            onSave = { title, content, mood, encrypt ->
                onSaveJournal(title, content, mood, encrypt)
                showNewDialog = false
            }
        )
    }

    val entryToDelete = pendingDeleteEntry
    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            title = { Text(stringResource(R.string.journal_delete_title)) },
            text = { Text(stringResource(R.string.journal_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteJournal(entryToDelete.id)
                        pendingDeleteEntry = null
                    },
                    modifier = Modifier.testTag("journal_confirm_delete")
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntry = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun JournalEntryCard(
    item: DecryptedJournalItem,
    formattedDate: String,
    onRequestDelete: () -> Unit
) {
    val palette = LocalMinaPalette.current
    val entry = item.entry
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val isLongText = item.plainContent.length > 220

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("journal_entry_card"),
        onClick = if (isLongText) {
            { expanded = !expanded }
        } else {
            null
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.gradientEnd.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${item.mood.emoji} ${item.mood.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.goldHighlight,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (entry.encrypted) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Encrypted entry",
                            tint = palette.goldHighlight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onRequestDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("journal_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete entry",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = entry.title.ifBlank { "Daily Reflection" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = formattedDate,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            RichJournalText(
                text = item.plainContent,
                bulletColor = palette.goldHighlight,
                style = MaterialTheme.typography.bodyLarge,
                maxLinesCollapsed = if (isLongText && !expanded) 4 else Int.MAX_VALUE
            )

            if (isLongText) {
                Text(
                    text = if (expanded) "Show less" else "Read full entry",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.goldHighlight,
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedVisibility(visible = entry.aiReflection.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            palette.gradientEnd.copy(alpha = 0.12f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = palette.goldHighlight,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp)
                    )
                    Text(
                        text = "MINA: ${entry.aiReflection}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.goldHighlight
                    )
                }
            }
        }
    }
}

@Composable
private fun NewJournalEntryDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, mood: JournalMood, encrypt: Boolean) -> Unit
) {
    val palette = LocalMinaPalette.current
    var title by rememberSaveable { mutableStateOf("") }
    var contentValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var isPreviewMode by rememberSaveable { mutableStateOf(false) }
    var mood by rememberSaveable { mutableStateOf(JournalMood.CALM) }
    var encrypt by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("journal_entry_dialog"),
        title = { Text(stringResource(R.string.journal_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 140) title = it },
                    label = { Text(stringResource(R.string.journal_field_title)) },
                    placeholder = { Text("e.g. Evening Gratitude") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("journal_title_input")
                )

                // Rich text formatting toolbar (Bold, Italic, Bullet List, Preview)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.glassFill,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("journal_format_toolbar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    isPreviewMode = false
                                    contentValue = JournalRichText.applyBold(contentValue)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("journal_format_bold")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FormatBold,
                                    contentDescription = stringResource(R.string.journal_format_bold),
                                    tint = palette.goldHighlight
                                )
                            }
                            IconButton(
                                onClick = {
                                    isPreviewMode = false
                                    contentValue = JournalRichText.applyItalic(contentValue)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("journal_format_italic")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FormatItalic,
                                    contentDescription = stringResource(R.string.journal_format_italic),
                                    tint = palette.goldHighlight
                                )
                            }
                            IconButton(
                                onClick = {
                                    isPreviewMode = false
                                    contentValue = JournalRichText.toggleBulletList(contentValue)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("journal_format_bullet")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = stringResource(R.string.journal_format_bullet),
                                    tint = palette.goldHighlight
                                )
                            }
                        }

                        FilterChip(
                            selected = isPreviewMode,
                            onClick = { isPreviewMode = !isPreviewMode },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isPreviewMode) Icons.Filled.Edit else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = if (isPreviewMode) {
                                        stringResource(R.string.journal_mode_write)
                                    } else {
                                        stringResource(R.string.journal_mode_preview)
                                    }
                                )
                            },
                            modifier = Modifier.testTag("journal_preview_toggle")
                        )
                    }
                }

                if (isPreviewMode) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("journal_rich_preview")
                    ) {
                        Box(modifier = Modifier.padding(14.dp)) {
                            if (contentValue.text.isBlank()) {
                                Text(
                                    text = "Nothing to preview yet. Switch to Write to compose your entry.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                RichJournalText(
                                    text = contentValue.text,
                                    bulletColor = palette.goldHighlight,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = contentValue,
                        onValueChange = { updated ->
                            if (updated.text.length <= JournalRichText.MAX_JOURNAL_LENGTH) {
                                contentValue = updated
                            }
                        },
                        label = { Text(stringResource(R.string.journal_field_body)) },
                        placeholder = { Text("Share what's on your mind today...") },
                        minLines = 4,
                        maxLines = 8,
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.journal_format_hint),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("${contentValue.text.length}/${JournalRichText.MAX_JOURNAL_LENGTH}")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("journal_content_input")
                    )
                }

                Text(
                    text = "Current Mood",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    JournalMood.entries.forEach { m ->
                        FilterChip(
                            selected = mood == m,
                            onClick = { mood = m },
                            label = { Text("${m.emoji} ${m.label}") },
                            modifier = Modifier.testTag("journal_mood_${m.name.lowercase(Locale.ROOT)}")
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.journal_encrypt_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = encrypt,
                        onCheckedChange = { encrypt = it },
                        modifier = Modifier.testTag("journal_encrypt_switch")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = contentValue.text.isNotBlank(),
                onClick = { onSave(title.trim(), contentValue.text.trim(), mood, encrypt) },
                modifier = Modifier.testTag("journal_save_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("journal_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@SuppressLint("MissingPermission")
@Composable
fun LocationScreen(
    settings: AppSettingsState,
    placesState: UiState<List<FavoritePlace>>,
    onBack: () -> Unit,
    onSavePlace: (String, String, Double, Double) -> Unit,
    onDeletePlace: (String) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = LocalMinaPalette.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isFetching by remember { mutableStateOf(false) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var resolvedAddress by remember { mutableStateOf("Tap 'Where Am I?' to check your current address.") }
    var showShareConfirm by remember { mutableStateOf(false) }
    var showSavePlaceDialog by remember { mutableStateOf(false) }

    fun fetchCurrentLocation() {
        scope.launch {
            isFetching = true
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                    ?: client.lastLocation.await()
                if (loc != null) {
                    latitude = loc.latitude
                    longitude = loc.longitude
                    val addressText = withContext(Dispatchers.IO) {
                        try {
                            @Suppress("DEPRECATION")
                            val list = Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                            val addr = list?.firstOrNull()
                            if (addr != null) {
                                (0..addr.maxAddressLineIndex).joinToString(", ") { addr.getAddressLine(it) }
                            } else {
                                "Lat ${String.format("%.4f", loc.latitude)}, Lng ${String.format("%.4f", loc.longitude)}"
                            }
                        } catch (_: Exception) {
                            "Lat ${String.format("%.4f", loc.latitude)}, Lng ${String.format("%.4f", loc.longitude)}"
                        }
                    }
                    resolvedAddress = addressText
                } else {
                    onToast("Location unavailable right now. Ensure GPS is enabled.")
                }
            } catch (e: Exception) {
                onToast(e.localizedMessage ?: "Failed to obtain location.")
            } finally {
                isFetching = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) fetchCurrentLocation()
        else onToast("Location permission denied. MINA never accesses location without permission.")
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && latitude == null) {
            fetchCurrentLocation()
        }
    }

    val favoritePlaces = (placesState as? UiState.Success)?.data.orEmpty()

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
                title = "Live Location & Where Am I?",
                subtitle = "Permission-First GPS, Address & Favorite Places",
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Radar Map Visual
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val c = Offset(size.width / 2f, size.height / 2f)
                                listOf(28.dp, 52.dp, 76.dp).forEach { r ->
                                    drawCircle(
                                        color = palette.goldHighlight.copy(alpha = 0.25f),
                                        radius = r.toPx(),
                                        center = c,
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                }
                                drawCircle(
                                    color = palette.gradientEnd,
                                    radius = 8.dp.toPx(),
                                    center = c
                                )
                            }
                            if (isFetching) {
                                CircularProgressIndicator(color = palette.goldHighlight)
                            }
                        }

                        Text(
                            text = if (latitude != null && longitude != null) {
                                "Coordinates: ${String.format("%.5f", latitude)}, ${String.format("%.5f", longitude)}"
                            } else {
                                "GPS Coordinates Pending"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.goldHighlight
                        )
                        Text(
                            text = resolvedAddress,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        AmberGradientButton(
                            text = if (hasLocationPermission) "Where Am I? (Refresh GPS)" else "Grant Location & Check",
                            icon = Icons.Filled.MyLocation,
                            onClick = {
                                if (hasLocationPermission) {
                                    fetchCurrentLocation()
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("location_refresh_button")
                        )

                        if (latitude != null && longitude != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { PhoneControlHelper.openMapNavigation(context, latitude!!, longitude!!, "My Location") },
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open Map")
                                }
                                OutlinedButton(
                                    onClick = { showSavePlaceDialog = true },
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save Spot")
                                }
                                OutlinedButton(
                                    onClick = { showShareConfirm = true },
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }

                // Favorite Places
                Text("Favorite Places", style = MaterialTheme.typography.titleLarge)
                if (favoritePlaces.isEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No favorite places saved yet. Fetch your location and tap 'Save Spot' to bookmark it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    favoritePlaces.forEach { place ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(place.name, style = MaterialTheme.typography.titleMedium)
                                    Text(place.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${String.format("%.4f", place.latitude)}, ${String.format("%.4f", place.longitude)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.goldHighlight
                                    )
                                }
                                IconButton(onClick = {
                                    PhoneControlHelper.openMapNavigation(context, place.latitude, place.longitude, place.name)
                                }) {
                                    Icon(Icons.Filled.Navigation, contentDescription = "Navigate", tint = palette.goldHighlight)
                                }
                                IconButton(onClick = { onDeletePlace(place.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete place", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareConfirm && latitude != null && longitude != null) {
        AlertDialog(
            onDismissRequest = { showShareConfirm = false },
            title = { Text("Confirm Location Sharing") },
            text = { Text("Share your current coordinates and address ($resolvedAddress) with another app?") },
            confirmButton = {
                TextButton(onClick = {
                    showShareConfirm = false
                    val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
                    PdfAndExportHelper.shareText(
                        context,
                        "My current location:\n$resolvedAddress\n$mapsLink",
                        "My Live Location"
                    )
                }) { Text("Share Now", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showShareConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showSavePlaceDialog && latitude != null && longitude != null) {
        var placeName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePlaceDialog = false },
            title = { Text("Save Favorite Place") },
            text = {
                OutlinedTextField(
                    value = placeName,
                    onValueChange = { placeName = it },
                    label = { Text("Place Name (e.g. Home, Studio, Cafe)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = placeName.isNotBlank(),
                    onClick = {
                        onSavePlace(placeName, resolvedAddress, latitude!!, longitude!!)
                        showSavePlaceDialog = false
                    }
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSavePlaceDialog = false }) { Text("Cancel") }
            }
        )
    }
}
