package com.noirplaybox.operator.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    onOpenLocalPilot: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1080.dp)
        ) {
            DetailHeader(device, onBack, onRefresh, actionLoading)

            Spacer(Modifier.height(16.dp))

            if (actionLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
                Spacer(Modifier.height(12.dp))
            }

            message?.let { NoticeCard(it, MaterialTheme.colorScheme.primary) }
            warning?.let { NoticeCard(it, Color(0xFFB45309)) }
            error?.let { NoticeCard(it, MaterialTheme.colorScheme.error) }

            LifecycleCard(
                device = device,
                packages = packages,
                actionLoading = actionLoading,
                onPrepare = onPrepare,
                onCancelPreparing = onCancelPreparing,
                onStartRental = onStartRental,
                onAddTime = onAddTime,
                onStopRental = onStopRental,
                onStartShutdown = onStartShutdown,
                onRetryShutdownMonitor = onRetryShutdownMonitor,
                onFinishShutdown = onFinishShutdown
            )

            Spacer(Modifier.height(14.dp))
            HardwareCard(device)

            Spacer(Modifier.height(14.dp))
            LocalPilotCard(
                deviceId = device.id,
                onOpenLocalPilot = onOpenLocalPilot
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DetailHeader(
    device: PlayboxDevice,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    actionLoading: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "PLAYBOX DETAIL",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(device.id, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Text(
                device.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !actionLoading) {
                Text("Refresh")
            }
            OutlinedButton(onClick = onBack, enabled = !actionLoading) {
                Text("Kembali")
            }
        }
    }
}

@Composable
private fun LifecycleCard(
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        lifecycleTitle(device),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        lifecycleSubtitle(device),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(device)
            }

            Spacer(Modifier.height(14.dp))

            Image(
                painter = painterResource(R.drawable.ps4),
                contentDescription = "PS4",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Fit
            )

            when (device.state) {
                DeviceState.READY -> ReadyActions(actionLoading, onPrepare)
                DeviceState.OFFLINE -> OfflineActions()
                DeviceState.PREPARING -> PreparingActions(
                    device,
                    packages,
                    actionLoading,
                    onStartRental,
                    onCancelPreparing
                )
                DeviceState.ACTIVE -> ActiveActions(
                    device,
                    packages,
                    actionLoading,
                    onAddTime,
                    onStopRental
                )
                DeviceState.SHUTDOWN -> ShutdownActions(
                    device,
                    actionLoading,
                    onStartShutdown,
                    onRetryShutdownMonitor,
                    onFinishShutdown
                )
            }
        }
    }
}

@Composable
private fun ReadyActions(
    actionLoading: Boolean,
    onPrepare: () -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Text(
        "Nyalakan monitor dan catat PREPARING sebelum pelanggan mulai bermain.",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onPrepare,
        enabled = !actionLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Text("SIAPKAN RENTAL")
    }
}

@Composable
private fun OfflineActions() {
    Spacer(Modifier.height(8.dp))
    Text(
        "Hardware terdeteksi offline. Cek listrik dan Wi-Fi smart plug sebelum menyiapkan rental.",
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun PreparingActions(
    device: PlayboxDevice,
    packages: List<RentalPackage>,
    actionLoading: Boolean,
    onStartRental: (RentalPackage) -> Unit,
    onCancelPreparing: () -> Unit
) {
    val risk = device.preparing?.risk() ?: PreparingRiskLevel.NORMAL

    Spacer(Modifier.height(8.dp))
    DetailRow("Durasi PREPARING", "${device.preparingMinutes} menit")
    DetailRow("Billing", "BELUM DIMULAI")
    DetailRow("Risk", risk.name)

    Spacer(Modifier.height(18.dp))
    Text("Pilih paket untuk mulai billing", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))

    PackageButtons(
        packages = packages,
        actionLoading = actionLoading,
        actionLabel = "MULAI",
        onClick = onStartRental
    )

    Spacer(Modifier.height(14.dp))
    OutlinedButton(
        onClick = onCancelPreparing,
        enabled = !actionLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("BATALKAN PREPARING")
    }
}

@Composable
private fun ActiveActions(
    device: PlayboxDevice,
    packages: List<RentalPackage>,
    actionLoading: Boolean,
    onAddTime: (RentalPackage) -> Unit,
    onStopRental: () -> Unit
) {
    Spacer(Modifier.height(6.dp))
    Text(
        formatCountdown(device.remainingSeconds),
        fontSize = 42.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(4.dp))
    DetailRow("Total billing", formatRupiah(device.session?.totalPrice ?: 0))
    DetailRow("Total durasi", "${device.session?.totalMinutes ?: 0} menit")

    Spacer(Modifier.height(18.dp))
    Text("Tambah waktu", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))

    PackageButtons(
        packages = packages,
        actionLoading = actionLoading,
        actionLabel = "+",
        onClick = onAddTime
    )

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onStopRental,
        enabled = !actionLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
    ) {
        Text("STOP SESSION")
    }
}

