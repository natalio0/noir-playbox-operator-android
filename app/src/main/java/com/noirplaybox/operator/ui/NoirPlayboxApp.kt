package com.noirplaybox.operator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.noirplaybox.operator.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.noirplaybox.operator.data.BackendRuntimeRepository
import com.noirplaybox.operator.data.FirebaseOperationalRepository
import com.noirplaybox.operator.data.NoirApiClient
import com.noirplaybox.operator.data.OverviewCacheStore
import com.noirplaybox.operator.data.OperatorTelemetryRepository
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
import com.noirplaybox.operator.ui.screens.DeviceSetupScreen
import com.noirplaybox.operator.ui.screens.ProfileScreen
import com.noirplaybox.operator.ui.screens.LoginScreen
import com.noirplaybox.operator.util.NoirServerClock
import com.noirplaybox.operator.util.friendlyError
import com.noirplaybox.operator.ui.screens.TinyTuyaPilotScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Screen {
    data object Boot : Screen
    data object Login : Screen
    data object Dashboard : Screen
    data class DeviceDetail(val deviceId: String) : Screen
    data class TinyTuyaPilot(val deviceId: String) : Screen
}

private const val BUSINESS_REFRESH_MS = 15_000L
private const val LOCAL_HARDWARE_POLL_MS = 3_500L
private const val TRANSITIONAL_CLOUD_HARDWARE_REFRESH_MS = 15 * 60_000L
private const val OFFLINE_WATCHDOG_THRESHOLD = 2

