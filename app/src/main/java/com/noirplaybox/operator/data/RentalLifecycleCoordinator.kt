package com.noirplaybox.operator.data

import com.noirplaybox.operator.hardware.HardwareController
import com.noirplaybox.operator.model.ActiveRentalSession
import com.noirplaybox.operator.model.LifecycleActionResult
import com.noirplaybox.operator.model.PreparingRuntime
import com.noirplaybox.operator.model.RentalPackage
import com.noirplaybox.operator.model.ShutdownRuntime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrator lifecycle. Urutan safety mengikuti web production:
 * - PREPARING: hardware ON + Firebase PREPARING paralel dengan rollback.
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
    suspend fun prepare(deviceId: String): Pair<PreparingRuntime, LifecycleActionResult> = coroutineScope {
        val hardwareJob = async { capture { hardware.monitorOn(deviceId) } }
        val preparingJob = async { capture { backend.startPreparing(deviceId) } }

        val hardwareResult = hardwareJob.await()
        val preparingResult = preparingJob.await()

        if (hardwareResult.isFailure) {
            if (preparingResult.isSuccess) {
                runCatchingSuspend { backend.endPreparing(preparingResult.getOrThrow().id) }
            }
            throw hardwareResult.exceptionOrNull()
                ?: IllegalStateException("Monitor gagal dinyalakan.")
        }

        if (preparingResult.isFailure) {
            runCatchingSuspend { hardware.monitorStop(deviceId) }
            throw preparingResult.exceptionOrNull()
                ?: IllegalStateException("PREPARING gagal dibuat.")
        }

        preparingResult.getOrThrow() to LifecycleActionResult(
            message = "PREPARING aktif. Pilih paket saat unit siap."
        )
    }

    suspend fun cancelPreparing(
        deviceId: String,
        preparingId: String
    ): LifecycleActionResult {
        // Sama seperti web: monitor dimatikan lebih dulu, lalu PREPARING ditutup.
        hardware.monitorStop(deviceId)
        backend.endPreparing(preparingId)
        return LifecycleActionResult("PREPARING dibatalkan. Monitor telah dimatikan.")
    }

    suspend fun startRental(
        deviceId: String,
        preparingId: String?,
        rentalPackage: RentalPackage
    ): LifecycleActionResult {
        hardware.startRentalTimer(deviceId, rentalPackage.durationMinutes)

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
            message = "Billing ${rentalPackage.label} berhasil dimulai."
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
        // Web menutup audit hanya setelah monitor berhasil dimatikan.
        hardware.monitorStop(deviceId)
        backend.completeShutdown(shutdownId)
        return LifecycleActionResult(
            "Shutdown selesai. Monitor OFF dan kabel power utama dapat dicabut."
        )
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun <T> runCatchingSuspend(block: suspend () -> T): Result<T> = capture(block)
}
