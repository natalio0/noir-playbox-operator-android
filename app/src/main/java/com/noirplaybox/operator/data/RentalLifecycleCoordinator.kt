package com.noirplaybox.operator.data

import com.noirplaybox.operator.hardware.HardwareController
import com.noirplaybox.operator.model.ActiveRentalSession
import com.noirplaybox.operator.model.LifecycleActionResult
import com.noirplaybox.operator.model.PreparingRuntime
import com.noirplaybox.operator.model.RentalPackage
import com.noirplaybox.operator.model.ShutdownRuntime

/**
 * Orchestrator lifecycle. Urutan safety mengikuti web production:
 * - PREPARING: hardware wajib ON dan terverifikasi dulu, baru backend PREPARING dibuat.
 * - START RENTAL: hardware timer dulu, Firebase session kemudian; bila Firebase
 *   gagal, hardware STOP ditunggu.
 * - ADD TIME: Firebase lebih dulu; hardware sync boleh gagal sebagai warning.
 * - STOP: Firebase COMPLETE lebih dulu; hardware STOP best-effort.
 * - SHUTDOWN START: Firebase SHUTDOWN_ACTIVE lebih dulu, baru monitor ON.
 * - SHUTDOWN COMPLETE: monitor STOP lebih dulu, baru audit shutdown ditutup.
 */
