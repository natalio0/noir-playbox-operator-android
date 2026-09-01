package com.noirplaybox.operator.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.noirplaybox.operator.data.BackendRuntimeRepository
import com.noirplaybox.operator.data.FirebaseOperationalRepository
import com.noirplaybox.operator.data.NoirApiClient
import com.noirplaybox.operator.data.RealtimeOverviewRepository
import com.noirplaybox.operator.data.RentalLifecycleCoordinator
import com.noirplaybox.operator.data.RentalLifecycleRepository
import com.noirplaybox.operator.hardware.LocalTinyTuyaHardwareController
import com.noirplaybox.operator.hardware.RoutedHardwareController
import com.noirplaybox.operator.hardware.TransitionalCloudHardwareController
import com.noirplaybox.operator.model.LifecycleActionResult
import com.noirplaybox.operator.model.OperatorSession
import com.noirplaybox.operator.model.PlayboxDevice
import com.noirplaybox.operator.model.RentalPackage
import com.noirplaybox.operator.ui.screens.DashboardScreen
import com.noirplaybox.operator.ui.screens.DeviceDetailScreen
import com.noirplaybox.operator.ui.screens.LoginScreen
import com.noirplaybox.operator.util.NoirServerClock
import com.noirplaybox.operator.ui.screens.TinyTuyaPilotScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Screen {
    data object Login : Screen
    data object Dashboard : Screen
    data class DeviceDetail(val deviceId: String) : Screen
    data class TinyTuyaPilot(val deviceId: String) : Screen
}

private const val BUSINESS_REFRESH_MS = 15_000L
private const val TRANSITIONAL_CLOUD_HARDWARE_REFRESH_MS = 15 * 60_000L

