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
    private var registryCache: List<RegistryDevice> = emptyList()
    private var registryLoadedAtMs: Long = 0L

    companion object {
        private const val REGISTRY_REFRESH_MS = 15 * 60_000L
    }

    suspend fun refresh(
        previous: List<PlayboxDevice>,
        refreshHardware: Boolean
    ): List<PlayboxDevice> = coroutineScope {
        val now = System.currentTimeMillis()
        val includeRegistry = registryCache.isEmpty() ||
            now - registryLoadedAtMs >= REGISTRY_REFRESH_MS

        val overviewDeferred = async { backend.loadOverview(includeRegistry) }

        val hardwareDeferred = async {
            if (refreshHardware) {
                val ids = if (previous.isNotEmpty()) {
                    previous.map { it.id }
                } else {
                    val overview = overviewDeferred.await()
                    val freshRegistry = overview.registry ?: registryCache
                    freshRegistry.map { it.id }
                }
                hardwareController.readAll(ids)
            } else {
                previous.associate { it.id to (it.hardware ?: HardwareSnapshot()) }
            }
        }

        val overview = overviewDeferred.await()
        overview.registry?.let { fresh ->
            registryCache = fresh
            registryLoadedAtMs = now
        }

        // A cache restored from disk can paint immediately. The repository still
        // requests registry on its first network refresh, so new/deleted devices
        // are reconciled without making registry reads every business cycle.
        val registry = registryCache.ifEmpty {
            previous.map { device ->
                RegistryDevice(
                    id = device.id,
                    name = device.name,
                    cafeId = device.cafeId,
                    cafeName = device.cafeName,
                    brand = device.brand,
                    model = device.model,
                    type = null
                )
            }
        }

        val hardware = hardwareDeferred.await()

        registry.map { registered ->
            merge(
                registered = registered,
                runtime = overview.business[registered.id] ?: BusinessRuntime(),
                hardware = hardware[registered.id]
            )
        }
    }

    suspend fun refreshHardwareOnly(
        previous: List<PlayboxDevice>,
        targetDeviceId: String? = null
    ): List<PlayboxDevice> = coroutineScope {
        if (previous.isEmpty()) return@coroutineScope previous

        val normalizedTarget = targetDeviceId?.trim()?.uppercase()?.ifBlank { null }
        val ids = previous
            .asSequence()
            .filter { normalizedTarget == null || it.id.uppercase() == normalizedTarget }
            .map { it.id }
            .toList()

        if (ids.isEmpty()) return@coroutineScope previous

        val hardware = hardwareController.readFast(ids)

        previous.map { device ->
            if (normalizedTarget != null && device.id.uppercase() != normalizedTarget) {
                return@map device
            }

            val snapshot = hardware[device.id] ?: device.hardware
            val state = when {
                device.session != null -> DeviceState.ACTIVE
                device.shutdown != null -> DeviceState.SHUTDOWN
                device.preparing != null -> DeviceState.PREPARING
                snapshot?.status == HardwareStatus.OFFLINE -> DeviceState.OFFLINE
                else -> DeviceState.READY
            }

            device.copy(
                state = state,
                connected = snapshot?.online == true,
                connectionLabel = when (snapshot?.transport) {
                    com.noirplaybox.operator.model.HardwareTransport.LOCAL_TINYTUYA -> "TinyTuya LAN"
                    com.noirplaybox.operator.model.HardwareTransport.TRANSITIONAL_TUYA_CLOUD -> "Tuya Cloud · transition"
                    null -> device.connectionLabel
                },
                hardware = snapshot
            )
        }
    }

    private fun merge(
        registered: RegistryDevice,
        runtime: BusinessRuntime,
        hardware: HardwareSnapshot?
    ): PlayboxDevice {
        val state = when {
            runtime.session != null -> DeviceState.ACTIVE
            runtime.shutdown != null -> DeviceState.SHUTDOWN
            runtime.preparing != null -> DeviceState.PREPARING
            hardware?.status == HardwareStatus.OFFLINE -> DeviceState.OFFLINE
            else -> DeviceState.READY
        }

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
            remainingSeconds = runtime.session?.remainingSeconds() ?: 0,
            preparingMinutes = runtime.preparing?.elapsedMinutes() ?: 0,
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