class RentalLifecycleCoordinator(
    private val backend: RentalLifecycleRepository,
    private val hardware: HardwareController
) {
    suspend fun prepare(deviceId: String): Pair<PreparingRuntime, LifecycleActionResult> {
        // Safety invariant: jangan pernah mencatat PREPARING sebelum hardware benar-benar siap.
        hardware.monitorOn(deviceId)

        val snapshot = hardware.readAll(listOf(deviceId))[deviceId.trim().uppercase()]
            ?: throw IllegalStateException("Status hardware $deviceId tidak tersedia setelah command ON.")

        if (!snapshot.online || snapshot.switchOn != true) {
            runCatchingSuspend { hardware.monitorStop(deviceId) }
            throw IllegalStateException(
                snapshot.error ?: "$deviceId belum terkonfirmasi online dan ON. PREPARING tidak dibuat."
            )
        }

        val preparing = try {
            backend.startPreparing(deviceId)
        } catch (error: Throwable) {
            // Backend gagal setelah hardware ON: rollback relay agar tidak ada penggunaan tanpa lifecycle.
            runCatchingSuspend { hardware.monitorStop(deviceId) }
            throw error
        }

        return preparing to LifecycleActionResult(
            message = "PREPARING aktif. Hardware sudah online dan terkonfirmasi ON."
        )
    }

    suspend fun cancelPreparing(
        deviceId: String,
        preparingId: String
    ): LifecycleActionResult {
        // PREPARING harus selalu ditutup walaupun hardware sedang offline/tidak merespons.
        val hardwareStop = runCatchingSuspend { hardware.monitorStop(deviceId) }
        backend.endPreparing(preparingId)
        return LifecycleActionResult(
            message = "PREPARING dibatalkan.",
            warning = if (hardwareStop.isFailure) {
                "Record PREPARING sudah ditutup, tetapi monitor tidak dapat dikonfirmasi OFF karena device offline/tidak merespons."
            } else {
                null
            }
        )
    }

    suspend fun startRental(
        deviceId: String,
        preparingId: String?,
        rentalPackage: RentalPackage
    ): LifecycleActionResult {
        require(!preparingId.isNullOrBlank()) {
            "Rental hanya dapat dimulai dari PREPARING yang valid."
        }

        // Hardware-first: session billing tidak boleh dibuat sebelum relay benar-benar siap.
        hardware.startRentalTimer(deviceId, rentalPackage.durationMinutes)

        val snapshot = hardware.readAll(listOf(deviceId))[deviceId.trim().uppercase()]
            ?: run {
                runCatchingSuspend { hardware.monitorStop(deviceId) }
                throw IllegalStateException(
                    "Status hardware $deviceId tidak tersedia setelah timer rental dikirim. Billing belum dibuat."
                )
            }

        if (!snapshot.online || snapshot.switchOn != true) {
            runCatchingSuspend { hardware.monitorStop(deviceId) }
            throw IllegalStateException(
                snapshot.error ?: "$deviceId belum terkonfirmasi online dan ON. Billing belum dibuat."
            )
        }

        try {
            backend.createSession(
                deviceId = deviceId,
                preparingId = preparingId,
                rentalPackage = rentalPackage
            )
        } catch (error: Throwable) {
            val rollback = runCatchingSuspend { hardware.monitorStop(deviceId) }
            if (rollback.isFailure) {
                throw IllegalStateException(
                    "Billing gagal dibuat dan rollback hardware juga gagal. Cek monitor secara manual. ${error.message.orEmpty()}"
                )
            }
            throw error
        }

        return LifecycleActionResult(
            message = "Rental ${rentalPackage.label} aktif. Hardware terverifikasi ON dan billing berhasil dibuat."
        )
    }

    suspend fun addTime(
        deviceId: String,
        activeSession: ActiveRentalSession,
        rentalPackage: RentalPackage
    ): LifecycleActionResult {
        val currentCountdown = activeSession.remainingSeconds()

        // Firebase adalah source of truth: commit billing lebih dulu.
        backend.addPackage(
            sessionId = activeSession.id,
            deviceId = deviceId,
            rentalPackage = rentalPackage
        )

        val hardwareSync = runCatchingSuspend {
            hardware.addRentalTime(
                deviceId = deviceId,
                durationMinutes = rentalPackage.durationMinutes,
                currentCountdownSeconds = currentCountdown
            )
        }

        return LifecycleActionResult(
            message = "${rentalPackage.label} berhasil ditambahkan ke billing.",
            warning = if (hardwareSync.isFailure) {
                "Billing sudah bertambah, tetapi timer hardware belum tersinkron."
            } else null
        )
    }

    suspend fun stopRental(
        deviceId: String,
        activeSession: ActiveRentalSession
    ): LifecycleActionResult {
        // Wajib Firebase-first. COMPLETE sekaligus membuat SHUTDOWN_PENDING.
        backend.completeSession(activeSession.id, deviceId)

        val hardwareStop = runCatchingSuspend {
            hardware.monitorStop(deviceId)
        }

        return LifecycleActionResult(
            message = "Rental selesai. Shutdown pending sudah tercatat.",
            warning = if (hardwareStop.isFailure) {
                "Billing sudah selesai, tetapi monitor belum dapat dikonfirmasi OFF. Cek monitor secara manual."
            } else null
        )
    }

    suspend fun startShutdown(
        deviceId: String,
        shutdown: ShutdownRuntime
    ): LifecycleActionResult {
        // Persistent state lebih dulu agar refresh/logout tidak kehilangan mode.
        backend.startShutdown(deviceId, shutdown.sourceSessionId)

        val monitor = runCatchingSuspend {
            hardware.monitorOn(deviceId)
        }

        return LifecycleActionResult(
            message = "Shutdown Mode aktif. Matikan PS4 secara normal.",
            warning = if (monitor.isFailure) {
                "Shutdown Mode sudah tercatat, tetapi monitor gagal dinyalakan. Gunakan tombol NYALAKAN MONITOR LAGI."
            } else null
        )
    }

    suspend fun retryShutdownMonitor(deviceId: String): LifecycleActionResult {
        hardware.monitorOn(deviceId)
        return LifecycleActionResult("Monitor aktif kembali. Silakan matikan PS4 secara normal.")
    }

    suspend fun finishShutdown(
        deviceId: String,
        shutdownId: String
    ): LifecycleActionResult {
        // Jangan pernah mengembalikan unit ke READY sebelum relay benar-benar OFF.
        hardware.monitorStop(deviceId)

        val snapshot = hardware.readAll(listOf(deviceId))[deviceId.trim().uppercase()]
            ?: throw IllegalStateException(
                "Status hardware $deviceId tidak tersedia setelah command OFF. Shutdown belum diselesaikan."
            )

        if (snapshot.switchOn == true) {
            throw IllegalStateException(
                snapshot.error ?: "$deviceId masih terdeteksi ON. Shutdown belum diselesaikan dan unit belum READY."
            )
        }

        backend.completeShutdown(shutdownId)
        return LifecycleActionResult(
            "Shutdown selesai. Hardware terverifikasi OFF dan unit kembali READY."
        )
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun <T> runCatchingSuspend(block: suspend () -> T): Result<T> = capture(block)
}
