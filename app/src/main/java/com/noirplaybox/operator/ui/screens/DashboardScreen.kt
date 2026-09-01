package com.noirplaybox.operator.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplaybox.operator.R
import com.noirplaybox.operator.model.DeviceState
import com.noirplaybox.operator.model.HardwareStatus
import com.noirplaybox.operator.model.OperatorSession
import com.noirplaybox.operator.model.PlayboxDevice
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DashboardScreen(
    session: OperatorSession,
    devices: List<PlayboxDevice>,
    isLoading: Boolean,
    error: String?,
    lastSyncedText: String,
    onRefresh: () -> Unit,
    onDeviceClick: (PlayboxDevice) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "REALTIME MONITORING",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = session.cafeName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Business: Firebase · Hardware: transition → TinyTuya LAN",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isLoading
                ) {
                    Text(if (isLoading) "Sync..." else "Refresh")
                }
                OutlinedButton(onClick = onLogout) {
                    Text("Logout")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SummaryBar(
            devices = devices,
            lastSyncedText = lastSyncedText
        )

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isLoading && devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }

        if (!isLoading && devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Tidak ada PlayBox untuk ${session.cafeId}",
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onRefresh) {
                        Text("COBA LAGI")
                    }
                }
            }
            return
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = when {
                maxWidth >= 900.dp -> 3
                maxWidth >= 600.dp -> 2
                else -> 1
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryBar(
    devices: List<PlayboxDevice>,
    lastSyncedText: String
) {
    val on = devices.count { it.hardware?.status == HardwareStatus.ON }
    val off = devices.count { it.hardware?.status == HardwareStatus.OFF }
    val offline = devices.count { it.hardware?.status == HardwareStatus.OFFLINE }
    val active = devices.count { it.session != null }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryItem("ON", on)
            SummaryItem("OFF", off)
            SummaryItem("OFFLINE", offline)
            SummaryItem("RENTAL", active)
            Text(
                text = "Total ${devices.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = lastSyncedText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(99.dp)
                )
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            value.toString(),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DeviceCard(
    device: PlayboxDevice,
    onClick: () -> Unit
) {
    val hardware = device.hardware
    val hardwareLabel = when (hardware?.status) {
        HardwareStatus.ON -> "ON"
        HardwareStatus.OFF -> "OFF"
        HardwareStatus.OFFLINE -> "OFFLINE"
        HardwareStatus.UNKNOWN, null -> "CHECKING"
    }

    val lifecycleLabel = when (device.state) {
        DeviceState.ACTIVE -> "RENTAL ACTIVE"
        DeviceState.PREPARING -> "PREPARING"
        DeviceState.SHUTDOWN -> device.shutdown?.status ?: "SHUTDOWN"
        DeviceState.OFFLINE -> "DEVICE OFFLINE"
        DeviceState.READY -> "READY"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "PlayBox",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = device.id,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (device.name != device.id) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusPill(hardwareLabel)
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ps4),
                    contentDescription = "PlayStation 4 ${device.id}",
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .alpha(if (hardware?.status == HardwareStatus.OFFLINE) 0.45f else 1f),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = lifecycleLabel,
                fontWeight = FontWeight.SemiBold,
                color = if (device.state == DeviceState.ACTIVE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            if (device.state == DeviceState.ACTIVE) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatCountdown(device.remainingSeconds)} · ${formatRupiah(device.session?.totalPrice ?: 0)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (device.state == DeviceState.PREPARING) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${device.preparingMinutes} menit · belum billing",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniValue(
                    modifier = Modifier.weight(1f),
                    label = "Power",
                    value = if (hardware == null) "..." else "${formatNumber(hardware.powerW)} W"
                )
                MiniValue(
                    modifier = Modifier.weight(1f),
                    label = "Voltage",
                    value = if (hardware == null) "..." else "${formatNumber(hardware.voltageV)} V"
                )
                MiniValue(
                    modifier = Modifier.weight(1f),
                    label = "Current",
                    value = if (hardware == null) "..." else "${formatNumber(hardware.currentMa)} mA"
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = device.connectionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OPEN DETAIL")
            }
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(99.dp)
            )
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MiniValue(
    modifier: Modifier,
    label: String,
    value: String
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall
        )
    }
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
