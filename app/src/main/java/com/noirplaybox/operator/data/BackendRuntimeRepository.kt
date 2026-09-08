package com.noirplaybox.operator.data

import com.noirplaybox.operator.model.ActiveRentalSession
import com.noirplaybox.operator.model.BusinessRuntime
import com.noirplaybox.operator.model.PreparingRuntime
import com.noirplaybox.operator.model.RegistryDevice
import com.noirplaybox.operator.model.ShutdownRuntime
import org.json.JSONObject
import java.time.Instant

data class OperatorOverviewSnapshot(
    val registry: List<RegistryDevice>?,
    val business: Map<String, BusinessRuntime>
)

class BackendRuntimeRepository(
    private val api: NoirApiClient
) {
    /**
     * v3.9.2: one HTTP request returns lifecycle state for the whole cafe.
     * Registry metadata is requested only at boot / periodic registry refresh.
     */
    suspend fun loadOverview(includeRegistry: Boolean): OperatorOverviewSnapshot {
        val suffix = if (includeRegistry) "?includeRegistry=1" else ""
        val root = api.get("/api/operator/overview$suffix")

        val registry = if (includeRegistry) {
            val devices = root.optJSONArray("devices")
            buildList {
                if (devices != null) {
                    for (index in 0 until devices.length()) {
                        val item = devices.optJSONObject(index) ?: continue
                        val id = item.optString("deviceId", item.optString("id"))
                            .trim()
                            .uppercase()
                        if (id.isBlank()) continue
                        add(
                            RegistryDevice(
                                id = id,
                                name = item.optString("name").ifBlank { id },
                                cafeId = item.optString("cafeId"),
                                cafeName = item.nullableString("cafeName"),
                                brand = item.nullableString("brand"),
                                model = item.nullableString("model"),
                                type = item.nullableString("type")
                            )
                        )
                    }
                }
            }.sortedBy { it.id }
        } else {
            null
        }

        val business = mutableMapOf<String, BusinessRuntime>()
        val runtimes = root.optJSONArray("runtimes")
        if (runtimes != null) {
            for (index in 0 until runtimes.length()) {
                val item = runtimes.optJSONObject(index) ?: continue
                val id = item.optString("deviceId").trim().uppercase()
                if (id.isBlank()) continue
                business[id] = BusinessRuntime(
                    session = item.optJSONObject("session")?.let(::parseSession),
                    preparing = item.optJSONObject("preparing")?.let(::parsePreparing),
                    shutdown = item.optJSONObject("shutdown")?.let(::parseShutdown)
                )
            }
        }

        return OperatorOverviewSnapshot(registry = registry, business = business)
    }

    private fun parseSession(item: JSONObject): ActiveRentalSession? {
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

    private fun parsePreparing(item: JSONObject): PreparingRuntime? {
        val id = item.optString("id")
        if (id.isBlank()) return null
        return PreparingRuntime(
            id = id,
            startedAtEpochMs = parseIso(item.nullableString("startedAt"))
        )
    }

    private fun parseShutdown(item: JSONObject): ShutdownRuntime? {
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
