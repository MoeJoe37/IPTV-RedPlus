package com.redplus.iptv.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager {
    private val alias = "redplus_session_key"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return "${cipher.iv.b64()}:${cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)).b64()}"
    }

    fun decrypt(payload: String): String {
        val parts = payload.split(":")
        if (parts.size != 2) return ""
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    companion object { private const val TRANSFORMATION = "AES/GCM/NoPadding" }
}
