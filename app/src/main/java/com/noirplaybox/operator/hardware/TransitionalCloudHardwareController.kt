package com.noirplaybox.operator.hardware

import com.noirplaybox.operator.data.NoirApiClient
import com.noirplaybox.operator.data.parseIso
import com.noirplaybox.operator.model.HardwareSnapshot
import com.noirplaybox.operator.model.HardwareStatus
import com.noirplaybox.operator.model.HardwareTransport
import org.json.JSONObject

/**
 * Bridge sementara ke route Tuya Cloud existing.
 *
 * Semua pemanggil memakai HardwareController, bukan route Tuya langsung.
 * Saat TinyTuya Android stabil, class ini dapat dibuang tanpa mengubah rental flow.
 */
class TransitionalCloudHardwareController(
    private val api: NoirApiClient
) : HardwareController {
    override val transport = HardwareTransport.TRANSITIONAL_TUYA_CLOUD

    override suspend fun readAll(
        deviceIds: List<String>
    ): Map<String, HardwareSnapshot> {
        if (deviceIds.isEmpty()) return emptyMap()

        val data = api.get("/api/realtime/overview")
        val devices = data.optJSONArray("devices") ?: return emptyMap()
        val allowed = deviceIds.map { it.uppercase() }.toSet()
        val result = mutableMapOf<String, HardwareSnapshot>()

        for (index in 0 until devices.length()) {
            val item = devices.optJSONObject(index) ?: continue
            val id = item.optString("id").trim().uppercase()
            if (id.isBlank() || id !in allowed) continue

            val status = when (item.optString("status").uppercase()) {
                "ON" -> HardwareStatus.ON
                "OFF" -> HardwareStatus.OFF
                "OFFLINE" -> HardwareStatus.OFFLINE
                else -> HardwareStatus.UNKNOWN
            }

            val state = item.optJSONObject("state")
            val online = item.optBoolean("online", status != HardwareStatus.OFFLINE)

            result[id] = HardwareSnapshot(
                status = status,
                online = online,
                switchOn = state?.optBoolean("switch", status == HardwareStatus.ON)
                    ?: (status == HardwareStatus.ON),
                countdownSeconds = state?.optInt("countdown", 0)?.coerceAtLeast(0) ?: 0,
                powerW = state.number("power"),
                currentMa = state.number("current"),
                // Tuya web source memakai voltage x10.
                voltageV = state.number("voltage") / 10.0,
                updatedAtEpochMs = parseIso(item.optString("updatedAt")),
                error = item.optString("error").ifBlank { null },
                transport = transport
            )
        }

        return result
    }

    override suspend fun monitorOn(deviceId: String) {
        command(deviceId, "ON")
    }

    override suspend fun monitorStop(deviceId: String) {
        command(deviceId, "STOP")
    }

    override suspend fun startRentalTimer(deviceId: String, durationMinutes: Int) {
        command(
            deviceId = deviceId,
            action = "TIMER",
            durationMinutes = durationMinutes
        )
    }

    override suspend fun addRentalTime(
        deviceId: String,
        durationMinutes: Int,
        currentCountdownSeconds: Int
    ) {
        command(
            deviceId = deviceId,
            action = "ADD_TIME",
            durationMinutes = durationMinutes,
            currentCountdownSeconds = currentCountdownSeconds
        )
    }

    private suspend fun command(
        deviceId: String,
        action: String,
        durationMinutes: Int? = null,
        currentCountdownSeconds: Int? = null
    ) {
        val body = JSONObject()
            .put("deviceId", deviceId.uppercase())
            .put("action", action)

        if (durationMinutes != null) {
            body.put("durationMinutes", durationMinutes)
        }

        if (currentCountdownSeconds != null) {
            body.put("currentCountdown", currentCountdownSeconds)
        }

        api.request(
            path = "/api/tuya/control",
            method = "POST",
            body = body
        )
    }
}

private fun JSONObject?.number(key: String): Double {
    if (this == null) return 0.0
    val value = opt(key)
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}
