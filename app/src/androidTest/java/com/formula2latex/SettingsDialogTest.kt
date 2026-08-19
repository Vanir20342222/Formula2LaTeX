package com.formula2latex

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.formula2latex.data.settings.SettingsSnapshot
import com.formula2latex.ui.settings.SettingsDialog
import org.junit.Rule
import org.junit.Test

class SettingsDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customProviderKeepsManualModelEntryAvailable() {
        compose.setContent {
            MaterialTheme {
                SettingsDialog(
                    initial = SettingsSnapshot(),
                    models = emptyList(),
                    loading = false,
                    error = null,
                    canDismiss = false,
                    onRefresh = {},
                    onSave = {},
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithText("Base URL (HTTPS)").assertIsDisplayed()
        compose.onNodeWithText("Model ID (manual entry always available)").assertIsDisplayed()
    }
}
