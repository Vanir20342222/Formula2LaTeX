package com.formula2latex.data.provider

import android.net.Uri
import com.formula2latex.domain.model.Capability
import com.formula2latex.domain.model.FormulaInput
import com.formula2latex.domain.model.FormulaProvider
import com.formula2latex.domain.model.FormulaResult
import com.formula2latex.domain.model.ModelInfo
import com.formula2latex.domain.model.ProviderConfig
import com.formula2latex.domain.model.ProviderKind
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

data class RawHttp(val status: Int, val body: String, val requestId: String?)

class GeminiFormulaProvider(private val client: OkHttpClient) : FormulaProvider {
    override suspend fun listModels(config: ProviderConfig): Result<List<ModelInfo>> = runCatching {
        require(config.apiKey.isNotBlank()) { "A Gemini API key is required." }
        val models = mutableListOf<ModelInfo>()
        var token: String? = null
        var pages = 0
        do {
            val suffix = if (token.isNullOrBlank()) "" else "&pageToken=${Uri.encode(token)}"
            val raw = client.executeRequest(Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/v1beta/models?pageSize=1000$suffix")
                .header("x-goog-api-key", config.apiKey)
                .get().build())
            ensureSuccess(raw)
            val root = JSONObject(raw.body)
            val array = root.optJSONArray("models") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val methods = item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                if ((0 until methods.length()).none { methods.optString(it) == "generateContent" }) continue
                val id = item.optString("baseModelId").ifBlank { item.optString("name").removePrefix("models/") }
                if (id.isNotBlank()) models += ModelInfo(
                    id = id,
                    displayName = item.optString("displayName", id),
                    description = item.optString("description"),
                )
            }
            token = root.optString("nextPageToken").takeIf { it.isNotBlank() }
            pages++
        } while (token != null && pages < 20)
        models.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
    }.mapProviderFailure()

    override suspend fun convert(config: ProviderConfig, modelId: String, input: FormulaInput): Result<FormulaResult> = runCatching {
        require(config.apiKey.isNotBlank()) { "A Gemini API key is required." }
        val id = modelId.removePrefix("models/")
        val url = "${config.baseUrl.trimEnd('/')}/v1beta/models/$id:generateContent"
        var raw = post(url, config.apiKey, RequestBuilders.gemini(input, structured = true))
        if (raw.status == 400 && looksLikeStructuredOutputRejection(raw.body)) {
            raw = post(url, config.apiKey, RequestBuilders.gemini(input, structured = false))
        }
        ensureSuccess(raw)
        val root = JSONObject(raw.body)
        val candidates = root.optJSONArray("candidates") ?: JSONArray()
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = buildString {
            for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
        }
        ResponseParser.parse(text)
    }.mapProviderFailure()

    private suspend fun post(url: String, key: String, body: JSONObject): RawHttp = client.executeRequest(Request.Builder()
        .url(url).header("x-goog-api-key", key)
        .post(body.toString().toRequestBody(jsonMediaType)).build())
}

