package com.noirplaybox.operator.hardware

import android.content.Context
import com.noirplaybox.operator.model.HardwareSnapshot
import com.noirplaybox.operator.model.HardwareStatus
import com.noirplaybox.operator.model.HardwareTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Production local hardware controller.
 *
 * All BARDI hardware status/control is LAN-only through TinyTuya.
 * Firebase/backend remains the billing/session source of truth.
 */
class LocalTinyTuyaHardwareController(
    context: Context
) : HardwareController {
    override val transport = HardwareTransport.LOCAL_TINYTUYA

    private val store = TinyTuyaSecureStore(context.applicationContext)
    private val bridge = TinyTuyaBridge(context.applicationContext)

    override suspend fun readAll(
        deviceIds: List<String>
    ): Map<String, HardwareSnapshot> = coroutineScope {
        deviceIds
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { id ->
                async { id to readOne(id) }
            }
            .awaitAll()
            .toMap()
    }

    private suspend fun readOne(id: String): HardwareSnapshot {
        val config = store.load(id)

        if (config == null) {
            return HardwareSnapshot(
                status = HardwareStatus.OFFLINE,
                online = false,
                updatedAtEpochMs = System.currentTimeMillis(),
                error = "LOCAL NOT READY · konfigurasi TinyTuya $id belum disimpan.",
                transport = transport
            )
        }

        val status = bridge.status(config)

        return if (status.ok) {
            HardwareSnapshot(
                status = when (status.switchOn) {
                    true -> HardwareStatus.ON
                    false -> HardwareStatus.OFF
                    else -> HardwareStatus.UNKNOWN
                },
                online = true,
                switchOn = status.switchOn == true,
                countdownSeconds = status.countdownSeconds,
                powerW = status.powerW,
                currentMa = status.currentMa,
                voltageV = status.voltageV,
                updatedAtEpochMs = System.currentTimeMillis(),
                error = null,
                transport = transport
            )
        } else {
            HardwareSnapshot(
                status = HardwareStatus.OFFLINE,
                online = false,
                updatedAtEpochMs = System.currentTimeMillis(),
                error = status.error ?: "TinyTuya LAN gagal.",
                transport = transport
            )
        }
    }

    override suspend fun monitorOn(deviceId: String) {
        setMonitor(deviceId, true)
    }

    override suspend fun monitorStop(deviceId: String) {
        setMonitor(deviceId, false)
    }

    override suspend fun startRentalTimer(
        deviceId: String,
        durationMinutes: Int
    ) {
        setMonitor(deviceId, true)
    }

    override suspend fun addRentalTime(
        deviceId: String,
        durationMinutes: Int,
        currentCountdownSeconds: Int
    ) {
        val config = config(deviceId)
        val status = bridge.status(config)

        if (!status.ok) {
            throw IllegalStateException(
                status.error ?: "Tidak dapat membaca monitor lokal."
            )
        }

        if (status.switchOn != true) {
            setMonitor(deviceId, true)
        }
    }

    private suspend fun setMonitor(
        deviceId: String,
        on: Boolean
    ) {
        val id = deviceId.trim().uppercase()
        val response = bridge.setPower(config(id), on)

        if (!response.ok) {
            throw IllegalStateException(
                response.error ?: "Command TinyTuya $id gagal."
            )
        }

        if (response.switchOn != null && response.switchOn != on) {
            throw IllegalStateException(
                "$id merespons, tetapi status relay belum sesuai command."
            )
        }
    }

    private fun config(deviceId: String): TinyTuyaLocalConfig {
        val id = deviceId.trim().uppercase()
        return store.load(id)
            ?: throw IllegalStateException(
                "LOCAL NOT READY · konfigurasi TinyTuya $id belum disimpan. " +
                    "Buka Local Setup pada detail device terlebih dahulu."
            )
    }
}
