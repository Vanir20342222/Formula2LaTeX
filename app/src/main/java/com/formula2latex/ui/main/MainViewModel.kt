package com.formula2latex.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.formula2latex.data.image.ImagePipeline
import com.formula2latex.data.provider.ProviderErrorKind
import com.formula2latex.data.provider.ProviderException
import com.formula2latex.data.provider.ProviderRegistry
import com.formula2latex.data.settings.SettingsRepository
import com.formula2latex.data.settings.SettingsSnapshot
import com.formula2latex.domain.model.FormulaInput
import com.formula2latex.domain.model.FormulaResult
import com.formula2latex.domain.model.ModelInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ConversionState {
    data object Idle : ConversionState
    data object Loading : ConversionState
    data class Success(val result: FormulaResult) : ConversionState
    data class Error(val message: String) : ConversionState
}

data class MainUiState(
    val loadingSettings: Boolean = true,
    val settings: SettingsSnapshot = SettingsSnapshot(),
    val showSettings: Boolean = false,
    val models: List<ModelInfo> = emptyList(),
    val modelsLoading: Boolean = false,
    val modelError: String? = null,
    val conversion: ConversionState = ConversionState.Idle,
    val editableLatex: String = "",
)

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val providers: ProviderRegistry,
    private val imagePipeline: ImagePipeline,
) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var conversionJob: Job? = null
    private var lastInput: FormulaInput? = null

    init {
        viewModelScope.launch {
            val saved = settingsRepository.load()
            _state.update {
                it.copy(
                    loadingSettings = false,
                    settings = saved,
                    showSettings = !saved.configured || !saved.privacyAccepted,
                )
            }
        }
    }

    fun openSettings() = _state.update { it.copy(showSettings = true, modelError = null) }
    fun closeSettings() {
        if (_state.value.settings.configured) _state.update { it.copy(showSettings = false) }
    }

    fun refreshModels(draft: SettingsSnapshot) {
        if (draft.provider != com.formula2latex.domain.model.ProviderKind.CUSTOM && draft.apiKey.isBlank()) {
            _state.update { it.copy(modelError = "Enter an API key before refreshing models.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(modelsLoading = true, modelError = null, models = emptyList()) }
            val result = withContext(Dispatchers.IO) {
                providers.get(draft.provider).listModels(draft.providerConfig())
            }
            _state.update {
                result.fold(
                    onSuccess = { models -> it.copy(modelsLoading = false, models = models) },
                    onFailure = { error -> it.copy(modelsLoading = false, modelError = safeMessage(error)) },
                )
            }
        }
    }

    fun saveSettings(draft: SettingsSnapshot) {
        viewModelScope.launch {
            val validation = validate(draft)
            if (validation != null) {
                _state.update { it.copy(modelError = validation) }
                return@launch
            }
            runCatching { settingsRepository.save(draft) }
                .onSuccess {
                    _state.update { it.copy(settings = draft.copy(privacyAccepted = true), showSettings = false, modelError = null) }
                }
                .onFailure { error -> _state.update { it.copy(modelError = safeMessage(error)) } }
        }
    }

    fun deleteConfiguration() {
        conversionJob?.cancel()
        viewModelScope.launch {
            settingsRepository.delete()
            val empty = SettingsSnapshot(
                privacyAccepted = true,
                theme = _state.value.settings.theme,
            )
            _state.value = MainUiState(
                loadingSettings = false,
                settings = empty,
                showSettings = true,
            )
        }
    }

    fun convert(input: FormulaInput) {
        if (_state.value.conversion is ConversionState.Loading) return
        val settings = _state.value.settings
        val validation = validate(settings)
        if (validation != null) {
            _state.update { it.copy(conversion = ConversionState.Error(validation), showSettings = true) }
            return
        }
        lastInput = input
        conversionJob = viewModelScope.launch {
            _state.update { it.copy(conversion = ConversionState.Loading) }
            val provider = providers.get(settings.provider)
            var attempted = input
            var result = withContext(Dispatchers.Default) {
                provider.convert(settings.providerConfig(), settings.modelId, attempted)
            }
            val firstError = result.exceptionOrNull() as? ProviderException
            if (firstError?.kind == ProviderErrorKind.IMAGE_TOO_LARGE && attempted is FormulaInput.Image) {
                val oversized = attempted
                attempted = withContext(Dispatchers.Default) {
                    FormulaInput.Image(imagePipeline.reduceFurther(oversized.bytes), oversized.mimeType)
                }
                result = withContext(Dispatchers.Default) {
                    provider.convert(settings.providerConfig(), settings.modelId, attempted)
                }
                lastInput = attempted
            }
            result.fold(
                onSuccess = { formula ->
                    _state.update { it.copy(conversion = ConversionState.Success(formula), editableLatex = formula.latex) }
                },
                onFailure = { error ->
                    if (error !is CancellationException) {
                        _state.update { it.copy(conversion = ConversionState.Error(safeMessage(error))) }
                    }
                },
            )
        }
    }

    fun retry() {
        val input = lastInput ?: return
        _state.update { it.copy(conversion = ConversionState.Idle) }
        convert(input)
    }

    fun cancel() {
        conversionJob?.cancel()
        _state.update { it.copy(conversion = ConversionState.Idle) }
    }

    fun editLatex(value: String) = _state.update { it.copy(editableLatex = value) }

    fun chooseAlternative(value: String) = editLatex(value)

    fun dismissResult() = _state.update {
        it.copy(conversion = ConversionState.Idle, editableLatex = "")
    }

    private fun validate(settings: SettingsSnapshot): String? = when {
        !settings.baseUrl.startsWith("https://") -> "Use an HTTPS provider endpoint."
        settings.provider != com.formula2latex.domain.model.ProviderKind.CUSTOM && settings.apiKey.isBlank() -> "Enter your provider API key."
        settings.modelId.isBlank() -> "Select or enter a model ID."
        else -> null
    }

    private fun safeMessage(error: Throwable): String = when (error) {
        is ProviderException -> error.message
        is IllegalArgumentException -> error.message ?: "Check the entered settings."
        else -> "The operation failed without exposing sensitive request details."
    }
}
