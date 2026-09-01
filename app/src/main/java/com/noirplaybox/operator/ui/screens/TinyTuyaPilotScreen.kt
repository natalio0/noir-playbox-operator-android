package com.noirplaybox.operator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplaybox.operator.hardware.TinyTuyaBridge
import com.noirplaybox.operator.hardware.TinyTuyaLocalConfig
import com.noirplaybox.operator.hardware.TinyTuyaPilotResult
import com.noirplaybox.operator.hardware.TinyTuyaSecureStore
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun TinyTuyaPilotScreen(
    logicalDeviceId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val store = remember(context) { TinyTuyaSecureStore(context) }
    val bridge = remember(context) { TinyTuyaBridge(context) }

    var tuyaDeviceId by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var localKey by remember { mutableStateOf("") }
    var protocolVersion by remember { mutableStateOf("3.3") }
    var switchDpsText by remember { mutableStateOf("1") }

    var configured by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("Loading TinyTuya runtime...") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<TinyTuyaPilotResult?>(null) }

    fun formConfig(): TinyTuyaLocalConfig? {
        val switchDps = switchDpsText.toIntOrNull()
        if (switchDps == null) {
            error = "Switch DPS harus berupa angka."
            return null
        }

        val config = TinyTuyaLocalConfig(
            logicalDeviceId = logicalDeviceId.uppercase(),
            tuyaDeviceId = tuyaDeviceId.trim(),
            ipAddress = ipAddress.trim(),
            localKey = localKey,
            protocolVersion = protocolVersion.trim(),
            switchDps = switchDps
        )

        val validation = config.validate()
        if (validation != null) {
            error = validation
            return null
        }

        return config
    }

    fun renderResult(result: TinyTuyaPilotResult, successMessage: String) {
        lastResult = result
        if (result.ok) {
            message = successMessage
            error = null
        } else {
            message = null
            error = result.error ?: "TinyTuya LAN gagal."
        }
    }

    fun runStatus() {
        val config = formConfig() ?: return
        if (busy) return

        scope.launch {
            busy = true
            error = null
            message = null
            try {
                renderResult(
                    bridge.status(config),
                    "${logicalDeviceId.uppercase()} merespons lewat LAN."
                )
            } catch (t: Throwable) {
                error = t.message ?: "Status TinyTuya gagal."
            } finally {
                busy = false
            }
        }
    }


    LaunchedEffect(logicalDeviceId) {
        store.load(logicalDeviceId)?.let { saved ->
            tuyaDeviceId = saved.tuyaDeviceId
            ipAddress = saved.ipAddress
            localKey = saved.localKey
            protocolVersion = saved.protocolVersion
            switchDpsText = saved.switchDps.toString()
            configured = true
        }

        info = runCatching {
            val json = JSONObject(bridge.libraryInfo())
            "TinyTuya ${json.optString("tinytuya", "?")} • ${json.optString("crypto", "?")} • LAN only"
        }.getOrElse {
            "Runtime belum siap: ${it.message.orEmpty()}"
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 720.dp
        val maxContent = if (isTablet) 1080.dp else 760.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTablet) 28.dp else 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PilotTopBar(
                logicalDeviceId = logicalDeviceId,
                configured = configured,
                busy = busy,
                onBack = onBack,
                onRefresh = ::runStatus,
                modifier = Modifier.fillMaxWidth().widthIn(max = maxContent)
            )

            Spacer(Modifier.height(14.dp))

            RuntimeBanner(
                info = info,
                configured = configured,
                modifier = Modifier.fillMaxWidth().widthIn(max = maxContent)
            )

            Spacer(Modifier.height(18.dp))

            if (isTablet) {
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContent),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ConfigCard(
                            logicalDeviceId = logicalDeviceId,
                            tuyaDeviceId = tuyaDeviceId,
                            ipAddress = ipAddress,
                            localKey = localKey,
                            protocolVersion = protocolVersion,
                            switchDpsText = switchDpsText,
                            configured = configured,
                            busy = busy,
                            onTuyaDeviceIdChange = { tuyaDeviceId = it },
                            onIpAddressChange = { ipAddress = it },
                            onLocalKeyChange = { localKey = it },
                            onProtocolChange = { protocolVersion = it },
                            onSwitchDpsChange = { switchDpsText = it.filter(Char::isDigit) },
                            onSave = {
                                val config = formConfig() ?: return@ConfigCard
                                runCatching { store.save(config) }
                                    .onSuccess {
                                        configured = true
                                        message = "Konfigurasi ${logicalDeviceId.uppercase()} disimpan terenkripsi."
                                        error = null
                                    }
                                    .onFailure { error = it.message ?: "Gagal menyimpan konfigurasi." }
                            },
                            onDelete = {
                                store.delete(logicalDeviceId)
                                tuyaDeviceId = ""
                                ipAddress = ""
                                localKey = ""
                                protocolVersion = "3.3"
                                switchDpsText = "1"
                                configured = false
                                lastResult = null
                                message = "Konfigurasi lokal dihapus dari perangkat."
                                error = null
                            }
                        )
                    }

                    Column(modifier = Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DiagnosticsCard(
                            logicalDeviceId = logicalDeviceId,
                            busy = busy,
                            onStatus = ::runStatus
                        )
                        ResultCard(lastResult = lastResult)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContent),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ConfigCard(
                        logicalDeviceId = logicalDeviceId,
                        tuyaDeviceId = tuyaDeviceId,
                        ipAddress = ipAddress,
                        localKey = localKey,
                        protocolVersion = protocolVersion,
                        switchDpsText = switchDpsText,
                        configured = configured,
                        busy = busy,
                        onTuyaDeviceIdChange = { tuyaDeviceId = it },
                        onIpAddressChange = { ipAddress = it },
                        onLocalKeyChange = { localKey = it },
                        onProtocolChange = { protocolVersion = it },
                        onSwitchDpsChange = { switchDpsText = it.filter(Char::isDigit) },
                        onSave = {
                            val config = formConfig() ?: return@ConfigCard
                            runCatching { store.save(config) }
                                .onSuccess {
                                    configured = true
                                    message = "Konfigurasi ${logicalDeviceId.uppercase()} disimpan terenkripsi."
                                    error = null
                                }
                                .onFailure { error = it.message ?: "Gagal menyimpan konfigurasi." }
                        },
                        onDelete = {
                            store.delete(logicalDeviceId)
                            tuyaDeviceId = ""
                            ipAddress = ""
                            localKey = ""
                            protocolVersion = "3.3"
                            switchDpsText = "1"
                            configured = false
                            lastResult = null
                            message = "Konfigurasi lokal dihapus dari perangkat."
                            error = null
                        }
                    )

                    DiagnosticsCard(
                        logicalDeviceId = logicalDeviceId,
                        busy = busy,
                        onStatus = ::runStatus
                    )

                    ResultCard(lastResult = lastResult)
                }
            }

            message?.let {
                Spacer(Modifier.height(14.dp))
                InlineNotice(
                    text = it,
                    success = true,
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContent)
                )
            }

            error?.let {
                Spacer(Modifier.height(14.dp))
                InlineNotice(
                    text = it,
                    success = false,
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContent)
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PilotTopBar(
    logicalDeviceId: String,
    configured: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBack, enabled = !busy) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Local Diagnostics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${logicalDeviceId.uppercase()} • TinyTuya LAN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (configured) "Configured" else "Not configured", configured)
            IconButton(
                onClick = onRefresh,
                enabled = !busy
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            }
        }
    }
}