@Composable
private fun ShutdownActions(
    device: PlayboxDevice,
    actionLoading: Boolean,
    onStartShutdown: () -> Unit,
    onRetryShutdownMonitor: () -> Unit,
    onFinishShutdown: () -> Unit
) {
    val status = device.shutdown?.status.orEmpty()

    Spacer(Modifier.height(8.dp))
    if (status == "SHUTDOWN_PENDING") {
        Text(
            "Billing sudah selesai. Jalankan Shutdown Mode untuk menyalakan monitor sementara dan matikan PS4 secara normal. Mode ini tidak masuk billing dan bukan PREPARING.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStartShutdown,
            enabled = !actionLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("MULAI SHUTDOWN MODE")
        }
    } else {
        DetailRow("Status", "SHUTDOWN ACTIVE")
        DetailRow("Durasi", "${device.shutdown?.elapsedMinutes() ?: 0} menit")
        DetailRow("Billing", "TIDAK BERJALAN")
        Spacer(Modifier.height(10.dp))
        Text(
            "1. Pastikan monitor menyala.\n2. Shutdown PS4 dari menu PS4.\n3. Tunggu PS4 benar-benar mati.\n4. Tekan SELESAI SHUTDOWN.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        if (device.hardware?.switchOn != true) {
            OutlinedButton(
                onClick = onRetryShutdownMonitor,
                enabled = !actionLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("NYALAKAN MONITOR LAGI")
            }
            Spacer(Modifier.height(10.dp))
        }

        Button(
            onClick = onFinishShutdown,
            enabled = !actionLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("SELESAI SHUTDOWN")
        }
    }
}

@Composable
private fun PackageButtons(
    packages: List<RentalPackage>,
    actionLoading: Boolean,
    actionLabel: String,
    onClick: (RentalPackage) -> Unit
) {
    packages.forEach { pkg ->
        OutlinedButton(
            onClick = { onClick(pkg) },
            enabled = !actionLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("$actionLabel ${pkg.label}")
                Text(formatRupiah(pkg.price), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HardwareCard(device: PlayboxDevice) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text("Hardware", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                device.connectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            DetailRow(
                "Switch",
                when (device.hardware?.status) {
                    HardwareStatus.ON -> "ON"
                    HardwareStatus.OFF -> "OFF"
                    HardwareStatus.OFFLINE -> "OFFLINE"
                    HardwareStatus.UNKNOWN, null -> "UNKNOWN"
                }
            )
            DetailRow("Power", "${formatNumber(device.hardware?.powerW ?: 0.0)} W")
            DetailRow("Voltage", "${formatNumber(device.hardware?.voltageV ?: 0.0)} V")
            DetailRow("Current", "${formatNumber(device.hardware?.currentMa ?: 0.0)} mA")
        }
    }
}

@Composable
private fun StatusPill(device: PlayboxDevice) {
    val text = when (device.hardware?.status) {
        HardwareStatus.ON -> "ON"
        HardwareStatus.OFF -> "OFF"
        HardwareStatus.OFFLINE -> "OFFLINE"
        else -> "UNKNOWN"
    }
    Text(
        text = text,
        modifier = Modifier.padding(10.dp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun NoticeCard(message: String, accent: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f))
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            color = accent,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LocalPilotCard(
    deviceId: String,
    onOpenLocalPilot: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                "TinyTuya Local Setup",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Setup ${deviceId.uppercase()} untuk kontrol langsung lewat LAN. Setelah config disimpan, device ini otomatis memakai TinyTuya lokal.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onOpenLocalPilot,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BUKA LOCAL SETUP ${deviceId.uppercase()}")
            }
        }
    }
}

private fun lifecycleTitle(device: PlayboxDevice): String = when (device.state) {
    DeviceState.READY -> "READY"
    DeviceState.PREPARING -> when (device.preparing?.risk()) {
        PreparingRiskLevel.WARNING -> "PREPARING WARNING"
        PreparingRiskLevel.SUSPICIOUS -> "SUSPICIOUS PREPARING"
        else -> "PREPARING"
    }
    DeviceState.ACTIVE -> "RENTAL ACTIVE"
    DeviceState.SHUTDOWN -> device.shutdown?.status ?: "SHUTDOWN"
    DeviceState.OFFLINE -> "DEVICE OFFLINE"
}

private fun lifecycleSubtitle(device: PlayboxDevice): String = when (device.state) {
    DeviceState.READY -> "Unit siap disiapkan untuk pelanggan"
    DeviceState.PREPARING -> "Monitor ON, billing belum berjalan"
    DeviceState.ACTIVE -> "Billing berasal dari Firebase session"
    DeviceState.SHUTDOWN -> "Mode shutdown tidak dihitung sebagai rental"
    DeviceState.OFFLINE -> "Cek koneksi hardware"
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
    formatter.maximumFractionDigits = 2
    return formatter.format(value)
}
