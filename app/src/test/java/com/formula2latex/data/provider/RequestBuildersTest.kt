package com.formula2latex.data.provider

import com.formula2latex.domain.model.FormulaInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestBuildersTest {
    @Test fun geminiUsesCurrentResponseJsonSchemaField() {
        val body = RequestBuilders.gemini(FormulaInput.Description("x squared"), structured = true)
        val config = body.getJSONObject("generationConfig")
        assertTrue(config.has("responseJsonSchema"))
        assertFalse(config.has("responseSchema"))
        assertEquals("application/json", config.getString("responseMimeType"))
    }

    @Test fun openRouterPreservesFullModelIdAndStrictSchema() {
        val body = RequestBuilders.openAi("org/model:free", FormulaInput.Description("x"), true, true)
        assertEquals("org/model:free", body.getString("model"))
        assertEquals("json_schema", body.getJSONObject("response_format").getString("type"))
        assertTrue(body.getJSONObject("provider").getBoolean("require_parameters"))
    }
}
