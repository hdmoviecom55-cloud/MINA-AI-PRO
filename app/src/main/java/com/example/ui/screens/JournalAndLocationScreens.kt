package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.CryptoVault
import com.example.core.PdfAndExportHelper
import com.example.core.PhoneControlHelper
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

@Composable
fun JournalScreen(
    settings: AppSettingsState,
    journalsState: UiState<List<JournalEntry>>,
    onBack: () -> Unit,
    onSaveJournal: (String, String, JournalMood, Boolean) -> Unit,
    onDeleteJournal: (String) -> Unit
) {
    val palette = LocalMinaPalette.current
    var searchQuery by remember { mutableStateOf("") }
    var showNewDialog by remember { mutableStateOf(false) }

    val rawEntries = (journalsState as? UiState.Success)?.data.orEmpty()
    val filteredEntries = remember(rawEntries, searchQuery) {
        val q = searchQuery.trim().lowercase()
        rawEntries.filter { entry ->
            val decrypted = if (entry.encrypted) CryptoVault.decrypt(entry.content) else entry.content
            q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                decrypted.lowercase().contains(q) ||
                entry.mood.lowercase().contains(q)
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
                title = "Private Journal",
                subtitle = "Daily Diary, Moods & AES-Encrypted Vault",
                onBack = onBack,
                trailing = {
                    IconButton(
                        onClick = { showNewDialog = true },
                        modifier = Modifier.testTag("journal_add_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New journal entry", tint = palette.goldHighlight)
                    }
                }
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("Search diary entries or moods") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                )

                AmberGradientButton(
                    text = "Write Today's Journal Entry",
                    icon = Icons.Filled.Add,
                    onClick = { showNewDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("journal_new_cta")
                )

                if (filteredEntries.isEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No journal entries match yet. Write your thoughts and MINA will reflect back warmly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    filteredEntries.forEach { entry ->
                        val mood = JournalMood.fromRaw(entry.mood)
                        val body = if (entry.encrypted) CryptoVault.decrypt(entry.content) else entry.content
                        val dateText = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            .format(entry.createdAt?.toDate() ?: Date())

                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${mood.emoji} ${entry.title}", style = MaterialTheme.typography.titleMedium)
                                        if (entry.encrypted) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Filled.Lock, contentDescription = "Encrypted", tint = palette.goldHighlight, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    IconButton(onClick = { onDeleteJournal(entry.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Text(dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(body, style = MaterialTheme.typography.bodyLarge)
                                if (entry.aiReflection.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(palette.gradientEnd.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                            .padding(10.dp)
                                    ) {
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
                }
            }
        }
    }

    if (showNewDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var mood by remember { mutableStateOf(JournalMood.CALM) }
        var encrypt by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("New Journal Entry") },
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
                        label = { Text("How are you feeling today?") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        JournalMood.entries.forEach { m ->
                            FilterChip(
                                selected = mood == m,
                                onClick = { mood = m },
                                label = { Text("${m.emoji} ${m.label}") }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Encrypt Entry (AES-256)")
                        Switch(checked = encrypt, onCheckedChange = { encrypt = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = content.isNotBlank(),
                    onClick = {
                        onSaveJournal(title, content, mood, encrypt)
                        showNewDialog = false
                    }
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) { Text("Cancel") }
            }
        )
    }
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
