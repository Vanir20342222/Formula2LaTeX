package com.formula2latex.data.provider

import com.formula2latex.domain.model.FormulaInput
import org.json.JSONArray
import org.json.JSONObject

object TranscriptionContract {
    val systemInstruction = """
        You are a mathematical-expression transcription engine. Convert the supplied description or image into valid LaTeX that preserves the expression's mathematical meaning and two-dimensional structure.

        Do not solve, simplify, evaluate, prove, explain, or teach. Transcribe only.

        Rules:
        1. Return one JSON object and no markdown, prose, or code fences.
        2. The `latex` value must contain LaTeX only, without `${'$'}`, `${'$'}${'$'}`, `\(`, `\)`, `\[`, or `\]` delimiters.
        3. Preserve fractions, roots, exponents, subscripts, limits, integrals, sums, products, matrices, vectors, accents, cases, aligned equations, brackets, and function names.
        4. Preserve equality and inequality chains exactly as shown or described.
        5. Use standard LaTeX commands such as `\frac`, `\sqrt`, `\sum`, `\int`, `\lim`, `\sin`, and `\operatorname` where appropriate.
        6. Ignore page decorations and unrelated surrounding prose. If surrounding prose changes the meaning of a symbol, use it only to interpret that symbol.
        7. If a symbol is genuinely ambiguous, put the most likely transcription in `latex`, add up to three complete LaTeX alternatives in `alternatives`, and briefly identify the ambiguity in `warnings`.
        8. If no mathematical expression can be identified, return an empty `latex` string, confidence 0, no alternatives, and a warning explaining why.
        9. `confidence` is a number from 0 to 1 representing transcription confidence, not mathematical truth.

        Required JSON shape:
        {"latex":"string","confidence":0.0,"alternatives":["string"],"warnings":["string"]}
    """.trimIndent()

    fun userPrompt(input: FormulaInput): String = when (input) {
        is FormulaInput.Description -> """
            Input type: natural-language description.
            Transcribe this description into a mathematical expression:
            ${input.text.trim()}
        """.trimIndent()
        is FormulaInput.Image -> "Input type: image. Transcribe the principal mathematical expression. If several lines clearly belong to one derivation or system, preserve them in an appropriate LaTeX environment."
    }

    fun jsonSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject()
            .put("latex", JSONObject().put("type", "string"))
            .put("confidence", JSONObject().put("type", "number").put("minimum", 0).put("maximum", 1))
            .put("alternatives", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("maxItems", 3))
            .put("warnings", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))))
        .put("required", JSONArray(listOf("latex", "confidence", "alternatives", "warnings")))
        .put("additionalProperties", false)
}
