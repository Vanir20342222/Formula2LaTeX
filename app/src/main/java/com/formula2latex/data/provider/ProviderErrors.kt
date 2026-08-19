package com.formula2latex.data.provider

import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

enum class ProviderErrorKind {
    INVALID_REQUEST, UNAUTHORIZED, NOT_FOUND, IMAGE_TOO_LARGE, RATE_LIMITED,
    SERVER_UNAVAILABLE, CONNECTIVITY, TLS, UNSUPPORTED_IMAGE, UNSUPPORTED_STRUCTURED_OUTPUT,
    MALFORMED_RESPONSE, CANCELLED, UNKNOWN
}

class ProviderException(
    val kind: ProviderErrorKind,
    override val message: String,
    val httpStatus: Int? = null,
    val requestId: String? = null,
) : Exception(message) {
    override fun toString(): String = "ProviderException(kind=$kind, status=$httpStatus, requestId=$requestId, message=$message)"
}

object ProviderErrorMapper {
    fun http(status: Int, safeMessage: String, requestId: String? = null): ProviderException {
        val lower = safeMessage.lowercase()
        val kind = when {
            status == 400 && ("image" in lower && ("unsupported" in lower || "modality" in lower)) -> ProviderErrorKind.UNSUPPORTED_IMAGE
            status == 400 && ("schema" in lower || "response_format" in lower || "structured" in lower) -> ProviderErrorKind.UNSUPPORTED_STRUCTURED_OUTPUT
            status == 400 -> ProviderErrorKind.INVALID_REQUEST
            status == 401 || status == 403 -> ProviderErrorKind.UNAUTHORIZED
            status == 404 -> ProviderErrorKind.NOT_FOUND
            status == 413 -> ProviderErrorKind.IMAGE_TOO_LARGE
            status == 429 -> ProviderErrorKind.RATE_LIMITED
            status >= 500 -> ProviderErrorKind.SERVER_UNAVAILABLE
            else -> ProviderErrorKind.UNKNOWN
        }
        val action = when (kind) {
            ProviderErrorKind.UNAUTHORIZED -> "The API key is invalid or is not authorized for this provider."
            ProviderErrorKind.NOT_FOUND -> "The selected model or endpoint was not found. Check the exact model ID and base URL."
            ProviderErrorKind.IMAGE_TOO_LARGE -> "The provider rejected the image size. The app can resize it and retry."
            ProviderErrorKind.RATE_LIMITED -> "The provider quota or rate limit was reached. Try again later or check your provider account."
            ProviderErrorKind.SERVER_UNAVAILABLE -> "The provider is temporarily unavailable. Your input has been preserved for Retry."
            ProviderErrorKind.UNSUPPORTED_IMAGE -> "This model or endpoint does not accept image input. Choose an image-capable model."
            ProviderErrorKind.UNSUPPORTED_STRUCTURED_OUTPUT -> "This model or endpoint rejected structured output."
            ProviderErrorKind.INVALID_REQUEST -> "The provider rejected the request. Check the model capability and endpoint."
            else -> "The provider request failed."
        }
        return ProviderException(kind, "$action ${safeMessage.take(240)}".trim(), status, requestId)
    }

    fun network(error: IOException): ProviderException = when (error) {
        is SocketTimeoutException -> ProviderException(ProviderErrorKind.CONNECTIVITY, "The provider request timed out. Check the connection and retry.")
        is SSLException -> ProviderException(ProviderErrorKind.TLS, "A secure TLS connection to the provider could not be established.")
        else -> ProviderException(ProviderErrorKind.CONNECTIVITY, "The provider could not be reached. Check the internet connection and endpoint.")
    }
}
