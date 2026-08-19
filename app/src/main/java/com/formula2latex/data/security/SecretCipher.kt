package com.formula2latex.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedSecret(val ciphertext: ByteArray, val iv: ByteArray)

interface SecretCipher {
    fun encrypt(alias: String, plaintext: CharArray): EncryptedSecret
    fun decrypt(alias: String, secret: EncryptedSecret): CharArray
    fun delete(alias: String)
}

class AndroidKeystoreSecretCipher : SecretCipher {
    private val keyStore: KeyStore
        get() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    override fun encrypt(alias: String, plaintext: CharArray): EncryptedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val bytes = plaintext.concatToString().toByteArray(Charsets.UTF_8)
        return try {
            EncryptedSecret(cipher.doFinal(bytes), cipher.iv)
        } finally {
            bytes.fill(0)
            plaintext.fill('\u0000')
        }
    }

    override fun decrypt(alias: String, secret: EncryptedSecret): CharArray {
        val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Saved key is unavailable.")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, entry.secretKey, GCMParameterSpec(128, secret.iv))
        val decoded = cipher.doFinal(secret.ciphertext)
        return try {
            decoded.toString(Charsets.UTF_8).toCharArray()
        } finally {
            decoded.fill(0)
        }
    }

    override fun delete(alias: String) {
        val store = keyStore
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
