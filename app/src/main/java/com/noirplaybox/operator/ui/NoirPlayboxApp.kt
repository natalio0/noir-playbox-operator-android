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
import com.noirplaybox.operator.data.PaymentRepository
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
import com.noirplaybox.operator.ui.screens.PaymentScreen
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

private const val BUSINESS_REFRESH_MS = 60_000L
private const val BUSINESS_BACKOFF_MAX_MS = 5 * 60_000L
private const val LOCAL_HARDWARE_POLL_MS = 2_000L
private const val LOCAL_HARDWARE_RECOVERY_POLL_MS = 1_000L
private const val TRANSITIONAL_CLOUD_HARDWARE_REFRESH_MS = 15 * 60_000L
private const val OFFLINE_WATCHDOG_THRESHOLD = 2
private const val HARDWARE_UI_OFFLINE_THRESHOLD = 3

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
    val paymentRepository = remember { PaymentRepository(api) }

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
    val preparingWatchdogInFlight = remember { mutableSetOf<String>() }
    val actionInFlight = remember { mutableSetOf<String>() }
    val offlineStreaks = remember { mutableMapOf<String, Int>() }
    val hardwareUiOfflineStreaks = remember { mutableMapOf<String, Int>() }
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
    var businessRefreshDelayMs by remember { mutableStateOf(BUSINESS_REFRESH_MS) }

    var detailActionLoading by remember { mutableStateOf(false) }
    var detailManualRefreshing by remember { mutableStateOf(false) }
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
                businessRefreshDelayMs = BUSINESS_REFRESH_MS
            } catch (error: Throwable) {
                val raw = error.message.orEmpty()
                val pressured = raw.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                    raw.contains("batas penggunaan", ignoreCase = true) ||
                    raw.contains("quota", ignoreCase = true) ||
                    (error is com.noirplaybox.operator.data.ApiException && error.statusCode == 429)
                if (pressured) {
                    businessRefreshDelayMs = (businessRefreshDelayMs * 2)
                        .coerceAtLeast(60_000L)
                        .coerceAtMost(BUSINESS_BACKOFF_MAX_MS)
                }
                deviceError = friendlyError(error)
                if (screen is Screen.DeviceDetail) {
                    detailError = deviceError
                }
            } finally {
                deviceLoading = false
            }
        }
    }

    suspend fun refreshHardwareOnlyNow(targetDeviceId: String? = null) {
        if (devices.isEmpty()) return
        hardwareRefreshMutex.withLock {
            runCatching { overviewRepository.refreshHardwareOnly(devices, targetDeviceId) }
                .onSuccess { refreshed ->
                    val previousById = devices.associateBy { it.id.uppercase() }

                    val stabilized = refreshed.map { candidate ->
                        val key = candidate.id.uppercase()
                        val previous = previousById[key]

                        val isOfflineSample =
                            candidate.hardware?.online == false ||
                            candidate.hardware?.status == com.noirplaybox.operator.model.HardwareStatus.OFFLINE

                        if (!isOfflineSample) {
                            hardwareUiOfflineStreaks.remove(key)
                            candidate
                        } else {
                            val streak = (hardwareUiOfflineStreaks[key] ?: 0) + 1
                            hardwareUiOfflineStreaks[key] = streak

                            val previousWasHealthy =
                                previous != null &&
                                previous.hardware?.online != false &&
                                previous.hardware?.status != com.noirplaybox.operator.model.HardwareStatus.OFFLINE

                            if (previousWasHealthy && streak < HARDWARE_UI_OFFLINE_THRESHOLD) {
                                // Transient LAN miss: keep the last healthy hardware presentation.
                                // Business/runtime fields from candidate remain fresh.
                                candidate.copy(
                                    state = previous.state,
                                    connected = previous.connected,
                                    connectionLabel = previous.connectionLabel,
                                    hardware = previous.hardware
                                )
                            } else {
                                candidate
                            }
                        }
                    }

                    devices = stabilized
                    evaluateOfflineWatchdog(stabilized)
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

    fun refreshHardwareFast(targetDeviceId: String? = null) {
        scope.launch { refreshHardwareOnlyNow(targetDeviceId) }
    }

    fun refreshDeviceDetailNow(deviceId: String) {
        if (detailManualRefreshing) return
        scope.launch {
            detailManualRefreshing = true
            detailError = null
            try {
                // Hardware current device first so field feedback feels immediate.
                refreshHardwareOnlyNow(deviceId)
                // Business state follows without doing a fleet hardware refresh.
                refreshOverviewNow(refreshHardware = false)
                // One final local probe catches state changes during business sync.
                refreshHardwareOnlyNow(deviceId)
            } catch (error: Throwable) {
                detailError = friendlyError(error)
            } finally {
                detailManualRefreshing = false
            }
        }
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
            // Telemetry tidak boleh menambah latency pada tombol rental.
            scope.launch { telemetry.audit(action, deviceId, "STARTED") }

            try {
                val result = block()
                detailMessage = result.message
                detailWarning = result.warning

                if (
                    action == "ADD_TIME" &&
                    result.updatedTotalMinutes != null &&
                    result.updatedTotalPrice != null
                ) {
                    devices = devices.map { current ->
                        if (current.id.equals(deviceId, ignoreCase = true) && current.session != null) {
                            val patchedSession = current.session.copy(
                                totalMinutes = result.updatedTotalMinutes,
                                totalPrice = result.updatedTotalPrice
                            )
                            current.copy(
                                session = patchedSession,
                                remainingSeconds = patchedSession.remainingSeconds()
                            )
                        } else current
                    }

                    // Prevent accidental second tap while operator has not yet seen
                    // the authoritative updated billing.
                    delay(1_200L)
                }

                // Release loading only after immediate UI patch for ADD_TIME.
                detailActionLoading = false
                actionInFlight.remove(key)

                scope.launch { telemetry.audit(action, deviceId, "SUCCESS", result.message) }

                // Sync hanya business state + hardware device ini di background.
                // Tidak ada full-fleet hardware refresh di critical path tombol.
                scope.launch { runCatching { refreshOverviewNow(refreshHardware = false) } }
                scope.launch { runCatching { refreshHardwareOnlyNow(deviceId) } }
            } catch (error: Throwable) {
                val friendly = friendlyError(error)
                detailError = friendly

                detailActionLoading = false
                actionInFlight.remove(key)

                scope.launch { telemetry.audit(action, deviceId, "FAILED", friendly) }
                scope.launch { runCatching { refreshOverviewNow(refreshHardware = false) } }
                scope.launch { runCatching { refreshHardwareOnlyNow(deviceId) } }
            } finally {
                // Fallback only: success/error paths above release immediately.
                if (detailActionLoading) detailActionLoading = false
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
            delay(businessRefreshDelayMs)
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

    // Field mode: physical plug/unplug must be visible quickly without spending
    // Firestore reads. Home polls all TinyTuya LAN devices; Device Detail polls
    // only the selected unit. If a device is offline, recovery probing becomes
    // more aggressive until it comes back on the LAN.
    LaunchedEffect(session?.uid, screen, mainTab) {
        if (session == null) return@LaunchedEffect

        val targetDeviceId = when (val current = screen) {
            is Screen.DeviceDetail -> current.deviceId
            Screen.Dashboard -> if (mainTab == MainTab.DASHBOARD) null else return@LaunchedEffect
            else -> return@LaunchedEffect
        }

        refreshHardwareOnlyNow(targetDeviceId)
        while (true) {
            val relevantDevices = if (targetDeviceId == null) {
                devices
            } else {
                devices.filter { it.id.equals(targetDeviceId, ignoreCase = true) }
            }
            val recovering = relevantDevices.any {
                it.hardware?.status == com.noirplaybox.operator.model.HardwareStatus.OFFLINE ||
                    it.connected.not()
            }

            delay(if (recovering) LOCAL_HARDWARE_RECOVERY_POLL_MS else LOCAL_HARDWARE_POLL_MS)
            refreshHardwareOnlyNow(targetDeviceId)
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
            val stalePreparing = mutableListOf<Pair<String, com.noirplaybox.operator.model.PreparingRuntime>>()

            devices = devices.map { device ->
                val active = device.session
                val remaining = active?.remainingSeconds(now) ?: 0
                if (active != null && active.isExpired(now)) {
                    expired += device.id to active
                }

                val preparingMinutes = device.preparing?.elapsedMinutes(now) ?: 0
                if (device.session == null && device.preparing != null && preparingMinutes >= 60) {
                    stalePreparing += device.id to device.preparing
                }

                device.copy(
                    remainingSeconds = remaining,
                    preparingMinutes = preparingMinutes
                )
            }

            stalePreparing.forEach { (deviceId, preparing) ->
                if (!preparingWatchdogInFlight.add(preparing.id)) return@forEach

                scope.launch {
                    try {
                        val result = lifecycle.cancelPreparing(deviceId, preparing.id)
                        watchdogAlert = "PREPARING $deviceId melewati 60 menit. Monitor dimatikan otomatis."
                        detailMessage = result.message
                        detailWarning = result.warning
                        refreshOverviewNow(refreshHardware = true)
                    } catch (error: Throwable) {
                        detailError = friendlyError(error)
                    } finally {
                        preparingWatchdogInFlight.remove(preparing.id)
                    }
                }
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

                        MainTab.PAYMENT -> PaymentScreen(
                            packages = authRepository.packages(),
                            repository = paymentRepository
                        )

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
                refreshLoading = detailManualRefreshing,
                message = detailMessage,
                warning = detailWarning,
                error = detailError,
                onBack = { screen = Screen.Dashboard },
                onRefresh = { refreshDeviceDetailNow(device.id) },
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
            painter = painterResource(R.drawable.logo_kagoengan_studio),
            contentDescription = "Kagoengan Studio Playbox",
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
