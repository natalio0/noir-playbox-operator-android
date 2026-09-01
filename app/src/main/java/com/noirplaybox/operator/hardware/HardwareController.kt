package com.noirplaybox.operator.hardware

import com.noirplaybox.operator.model.HardwareSnapshot
import com.noirplaybox.operator.model.HardwareTransport

/**
 * Satu-satunya boundary hardware di APK.
 *
 * Billing / PREPARING / SHUTDOWN tidak boleh disimpan di implementasi ini.
 * Implementasi final akan menjadi TinyTuya LAN lokal.
 */
interface HardwareController {
    val transport: HardwareTransport

    suspend fun readAll(deviceIds: List<String>): Map<String, HardwareSnapshot>

    /**
     * Lightweight presence polling. Implementations may skip expensive/cloud targets.
     * Default keeps backward compatibility.
     */
    suspend fun readFast(deviceIds: List<String>): Map<String, HardwareSnapshot> = readAll(deviceIds)

    suspend fun monitorOn(deviceId: String)

    suspend fun monitorStop(deviceId: String)

    suspend fun startRentalTimer(deviceId: String, durationMinutes: Int)

    suspend fun addRentalTime(
        deviceId: String,
        durationMinutes: Int,
        currentCountdownSeconds: Int
    )
}
