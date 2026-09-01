package com.noirplaybox.operator.data

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Secure backend-only TinyTuya registry.
 *
 * Android never reads cafes/{cafeId}/tuyaDevices directly. This is intentional:
 * local_key is a secret and Firestore rules may keep that collection inaccessible
 * to mobile clients.
 *
 * Every lookup goes through POST /api/tuya/local-key. The backend performs the
 * fast Firestore lookup first and only calls Tuya Cloud when the key is missing.
 */
data class TuyaRegistryEntry(
    val tuyaDeviceId: String,
    val localKey: String,
    val logicalDeviceId: String?,
    val name: String?,
    val protocolVersion: String?,
    val switchDps: Int?
)

class TuyaDeviceRegistryRepository(
    @Suppress("UNUSED_PARAMETER") private val context: Context,
    private val api: NoirApiClient = NoirApiClient()
) {
    /**
     * Compatibility lookup used by the Devices UI.
     * It intentionally goes through Noir backend instead of Firestore SDK.
     */
    suspend fun find(cafeId: String, tuyaDeviceId: String): TuyaRegistryEntry? {
        if (cafeId.isBlank() || tuyaDeviceId.isBlank()) return null
        return resolveFromCloud(
            cafeId = cafeId,
            tuyaDeviceId = tuyaDeviceId,
            logicalDeviceId = null,
            protocolVersion = null,
            ipAddress = null
        )
    }

    /**
     * Backend contract:
     * POST /api/tuya/local-key
     * Authorization: Bearer <Firebase ID token>
     *
     * Backend behavior:
     * 1. validates operator + cafe ownership,
     * 2. checks Firestore registry server-side,
     * 3. if missing, requests local_key from Tuya Cloud,
     * 4. stores it server-side and returns only the matched device entry.
     */
    suspend fun resolveFromCloud(
        cafeId: String,
        tuyaDeviceId: String,
        logicalDeviceId: String?,
        protocolVersion: String?,
        ipAddress: String?
    ): TuyaRegistryEntry {
        require(cafeId.isNotBlank()) { "Cafe ID tidak tersedia." }
        require(tuyaDeviceId.isNotBlank()) { "Tuya Device ID kosong." }

        val body = JSONObject()
            .put("cafeId", cafeId)
            .put("tuyaDeviceId", tuyaDeviceId)

        logicalDeviceId?.takeIf { it.isNotBlank() }?.let { body.put("logicalDeviceId", it) }
        protocolVersion?.takeIf { it.isNotBlank() }?.let { body.put("protocolVersion", it) }
        ipAddress?.takeIf { it.isNotBlank() }?.let { body.put("ipAddress", it) }

        Log.d("NoirTuyaRegistry", "Backend registry lookup for $tuyaDeviceId / cafe=$cafeId")
        val response = try {
            api.request(
                path = "/api/tuya/local-key",
                method = "POST",
                body = body
            )
        } catch (error: Throwable) {
            Log.e("NoirTuyaRegistry", "Backend registry lookup failed for $tuyaDeviceId", error)
            throw error
        }

        val source = response.optString("source", "backend")
        Log.d("NoirTuyaRegistry", "Backend registry success for $tuyaDeviceId source=$source")

        val data = response.optJSONObject("device") ?: response.optJSONObject("result") ?: response
        val localKey = data.optString("localKey")
            .ifBlank { data.optString("local_key") }
            .ifBlank { throw IllegalStateException("Backend tidak mengembalikan local key.") }

        fun nullableText(key: String): String? {
            if (!data.has(key) || data.isNull(key)) return null
            return data.optString(key).trim().takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        }

        val resolvedProtocol = nullableText("protocolVersion")
            ?: protocolVersion?.trim()?.takeIf { it in setOf("3.1", "3.2", "3.3", "3.4", "3.5") }

        return TuyaRegistryEntry(
            tuyaDeviceId = nullableText("tuyaDeviceId") ?: tuyaDeviceId,
            localKey = localKey,
            logicalDeviceId = nullableText("logicalDeviceId") ?: logicalDeviceId,
            name = nullableText("name"),
            protocolVersion = resolvedProtocol,
            switchDps = when {
                data.has("switchDps") && !data.isNull("switchDps") -> data.optInt("switchDps", 1)
                else -> null
            }
        )
    }
}
