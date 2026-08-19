package com.formula2latex.data.provider

import android.util.Base64
import com.formula2latex.domain.model.FormulaInput
import org.json.JSONArray
import org.json.JSONObject

object RequestBuilders {
    fun gemini(input: FormulaInput, structured: Boolean): JSONObject {
        val userParts = JSONArray().put(JSONObject().put("text", TranscriptionContract.userPrompt(input)))
        if (input is FormulaInput.Image) {
            userParts.put(JSONObject().put("inlineData", JSONObject()
                .put("mimeType", input.mimeType)
                .put("data", Base64.encodeToString(input.bytes, Base64.NO_WRAP))))
        }
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", TranscriptionContract.systemInstruction))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", userParts)))
            .apply {
                if (structured) put("generationConfig", JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseJsonSchema", TranscriptionContract.jsonSchema()))
            }
    }

    fun openAi(modelId: String, input: FormulaInput, structured: Boolean, requireParameters: Boolean): JSONObject {
        val userContent: Any = when (input) {
            is FormulaInput.Description -> TranscriptionContract.userPrompt(input)
            is FormulaInput.Image -> JSONArray()
                .put(JSONObject().put("type", "text").put("text", TranscriptionContract.userPrompt(input)))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put(
                    "url", "data:${input.mimeType};base64,${Base64.encodeToString(input.bytes, Base64.NO_WRAP)}"
                )))
        }
        return JSONObject()
            .put("model", modelId)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", TranscriptionContract.systemInstruction))
                .put(JSONObject().put("role", "user").put("content", userContent)))
            .apply {
                if (structured) {
                    put("response_format", JSONObject()
                        .put("type", "json_schema")
                        .put("json_schema", JSONObject()
                            .put("name", "formula_transcription")
                            .put("strict", true)
                            .put("schema", TranscriptionContract.jsonSchema())))
                    if (requireParameters) put("provider", JSONObject().put("require_parameters", true))
                }
            }
    }
}
