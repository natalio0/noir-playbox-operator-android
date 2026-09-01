package com.noirplaybox.operator.data

import android.util.Log
import org.json.JSONObject

/** Best-effort production telemetry. Failures must never block rental lifecycle. */
class OperatorTelemetryRepository(
    private val api: NoirApiClient
) {
    suspend fun audit(
        action: String,
        deviceId: String?,
        status: String,
        message: String? = null,
        metadata: JSONObject? = null
    ) {
        runCatching {
            api.request(
                path = "/api/operator/audit",
                method = "POST",
                body = JSONObject()
                    .put("action", action)
                    .put("deviceId", deviceId ?: JSONObject.NULL)
                    .put("status", status)
                    .put("message", message ?: JSONObject.NULL)
                    .put("metadata", metadata ?: JSONObject())
            )
        }.onFailure { Log.w("NoirTelemetry", "Audit gagal: $action", it) }
    }

    suspend fun incident(
        type: String,
        deviceId: String,
        sessionId: String?,
        message: String
    ) {
        runCatching {
            api.request(
                path = "/api/operator/incidents",
                method = "POST",
                body = JSONObject()
                    .put("type", type)
                    .put("deviceId", deviceId)
                    .put("sessionId", sessionId ?: JSONObject.NULL)
                    .put("message", message)
            )
        }.onFailure { Log.w("NoirTelemetry", "Incident gagal: $type", it) }
    }
}