@Composable
fun NoirPlayboxApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authRepository = remember(context) {
        FirebaseOperationalRepository(context.applicationContext)
    }

    val api = remember { NoirApiClient() }

    // V5 Local Fleet:
    // device dengan encrypted TinyTuya config -> LAN local
    // device tanpa config -> transitional cloud selama migrasi
    val hardwareController = remember(context, api) {
        RoutedHardwareController(
            context = context.applicationContext,
            localController = LocalTinyTuyaHardwareController(
                context.applicationContext
            ),
            cloudController = TransitionalCloudHardwareController(api)
        )
    }
    val overviewRepository = remember {
        RealtimeOverviewRepository(
            backend = BackendRuntimeRepository(api),
            hardwareController = hardwareController
        )
    }
    val lifecycle = remember {
        RentalLifecycleCoordinator(
            backend = RentalLifecycleRepository(api),
            hardware = hardwareController
        )
    }
    val refreshMutex = remember { Mutex() }
    val expiryInFlight = remember { mutableSetOf<String>() }

    var screen by remember { mutableStateOf<Screen>(Screen.Login) }
    var session by remember { mutableStateOf<OperatorSession?>(null) }
    var devices by remember { mutableStateOf<List<PlayboxDevice>>(emptyList()) }

    var bootChecked by remember { mutableStateOf(false) }
    var loginLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var deviceLoading by remember { mutableStateOf(false) }
    var deviceError by remember { mutableStateOf<String?>(null) }
    var lastSyncedAt by remember { mutableStateOf<Long?>(null) }

    var detailActionLoading by remember { mutableStateOf(false) }
    var detailMessage by remember { mutableStateOf<String?>(null) }
    var detailWarning by remember { mutableStateOf<String?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }

    suspend fun refreshOverviewNow(refreshHardware: Boolean) {
        refreshMutex.withLock {
            deviceLoading = true
            deviceError = null

            try {
                devices = overviewRepository.refresh(
                    previous = devices,
                    refreshHardware = refreshHardware
                )
                lastSyncedAt = System.currentTimeMillis()
            } catch (error: Throwable) {
                deviceError = error.message ?: "Gagal memuat realtime overview."
                if (screen is Screen.DeviceDetail) {
                    detailError = deviceError
                }
            } finally {
                deviceLoading = false
            }
        }
    }

    fun refreshOverview(refreshHardware: Boolean) {
        scope.launch { refreshOverviewNow(refreshHardware) }
    }

    fun runDetailAction(block: suspend () -> LifecycleActionResult) {
        if (detailActionLoading) return

        scope.launch {
            detailActionLoading = true
            detailError = null
            detailMessage = null
            detailWarning = null

            try {
                val result = block()
                detailMessage = result.message
                detailWarning = result.warning
                refreshOverviewNow(refreshHardware = true)
            } catch (error: Throwable) {
                detailError = error.message ?: "Action gagal."
                // Business state tetap direstore setelah error agar UI tidak menebak.
                runCatching { refreshOverviewNow(refreshHardware = true) }
            } finally {
                detailActionLoading = false
            }
        }
    }

    LaunchedEffect(authRepository.firebaseConfigured) {
        if (bootChecked) return@LaunchedEffect

        if (!authRepository.firebaseConfigured) {
            bootChecked = true
            return@LaunchedEffect
        }

        loginLoading = true
        authRepository.restoreSession { result ->
            loginLoading = false
            bootChecked = true

            result
                .onSuccess { restored ->
                    if (restored != null) {
                        session = restored
                        screen = Screen.Dashboard
                        refreshOverview(refreshHardware = true)
                    }
                }
                .onFailure {
                    screen = Screen.Login
                }
        }
    }

    // Business state tetap hidup di dashboard maupun detail.
    LaunchedEffect(session?.uid, screen) {
        if (session == null || screen is Screen.Login) return@LaunchedEffect

        while (true) {
            delay(BUSINESS_REFRESH_MS)
            refreshOverview(refreshHardware = false)
        }
    }

    // V5: each device becomes local automatically after TinyTuya config is saved.
    LaunchedEffect(session?.uid, screen) {
        if (session == null || screen is Screen.Login) return@LaunchedEffect

        while (true) {
            delay(TRANSITIONAL_CLOUD_HARDWARE_REFRESH_MS)
            refreshOverview(refreshHardware = true)
        }
    }

    // Countdown / PREPARING clock lokal + auto-complete ketika billing expired.
    LaunchedEffect(session?.uid) {
        if (session == null) return@LaunchedEffect

        while (true) {
            delay(1_000L)
            val now = NoirServerClock.nowEpochMs()
            val expired = mutableListOf<Pair<String, com.noirplaybox.operator.model.ActiveRentalSession>>()

            devices = devices.map { device ->
                val active = device.session
                val remaining = active?.remainingSeconds(now) ?: 0
                if (active != null && active.isExpired(now)) {
                    expired += device.id to active
                }

                device.copy(
                    remainingSeconds = remaining,
                    preparingMinutes = device.preparing?.elapsedMinutes(now) ?: 0
                )
            }

            expired.forEach { (deviceId, active) ->
                if (!expiryInFlight.add(active.id)) return@forEach

                scope.launch {
                    try {
                        val result = lifecycle.stopRental(deviceId, active)
                        detailMessage = "Waktu rental habis. ${result.message}"
                        detailWarning = result.warning
                        refreshOverviewNow(refreshHardware = true)
                    } catch (error: Throwable) {
                        detailError = error.message ?: "Gagal auto-stop session expired."
                    } finally {
                        expiryInFlight.remove(active.id)
                    }
                }
            }
        }
    }

    when (val current = screen) {
        Screen.Login -> {
            LoginScreen(
                firebaseConfigured = authRepository.firebaseConfigured,
                isLoading = loginLoading,
                error = loginError,
                onLogin = { email, password ->
                    loginLoading = true
                    loginError = null

                    authRepository.login(email, password) { result ->
                        loginLoading = false

                        result
                            .onSuccess { newSession ->
                                session = newSession
                                screen = Screen.Dashboard
                                refreshOverview(refreshHardware = true)
                            }
                            .onFailure { error ->
                                loginError = error.message ?: "Login gagal."
                            }
                    }
                }
            )
        }

        Screen.Dashboard -> {
            val activeSession = session
            if (activeSession == null) {
                screen = Screen.Login
                return
            }

            DashboardScreen(
                session = activeSession,
                devices = devices,
                isLoading = deviceLoading,
                error = deviceError,
                lastSyncedText = formatLastSynced(lastSyncedAt),
                onRefresh = { refreshOverview(refreshHardware = true) },
                onDeviceClick = { device ->
                    detailMessage = null
                    detailWarning = null
                    detailError = null
                    screen = Screen.DeviceDetail(device.id)
                },
                onLogout = {
                    authRepository.logout()
                    session = null
                    devices = emptyList()
                    loginError = null
                    deviceError = null
                    detailMessage = null
                    detailWarning = null
                    detailError = null
                    lastSyncedAt = null
                    screen = Screen.Login
                }
            )
        }

        is Screen.DeviceDetail -> {
            val device = devices.firstOrNull { it.id == current.deviceId }
            if (device == null) {
                screen = Screen.Dashboard
                return
            }

            DeviceDetailScreen(
                device = device,
                packages = authRepository.packages(),
                actionLoading = detailActionLoading,
                message = detailMessage,
                warning = detailWarning,
                error = detailError,
                onBack = { screen = Screen.Dashboard },
                onRefresh = { refreshOverview(refreshHardware = true) },
                onPrepare = {
                    runDetailAction {
                        lifecycle.prepare(device.id).second
                    }
                },
                onCancelPreparing = {
                    val preparing = device.preparing
                    if (preparing == null) {
                        detailError = "PREPARING tidak ditemukan."
                    } else {
                        runDetailAction {
                            lifecycle.cancelPreparing(device.id, preparing.id)
                        }
                    }
                },
                onStartRental = { pkg: RentalPackage ->
                    runDetailAction {
                        lifecycle.startRental(
                            deviceId = device.id,
                            preparingId = device.preparing?.id,
                            rentalPackage = pkg
                        )
                    }
                },
                onAddTime = { pkg: RentalPackage ->
                    val active = device.session
                    if (active == null) {
                        detailError = "Session ACTIVE tidak ditemukan."
                    } else {
                        runDetailAction {
                            lifecycle.addTime(device.id, active, pkg)
                        }
                    }
                },
                onStopRental = {
                    val active = device.session
                    if (active == null) {
                        detailError = "Session ACTIVE tidak ditemukan."
                    } else {
                        runDetailAction {
                            lifecycle.stopRental(device.id, active)
                        }
                    }
                },
                onStartShutdown = {
                    val shutdown = device.shutdown
                    if (shutdown == null) {
                        detailError = "Shutdown pending tidak ditemukan."
                    } else {
                        runDetailAction {
                            lifecycle.startShutdown(device.id, shutdown)
                        }
                    }
                },
                onRetryShutdownMonitor = {
                    runDetailAction {
                        lifecycle.retryShutdownMonitor(device.id)
                    }
                },
                onFinishShutdown = {
                    val shutdown = device.shutdown
                    if (shutdown == null) {
                        detailError = "Shutdown Mode tidak ditemukan."
                    } else {
                        runDetailAction {
                            lifecycle.finishShutdown(device.id, shutdown.id)
                        }
                    }
                },
                onOpenLocalPilot = {
                    screen = Screen.TinyTuyaPilot(device.id)
                }
            )
        }

        is Screen.TinyTuyaPilot -> {
            TinyTuyaPilotScreen(
                logicalDeviceId = current.deviceId,
                onBack = {
                    screen = Screen.DeviceDetail(current.deviceId)
                }
            )
        }
    }
}

private fun formatLastSynced(epochMs: Long?): String {
    if (epochMs == null) return "Waiting for sync..."
    val formatter = SimpleDateFormat("HH:mm:ss", Locale("id", "ID"))
    return "Last synced ${formatter.format(Date(epochMs))}"
}
