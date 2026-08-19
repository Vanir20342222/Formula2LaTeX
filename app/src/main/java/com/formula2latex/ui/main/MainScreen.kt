package com.formula2latex.ui.main

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.formula2latex.data.image.ImagePipeline
import com.formula2latex.domain.model.FormulaInput
import com.formula2latex.ui.components.FormulaPreview
import com.formula2latex.ui.settings.SettingsDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(viewModel: MainViewModel, imagePipeline: ImagePipeline) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var description by rememberSaveable { mutableStateOf("") }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    val drawing = remember { DrawingEditor() }
    var showCamera by remember { mutableStateOf(false) }
    var showPhotoReview by remember { mutableStateOf(false) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.Default) { imagePipeline.fromUri(uri) } }
                .onSuccess { photoBytes = it }
                .onFailure { snackbar.showSnackbar(it.message ?: "Could not read image.") }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = true
        else scope.launch { snackbar.showSnackbar("Camera permission is needed to take a photo.") }
    }

    if (showCamera) CameraCaptureDialog(
        onCaptured = { path ->
            showCamera = false
            scope.launch {
                runCatching { withContext(Dispatchers.Default) { imagePipeline.fromFile(path) } }
                    .onSuccess { photoBytes = it }
                    .onFailure { snackbar.showSnackbar(it.message ?: "Could not read photo.") }
                java.io.File(path).delete()
            }
        },
        onDismiss = { showCamera = false },
    )

    if (state.showSettings && !state.loadingSettings) {
        SettingsDialog(
            initial = state.settings,
            models = state.models,
            loading = state.modelsLoading,
            error = state.modelError,
            canDismiss = state.settings.configured,
            onRefresh = viewModel::refreshModels,
            onSave = viewModel::saveSettings,
            onDelete = viewModel::deleteConfiguration,
            onDismiss = viewModel::closeSettings,
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (state.loadingSettings) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val tablet = maxWidth >= 600.dp
                Column(
                    Modifier
                        .widthIn(max = 1400.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = if (tablet) 24.dp else 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            "Formula2LaTeX",
                            style = if (tablet) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "${state.settings.provider.label} · ${state.settings.modelId.ifBlank { "No model selected" }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = viewModel::openSettings) { Icon(Icons.Default.Settings, "Provider settings") }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val roomy = maxWidth >= 600.dp
                    val input: @Composable (Modifier) -> Unit = { modifier ->
                        Card(modifier) {
                        Column(
                            Modifier.padding(if (roomy) 16.dp else 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PrimaryTabRow(tab) {
                                listOf("Describe", "Photo", "Draw").forEachIndexed { index, label ->
                                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                                }
                            }
                            when (tab) {
                                0 -> {
                                    OutlinedTextField(
                                        value = description,
                                        onValueChange = { description = it },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                                        label = { Text("Describe the expression") },
                                        supportingText = { Text("Example: the integral from zero to infinity of e to the minus x squared") },
                                    )
                                    Button(
                                        onClick = { viewModel.convert(FormulaInput.Description(description.trim())) },
                                        enabled = description.isNotBlank() && state.conversion !is ConversionState.Loading,
                                    ) { Text("Convert description") }
                                }
                                1 -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) {
                                            Text("Take photo")
                                        }
                                        OutlinedButton(onClick = {
                                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }) {
                                            Text("Choose image")
                                        }
                                    }
                                    photoBytes?.let { bytes ->
                                        val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                                        bitmap?.let {
                                            if (showPhotoReview) {
                                                PhotoReviewDialog(it, onDismiss = { showPhotoReview = false })
                                            }
                                            Image(
                                                it.asImageBitmap(),
                                                "Selected formula image",
                                                Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 180.dp, max = if (roomy) 420.dp else 320.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { showPhotoReview = true },
                                                contentScale = ContentScale.Fit,
                                            )
                                            Text("Tap the image to review it full size.", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Button(
                                            onClick = { viewModel.convert(FormulaInput.Image(bytes, "image/jpeg")) },
                                            enabled = state.conversion !is ConversionState.Loading,
                                        ) { Text("Convert image") }
                                    } ?: Text("Take a photo or choose an image. Nothing is retained after this session.")
                                }
                                else -> DrawingPad(
                                    editor = drawing,
                                    canvasHeight = if (roomy) 480.dp else 360.dp,
                                ) { viewModel.convert(FormulaInput.Image(it, "image/png")) }
                            }
                            if (state.conversion is ConversionState.Loading) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                                    Text("Transcribing…", Modifier.weight(1f))
                                    TextButton(onClick = viewModel::cancel) { Text("Cancel") }
                                }
                            }
                            (state.conversion as? ConversionState.Error)?.let {
                                Text(it.message, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        }
                    }
                    val result: @Composable (Modifier) -> Unit = { modifier ->
                        (state.conversion as? ConversionState.Success)?.let {
                            ResultCard(
                                result = it.result,
                                latex = state.editableLatex,
                                onLatexChange = viewModel::editLatex,
                                onAlternative = viewModel::chooseAlternative,
                                onRetry = viewModel::retry,
                                onDismiss = viewModel::dismissResult,
                                onCopy = {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .setPrimaryClip(ClipData.newPlainText("LaTeX", state.editableLatex))
                                    scope.launch { snackbar.showSnackbar("LaTeX copied") }
                                },
                                modifier = modifier,
                            )
                        }
                    }
                    if (maxWidth >= 840.dp && state.conversion is ConversionState.Success) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            input(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                            result(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                        }
                    } else {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            input(Modifier.fillMaxWidth())
                            result(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun PhotoReviewDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            color = androidx.compose.ui.graphics.Color(0xFF101014),
        ) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap.asImageBitmap(),
                    "Full-size selected formula image",
                    Modifier.fillMaxSize().padding(20.dp),
                    contentScale = ContentScale.Fit,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(Icons.Default.Close, "Close image review", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@Composable
internal fun ResultCard(
    result: com.formula2latex.domain.model.FormulaResult,
    latex: String,
    onLatexChange: (String) -> Unit,
    onAlternative: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayMode by rememberSaveable { mutableStateOf(true) }
    var renderError by remember { mutableStateOf<String?>(null) }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Result", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { displayMode = !displayMode }) {
                        Text(if (displayMode) "Display" else "Inline")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close result")
                    }
                }
            }
            FormulaPreview(latex, displayMode, onRenderError = { renderError = it })
            renderError?.let {
                Text("Preview warning: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = latex,
                onValueChange = onLatexChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Raw LaTeX") },
                minLines = 3,
            )
            result.confidence?.let { Text("Confidence: ${(it * 100).toInt()}%") }
            result.warnings.forEach { Text("Warning: $it", color = MaterialTheme.colorScheme.error) }
            if (result.alternatives.isNotEmpty()) {
                Text("Alternative interpretations")
                result.alternatives.forEach { alternative ->
                    TextButton(onClick = { onAlternative(alternative) }) { Text(alternative) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCopy) {
                    Text("Copy LaTeX")
                }
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