open class OpenAiCompatibleFormulaProvider(
    private val client: OkHttpClient,
    private val openRouter: Boolean,
) : FormulaProvider {
    private val discovered = ConcurrentHashMap<String, ModelInfo>()

    override suspend fun listModels(config: ProviderConfig): Result<List<ModelInfo>> = runCatching {
        val base = apiV1Base(config)
        val request = Request.Builder().url("$base/models").get().apply { authorization(config) }.build()
        val raw = client.executeRequest(request)
        ensureSuccess(raw)
        val root = JSONObject(raw.body)
        val data = root.optJSONArray("data") ?: JSONArray()
        val models = (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val architecture = item.optJSONObject("architecture")
            val inputModalities = architecture?.optJSONArray("input_modalities")
            val supported = item.optJSONArray("supported_parameters")
            val image = when {
                inputModalities == null -> Capability.UNKNOWN
                (0 until inputModalities.length()).any { inputModalities.optString(it) == "image" } -> Capability.SUPPORTED
                else -> Capability.UNSUPPORTED
            }
            val structured = when {
                supported == null -> Capability.UNKNOWN
                (0 until supported.length()).any { supported.optString(it) == "structured_outputs" } -> Capability.SUPPORTED
                else -> Capability.UNSUPPORTED
            }
            ModelInfo(id, item.optString("name", id), item.optString("description"), image, structured)
        }.sortedBy { it.displayName.lowercase() }
        models.forEach { discovered[it.id] = it }
        models
    }.mapProviderFailure()

    override suspend fun convert(config: ProviderConfig, modelId: String, input: FormulaInput): Result<FormulaResult> = runCatching {
        val known = discovered[modelId]
        if (input is FormulaInput.Image && known?.imageInput == Capability.UNSUPPORTED) {
            throw ProviderException(ProviderErrorKind.UNSUPPORTED_IMAGE, "The selected model is marked as text-only. Choose an image-capable model.")
        }
        val structured = known?.structuredOutput == Capability.SUPPORTED
        val url = "${apiV1Base(config)}/chat/completions"
        var raw = post(config, url, RequestBuilders.openAi(modelId, input, structured, openRouter && structured))
        if (structured && raw.status == 400 && looksLikeStructuredOutputRejection(raw.body)) {
            discovered.computeIfPresent(modelId) { _, info -> info.copy(structuredOutput = Capability.UNSUPPORTED) }
            raw = post(config, url, RequestBuilders.openAi(modelId, input, structured = false, requireParameters = false))
        }
        if (raw.status == 400 && input is FormulaInput.Image && looksLikeImageRejection(raw.body)) {
            discovered.computeIfPresent(modelId) { _, info -> info.copy(imageInput = Capability.UNSUPPORTED) }
        }
        ensureSuccess(raw)
        val root = JSONObject(raw.body)
        val content = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
        val text = when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (i in 0 until content.length()) append(content.optJSONObject(i)?.optString("text").orEmpty())
            }
            else -> ""
        }
        ResponseParser.parse(text)
    }.mapProviderFailure()

    private suspend fun post(config: ProviderConfig, url: String, body: JSONObject): RawHttp = client.executeRequest(
        Request.Builder().url(url).apply { authorization(config) }
            .post(body.toString().toRequestBody(jsonMediaType)).build()
    )

    private fun Request.Builder.authorization(config: ProviderConfig) {
        if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}")
        else if (openRouter) throw IllegalArgumentException("An OpenRouter API key is required.")
    }

    private fun apiV1Base(config: ProviderConfig): String {
        val base = config.baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "The endpoint must use HTTPS. Cleartext HTTP is disabled." }
        return if (base.endsWith("/v1")) base else "$base/v1"
    }
}

class OpenRouterFormulaProvider(client: OkHttpClient) : OpenAiCompatibleFormulaProvider(client, openRouter = true)
class CustomFormulaProvider(client: OkHttpClient) : OpenAiCompatibleFormulaProvider(client, openRouter = false)

class ProviderRegistry(client: OkHttpClient) {
    private val gemini = GeminiFormulaProvider(client)
    private val openRouter = OpenRouterFormulaProvider(client)
    private val custom = CustomFormulaProvider(client)
    fun get(kind: ProviderKind): FormulaProvider = when (kind) {
        ProviderKind.GEMINI -> gemini
        ProviderKind.OPEN_ROUTER -> openRouter
        ProviderKind.CUSTOM -> custom
    }
}

private suspend fun OkHttpClient.executeRequest(request: Request): RawHttp {
    val response = newCall(request).await()
    return response.use {
        RawHttp(it.code, it.body.string(), it.header("x-request-id") ?: it.header("request-id"))
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(ProviderErrorMapper.network(e))
        }
        override fun onResponse(call: Call, response: Response) = continuation.resume(response)
    })
}

private fun ensureSuccess(raw: RawHttp) {
    if (raw.status in 200..299) return
    throw ProviderErrorMapper.http(raw.status, safeProviderMessage(raw.body), raw.requestId)
}

private fun safeProviderMessage(body: String): String {
    val message = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: "HTTP error"
    return message
        .replace(Regex("data:[^;]+;base64,[A-Za-z0-9+/=]+"), "<image omitted>")
        .replace(Regex("(?i)(bearer|api[-_ ]?key)\\s*[:=]?\\s*[^\\s,]+"), "$1 <redacted>")
        .take(240)
}

private fun looksLikeStructuredOutputRejection(body: String): Boolean {
    val lower = body.lowercase()
    return "responsejsonschema" in lower || "response_format" in lower || "structured" in lower || "json schema" in lower || "responsemimetype" in lower
}

private fun looksLikeImageRejection(body: String): Boolean {
    val lower = body.lowercase()
    return "image" in lower && ("unsupported" in lower || "not support" in lower || "modality" in lower)
}

private fun <T> Result<T>.mapProviderFailure(): Result<T> = fold(
    onSuccess = { Result.success(it) },
    onFailure = {
        Result.failure(when (it) {
            is ProviderException -> it
            is IllegalArgumentException -> ProviderException(ProviderErrorKind.INVALID_REQUEST, it.message ?: "Invalid configuration.")
            else -> ProviderException(ProviderErrorKind.UNKNOWN, "The provider response could not be processed.")
        })
    },
)
