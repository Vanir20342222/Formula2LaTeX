package com.formula2latex.data.settings

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.formula2latex.data.security.EncryptedSecret
import com.formula2latex.data.security.SecretCipher
import com.formula2latex.domain.model.ProviderConfig
import com.formula2latex.domain.model.ProviderKind
import kotlinx.coroutines.flow.first

private val Context.formulaSettings by preferencesDataStore(name = "formula_settings")

enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED"),
}

data class SettingsSnapshot(
    val provider: ProviderKind = ProviderKind.GEMINI,
    val baseUrl: String = ProviderConfig.defaultBaseUrl(ProviderKind.GEMINI),
    val modelId: String = "",
    val apiKey: String = "",
    val saveKey: Boolean = false,
    val privacyAccepted: Boolean = false,
    val theme: ThemePreference = ThemePreference.SYSTEM,
) {
    val configured: Boolean
        get() = modelId.isNotBlank() && (provider == ProviderKind.CUSTOM || apiKey.isNotBlank())

    fun providerConfig() = ProviderConfig(provider, apiKey, baseUrl)

    override fun toString(): String =
        "SettingsSnapshot(provider=$provider, baseUrl=$baseUrl, modelId=$modelId, apiKey=<redacted>, saveKey=$saveKey, privacyAccepted=$privacyAccepted, theme=$theme)"
}

class SettingsRepository(
    private val context: Context,
    private val cipher: SecretCipher,
) {
    @Volatile private var sessionKey: CharArray? = null

    suspend fun load(): SettingsSnapshot {
        val values = context.formulaSettings.data.first()
        val provider = runCatching {
            ProviderKind.valueOf(values[Keys.provider] ?: ProviderKind.GEMINI.name)
        }.getOrDefault(ProviderKind.GEMINI)
        val saved = values[Keys.saveKey] == true
        val theme = runCatching {
            ThemePreference.valueOf(values[Keys.theme] ?: ThemePreference.SYSTEM.name)
        }.getOrDefault(ThemePreference.SYSTEM)
        val key = when {
            saved -> decryptSaved(values[Keys.ciphertext], values[Keys.iv])
            else -> sessionKey?.concatToString().orEmpty()
        }
        return SettingsSnapshot(
            provider = provider,
            baseUrl = values[Keys.baseUrl] ?: ProviderConfig.defaultBaseUrl(provider),
            modelId = values[Keys.modelId].orEmpty(),
            apiKey = key,
            saveKey = saved,
            privacyAccepted = values[Keys.privacyAccepted] == true,
            theme = theme,
        )
    }

    suspend fun save(snapshot: SettingsSnapshot) {
        require(snapshot.baseUrl.startsWith("https://")) { "The endpoint must use HTTPS." }
        sessionKey?.fill('\u0000')
        sessionKey = null
        var encrypted: EncryptedSecret? = null
        if (snapshot.saveKey && snapshot.apiKey.isNotEmpty()) {
            encrypted = cipher.encrypt(ALIAS, snapshot.apiKey.toCharArray())
        } else if (snapshot.apiKey.isNotEmpty()) {
            sessionKey = snapshot.apiKey.toCharArray()
            cipher.delete(ALIAS)
        } else {
            cipher.delete(ALIAS)
        }
        context.formulaSettings.edit { values ->
            values[Keys.provider] = snapshot.provider.name
            values[Keys.baseUrl] = snapshot.baseUrl.trimEnd('/')
            values[Keys.modelId] = snapshot.modelId.trim()
            values[Keys.saveKey] = snapshot.saveKey && snapshot.apiKey.isNotEmpty()
            values[Keys.privacyAccepted] = true
            values[Keys.theme] = snapshot.theme.name
            if (encrypted != null) {
                values[Keys.ciphertext] = Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP)
                values[Keys.iv] = Base64.encodeToString(encrypted.iv, Base64.NO_WRAP)
            } else {
                values.remove(Keys.ciphertext)
                values.remove(Keys.iv)
            }
        }
    }

    suspend fun delete() {
        sessionKey?.fill('\u0000')
        sessionKey = null
        cipher.delete(ALIAS)
        context.formulaSettings.edit { values ->
            values.remove(Keys.provider)
            values.remove(Keys.baseUrl)
            values.remove(Keys.ciphertext)
            values.remove(Keys.iv)
            values.remove(Keys.modelId)
            values[Keys.saveKey] = false
        }
    }

    suspend fun acceptPrivacy() {
        context.formulaSettings.edit { it[Keys.privacyAccepted] = true }
    }

    private fun decryptSaved(ciphertext: String?, iv: String?): String {
        if (ciphertext.isNullOrBlank() || iv.isNullOrBlank()) return ""
        return runCatching {
            val chars = cipher.decrypt(ALIAS, EncryptedSecret(
                Base64.decode(ciphertext, Base64.NO_WRAP),
                Base64.decode(iv, Base64.NO_WRAP),
            ))
            try { chars.concatToString() } finally { chars.fill('\u0000') }
        }.getOrDefault("")
    }

    private object Keys {
        val provider = stringPreferencesKey("provider")
        val baseUrl = stringPreferencesKey("base_url")
        val modelId = stringPreferencesKey("model_id")
        val saveKey = booleanPreferencesKey("save_key")
        val ciphertext = stringPreferencesKey("key_ciphertext")
        val iv = stringPreferencesKey("key_iv")
        val privacyAccepted = booleanPreferencesKey("privacy_accepted")
        val theme = stringPreferencesKey("theme")
    }

    companion object {
        private const val ALIAS = "formula2latex_provider_key_v1"
    }
}
