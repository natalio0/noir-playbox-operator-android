package com.noirplaybox.operator.data

import com.noirplaybox.operator.model.ActiveRentalSession
import com.noirplaybox.operator.model.BusinessRuntime
import com.noirplaybox.operator.model.PreparingRuntime
import com.noirplaybox.operator.model.RegistryDevice
import com.noirplaybox.operator.model.ShutdownRuntime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

class BackendRuntimeRepository(
    private val api: NoirApiClient
) {
    suspend fun loadRegistry(): List<RegistryDevice> {
        val data = api.get("/api/devices")
        val devices = data.optJSONArray("devices") ?: return emptyList()
        val result = mutableListOf<RegistryDevice>()

        for (index in 0 until devices.length()) {
            val item = devices.optJSONObject(index) ?: continue
            val id = item.optString("deviceId", item.optString("id"))
                .trim()
                .uppercase()
            if (id.isBlank()) continue

            result += RegistryDevice(
                id = id,
                name = item.optString("name").ifBlank { id },
                cafeId = item.optString("cafeId"),
                cafeName = item.nullableString("cafeName"),
                brand = item.nullableString("brand"),
                model = item.nullableString("model"),
                type = item.nullableString("type")
            )
        }

        return result.sortedBy { it.id }
    }

    suspend fun loadAllBusinessRuntime(
        deviceIds: List<String>
    ): Map<String, BusinessRuntime> = coroutineScope {
        deviceIds.map { rawId ->
            async {
                val id = rawId.uppercase()
                id to loadBusinessRuntime(id)
            }
        }.awaitAll().toMap()
    }

    suspend fun loadBusinessRuntime(deviceId: String): BusinessRuntime = coroutineScope {
        val encoded = URLEncoder.encode(deviceId, StandardCharsets.UTF_8.toString())

        val sessionDeferred = async {
            api.get("/api/sessions/active?deviceId=$encoded")
        }
        val preparingDeferred = async {
            api.get("/api/preparing/active?deviceId=$encoded")
        }
        val shutdownDeferred = async {
            api.get("/api/shutdown/active?deviceId=$encoded")
        }

        val sessionJson = sessionDeferred.await()
        val preparingJson = preparingDeferred.await()
        val shutdownJson = shutdownDeferred.await()

        BusinessRuntime(
            session = parseSession(sessionJson),
            preparing = parsePreparing(preparingJson),
            shutdown = parseShutdown(shutdownJson)
        )
    }

    private fun parseSession(root: JSONObject): ActiveRentalSession? {
        if (!root.optBoolean("active")) return null
        val item = root.optJSONObject("session") ?: return null
        val id = item.optString("id")
        if (id.isBlank()) return null

        return ActiveRentalSession(
            id = id,
            deviceId = item.optString("deviceId").uppercase(),
            startedAtEpochMs = parseIso(item.nullableString("startedAt")),
            totalMinutes = item.optInt("totalMinutes", 0).coerceAtLeast(0),
            totalPrice = item.optInt("totalPrice", 0).coerceAtLeast(0)
        )
    }

    private fun parsePreparing(root: JSONObject): PreparingRuntime? {
        if (!root.optBoolean("active")) return null
        val item = root.optJSONObject("preparing") ?: return null
        val id = item.optString("id")
        if (id.isBlank()) return null

        return PreparingRuntime(
            id = id,
            startedAtEpochMs = parseIso(item.nullableString("startedAt"))
        )
    }

    private fun parseShutdown(root: JSONObject): ShutdownRuntime? {
        if (!root.optBoolean("active")) return null
        val item = root.optJSONObject("shutdown") ?: return null
        val id = item.optString("id")
        if (id.isBlank()) return null

        return ShutdownRuntime(
            id = id,
            status = item.optString("status").ifBlank { "SHUTDOWN_PENDING" },
            startedAtEpochMs = parseIso(item.nullableString("startedAt")),
            sourceSessionId = item.nullableString("sourceSessionId")
        )
    }
}

internal fun JSONObject.nullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().ifBlank { null }
}

internal fun parseIso(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
