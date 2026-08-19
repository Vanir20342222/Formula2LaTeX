package com.formula2latex.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.formula2latex.domain.model.FormulaResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResultCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resultCanBeEditedCopiedRetriedAndReplacedWithAlternative() {
        var edited = "x^2"
        var copied = false
        var retried = false
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                ResultCard(
                    result = FormulaResult(
                        latex = "x^2",
                        confidence = 0.8,
                        alternatives = listOf("x_2"),
                        warnings = listOf("Ambiguous position"),
                    ),
                    latex = edited,
                    onLatexChange = { edited = it },
                    onAlternative = { edited = it },
                    onRetry = { retried = true },
                    onDismiss = { dismissed = true },
                    onCopy = { copied = true },
                )
            }
        }

        compose.onNodeWithText("Confidence: 80%").assertIsDisplayed()
        compose.onNodeWithText("Warning: Ambiguous position").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("\\sqrt{x}")
        compose.runOnIdle { assertEquals("\\sqrt{x}", edited) }
        compose.onNodeWithText("x_2").performClick()
        compose.runOnIdle { assertEquals("x_2", edited) }
        compose.onNodeWithText("Copy LaTeX").performClick()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithContentDescription("Close result").performClick()
        compose.runOnIdle {
            assertTrue(copied)
            assertTrue(retried)
            assertTrue(dismissed)
        }
    }
}
