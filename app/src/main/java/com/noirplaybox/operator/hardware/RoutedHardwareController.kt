package com.noirplaybox.operator.hardware

import android.content.Context
import com.noirplaybox.operator.model.HardwareSnapshot
import com.noirplaybox.operator.model.HardwareTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class RoutedHardwareController(
    context: Context,
    private val localController: HardwareController,
    private val cloudController: HardwareController
) : HardwareController {
    private val store = TinyTuyaSecureStore(context.applicationContext)

    override val transport: HardwareTransport =
        HardwareTransport.LOCAL_TINYTUYA

    override suspend fun readAll(
        deviceIds: List<String>
    ): Map<String, HardwareSnapshot> = coroutineScope {
        val normalized = deviceIds
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()

        val localTargets = normalized.filter(::isLocalDevice)
        val cloudTargets = normalized.filterNot(::isLocalDevice)

        val localDeferred = async {
            if (localTargets.isEmpty()) emptyMap()
            else localController.readAll(localTargets)
        }

        val cloudDeferred = async {
            if (cloudTargets.isEmpty()) emptyMap()
            else cloudController.readAll(cloudTargets)
        }

        buildMap {
            putAll(cloudDeferred.await())
            putAll(localDeferred.await())
        }
    }

    override suspend fun readFast(
        deviceIds: List<String>
    ): Map<String, HardwareSnapshot> {
        val localTargets = deviceIds
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() && isLocalDevice(it) }
            .distinct()

        return if (localTargets.isEmpty()) emptyMap() else localController.readFast(localTargets)
    }

    override suspend fun monitorOn(deviceId: String) {
        controllerFor(deviceId).monitorOn(deviceId)
    }

    override suspend fun monitorStop(deviceId: String) {
        controllerFor(deviceId).monitorStop(deviceId)
    }

    override suspend fun startRentalTimer(
        deviceId: String,
        durationMinutes: Int
    ) {
        controllerFor(deviceId).startRentalTimer(deviceId, durationMinutes)
    }

    override suspend fun addRentalTime(
        deviceId: String,
        durationMinutes: Int,
        currentCountdownSeconds: Int
    ) {
        controllerFor(deviceId).addRentalTime(
            deviceId = deviceId,
            durationMinutes = durationMinutes,
            currentCountdownSeconds = currentCountdownSeconds
        )
    }

    fun isLocalDevice(deviceId: String): Boolean {
        return store.has(deviceId.trim().uppercase())
    }

    private fun controllerFor(deviceId: String): HardwareController {
        return if (isLocalDevice(deviceId)) localController else cloudController
    }
}
