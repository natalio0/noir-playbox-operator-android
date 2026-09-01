package com.noirplaybox.operator.data

import android.content.Context
import com.noirplaybox.operator.model.ActiveRentalSession
import com.noirplaybox.operator.model.DeviceState
import com.noirplaybox.operator.model.HardwareSnapshot
import com.noirplaybox.operator.model.HardwareStatus
import com.noirplaybox.operator.model.HardwareTransport
import com.noirplaybox.operator.model.PlayboxDevice
import com.noirplaybox.operator.model.PreparingRuntime
import com.noirplaybox.operator.model.ShutdownRuntime
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small last-known overview cache used only to make Home paint immediately after
 * Firebase restores the operator session. Backend refresh remains source of truth.
 */
class OverviewCacheStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "noir_overview_cache_v1",
        Context.MODE_PRIVATE
    )

    fun save(cafeId: String, devices: List<PlayboxDevice>) {
        val array = JSONArray()
        devices.forEach { device -> array.put(device.toJson()) }
        prefs.edit()
            .putString("cafe_id", cafeId)
            .putString("devices", array.toString())
            .putLong("saved_at", System.currentTimeMillis())
            .apply()
    }

    fun load(cafeId: String): List<PlayboxDevice> {
        if (prefs.getString("cafe_id", null) != cafeId) return emptyList()
        val raw = prefs.getString("devices", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    item.toPlayboxDevice()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

private fun PlayboxDevice.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("cafeId", cafeId)
    put("state", state.name)
    put("connected", connected)
    put("connectionLabel", connectionLabel)
    put("remainingSeconds", remainingSeconds)
    put("preparingMinutes", preparingMinutes)
    put("cafeName", cafeName ?: JSONObject.NULL)
    put("brand", brand ?: JSONObject.NULL)
    put("model", model ?: JSONObject.NULL)

    hardware?.let { snapshot ->
        put("hardware", JSONObject().apply {
            put("status", snapshot.status.name)
            put("online", snapshot.online)
            put("switchOn", snapshot.switchOn)
            put("countdownSeconds", snapshot.countdownSeconds)
            put("powerW", snapshot.powerW)
            put("currentMa", snapshot.currentMa)
            put("voltageV", snapshot.voltageV)
            put("updatedAtEpochMs", snapshot.updatedAtEpochMs ?: JSONObject.NULL)
            put("error", snapshot.error ?: JSONObject.NULL)
            put("transport", snapshot.transport.name)
        })
    }

    session?.let { active ->
        put("session", JSONObject().apply {
            put("id", active.id)
            put("deviceId", active.deviceId)
            put("startedAtEpochMs", active.startedAtEpochMs ?: JSONObject.NULL)
            put("totalMinutes", active.totalMinutes)
            put("totalPrice", active.totalPrice)
        })
    }

    preparing?.let { runtime ->
        put("preparing", JSONObject().apply {
            put("id", runtime.id)
            put("startedAtEpochMs", runtime.startedAtEpochMs ?: JSONObject.NULL)
        })
    }

    shutdown?.let { runtime ->
        put("shutdown", JSONObject().apply {
            put("id", runtime.id)
            put("status", runtime.status)
            put("startedAtEpochMs", runtime.startedAtEpochMs ?: JSONObject.NULL)
            put("sourceSessionId", runtime.sourceSessionId ?: JSONObject.NULL)
        })
    }
}

private fun JSONObject.toPlayboxDevice(): PlayboxDevice? {
    val id = optString("id").trim().uppercase()
    if (id.isBlank()) return null

    val hardwareJson = optJSONObject("hardware")
    val hardware = hardwareJson?.let { item ->
        HardwareSnapshot(
            status = enumValueOrDefault(item.optString("status"), HardwareStatus.UNKNOWN),
            online = item.optBoolean("online", false),
            switchOn = item.optBoolean("switchOn", false),
            countdownSeconds = item.optInt("countdownSeconds", 0),
            powerW = item.optDouble("powerW", 0.0),
            currentMa = item.optDouble("currentMa", 0.0),
            voltageV = item.optDouble("voltageV", 0.0),
            updatedAtEpochMs = item.nullableLong("updatedAtEpochMs"),
            error = item.nullableText("error"),
            transport = enumValueOrDefault(
                item.optString("transport"),
                HardwareTransport.TRANSITIONAL_TUYA_CLOUD
            )
        )
    }

    val session = optJSONObject("session")?.let { item ->
        ActiveRentalSession(
            id = item.optString("id"),
            deviceId = item.optString("deviceId").ifBlank { id },
            startedAtEpochMs = item.nullableLong("startedAtEpochMs"),
            totalMinutes = item.optInt("totalMinutes", 0),
            totalPrice = item.optInt("totalPrice", 0)
        )
    }

    val preparing = optJSONObject("preparing")?.let { item ->
        PreparingRuntime(
            id = item.optString("id"),
            startedAtEpochMs = item.nullableLong("startedAtEpochMs")
        )
    }

    val shutdown = optJSONObject("shutdown")?.let { item ->
        ShutdownRuntime(
            id = item.optString("id"),
            status = item.optString("status"),
            startedAtEpochMs = item.nullableLong("startedAtEpochMs"),
            sourceSessionId = item.nullableText("sourceSessionId")
        )
    }

    return PlayboxDevice(
        id = id,
        name = optString("name").ifBlank { id },
        cafeId = optString("cafeId"),
        state = enumValueOrDefault(optString("state"), DeviceState.READY),
        connected = optBoolean("connected", hardware?.online == true),
        connectionLabel = optString("connectionLabel").ifBlank { "Last known" },
        remainingSeconds = optInt("remainingSeconds", 0),
        preparingMinutes = optInt("preparingMinutes", 0),
        hardware = hardware,
        session = session,
        preparing = preparing,
        shutdown = shutdown,
        cafeName = nullableText("cafeName"),
        brand = nullableText("brand"),
        model = nullableText("model")
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T {
    return enumValues<T>().firstOrNull { it.name == value } ?: fallback
}

private fun JSONObject.nullableText(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().ifBlank { null }
}

private fun JSONObject.nullableLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return optLong(key)
}
