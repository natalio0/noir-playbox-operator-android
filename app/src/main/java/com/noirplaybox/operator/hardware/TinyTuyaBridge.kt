package com.noirplaybox.operator.hardware

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class TinyTuyaPilotResult(
    val ok: Boolean,
    val switchOn: Boolean?,
    val countdownSeconds: Int,
    val currentMa: Double,
    val powerW: Double,
    val voltageV: Double,
    val dpsText: String,
    val rawText: String,
    val error: String?
)

class TinyTuyaBridge(
    private val context: Context
) {
    suspend fun libraryInfo(): String = withContext(Dispatchers.IO) {
        call("library_info")
    }

    suspend fun status(config: TinyTuyaLocalConfig): TinyTuyaPilotResult {
        return withContext(Dispatchers.IO) {
            parse(
                call(
                    "status_json",
                    config.tuyaDeviceId,
                    config.ipAddress,
                    config.localKey,
                    config.protocolVersion,
                    config.switchDps
                )
            )
        }
    }

    suspend fun setPower(
        config: TinyTuyaLocalConfig,
        on: Boolean
    ): TinyTuyaPilotResult {
        return withContext(Dispatchers.IO) {
            parse(
                call(
                    "set_power_json",
                    config.tuyaDeviceId,
                    config.ipAddress,
                    config.localKey,
                    config.protocolVersion,
                    config.switchDps,
                    on
                )
            )
        }
    }

    private fun call(
        function: String,
        vararg args: Any
    ): String {
        ensurePythonStarted()
        return Python.getInstance()
            .getModule("noir_tinytuya_bridge")
            .callAttr(function, *args)
            .toString()
    }

    private fun ensurePythonStarted() {
        synchronized(PYTHON_LOCK) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context.applicationContext))
            }
        }
    }

    private fun parse(raw: String): TinyTuyaPilotResult {
        return runCatching {
            val json = JSONObject(raw)
            val ok = json.optBoolean("ok", false)
            val dps = json.optJSONObject("dps")

            val switchOn = if (json.isNull("switch")) {
                null
            } else {
                json.optBoolean("switch")
            }

            TinyTuyaPilotResult(
                ok = ok,
                switchOn = switchOn,
                countdownSeconds = dps.number("9")
                    .toInt()
                    .coerceAtLeast(0),
                currentMa = dps.number("18"),
                powerW = dps.number("19") / 10.0,
                voltageV = dps.number("20") / 10.0,
                dpsText = prettyObject(dps),
                rawText = json.optJSONObject("raw")?.toString(2)
                    ?: json.toString(2),
                error = json.optString("error").ifBlank { null }
            )
        }.getOrElse { error ->
            TinyTuyaPilotResult(
                ok = false,
                switchOn = null,
                countdownSeconds = 0,
                currentMa = 0.0,
                powerW = 0.0,
                voltageV = 0.0,
                dpsText = "",
                rawText = raw,
                error = error.message ?: "Response TinyTuya tidak valid."
            )
        }
    }

    private fun prettyObject(obj: JSONObject?): String {
        if (obj == null) return "(DPS kosong)"

        val keys = mutableListOf<String>()
        val iterator = obj.keys()
        while (iterator.hasNext()) {
            keys += iterator.next()
        }

        return keys
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
            .joinToString("\n") { key ->
                "$key = ${obj.opt(key)}"
            }
            .ifBlank { "(DPS kosong)" }
    }

    private fun JSONObject?.number(key: String): Double {
        if (this == null) return 0.0
        return when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private companion object {
        val PYTHON_LOCK = Any()
    }
}
