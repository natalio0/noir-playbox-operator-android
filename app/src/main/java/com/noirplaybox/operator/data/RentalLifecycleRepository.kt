package com.noirplaybox.operator.data

import com.noirplaybox.operator.model.ActiveRentalSession
import com.noirplaybox.operator.model.PreparingRuntime
import com.noirplaybox.operator.model.RentalPackage
import com.noirplaybox.operator.model.SessionPackage
import com.noirplaybox.operator.model.ShutdownRuntime
import org.json.JSONObject

class RentalLifecycleRepository(
    private val api: NoirApiClient
) {
    suspend fun startPreparing(deviceId: String): PreparingRuntime {
        val data = api.request(
            path = "/api/preparing/start",
            method = "POST",
            body = JSONObject().put("deviceId", deviceId.uppercase())
        )
        val preparing = data.optJSONObject("preparing")
            ?: throw IllegalStateException("Response PREPARING tidak lengkap.")

        return PreparingRuntime(
            id = preparing.optString("id"),
            startedAtEpochMs = parseIso(preparing.nullableString("startedAt"))
        )
    }

    suspend fun endPreparing(preparingId: String) {
        api.request(
            path = "/api/preparing/$preparingId/end",
            method = "PATCH"
        )
    }

    suspend fun createSession(
        deviceId: String,
        preparingId: String?,
        rentalPackage: RentalPackage
    ): CreatedSessionResult {
        val body = JSONObject()
            .put("deviceId", deviceId.uppercase())
            .put("preparingId", preparingId ?: JSONObject.NULL)
            .put("packageId", rentalPackage.id)
            .put("durationMinutes", rentalPackage.durationMinutes)
            .put("packageName", rentalPackage.label)
            .put("price", rentalPackage.price)

        val data = api.request(
            path = "/api/sessions",
            method = "POST",
            body = body
        )

        val sessionJson = data.optJSONObject("session")
            ?: throw IllegalStateException("Response session tidak lengkap.")

        return CreatedSessionResult(
            session = parseSession(sessionJson),
            packageItem = data.optJSONObject("package")?.let(::parseSessionPackage),
            preparingConverted = data.optBoolean("preparingConverted")
        )
    }

    suspend fun addPackage(
        sessionId: String,
        deviceId: String,
        rentalPackage: RentalPackage
    ): AddedPackageResult {
        val body = JSONObject()
            .put("deviceId", deviceId.uppercase())
            .put("packageId", rentalPackage.id)
            .put("name", rentalPackage.label)
            .put("durationMinutes", rentalPackage.durationMinutes)
            .put("price", rentalPackage.price)

        val data = api.request(
            path = "/api/sessions/$sessionId/packages",
            method = "POST",
            body = body
        )

        val session = data.optJSONObject("session")
            ?: throw IllegalStateException("Response ADD TIME tidak lengkap.")
        val packageJson = data.optJSONObject("package")
            ?: throw IllegalStateException("Package ADD TIME tidak ditemukan.")

        return AddedPackageResult(
            totalMinutes = session.optInt("totalMinutes", 0),
            totalPrice = session.optInt("totalPrice", 0),
            packageItem = parseSessionPackage(packageJson)
        )
    }

    suspend fun completeSession(
        sessionId: String,
        deviceId: String
    ): CompletedSessionResult {
        val data = api.request(
            path = "/api/sessions/$sessionId/complete",
            method = "PATCH",
            body = JSONObject().put("deviceId", deviceId.uppercase())
        )

        return CompletedSessionResult(
            shutdown = data.optJSONObject("shutdown")?.let(::parseShutdown),
            totalMinutes = data.optInt("totalMinutes", 0),
            totalPrice = data.optInt("totalPrice", 0)
        )
    }

    suspend fun startShutdown(
        deviceId: String,
        sourceSessionId: String?
    ): ShutdownRuntime {
        val data = api.request(
            path = "/api/shutdown/start",
            method = "POST",
            body = JSONObject()
                .put("deviceId", deviceId.uppercase())
                .put("sourceSessionId", sourceSessionId ?: JSONObject.NULL)
        )

        val shutdown = data.optJSONObject("shutdown")
            ?: throw IllegalStateException("Response Shutdown Mode tidak lengkap.")

        return parseShutdown(shutdown)
    }

    suspend fun completeShutdown(shutdownId: String) {
        api.request(
            path = "/api/shutdown/$shutdownId/complete",
            method = "PATCH"
        )
    }

    private fun parseSession(item: JSONObject): ActiveRentalSession = ActiveRentalSession(
        id = item.optString("id"),
        deviceId = item.optString("deviceId").uppercase(),
        startedAtEpochMs = parseIso(item.nullableString("startedAt")),
        totalMinutes = item.optInt("totalMinutes", 0).coerceAtLeast(0),
        totalPrice = item.optInt("totalPrice", 0).coerceAtLeast(0)
    )

    private fun parseSessionPackage(item: JSONObject): SessionPackage = SessionPackage(
        id = item.optString("id"),
        packageId = item.nullableString("packageId"),
        name = item.optString("name"),
        durationMinutes = item.optInt("durationMinutes", 0).coerceAtLeast(0),
        price = item.optInt("price", 0).coerceAtLeast(0),
        type = item.optString("type").ifBlank { "UNKNOWN" },
        addedAtEpochMs = parseIso(item.nullableString("addedAt"))
    )

    private fun parseShutdown(item: JSONObject): ShutdownRuntime = ShutdownRuntime(
        id = item.optString("id"),
        status = item.optString("status").ifBlank { "SHUTDOWN_PENDING" },
        startedAtEpochMs = parseIso(item.nullableString("startedAt")),
        sourceSessionId = item.nullableString("sourceSessionId")
    )
}

data class CreatedSessionResult(
    val session: ActiveRentalSession,
    val packageItem: SessionPackage?,
    val preparingConverted: Boolean
)

data class AddedPackageResult(
    val totalMinutes: Int,
    val totalPrice: Int,
    val packageItem: SessionPackage
)

data class CompletedSessionResult(
    val shutdown: ShutdownRuntime?,
    val totalMinutes: Int,
    val totalPrice: Int
)
