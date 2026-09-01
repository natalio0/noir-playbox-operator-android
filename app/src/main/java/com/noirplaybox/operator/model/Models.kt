package com.noirplaybox.operator.model

import com.noirplaybox.operator.util.NoirServerClock

enum class DeviceState {
    READY,
    PREPARING,
    ACTIVE,
    SHUTDOWN,
    OFFLINE
}

enum class HardwareStatus {
    ON,
    OFF,
    OFFLINE,
    UNKNOWN
}

enum class HardwareTransport {
    TRANSITIONAL_TUYA_CLOUD,
    LOCAL_TINYTUYA
}

enum class PreparingRiskLevel {
    NORMAL,
    WARNING,
    SUSPICIOUS
}

data class OperatorSession(
    val uid: String,
    val displayName: String,
    val email: String,
    val role: String,
    val cafeId: String,
    val cafeName: String
)

data class RegistryDevice(
    val id: String,
    val name: String,
    val cafeId: String,
    val cafeName: String?,
    val brand: String?,
    val model: String?,
    val type: String?
)

data class HardwareSnapshot(
    val status: HardwareStatus = HardwareStatus.UNKNOWN,
    val online: Boolean = false,
    val switchOn: Boolean = false,
    val countdownSeconds: Int = 0,
    val powerW: Double = 0.0,
    val currentMa: Double = 0.0,
    val voltageV: Double = 0.0,
    val updatedAtEpochMs: Long? = null,
    val error: String? = null,
    val transport: HardwareTransport = HardwareTransport.TRANSITIONAL_TUYA_CLOUD
)

data class ActiveRentalSession(
    val id: String,
    val deviceId: String,
    val startedAtEpochMs: Long?,
    val totalMinutes: Int,
    val totalPrice: Int
) {
    fun remainingSeconds(nowMs: Long = NoirServerClock.nowEpochMs()): Int {
        val started = startedAtEpochMs ?: return 0
        val endAt = started + totalMinutes.coerceAtLeast(0) * 60_000L
        val totalSeconds = totalMinutes.coerceAtLeast(0).toLong() * 60L
        return ((endAt - nowMs + 999L) / 1000L)
            .coerceAtLeast(0L)
            .coerceAtMost(totalSeconds)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun isExpired(nowMs: Long = NoirServerClock.nowEpochMs()): Boolean {
        if (startedAtEpochMs == null || totalMinutes <= 0) return false
        return remainingSeconds(nowMs) <= 0
    }
}

data class PreparingRuntime(
    val id: String,
    val startedAtEpochMs: Long?
) {
    fun elapsedMinutes(nowMs: Long = NoirServerClock.nowEpochMs()): Int {
        val started = startedAtEpochMs ?: return 0
        return ((nowMs - started).coerceAtLeast(0L) / 60_000L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun risk(nowMs: Long = NoirServerClock.nowEpochMs()): PreparingRiskLevel = when {
        elapsedMinutes(nowMs) >= 60 -> PreparingRiskLevel.SUSPICIOUS
        elapsedMinutes(nowMs) >= 45 -> PreparingRiskLevel.WARNING
        else -> PreparingRiskLevel.NORMAL
    }
}

data class ShutdownRuntime(
    val id: String,
    val status: String,
    val startedAtEpochMs: Long?,
    val sourceSessionId: String?
) {
    fun elapsedMinutes(nowMs: Long = NoirServerClock.nowEpochMs()): Int {
        val started = startedAtEpochMs ?: return 0
        return ((nowMs - started).coerceAtLeast(0L) / 60_000L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }
}

data class BusinessRuntime(
    val session: ActiveRentalSession? = null,
    val preparing: PreparingRuntime? = null,
    val shutdown: ShutdownRuntime? = null
)

data class PlayboxDevice(
    val id: String,
    val name: String,
    val cafeId: String,
    val state: DeviceState,
    val connected: Boolean,
    val connectionLabel: String,
    val remainingSeconds: Int = 0,
    val preparingMinutes: Int = 0,
    val hardware: HardwareSnapshot? = null,
    val session: ActiveRentalSession? = null,
    val preparing: PreparingRuntime? = null,
    val shutdown: ShutdownRuntime? = null,
    val cafeName: String? = null,
    val brand: String? = null,
    val model: String? = null
)

data class RentalPackage(
    val id: String,
    val label: String,
    val durationMinutes: Int,
    val price: Int
)

data class SessionPackage(
    val id: String,
    val packageId: String?,
    val name: String,
    val durationMinutes: Int,
    val price: Int,
    val type: String,
    val addedAtEpochMs: Long?
)

data class LifecycleActionResult(
    val message: String,
    val warning: String? = null
)
