package com.formula2latex.ui.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.formula2latex.data.settings.SettingsSnapshot
import com.formula2latex.data.settings.ThemePreference
import com.formula2latex.domain.model.Capability
import com.formula2latex.domain.model.ModelInfo
import com.formula2latex.domain.model.ProviderConfig
import com.formula2latex.domain.model.ProviderKind

@Composable
fun SettingsDialog(
    initial: SettingsSnapshot,
    models: List<ModelInfo>,
    loading: Boolean,
    error: String?,
    canDismiss: Boolean,
    onRefresh: (SettingsSnapshot) -> Unit,
    onSave: (SettingsSnapshot) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var provider by remember(initial) { mutableStateOf(initial.provider) }
    var key by remember(initial) { mutableStateOf(initial.apiKey) }
    var baseUrl by remember(initial) { mutableStateOf(initial.baseUrl) }
    var modelId by remember(initial) { mutableStateOf(initial.modelId) }
    var saveKey by remember(initial) { mutableStateOf(initial.saveKey) }
    var theme by remember(initial) { mutableStateOf(initial.theme) }
    var reveal by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    fun draft() = SettingsSnapshot(
        provider = provider,
        baseUrl = baseUrl.trim(),
        modelId = modelId.trim(),
        apiKey = key,
        saveKey = saveKey,
        privacyAccepted = true,
        theme = theme,
    )

    LaunchedEffect(provider) {
        baseUrl = if (provider == initial.provider) initial.baseUrl else ProviderConfig.defaultBaseUrl(provider)
        modelId = if (provider == initial.provider) initial.modelId else ""
    }

    AlertDialog(
        modifier = Modifier.widthIn(max = 720.dp),
        onDismissRequest = { if (canDismiss) onDismiss() },
        title = { Text("Provider setup") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        "Requests go directly from this device to the provider you select. Formula2LaTeX has no app backend, account, analytics, or formula history.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Text("Appearance", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { option ->
                            FilterChip(
                                selected = theme == option,
                                onClick = { theme = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                    Text("The selected theme applies after saving.", style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProviderKind.entries.forEach {
                            FilterChip(
                                selected = provider == it,
                                onClick = { provider = it },
                                label = { Text(when (it) {
                                    ProviderKind.GEMINI -> "Gemini"
                                    ProviderKind.OPEN_ROUTER -> "OpenRouter"
                                    ProviderKind.CUSTOM -> "Custom"
                                }) },
                            )
                        }
                    }
                }
                if (provider == ProviderKind.CUSTOM) item {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Base URL (HTTPS)") },
                        supportingText = { Text("The app appends /v1 when needed. Empty keys are allowed.") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (provider == ProviderKind.CUSTOM) "API key (optional)" else "API key") },
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .semantics { contentDescription = "Press and hold to reveal API key" }
                                    .pointerInput(Unit) {
                                        detectTapGestures(onPress = {
                                            reveal = true
                                            tryAwaitRelease()
                                            reveal = false
                                        })
                                    },
                            ) { Text(if (reveal) "Hide" else "Hold") }
                        },
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Save on this device")
                            Text("Encrypted with Android Keystore", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = saveKey, onCheckedChange = { saveKey = it })
                    }
                }
                item {
                    OutlinedButton(onClick = { onRefresh(draft()) }, enabled = !loading) {
                        if (loading) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, null)
                        Text("Refresh models")
                    }
                }
                if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
                if (models.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search discovered models") },
                            singleLine = true,
                        )
                    }
                    items(
                        models.filter { search.isBlank() || it.id.contains(search, true) || it.displayName.contains(search, true) }.take(80),
                        key = { it.id },
                    ) { model ->
                        TextButton(onClick = { modelId = model.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(model.displayName)
                                Text(model.id, style = MaterialTheme.typography.bodySmall)
                                if (model.imageInput == Capability.UNSUPPORTED) {
                                    Text("Text only", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model ID (manual entry always available)") },
                        singleLine = true,
                    )
                }
                if (initial.configured) item {
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null)
                        Text("Delete key and configuration")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(draft()) }) { Text("Save") } },
        dismissButton = { if (canDismiss) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