@Composable
private fun RuntimeBanner(info: String, configured: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("LAN runtime", fontWeight = FontWeight.SemiBold)
                Text(
                    info,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                if (configured) Icons.Rounded.CheckCircle else Icons.Rounded.Router,
                contentDescription = null,
                tint = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConfigCard(
    logicalDeviceId: String,
    tuyaDeviceId: String,
    ipAddress: String,
    localKey: String,
    protocolVersion: String,
    switchDpsText: String,
    configured: Boolean,
    busy: Boolean,
    onTuyaDeviceIdChange: (String) -> Unit,
    onIpAddressChange: (String) -> Unit,
    onLocalKeyChange: (String) -> Unit,
    onProtocolChange: (String) -> Unit,
    onSwitchDpsChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Local configuration", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Data ${logicalDeviceId.uppercase()} disimpan terenkripsi di perangkat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))

            OutlinedTextField(
                value = tuyaDeviceId,
                onValueChange = onTuyaDeviceIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tuya Device ID") },
                singleLine = true,
                enabled = !busy
            )

            OutlinedTextField(
                value = ipAddress,
                onValueChange = onIpAddressChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("IP address") },
                placeholder = { Text("192.168.x.x") },
                singleLine = true,
                enabled = !busy
            )

            OutlinedTextField(
                value = localKey,
                onValueChange = onLocalKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Local Key") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = protocolVersion,
                    onValueChange = onProtocolChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Protocol") },
                    placeholder = { Text("3.3") },
                    singleLine = true,
                    enabled = !busy
                )
                OutlinedTextField(
                    value = switchDpsText,
                    onValueChange = onSwitchDpsChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Switch DPS") },
                    placeholder = { Text("1") },
                    singleLine = true,
                    enabled = !busy
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = onSave, enabled = !busy, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Text(if (configured) "  Update" else "  Save")
                }
                OutlinedButton(onClick = onDelete, enabled = !busy, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Text("  Delete")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    logicalDeviceId: String,
    busy: Boolean,
    onStatus: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Local diagnostics", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Hanya membaca status ${logicalDeviceId.uppercase()} melalui LAN. Tidak ada perintah power langsung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Production safe", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Power ON/OFF hanya dapat dijalankan melalui flow rental agar session dan billing tetap tercatat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(onClick = onStatus, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Memory, contentDescription = null)
                }
                Text(if (busy) "  Memeriksa..." else "  Test status LAN")
            }
        }
    }
}

@Composable
private fun ResultCard(lastResult: TinyTuyaPilotResult?) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Last response", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            if (lastResult == null) {
                Text(
                    "Belum ada hasil test. Jalankan Test status untuk membaca DPS dari perangkat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    label = when (lastResult.switchOn) {
                        true -> "Power ON"
                        false -> "Power OFF"
                        null -> "Unknown"
                    },
                    success = lastResult.ok
                )
                Text(
                    if (lastResult.ok) "LAN response received" else "Response error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

            Text("DPS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                lastResult.dpsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text("Raw response", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                lastResult.rawText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, success: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InlineNotice(text: String, success: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(13.dp),
            color = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
