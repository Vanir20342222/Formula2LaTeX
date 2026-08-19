package com.formula2latex.data.provider

import com.formula2latex.domain.model.FormulaResult
import org.json.JSONObject

object ResponseParser {
    fun parse(raw: String): FormulaResult {
        val trimmed = raw.trim()
        parseJson(trimmed)?.let { return it }

        val unfenced = removeSingleCodeFence(trimmed)
        if (unfenced != trimmed) parseJson(unfenced)?.let { return it }

        firstCompleteJsonObject(unfenced)?.let { parseJson(it)?.let { parsed -> return parsed } }

        val latex = stripOuterMathDelimiters(removeSingleCodeFence(trimmed)).trim()
        if (latex.isNotEmpty() && latex.length <= 100_000) {
            return FormulaResult(
                latex = latex,
                confidence = null,
                warnings = listOf("The provider did not follow the JSON response schema; its raw LaTeX was accepted."),
            )
        }
        throw ProviderException(ProviderErrorKind.MALFORMED_RESPONSE, "The provider returned no usable LaTeX.")
    }

    fun stripOuterMathDelimiters(value: String): String {
        var text = value.trim()
        val pairs = listOf("\$\$" to "\$\$", "\\[" to "\\]", "\\(" to "\\)", "\$" to "\$")
        for ((start, end) in pairs) {
            if (text.startsWith(start) && text.endsWith(end) && text.length >= start.length + end.length) {
                text = text.substring(start.length, text.length - end.length).trim()
                break
            }
        }
        return text
    }

    fun removeSingleCodeFence(value: String): String {
        val lines = value.trim().lines()
        if (lines.size >= 2 && lines.first().trim().matches(Regex("```(?:json|latex|tex)?", RegexOption.IGNORE_CASE)) && lines.last().trim() == "```") {
            return lines.subList(1, lines.lastIndex).joinToString("\n").trim()
        }
        return value.trim()
    }

    internal fun firstCompleteJsonObject(text: String): String? {
        var start = -1
        var depth = 0
        var quoted = false
        var escaped = false
        text.forEachIndexed { index, char ->
            if (start < 0) {
                if (char == '{') { start = index; depth = 1 }
                return@forEachIndexed
            }
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else {
                when (char) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun parseJson(value: String): FormulaResult? = runCatching {
        val obj = JSONObject(value)
        require(obj.has("latex"))
        val alternatives = obj.optJSONArray("alternatives")?.let { array ->
            (0 until minOf(array.length(), 3)).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        val warnings = obj.optJSONArray("warnings")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        val confidence = if (obj.has("confidence") && !obj.isNull("confidence")) obj.optDouble("confidence").takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) else null
        FormulaResult(
            latex = stripOuterMathDelimiters(obj.getString("latex")),
            confidence = confidence,
            alternatives = alternatives.map(::stripOuterMathDelimiters),
            warnings = warnings,
        )
    }.getOrNull()
}
