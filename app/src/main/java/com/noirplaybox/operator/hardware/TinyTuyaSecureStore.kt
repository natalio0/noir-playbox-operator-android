package com.noirplaybox.operator.hardware

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TinyTuyaSecureStore(
    context: Context
) {
    private val prefs = context.getSharedPreferences(
        "noir_tinytuya_secure_v1",
        Context.MODE_PRIVATE
    )

    fun save(config: TinyTuyaLocalConfig) {
        val validation = config.validate()
        require(validation == null) { validation.orEmpty() }

        val json = JSONObject()
            .put("logicalDeviceId", config.logicalDeviceId.uppercase())
            .put("tuyaDeviceId", config.tuyaDeviceId)
            .put("ipAddress", config.ipAddress)
            .put("localKey", config.localKey)
            .put("protocolVersion", config.protocolVersion)
            .put("switchDps", config.switchDps)
            .toString()

        prefs.edit()
            .putString(keyFor(config.logicalDeviceId), encrypt(json))
            .apply()
    }

    fun load(logicalDeviceId: String): TinyTuyaLocalConfig? {
        val encrypted = prefs.getString(keyFor(logicalDeviceId), null) ?: return null

        return runCatching {
            val json = JSONObject(decrypt(encrypted))
            TinyTuyaLocalConfig(
                logicalDeviceId = json.optString("logicalDeviceId")
                    .ifBlank { logicalDeviceId.uppercase() },
                tuyaDeviceId = json.getString("tuyaDeviceId"),
                ipAddress = json.getString("ipAddress"),
                localKey = json.getString("localKey"),
                protocolVersion = json.optString("protocolVersion", "3.3"),
                switchDps = json.optInt("switchDps", 1)
            )
        }.getOrNull()
    }

    fun delete(logicalDeviceId: String) {
        prefs.edit().remove(keyFor(logicalDeviceId)).apply()
    }

    fun has(logicalDeviceId: String): Boolean {
        return prefs.contains(keyFor(logicalDeviceId))
    }

    private fun keyFor(logicalDeviceId: String): String {
        return "device_${logicalDeviceId.trim().uppercase()}"
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())

        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(
            cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )

        return "$iv.$encrypted"
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(".", limit = 2)
        require(parts.size == 2) { "Encrypted config invalid." }

        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, iv)
        )

        return String(
            cipher.doFinal(encrypted),
            StandardCharsets.UTF_8
        )
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "noir_playbox_tinytuya_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
