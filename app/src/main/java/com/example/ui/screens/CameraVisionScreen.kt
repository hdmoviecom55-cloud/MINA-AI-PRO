package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.AppSettingsState
import com.example.data.model.UiState
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.GlassCard
import com.example.ui.components.MinaBackdrop
import com.example.ui.components.MinaTopBar
import com.example.ui.theme.LocalMinaPalette

@Composable
fun CameraVisionScreen(
    settings: AppSettingsState,
    visionState: UiState<String>?,
    onAnalyzeBitmap: (Bitmap, String) -> Unit,
    onSpeakResult: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = LocalMinaPalette.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var customQuestion by remember { mutableStateOf("") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                capturedBitmap = bmp
            } catch (_: Exception) {
            }
        }
    }

    fun captureFromCamera(promptAfterCapture: String? = null) {
        val cap = imageCapture ?: return
        cap.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        capturedBitmap = bmp
                        if (bmp != null && promptAfterCapture != null) {
                            onAnalyzeBitmap(bmp, promptAfterCapture)
                        }
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                }
            }
        )
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
                title = "Live Vision",
                subtitle = "Objects, OCR Text & Surroundings AI",
                trailing = {
                    if (capturedBitmap != null) {
                        IconButton(onClick = { capturedBitmap = null }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Return to live preview", tint = palette.goldHighlight)
                        }
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Camera Preview or Captured Image Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.glassFill)
                        .border(1.5.dp, palette.goldHighlight.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = capturedBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Captured image for AI analysis",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (hasCameraPermission) {
                        val previewView = remember { PreviewView(context) }
                        DisposableEffect(lifecycleOwner) {
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            val executor = ContextCompat.getMainExecutor(context)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val cap = ImageCapture.Builder().build()
                                    imageCapture = cap
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        cap
                                    )
                                } catch (_: Exception) {
                                }
                            }, executor)
                            onDispose {
                                try {
                                    cameraProviderFuture.get().unbindAll()
                                } catch (_: Exception) {
                                }
                            }
                        }
                        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = palette.goldHighlight,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Grant Camera permission for live preview or pick a photo from gallery.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            AmberGradientButton(
                                text = "Allow Camera",
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.testTag("vision_grant_camera_button")
                            )
                        }
                    }
                }

                // Capture & Gallery Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AmberGradientButton(
                        text = if (capturedBitmap == null) "Capture Frame" else "Re-Analyze",
                        icon = Icons.Filled.CameraAlt,
                        onClick = {
                            val bmp = capturedBitmap
                            if (bmp != null) {
                                onAnalyzeBitmap(bmp, customQuestion.ifBlank { "Describe everything you see in detail." })
                            } else if (hasCameraPermission) {
                                captureFromCamera("Identify objects and explain this scene.")
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vision_capture_button")
                    )

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("vision_gallery_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "Pick photo")
                        Spacer(Modifier.width(6.dp))
                        Text("Photo")
                    }
                }

                // Vision Preset Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Analyze Objects" to "List all key objects, brands, or items visible and what they do.",
                        "Read Text (OCR)" to "Extract and transcribe all visible text accurately, then summarize it.",
                        "Explain Surroundings" to "Explain the environment, lighting, and context of this scene warmly."
                    ).forEach { (label, prompt) ->
                        GlassCard(
                            cornerRadius = 50.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                            onClick = {
                                val bmp = capturedBitmap
                                if (bmp != null) {
                                    onAnalyzeBitmap(bmp, prompt)
                                } else if (hasCameraPermission) {
                                    captureFromCamera(prompt)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, tint = palette.goldHighlight, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // Custom question input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customQuestion,
                        onValueChange = { customQuestion = it },
                        label = { Text("Ask a question about this image") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    AmberGradientButton(
                        text = "Ask",
                        enabled = capturedBitmap != null && customQuestion.isNotBlank(),
                        onClick = {
                            capturedBitmap?.let { onAnalyzeBitmap(it, customQuestion) }
                        },
                        height = 50.dp
                    )
                }

                // AI Output Card
                when (visionState) {
                    UiState.Loading -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = palette.goldHighlight, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("MINA Vision is analyzing...", style = MaterialTheme.typography.bodyLarge)
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
                                    Text("MINA Vision Insight", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                                    IconButton(onClick = { onSpeakResult(visionState.data) }) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak result", tint = palette.goldHighlight)
                                    }
                                }
                                Text(visionState.data, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    is UiState.Error -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(visionState.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    null -> {}
                }
            }
        }
    }
}
