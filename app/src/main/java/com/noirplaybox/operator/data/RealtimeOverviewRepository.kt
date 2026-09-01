package com.noirplaybox.operator.data

import com.noirplaybox.operator.hardware.HardwareController
import com.noirplaybox.operator.model.BusinessRuntime
import com.noirplaybox.operator.model.DeviceState
import com.noirplaybox.operator.model.HardwareSnapshot
import com.noirplaybox.operator.model.HardwareStatus
import com.noirplaybox.operator.model.PlayboxDevice
import com.noirplaybox.operator.model.RegistryDevice
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class RealtimeOverviewRepository(
    private val backend: BackendRuntimeRepository,
    private val hardwareController: HardwareController
) {
    suspend fun refresh(
        previous: List<PlayboxDevice>,
        refreshHardware: Boolean
    ): List<PlayboxDevice> = coroutineScope {
        val registry = backend.loadRegistry()
        val ids = registry.map { it.id }

        val businessDeferred = async {
            backend.loadAllBusinessRuntime(ids)
        }

        val hardwareDeferred = async {
            if (refreshHardware) {
                hardwareController.readAll(ids)
            } else {
                previous.associate { it.id to (it.hardware ?: HardwareSnapshot()) }
            }
        }

        val business = businessDeferred.await()
        val hardware = hardwareDeferred.await()

        registry.map { registered ->
            merge(
                registered = registered,
                runtime = business[registered.id] ?: BusinessRuntime(),
                hardware = hardware[registered.id]
            )
        }
    }

    private fun merge(
        registered: RegistryDevice,
        runtime: BusinessRuntime,
        hardware: HardwareSnapshot?
    ): PlayboxDevice {
        /*
         * Prioritas lifecycle mengikuti source of truth bisnis:
         * ACTIVE > SHUTDOWN > PREPARING > READY.
         * Hardware OFFLINE tidak boleh menghapus billing/session Firebase.
         */
        val state = when {
            runtime.session != null -> DeviceState.ACTIVE
            runtime.shutdown != null -> DeviceState.SHUTDOWN
            runtime.preparing != null -> DeviceState.PREPARING
            hardware?.status == HardwareStatus.OFFLINE -> DeviceState.OFFLINE
            else -> DeviceState.READY
        }

        val remaining = runtime.session?.remainingSeconds() ?: 0
        val preparingMinutes = runtime.preparing?.elapsedMinutes() ?: 0

        return PlayboxDevice(
            id = registered.id,
            name = registered.name,
            cafeId = registered.cafeId,
            state = state,
            connected = hardware?.online == true,
            connectionLabel = when (hardware?.transport) {
                com.noirplaybox.operator.model.HardwareTransport.LOCAL_TINYTUYA -> "TinyTuya LAN"
                com.noirplaybox.operator.model.HardwareTransport.TRANSITIONAL_TUYA_CLOUD -> "Tuya Cloud · transition"
                null -> "Hardware pending"
            },
            remainingSeconds = remaining,
            preparingMinutes = preparingMinutes,
            hardware = hardware,
            session = runtime.session,
            preparing = runtime.preparing,
            shutdown = runtime.shutdown,
            cafeName = registered.cafeName,
            brand = registered.brand,
            model = registered.model
        )
    }
}
