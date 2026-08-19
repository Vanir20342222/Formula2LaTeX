package com.formula2latex.domain.model

enum class ProviderKind(val label: String) {
    GEMINI("Google Gemini"),
    OPEN_ROUTER("OpenRouter"),
    CUSTOM("Custom OpenAI-Compatible")
}

enum class Capability { SUPPORTED, UNSUPPORTED, UNKNOWN }

class ProviderConfig(
    val kind: ProviderKind,
    val apiKey: String,
    val baseUrl: String = defaultBaseUrl(kind),
) {
    fun withKey(key: String) = ProviderConfig(kind, key, baseUrl)
    override fun toString(): String = "ProviderConfig(kind=$kind, apiKey=<redacted>, baseUrl=$baseUrl)"

    companion object {
        fun defaultBaseUrl(kind: ProviderKind): String = when (kind) {
            ProviderKind.GEMINI -> "https://generativelanguage.googleapis.com"
            ProviderKind.OPEN_ROUTER -> "https://openrouter.ai/api"
            ProviderKind.CUSTOM -> "https://example.com"
        }
    }
}

data class ModelInfo(
    val id: String,
    val displayName: String = id,
    val description: String = "",
    val imageInput: Capability = Capability.UNKNOWN,
    val structuredOutput: Capability = Capability.UNKNOWN,
)

sealed interface FormulaInput {
    data class Description(val text: String) : FormulaInput
    data class Image(val bytes: ByteArray, val mimeType: String) : FormulaInput
}

data class FormulaResult(
    val latex: String,
    val confidence: Double?,
    val alternatives: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

interface FormulaProvider {
    suspend fun listModels(config: ProviderConfig): Result<List<ModelInfo>>
    suspend fun convert(config: ProviderConfig, modelId: String, input: FormulaInput): Result<FormulaResult>
}
