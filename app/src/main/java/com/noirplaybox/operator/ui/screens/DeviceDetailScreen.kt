package com.noirplaybox.operator.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplaybox.operator.R
import com.noirplaybox.operator.model.DeviceState
import com.noirplaybox.operator.model.HardwareStatus
import com.noirplaybox.operator.model.PlayboxDevice
import com.noirplaybox.operator.model.PreparingRiskLevel
import com.noirplaybox.operator.model.RentalPackage
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DeviceDetailScreen(
    device: PlayboxDevice,
    packages: List<RentalPackage>,
    actionLoading: Boolean,
    message: String?,
    warning: String?,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPrepare: () -> Unit,
    onCancelPreparing: () -> Unit,
    onStartRental: (RentalPackage) -> Unit,
    onAddTime: (RentalPackage) -> Unit,
    onStopRental: () -> Unit,
    onStartShutdown: () -> Unit,
    onRetryShutdownMonitor: () -> Unit,
    onFinishShutdown: () -> Unit,
    canOpenLocalPilot: Boolean = true,
    onOpenLocalPilot: () -> Unit
) {
    var confirmTitle by remember { mutableStateOf<String?>(null) }
    var confirmBody by remember { mutableStateOf("") }
    var confirmLabel by remember { mutableStateOf("Lanjutkan") }
    var confirmDestructive by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun askConfirmation(
        title: String,
        body: String,
        label: String = "Lanjutkan",
        destructive: Boolean = false,
        action: () -> Unit
    ) {
        if (actionLoading) return
        confirmTitle = title
        confirmBody = body
        confirmLabel = label
        confirmDestructive = destructive
        confirmAction = action
    }

    val dismissConfirmation = {
        confirmTitle = null
        confirmBody = ""
        confirmAction = null
    }

    confirmTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { if (!actionLoading) dismissConfirmation() },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(confirmBody, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = confirmAction
                        dismissConfirmation()
                        action?.invoke()
                    },
                    enabled = !actionLoading
                ) {
                    Text(
                        confirmLabel,
                        color = if (confirmDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = dismissConfirmation, enabled = !actionLoading) {
                    Text("Batal")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 720.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTablet) 28.dp else 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 1040.dp)) {
                DetailTopBar(
                    device = device,
                    actionLoading = actionLoading,
                    onBack = onBack,
                    onRefresh = onRefresh
                )

                Spacer(Modifier.height(16.dp))

                if (actionLoading) {
                    LoadingActionBanner()
                    Spacer(Modifier.height(12.dp))
                }

                message?.let { NoticeCard(it, MaterialTheme.colorScheme.primary) }
                warning?.let { NoticeCard(it, Color(0xFFB56A00)) }
                error?.let { NoticeCard(it, MaterialTheme.colorScheme.error) }

                if (isTablet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1.25f)) {
                            RentalPanel(
                                device = device,
                                packages = packages,
                                actionLoading = actionLoading,
                                onPrepare = onPrepare,
                                onCancelPreparing = {
                                    askConfirmation(
                                        "Batalkan preparing?",
                                        "Unit akan kembali ke status Ready dan proses persiapan dibatalkan.",
                                        "Batalkan Preparing",
                                        destructive = true,
                                        action = onCancelPreparing
                                    )
                                },
                                onStartRental = { pkg ->
                                    askConfirmation(
                                        "Mulai rental ${pkg.label}?",
                                        "Billing akan mulai berjalan sebesar ${formatRupiah(pkg.price)}.",
                                        "Mulai Rental"
                                    ) { onStartRental(pkg) }
                                },
                                onAddTime = { pkg ->
                                    askConfirmation(
                                        "Tambah ${pkg.label}?",
                                        "Waktu dan tagihan rental akan bertambah ${formatRupiah(pkg.price)}.",
                                        "Tambah Waktu"
                                    ) { onAddTime(pkg) }
                                },
                                onStopRental = {
                                    askConfirmation(
                                        "Selesaikan rental?",
                                        "Billing akan dihentikan dan unit masuk ke proses shutdown.",
                                        "Selesaikan Rental",
                                        destructive = true,
                                        action = onStopRental
                                    )
                                },
                                onStartShutdown = {
                                    askConfirmation(
                                        "Mulai shutdown mode?",
                                        "Billing sudah berhenti. Mode ini memberi waktu operator mematikan PS4 dengan aman.",
                                        "Mulai Shutdown",
                                        action = onStartShutdown
                                    )
                                },
                                onRetryShutdownMonitor = onRetryShutdownMonitor,
                                onFinishShutdown = {
                                    askConfirmation(
                                        "Selesaikan shutdown?",
                                        "Pastikan PS4 sudah benar-benar mati sebelum unit dikembalikan ke Ready.",
                                        "Selesai Shutdown",
                                        destructive = true,
                                        action = onFinishShutdown
                                    )
                                }
                            )
                        }
                        Column(modifier = Modifier.weight(0.75f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            HardwareOverview(device)
                            if (canOpenLocalPilot) {
                                AdvancedCard(device.id, onOpenLocalPilot)
                            }
                        }
                    }
                } else {
                    RentalPanel(
                        device = device,
                        packages = packages,
                        actionLoading = actionLoading,
                        onPrepare = onPrepare,
                        onCancelPreparing = {
                            askConfirmation(
                                "Batalkan preparing?",
                                "Unit akan kembali ke status Ready dan proses persiapan dibatalkan.",
                                "Batalkan Preparing",
                                destructive = true,
                                action = onCancelPreparing
                            )
                        },
                        onStartRental = { pkg ->
                            askConfirmation(
                                "Mulai rental ${pkg.label}?",
                                "Billing akan mulai berjalan sebesar ${formatRupiah(pkg.price)}.",
                                "Mulai Rental"
                            ) { onStartRental(pkg) }
                        },
                        onAddTime = { pkg ->
                            askConfirmation(
                                "Tambah ${pkg.label}?",
                                "Waktu dan tagihan rental akan bertambah ${formatRupiah(pkg.price)}.",
                                "Tambah Waktu"
                            ) { onAddTime(pkg) }
                        },
                        onStopRental = {
                            askConfirmation(
                                "Selesaikan rental?",
                                "Billing akan dihentikan dan unit masuk ke proses shutdown.",
                                "Selesaikan Rental",
                                destructive = true,
                                action = onStopRental
                            )
                        },
                        onStartShutdown = {
                            askConfirmation(
                                "Mulai shutdown mode?",
                                "Billing sudah berhenti. Mode ini memberi waktu operator mematikan PS4 dengan aman.",
                                "Mulai Shutdown",
                                action = onStartShutdown
                            )
                        },
                        onRetryShutdownMonitor = onRetryShutdownMonitor,
                        onFinishShutdown = {
                            askConfirmation(
                                "Selesaikan shutdown?",
                                "Pastikan PS4 sudah benar-benar mati sebelum unit dikembalikan ke Ready.",
                                "Selesai Shutdown",
                                destructive = true,
                                action = onFinishShutdown
                            )
                        }
                    )
                    Spacer(Modifier.height(14.dp))
                    HardwareOverview(device)
                    if (canOpenLocalPilot) {
                        Spacer(Modifier.height(14.dp))
                        AdvancedCard(device.id, onOpenLocalPilot)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    device: PlayboxDevice,
    actionLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = onBack,
            enabled = !actionLoading,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        ) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali")
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = device.id,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = device.name.ifBlank { device.cafeName ?: "PlayBox" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DeviceStateBadge(device)

        IconButton(
            onClick = onRefresh,
            enabled = !actionLoading,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RentalPanel(
    device: PlayboxDevice,
    packages: List<RentalPackage>,
    actionLoading: Boolean,
    onPrepare: () -> Unit,
    onCancelPreparing: () -> Unit,
    onStartRental: (RentalPackage) -> Unit,
    onAddTime: (RentalPackage) -> Unit,
    onStopRental: () -> Unit,
    onStartShutdown: () -> Unit,
    onRetryShutdownMonitor: () -> Unit,
    onFinishShutdown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(lifecycleTitle(device), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        lifecycleSubtitle(device),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            DeviceVisual(device)

            when (device.state) {
                DeviceState.READY -> ReadyState(actionLoading, onPrepare)
                DeviceState.OFFLINE -> OfflineState()
                DeviceState.PREPARING -> PreparingState(
                    device = device,
                    packages = packages,
                    actionLoading = actionLoading,
                    onStartRental = onStartRental,
                    onCancelPreparing = onCancelPreparing
                )
                DeviceState.ACTIVE -> ActiveState(
                    device = device,
                    packages = packages,
                    actionLoading = actionLoading,
                    onAddTime = onAddTime,
                    onStopRental = onStopRental
                )
                DeviceState.SHUTDOWN -> ShutdownState(
                    device = device,
                    actionLoading = actionLoading,
                    onStartShutdown = onStartShutdown,
                    onRetryShutdownMonitor = onRetryShutdownMonitor,
                    onFinishShutdown = onFinishShutdown
                )
            }
        }
    }
}

@Composable
private fun DeviceVisual(device: PlayboxDevice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 68.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ps4),
                contentDescription = device.id,
                modifier = Modifier.fillMaxWidth(0.8f).height(54.dp),
                contentScale = ContentScale.Fit
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Connection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(device.connectionLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Power", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (device.hardware?.status == HardwareStatus.ON) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                    Text(powerStatus(device), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ReadyState(actionLoading: Boolean, onPrepare: () -> Unit) {
    Text(
        "Unit siap digunakan. Siapkan perangkat sebelum pelanggan mulai bermain.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
    Button(
        onClick = onPrepare,
        enabled = !actionLoading,
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        Text("Siapkan Rental")
    }
}

@Composable
private fun OfflineState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(14.dp)
    ) {
        Text(
            "Perangkat offline. Periksa listrik, Wi-Fi, dan smart plug sebelum memulai rental.",
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun PreparingState(
    device: PlayboxDevice,
    packages: List<RentalPackage>,
    actionLoading: Boolean,
    onStartRental: (RentalPackage) -> Unit,
    onCancelPreparing: () -> Unit
) {
    val risk = device.preparing?.risk() ?: PreparingRiskLevel.NORMAL

    StatStrip(
        items = listOf(
            "Preparing" to "${device.preparingMinutes} min",
            "Billing" to "Belum mulai",
            "Status" to when (risk) {
                PreparingRiskLevel.WARNING -> "Warning"
                PreparingRiskLevel.SUSPICIOUS -> "Check"
                else -> "Normal"
            }
        )
    )

    SectionLabel("Pilih paket rental")
    PackageSelector(
        packages = packages,
        actionLoading = actionLoading,
        actionText = "Mulai Rental",
        helperText = "Pilih durasi dan harga, lalu konfirmasi sebelum billing dimulai.",
        onSubmit = onStartRental
    )

    OutlinedButton(
        onClick = onCancelPreparing,
        enabled = !actionLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Batalkan Preparing")
    }
}

@Composable
private fun ActiveState(
    device: PlayboxDevice,
    packages: List<RentalPackage>,
    actionLoading: Boolean,
    onAddTime: (RentalPackage) -> Unit,
    onStopRental: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
            .padding(horizontal = 18.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    "RENTAL BERJALAN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                formatCountdown(device.remainingSeconds),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${device.session?.totalMinutes ?: 0} menit  •  ${formatRupiah(device.session?.totalPrice ?: 0)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    SectionLabel("Tambah waktu")
    PackageSelector(
        packages = packages,
        actionLoading = actionLoading,
        actionText = "Tambah Waktu",
        helperText = "Pilih tambahan durasi terlebih dahulu agar tidak salah menambah billing.",
        onSubmit = onAddTime
    )

    OutlinedButton(
        onClick = onStopRental,
        enabled = !actionLoading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f))
    ) {
        Text("Selesaikan Rental", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ShutdownState(
    device: PlayboxDevice,
    actionLoading: Boolean,
    onStartShutdown: () -> Unit,
    onRetryShutdownMonitor: () -> Unit,
    onFinishShutdown: () -> Unit
) {
    val status = device.shutdown?.status.orEmpty()

    if (status == "SHUTDOWN_PENDING") {
        Text(
            "Billing sudah selesai. Jalankan shutdown mode untuk memberi waktu mematikan PS4 secara normal.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onStartShutdown,
            enabled = !actionLoading,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text("Mulai Shutdown Mode")
        }
    } else {
        StatStrip(
            items = listOf(
                "Mode" to "Shutdown",
                "Durasi" to "${device.shutdown?.elapsedMinutes() ?: 0} min",
                "Billing" to "Stop"
            )
        )

        Text(
            "Matikan PS4 dari menu sistem. Setelah konsol benar-benar mati, selesaikan shutdown mode.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        if (device.hardware?.switchOn != true) {
            OutlinedButton(
                onClick = onRetryShutdownMonitor,
                enabled = !actionLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Nyalakan Monitor Lagi")
            }
        }

        Button(
            onClick = onFinishShutdown,
            enabled = !actionLoading,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text("Selesai Shutdown")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageSelector(
    packages: List<RentalPackage>,
    actionLoading: Boolean,
    actionText: String,
    helperText: String,
    onSubmit: (RentalPackage) -> Unit
) {
    var selected by remember(packages) { mutableStateOf(packages.firstOrNull()) }
    var showSheet by remember { mutableStateOf(false) }

    if (packages.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Paket rental belum tersedia", fontWeight = FontWeight.SemiBold)
                Text(
                    "Refresh data atau periksa konfigurasi paket sebelum melanjutkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (actionText == "Tambah Waktu") "Pilih tambahan waktu" else "Pilih paket rental",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Periksa durasi dan harga sebelum memilih.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                packages.forEach { pkg ->
                    val isSelected = selected == pkg
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            )
                            .clickable {
                                selected = pkg
                                showSheet = false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selected = pkg
                                showSheet = false
                            }
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(pkg.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                formatRupiah(pkg.price),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                .clickable(enabled = !actionLoading) { showSheet = true },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        selected?.label ?: "Pilih paket",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        selected?.let { formatRupiah(it.price) } ?: "Belum ada paket dipilih",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Buka pilihan paket",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = { selected?.let(onSubmit) },
            enabled = !actionLoading && selected != null,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(actionText, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoadingActionBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Memproses perintah", fontWeight = FontWeight.SemiBold)
            Text(
                "Tunggu sampai status unit diperbarui.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HardwareOverview(device: PlayboxDevice) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Device Status", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(powerStatus(device), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactMetric("Power", "${formatNumber(device.hardware?.powerW ?: 0.0)} W", Modifier.weight(1f))
                CompactMetric("Voltage", "${formatNumber(device.hardware?.voltageV ?: 0.0)} V", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactMetric("Current", "${formatNumber(device.hardware?.currentMa ?: 0.0)} mA", Modifier.weight(1f))
                CompactMetric("Transport", device.hardware?.transport?.name?.replace("_", " ") ?: "-", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompactMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AdvancedCard(deviceId: String, onOpenLocalPilot: () -> Unit) {
    OutlinedButton(
        onClick = onOpenLocalPilot,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Icon(Icons.Rounded.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("  Advanced local setup", fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HardwareLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HardwareMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatStrip(items: List<Pair<String, String>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (label, value) ->
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun DeviceStateBadge(device: PlayboxDevice) {
    val text = when (device.state) {
        DeviceState.READY -> "Ready"
        DeviceState.PREPARING -> "Preparing"
        DeviceState.ACTIVE -> "Rental"
        DeviceState.SHUTDOWN -> "Shutdown"
        DeviceState.OFFLINE -> "Offline"
    }
    val accent = device.state == DeviceState.READY || device.state == DeviceState.ACTIVE

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoticeCard(message: String, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            color = accent,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun powerStatus(device: PlayboxDevice): String = when (device.hardware?.status) {
    HardwareStatus.ON -> "ON"
    HardwareStatus.OFF -> "OFF"
    HardwareStatus.OFFLINE -> "Offline"
    HardwareStatus.UNKNOWN, null -> "Checking"
}

private fun lifecycleTitle(device: PlayboxDevice): String = when (device.state) {
    DeviceState.READY -> "Ready to Play"
    DeviceState.PREPARING -> when (device.preparing?.risk()) {
        PreparingRiskLevel.WARNING -> "Preparing Warning"
        PreparingRiskLevel.SUSPICIOUS -> "Check Preparing"
        else -> "Preparing"
    }
    DeviceState.ACTIVE -> "Rental Active"
    DeviceState.SHUTDOWN -> "Shutdown Mode"
    DeviceState.OFFLINE -> "Device Offline"
}

private fun lifecycleSubtitle(device: PlayboxDevice): String = when (device.state) {
    DeviceState.READY -> "Unit siap disiapkan untuk pelanggan"
    DeviceState.PREPARING -> "Perangkat aktif, billing belum berjalan"
    DeviceState.ACTIVE -> "Rental sedang berjalan"
    DeviceState.SHUTDOWN -> "Mode shutdown tidak masuk billing"
    DeviceState.OFFLINE -> "Periksa koneksi perangkat"
}

private fun formatCountdown(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatRupiah(value: Int): String {
    val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
    return "Rp${formatter.format(value)}"
}

private fun formatNumber(value: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
    formatter.maximumFractionDigits = 1
    return formatter.format(value)
}
