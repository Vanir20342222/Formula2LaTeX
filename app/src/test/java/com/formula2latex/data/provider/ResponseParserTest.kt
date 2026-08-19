package com.formula2latex.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParserTest {
    @Test fun parsesStrictJson() {
        val result = ResponseParser.parse("""{"latex":"\\frac{1}{2}","confidence":0.9,"alternatives":[],"warnings":[]}""")
        assertEquals("\\frac{1}{2}", result.latex)
        assertEquals(0.9, result.confidence!!, 0.0)
    }

    @Test fun parsesFencedAndEmbeddedJson() {
        val fenced = ResponseParser.parse("""```json
{"latex":"x^2","confidence":0.8,"alternatives":[],"warnings":[]}
```""")
        assertEquals("x^2", fenced.latex)
        val embedded = ResponseParser.parse("""Result: {"latex":"y_1","confidence":0.7,"alternatives":[],"warnings":[]} trailing""")
        assertEquals("y_1", embedded.latex)
    }

    @Test fun acceptsRawLatexAndStripsOnlyOuterDelimiters() {
        val result = ResponseParser.parse("""\[\sum_{i=1}^n i\]""")
        assertEquals("\\sum_{i=1}^n i", result.latex)
        assertNull(result.confidence)
        assertTrue(result.warnings.single().contains("schema"))
    }

    @Test fun completeObjectScannerHandlesBracesInsideStrings() {
        val text = """prefix {"latex":"\\text{a { b }}","confidence":1,"alternatives":[],"warnings":[]} suffix"""
        assertTrue(ResponseParser.firstCompleteJsonObject(text)!!.contains("a { b }"))
    }
}