@Composable
fun NoirPlayboxApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authRepository = remember(context) {
        FirebaseOperationalRepository(context.applicationContext)
    }

    val overviewCache = remember(context) {
        OverviewCacheStore(context.applicationContext)
    }

    val api = remember { NoirApiClient() }
    val telemetry = remember { OperatorTelemetryRepository(api) }

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
    val hardwareRefreshMutex = remember { Mutex() }
    val expiryInFlight = remember { mutableSetOf<String>() }
    val actionInFlight = remember { mutableSetOf<String>() }
    val offlineStreaks = remember { mutableMapOf<String, Int>() }
    val reportedOfflineSessions = remember { mutableSetOf<String>() }

    var screen by remember { mutableStateOf<Screen>(Screen.Boot) }
    var mainTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var session by remember { mutableStateOf<OperatorSession?>(null) }
    var devices by remember { mutableStateOf<List<PlayboxDevice>>(emptyList()) }

    var bootChecked by remember { mutableStateOf(false) }
    var loginLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var deviceLoading by remember { mutableStateOf(false) }
    var deviceError by remember { mutableStateOf<String?>(null) }
    var watchdogAlert by remember { mutableStateOf<String?>(null) }
    var lastSyncedAt by remember { mutableStateOf<Long?>(null) }

    var detailActionLoading by remember { mutableStateOf(false) }
    var detailMessage by remember { mutableStateOf<String?>(null) }
    var detailWarning by remember { mutableStateOf<String?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }

    fun canManageHardware(current: OperatorSession?): Boolean {
        val role = current?.role?.trim()?.lowercase().orEmpty()
        return role in setOf("operational", "admin", "super-admin", "super_admin")
    }

    suspend fun evaluateOfflineWatchdog(refreshed: List<PlayboxDevice>) {
        var activeOfflineAlert: String? = null
        refreshed.forEach { device ->
            val active = device.session ?: run {
                offlineStreaks.remove(device.id)
                return@forEach
            }
            val offline = device.hardware?.online == false || device.hardware?.status == com.noirplaybox.operator.model.HardwareStatus.OFFLINE
            if (!offline) {
                offlineStreaks.remove(device.id)
                return@forEach
            }
            val streak = (offlineStreaks[device.id] ?: 0) + 1
            offlineStreaks[device.id] = streak
            if (streak >= OFFLINE_WATCHDOG_THRESHOLD) {
                val warning = "${device.id} offline saat rental aktif. Billing tetap berjalan sampai operator menyelesaikan rental."
                activeOfflineAlert = warning
                if ((screen as? Screen.DeviceDetail)?.deviceId == device.id) detailWarning = warning
                if (reportedOfflineSessions.add(active.id)) {
                    telemetry.incident("ACTIVE_DEVICE_OFFLINE", device.id, active.id, warning)
                }
            }
        }
        watchdogAlert = activeOfflineAlert
    }

    suspend fun refreshOverviewNow(refreshHardware: Boolean) {
        refreshMutex.withLock {
            deviceLoading = true
            deviceError = null

            try {
                devices = overviewRepository.refresh(
                    previous = devices,
                    refreshHardware = refreshHardware
                )
                session?.let { overviewCache.save(it.cafeId, devices) }
                lastSyncedAt = System.currentTimeMillis()
            } catch (error: Throwable) {
                deviceError = friendlyError(error)
                if (screen is Screen.DeviceDetail) {
                    detailError = deviceError
                }
            } finally {
                deviceLoading = false
            }
        }
    }

    suspend fun refreshHardwareOnlyNow() {
        if (devices.isEmpty()) return
        hardwareRefreshMutex.withLock {
            runCatching { overviewRepository.refreshHardwareOnly(devices) }
                .onSuccess { refreshed ->
                    devices = refreshed
                    evaluateOfflineWatchdog(refreshed)
                    session?.let { overviewCache.save(it.cafeId, devices) }
                    lastSyncedAt = System.currentTimeMillis()
                }
                .onFailure { error ->
                    // Fast LAN polling must not replace business data with an error screen.
                    android.util.Log.w("NoirHardwarePoll", "Hardware refresh gagal", error)
                }
        }
    }

    fun refreshOverview(refreshHardware: Boolean) {
        scope.launch { refreshOverviewNow(refreshHardware) }
    }

    fun refreshHardwareFast() {
        scope.launch { refreshHardwareOnlyNow() }
    }

    fun runDetailAction(
        action: String,
        deviceId: String,
        block: suspend () -> LifecycleActionResult
    ) {
        val key = "$deviceId:$action"
        if (detailActionLoading || !actionInFlight.add(key)) return

        scope.launch {
            detailActionLoading = true
            detailError = null
            detailMessage = null
            detailWarning = null
            telemetry.audit(action, deviceId, "STARTED")

            try {
                val result = block()
                detailMessage = result.message
                detailWarning = result.warning
                telemetry.audit(action, deviceId, "SUCCESS", result.message)
                refreshOverviewNow(refreshHardware = true)
            } catch (error: Throwable) {
                val friendly = friendlyError(error)
                detailError = friendly
                telemetry.audit(action, deviceId, "FAILED", friendly)
                runCatching { refreshOverviewNow(refreshHardware = true) }
            } finally {
                detailActionLoading = false
                actionInFlight.remove(key)
            }
        }
    }

    val activity = context as? ComponentActivity
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && session != null && screen !is Screen.Login) {
                refreshOverview(refreshHardware = false)
                refreshHardwareFast()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(authRepository.firebaseConfigured) {
        if (bootChecked) return@LaunchedEffect

        if (!authRepository.firebaseConfigured) {
            bootChecked = true
            screen = Screen.Login
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
                        val cached = overviewCache.load(restored.cafeId)
                        if (cached.isNotEmpty()) {
                            devices = cached
                        }
                        screen = Screen.Dashboard

                        // Paint cached Home immediately, then reconcile business + hardware in background.
                        refreshOverview(refreshHardware = false)
                        refreshHardwareFast()
                    } else {
                        screen = Screen.Login
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

    // Fast LAN presence polling while Home is selected. Business state stays on its
    // own backend cadence; this loop only refreshes physical ON/OFF/OFFLINE state.
    LaunchedEffect(session?.uid, screen, mainTab) {
        if (session == null || screen !is Screen.Dashboard || mainTab != MainTab.DASHBOARD) {
            return@LaunchedEffect
        }

        refreshHardwareOnlyNow()
        while (true) {
            delay(LOCAL_HARDWARE_POLL_MS)
            refreshHardwareOnlyNow()
        }
    }

    // Android system Back mengikuti hierarchy navigasi aplikasi.
    // Root Home tetap memakai default system Back (keluar/minimize aplikasi).
    BackHandler(
        enabled = when (screen) {
            is Screen.DeviceDetail, is Screen.TinyTuyaPilot -> true
            Screen.Dashboard -> mainTab != MainTab.DASHBOARD
            Screen.Boot, Screen.Login -> false
        }
    ) {
        when (val current = screen) {
            is Screen.TinyTuyaPilot -> screen = Screen.DeviceDetail(current.deviceId)
            is Screen.DeviceDetail -> {
                screen = Screen.Dashboard
                mainTab = MainTab.DASHBOARD
            }
            Screen.Dashboard -> mainTab = MainTab.DASHBOARD
            Screen.Boot, Screen.Login -> Unit
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
                        detailError = friendlyError(error)
                    } finally {
                        expiryInFlight.remove(active.id)
                    }
                }
            }
        }
    }

    when (val current = screen) {
        Screen.Boot -> {
            NoirBootScreen()
        }

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

            fun logout() {
                authRepository.logout()
                overviewCache.clear()
                session = null
                devices = emptyList()
                loginError = null
                deviceError = null
                watchdogAlert = null
                detailMessage = null
                detailWarning = null
                detailError = null
                lastSyncedAt = null
                mainTab = MainTab.DASHBOARD
                screen = Screen.Login
            }

            Scaffold(
                bottomBar = {
                    AppBottomBar(
                        selected = mainTab,
                        allowDeviceSetup = canManageHardware(activeSession),
                        onSelect = { requested ->
                            mainTab = if (requested == MainTab.SETUP && !canManageHardware(activeSession)) MainTab.ACCOUNT else requested
                        }
                    )
                }
            ) { contentPadding ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.padding(contentPadding)
                ) {
                    when (mainTab) {
                        MainTab.DASHBOARD -> DashboardScreen(
                            session = activeSession,
                            devices = devices,
                            isLoading = deviceLoading,
                            error = watchdogAlert ?: deviceError,
                            lastSyncedText = formatLastSynced(lastSyncedAt),
                            onRefresh = { refreshOverview(refreshHardware = true) },
                            onDeviceClick = { device ->
                                detailMessage = null
                                detailWarning = null
                                detailError = null
                                screen = Screen.DeviceDetail(device.id)
                            },
                            onLogout = ::logout
                        )

                        MainTab.SETUP -> {
                            if (canManageHardware(activeSession)) {
                                DeviceSetupScreen(
                                    devices = devices,
                                    cafeId = activeSession.cafeId,
                                    onConfigurationSaved = { refreshOverview(refreshHardware = true) }
                                )
                            } else {
                                ProfileScreen(session = activeSession, onLogout = ::logout)
                            }
                        }

                        MainTab.ACCOUNT -> ProfileScreen(
                            session = activeSession,
                            onLogout = ::logout
                        )
                    }
                }
            }
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
                    runDetailAction("PREPARE", device.id) {
                        lifecycle.prepare(device.id).second
                    }
                },
                onCancelPreparing = {
                    val preparing = device.preparing
                    if (preparing == null) {
                        detailError = "PREPARING tidak ditemukan."
                    } else {
                        runDetailAction("CANCEL_PREPARING", device.id) {
                            lifecycle.cancelPreparing(device.id, preparing.id)
                        }
                    }
                },
                onStartRental = { pkg: RentalPackage ->
                    runDetailAction("START_RENTAL", device.id) {
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
                        runDetailAction("ADD_TIME", device.id) {
                            lifecycle.addTime(device.id, active, pkg)
                        }
                    }
                },
                onStopRental = {
                    val active = device.session
                    if (active == null) {
                        detailError = "Session ACTIVE tidak ditemukan."
                    } else {
                        runDetailAction("STOP_RENTAL", device.id) {
                            lifecycle.stopRental(device.id, active)
                        }
                    }
                },
                onStartShutdown = {
                    val shutdown = device.shutdown
                    if (shutdown == null) {
                        detailError = "Shutdown pending tidak ditemukan."
                    } else {
                        runDetailAction("START_SHUTDOWN", device.id) {
                            lifecycle.startShutdown(device.id, shutdown)
                        }
                    }
                },
                onRetryShutdownMonitor = {
                    runDetailAction("RETRY_SHUTDOWN_MONITOR", device.id) {
                        lifecycle.retryShutdownMonitor(device.id)
                    }
                },
                onFinishShutdown = {
                    val shutdown = device.shutdown
                    if (shutdown == null) {
                        detailError = "Shutdown Mode tidak ditemukan."
                    } else {
                        runDetailAction("FINISH_SHUTDOWN", device.id) {
                            lifecycle.finishShutdown(device.id, shutdown.id)
                        }
                    }
                },
                canOpenLocalPilot = canManageHardware(session),
                onOpenLocalPilot = {
                    if (canManageHardware(session)) {
                        screen = Screen.TinyTuyaPilot(device.id)
                    } else {
                        detailError = "Akun ini tidak memiliki izin untuk Advanced local setup."
                    }
                }
            )
        }

        is Screen.TinyTuyaPilot -> {
            if (!canManageHardware(session)) {
                screen = Screen.Dashboard
                return
            }
            TinyTuyaPilotScreen(
                logicalDeviceId = current.deviceId,
                onBack = {
                    screen = Screen.DeviceDetail(current.deviceId)
                }
            )
        }
    }
}

@Composable
private fun NoirBootScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.logo_noir_symbol),
            contentDescription = "Noir",
            modifier = Modifier.size(72.dp),
            contentScale = ContentScale.Fit
        )
    }
}

private fun formatLastSynced(epochMs: Long?): String {
    if (epochMs == null) return "Waiting for sync..."
    val formatter = SimpleDateFormat("HH:mm:ss", Locale("id", "ID"))
    return "Last synced ${formatter.format(Date(epochMs))}"
}
