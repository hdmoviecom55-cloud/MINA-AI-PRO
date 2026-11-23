package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.PdfAndExportHelper
import com.example.data.model.AppSettingsState
import com.example.data.model.CodeSyntaxStyle
import com.example.data.model.DocumentType
import com.example.data.model.SavedDocument
import com.example.data.model.UiState
import com.example.ui.components.AmberGradientButton
import com.example.ui.components.GlassCard
import com.example.ui.components.MinaBackdrop
import com.example.ui.components.MinaTopBar
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMinaPalette

@Composable
fun DocumentsScreen(
    settings: AppSettingsState,
    documentsState: UiState<List<SavedDocument>>,
    docGenState: UiState<String>?,
    onBack: () -> Unit,
    onGenerateDraft: (DocumentType, String) -> Unit,
    onSaveDocument: (String?, String, DocumentType, String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onExportPdf: (String, DocumentType, String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMinaPalette.current

    var selectedType by remember { mutableStateOf(DocumentType.RESUME) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var docTitle by remember { mutableStateOf("") }
    var promptDetails by remember { mutableStateOf("") }
    var editorContent by remember { mutableStateOf("") }

    LaunchedEffect(docGenState) {
        if (docGenState is UiState.Success) {
            editorContent = docGenState.data
            if (docTitle.isBlank()) {
                docTitle = "${selectedType.label} Draft"
            }
        }
    }

    val savedDocs = (documentsState as? UiState.Success)?.data.orEmpty()

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
                title = "Documents & PDF Studio",
                subtitle = "Resumes, Biodata, Letters & Export",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Document Type", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(DocumentType.RESUME, DocumentType.BIODATA, DocumentType.LETTER, DocumentType.NOTE).forEach { dt ->
                                FilterChip(
                                    selected = selectedType == dt,
                                    onClick = { selectedType = dt },
                                    label = { Text(dt.label) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = docTitle,
                            onValueChange = { docTitle = it },
                            label = { Text("Document Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        OutlinedTextField(
                            value = promptDetails,
                            onValueChange = { promptDetails = it },
                            label = { Text("Key Details for AI (name, skills, experience, purpose)") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        AmberGradientButton(
                            text = if (docGenState is UiState.Loading) "Drafting..." else "Generate ${selectedType.label} with MINA",
                            icon = Icons.Filled.AutoAwesome,
                            enabled = promptDetails.isNotBlank() && docGenState !is UiState.Loading,
                            onClick = { onGenerateDraft(selectedType, promptDetails) },
                            modifier = Modifier.fillMaxWidth().testTag("doc_generate_button")
                        )

                        if (docGenState is UiState.Error) {
                            Text(docGenState.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Document Editor", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        OutlinedTextField(
                            value = editorContent,
                            onValueChange = { editorContent = it },
                            placeholder = { Text("Your generated or custom document text appears here...") },
                            minLines = 7,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                enabled = editorContent.isNotBlank(),
                                onClick = {
                                    onSaveDocument(editingId, docTitle.ifBlank { "${selectedType.label} Draft" }, selectedType, editorContent)
                                    editingId = null
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save")
                            }
                            AmberGradientButton(
                                text = "Export PDF",
                                icon = Icons.Filled.PictureAsPdf,
                                enabled = editorContent.isNotBlank(),
                                onClick = {
                                    onExportPdf(docTitle.ifBlank { selectedType.label }, selectedType, editorContent)
                                },
                                modifier = Modifier.weight(1f).testTag("doc_export_pdf_button"),
                                height = 48.dp
                            )
                        }

                        OutlinedButton(
                            enabled = editorContent.isNotBlank(),
                            onClick = {
                                PdfAndExportHelper.shareText(context, editorContent, docTitle.ifBlank { selectedType.label })
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share as Text")
                        }
                    }
                }

                // Saved Documents
                if (savedDocs.isNotEmpty()) {
                    Text("Saved Documents", style = MaterialTheme.typography.titleLarge)
                    savedDocs.forEach { doc ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            contentPadding = PaddingValues(12.dp),
                            onClick = {
                                editingId = doc.id
                                docTitle = doc.title
                                selectedType = DocumentType.fromRaw(doc.docType)
                                editorContent = doc.content
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(doc.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${DocumentType.fromRaw(doc.docType).label} • ${doc.content.take(70)}...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onExportPdf(doc.title, DocumentType.fromRaw(doc.docType), doc.content) }) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = "Export PDF", tint = palette.goldHighlight)
                                }
                                IconButton(onClick = { onDeleteDocument(doc.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete document", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CodingScreen(
    settings: AppSettingsState,
    codeState: UiState<String>?,
    onBack: () -> Unit,
    onRunCoding: (String, String, String) -> Unit,
    onSaveSnippet: (String, String) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMinaPalette.current

    var mode by remember { mutableStateOf("Generate") }
    var stack by remember { mutableStateOf("HTML / CSS / JS") }
    var prompt by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }

    val codeColor = when (settings.codeSyntaxStyle) {
        CodeSyntaxStyle.AMBER_DARK -> Color(0xFFFFD54F)
        CodeSyntaxStyle.EMERALD_MATRIX -> Color(0xFF69F0AE)
        CodeSyntaxStyle.CYAN_CYBER -> Color(0xFF40C4FF)
        CodeSyntaxStyle.MONOCHROME_INK -> Color(0xFFF5F5F5)
    }

    val codeOutput = (codeState as? UiState.Success)?.data.orEmpty()
    val extractedHtml = remember(codeOutput) {
        val regex = Regex("```html\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        regex.find(codeOutput)?.groupValues?.getOrNull(1)?.trim()
            ?: if (codeOutput.contains("<html", ignoreCase = true) || codeOutput.contains("<!DOCTYPE", ignoreCase = true)) {
                codeOutput
            } else null
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
                title = "MINA Coding Studio",
                subtitle = "HTML/CSS/JS, Android Kotlin & Debugger",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Mode", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Generate", "Website Prototype", "App Prototype", "Explain Code", "Debug Error").forEach { m ->
                                FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) })
                            }
                        }

                        Text("Stack / Language", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("HTML / CSS / JS", "Kotlin / Jetpack Compose", "Python", "React / Web", "SQL").forEach { s ->
                                FilterChip(selected = stack == s, onClick = { stack = s }, label = { Text(s) })
                            }
                        }

                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            label = { Text("Describe what to build, explain, or debug") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        AmberGradientButton(
                            text = if (codeState is UiState.Loading) "Generating Code..." else "Run Coding AI",
                            icon = Icons.Filled.Code,
                            enabled = prompt.isNotBlank() && codeState !is UiState.Loading,
                            onClick = {
                                showPreview = false
                                onRunCoding(mode, stack, prompt)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("coding_run_button")
                        )
                    }
                }

                when (codeState) {
                    UiState.Loading -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = palette.goldHighlight, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Compiling AI solution...")
                            }
                        }
                    }
                    is UiState.Error -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(codeState.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is UiState.Success -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Code Output", style = MaterialTheme.typography.titleMedium, color = palette.goldHighlight)
                                    Row {
                                        if (extractedHtml != null) {
                                            IconButton(onClick = { showPreview = !showPreview }) {
                                                Icon(Icons.Filled.Preview, contentDescription = "Toggle live preview", tint = palette.goldHighlight)
                                            }
                                        }
                                        IconButton(onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("MINA Code", codeOutput))
                                            onToast("Code copied")
                                        }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code", tint = palette.goldHighlight)
                                        }
                                        IconButton(onClick = { onSaveSnippet("Code: ${prompt.take(30)}", codeOutput) }) {
                                            Icon(Icons.Filled.Save, contentDescription = "Save snippet", tint = palette.goldHighlight)
                                        }
                                    }
                                }

                                if (showPreview && extractedHtml != null) {
                                    AndroidView(
                                        factory = { ctx ->
                                            WebView(ctx).apply {
                                                this.settings.javaScriptEnabled = true
                                                this.settings.allowFileAccess = false
                                                this.settings.allowContentAccess = false
                                                loadDataWithBaseURL(null, extractedHtml, "text/html", "UTF-8", null)
                                            }
                                        },
                                        update = { webView ->
                                            webView.loadDataWithBaseURL(null, extractedHtml, "text/html", "UTF-8", null)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(340.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(palette.codeBackground)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = codeOutput,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = codeColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                    null -> {}
                }
            }
        }
    }
}
