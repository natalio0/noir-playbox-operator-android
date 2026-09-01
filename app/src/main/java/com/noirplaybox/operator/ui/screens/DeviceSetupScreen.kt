package com.noirplaybox.operator.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noirplaybox.operator.data.TuyaDeviceRegistryRepository
import com.noirplaybox.operator.data.TuyaRegistryEntry
import com.noirplaybox.operator.hardware.TinyTuyaBridge
import com.noirplaybox.operator.hardware.TinyTuyaDiscoveredDevice
import com.noirplaybox.operator.hardware.TinyTuyaLocalConfig
import com.noirplaybox.operator.hardware.TinyTuyaSecureStore
import com.noirplaybox.operator.model.PlayboxDevice
import kotlinx.coroutines.launch

@Composable
fun DeviceSetupScreen(
    devices: List<PlayboxDevice>,
    cafeId: String,
    onConfigurationSaved: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val bridge = remember(context) { TinyTuyaBridge(context.applicationContext) }
    val store = remember(context) { TinyTuyaSecureStore(context.applicationContext) }
    val registry = remember(context) { TuyaDeviceRegistryRepository(context.applicationContext) }

    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf<List<TinyTuyaDiscoveredDevice>>(emptyList()) }
    val registryEntries = remember { mutableStateMapOf<String, TuyaRegistryEntry>() }
    val registryMissing = remember { mutableStateMapOf<String, Boolean>() }
    var selectedLan by remember { mutableStateOf<TinyTuyaDiscoveredDevice?>(null) }
    var selectedLogicalId by remember { mutableStateOf(devices.firstOrNull()?.id.orEmpty()) }
    var localKey by remember { mutableStateOf("") }
    var switchDps by remember { mutableStateOf("1") }
    var registryLoadingId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val configuredCount = remember(devices, message) { devices.count { store.has(it.id) } }
    val readyKeyCount = registryEntries.size

    fun applyRegistryEntry(lan: TinyTuyaDiscoveredDevice, entry: TuyaRegistryEntry?) {
        selectedLan = lan
        if (entry != null) {
            localKey = entry.localKey
            entry.logicalDeviceId?.takeIf { it.isNotBlank() }?.let { logical ->
                if (devices.any { it.id.equals(logical, ignoreCase = true) }) {
                    selectedLogicalId = logical.uppercase()
                }
            }
            entry.switchDps?.let { switchDps = it.toString() }
            message = "Local key ${lan.name} siap dari Noir backend."
            error = null
        } else {
            localKey = ""
            message = null
            error = "Local key ${lan.id} belum tersedia dari Noir backend. Input manual tetap tersedia sebagai fallback."
        }
    }

    fun lookupRegistry(lan: TinyTuyaDiscoveredDevice, selectAfterLookup: Boolean) {
        registryLoadingId = lan.id
        error = null
        message = "Mencari local key ${lan.name}..."
        scope.launch {
            runCatching {
                registry.find(cafeId, lan.id) ?: registry.resolveFromCloud(
                    cafeId = cafeId,
                    tuyaDeviceId = lan.id,
                    logicalDeviceId = selectedLogicalId.takeIf { it.isNotBlank() },
                    protocolVersion = lan.protocolVersion,
                    ipAddress = lan.ipAddress
                )
            }.onSuccess { entry ->
                registryEntries[lan.id] = entry
                registryMissing.remove(lan.id)
                if (selectAfterLookup) applyRegistryEntry(lan, entry)
                message = "Local key siap • ${lan.name} dapat dipasangkan ke ${selectedLogicalId.ifBlank { "PlayBox" }}."
                error = null
            }.onFailure { throwable ->
                registryEntries.remove(lan.id)
                registryMissing[lan.id] = true
                if (selectAfterLookup) {
                    selectedLan = lan
                    localKey = ""
                }
                message = null
                error = (throwable.message ?: "Local key belum tersedia.") +
                    " Anda masih dapat menggunakan input manual sebagai fallback."
            }
            registryLoadingId = null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 720.dp
        val side = if (isTablet) 28.dp else 20.dp
        val maxContent = if (isTablet) 920.dp else 760.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = side),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Spacer(Modifier.height(10.dp)) }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContent),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Devices", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Scan smart plug, sinkronkan local key, lalu hubungkan ke PlayBox.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maxContent)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Self-service device pairing", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(
                                    "Cafe: $cafeId • registry aman via Noir backend",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard("Configured", configuredCount, Modifier.weight(1f))
                            MetricCard("Discovered", scanned.size, Modifier.weight(1f))
                            MetricCard("Keys ready", readyKeyCount, Modifier.weight(1f))
                        }

                        Button(
                            onClick = {
                                scanning = true
                                registryEntries.clear()
                                registryMissing.clear()
                                scanned = emptyList()
                                selectedLan = null
                                localKey = ""
                                error = null
                                message = null
                                scope.launch {
                                    bridge.scan(12).onSuccess { found ->
                                        scanned = found
                                        if (found.isEmpty()) {
                                            message = "Tidak ada device Tuya ditemukan di jaringan."
                                        } else {
                                            var matched = 0
                                            var failed = 0
                                            found.forEach { lan ->
                                                registryLoadingId = lan.id
                                                runCatching {
                                                    registry.find(cafeId, lan.id) ?: registry.resolveFromCloud(
                                                        cafeId = cafeId,
                                                        tuyaDeviceId = lan.id,
                                                        logicalDeviceId = selectedLogicalId.takeIf { it.isNotBlank() },
                                                        protocolVersion = lan.protocolVersion,
                                                        ipAddress = lan.ipAddress
                                                    )
                                                }.onSuccess { entry ->
                                                    registryEntries[lan.id] = entry
                                                    registryMissing.remove(lan.id)
                                                    matched += 1
                                                }.onFailure { throwable ->
                                                    registryEntries.remove(lan.id)
                                                    registryMissing[lan.id] = true
                                                    failed += 1
                                                    error = "${lan.name}: ${throwable.message ?: "local key belum tersedia"}"
                                                }
                                            }
                                            registryLoadingId = null
                                            message = if (matched > 0) {
                                                "${found.size} device ditemukan • $matched local key siap digunakan."
                                            } else {
                                                "${found.size} device ditemukan • belum ada local key yang berhasil di-resolve."
                                            }
                                            if (failed == 0) error = null
                                        }
                                    }.onFailure { error = it.message ?: "Scan gagal." }
                                    scanning = false
                                }
                            },
                            enabled = !scanning,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            if (scanning) {
                                CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp)
                                Text("  Scanning local network...")
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                                Text("  Scan local network")
                            }
                        }
                    }
                }
            }

            if (devices.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "PlayBox units",
                        subtitle = "Pilih logical unit tujuan pairing",
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContent)
                    )
                }

                items(devices, key = { it.id }) { device ->
                    val config = store.load(device.id)
                    DeviceRowModern(
                        device = device,
                        configured = config != null,
                        ipAddress = config?.ipAddress,
                        selected = selectedLogicalId == device.id,
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContent),
                        onClick = {
                            selectedLogicalId = device.id
                            message = if (config != null) {
                                "${device.id} sudah terhubung ke ${config.ipAddress}."
                            } else {
                                "${device.id} dipilih. Pilih smart plug hasil scan."
                            }
                            error = null
                        }
                    )
                }
            }

            if (scanned.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Discovered devices",
                        subtitle = "Pilih plug • Noir backend cek registry lalu sinkron ke Tuya Cloud bila perlu",
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContent)
                    )
                }

                items(scanned, key = { it.id }) { item ->
                    val entry = registryEntries[item.id]
                    val knownMissing = registryMissing[item.id] == true
                    val loading = registryLoadingId == item.id
                    DiscoveredDeviceRow(
                        item = item,
                        selected = selectedLan?.id == item.id,
                        keyAvailable = entry != null,
                        checkedMissing = knownMissing,
                        loading = loading,
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContent),
                        onClick = {
                            val cached = registryEntries[item.id]
                            if (cached != null) {
                                applyRegistryEntry(item, cached)
                            } else {
                                lookupRegistry(item, selectAfterLookup = true)
                            }
                        }
                    )
                }
            }

            selectedLan?.let { lan ->
                item {
                    SectionTitle(
                        title = "Pairing",
                        subtitle = if (registryEntries[lan.id] != null) {
                            "Local key siap dan akan disimpan terenkripsi di perangkat"
                        } else if (registryLoadingId == lan.id) {
                            "Mengambil local key secara aman..."
                        } else {
                            "Cloud sync gagal • fallback manual tetap tersedia"
                        },
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContent)
                    )
                }
                item {
                    PairingPanel(
                        lan = lan,
                        selectedLogicalId = selectedLogicalId,
                        localKey = localKey,
                        switchDps = switchDps,
                        keyFromRegistry = registryEntries[lan.id] != null,
                        connectionSaved = store.load(selectedLogicalId)?.tuyaDeviceId == lan.id,
                        onLogicalIdChange = { selectedLogicalId = it.uppercase() },
                        onLocalKeyChange = { localKey = it },
                        onDpsChange = { switchDps = it.filter(Char::isDigit) },
                        onSave = {
                            runCatching {
                                require(selectedLogicalId.isNotBlank()) { "PlayBox ID wajib dipilih." }
                                require(localKey.isNotBlank()) { "Local key belum tersedia." }
                                val registryEntry = registryEntries[lan.id]
                                val config = TinyTuyaLocalConfig(
                                    logicalDeviceId = selectedLogicalId.trim(),
                                    tuyaDeviceId = lan.id,
                                    ipAddress = lan.ipAddress,
                                    localKey = localKey.trim(),
                                    protocolVersion = (registryEntry?.protocolVersion ?: lan.protocolVersion)
                                        .takeIf { it in setOf("3.1", "3.2", "3.3", "3.4", "3.5") }
                                        ?: "3.3",
                                    switchDps = switchDps.toIntOrNull() ?: registryEntry?.switchDps ?: 1
                                )
                                store.save(config)
                            }.onSuccess {
                                message = "${selectedLogicalId.uppercase()} berhasil dihubungkan dan key disimpan terenkripsi di perangkat."
                                error = null
                                onConfigurationSaved()
                            }.onFailure { error = it.message ?: "Konfigurasi gagal disimpan." }
                        },
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContent)
                    )
                }
            }

            message?.let {
                item { InlineNotice(it, success = true, Modifier.fillMaxWidth().widthIn(max = maxContent)) }
            }
            error?.let {
                item { InlineNotice(it, success = false, Modifier.fillMaxWidth().widthIn(max = maxContent)) }
            }

            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value.toString(), fontWeight = FontWeight.Bold, fontSize = 21.sp, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun DeviceRowModern(
    device: PlayboxDevice,
    configured: Boolean,
    ipAddress: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f) else MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(device.id, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (configured) (ipAddress ?: "Connected") else "Belum dipasangkan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusPill(if (configured) "Configured" else "Not paired", configured)
        }
    }
}

@Composable
private fun StatusPill(label: String, success: Boolean) {
    Box(modifier = Modifier.background(if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp)).padding(horizontal = 11.dp, vertical = 7.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiscoveredDeviceRow(
    item: TinyTuyaDiscoveredDevice,
    selected: Boolean,
    keyAvailable: Boolean,
    checkedMissing: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.ipAddress} • ${item.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        keyAvailable -> "Noir Registry • key ready"
                        checkedMissing -> "Registry key unavailable"
                        else -> "Checking Noir registry"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (keyAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (keyAvailable) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PairingPanel(
    lan: TinyTuyaDiscoveredDevice,
    selectedLogicalId: String,
    localKey: String,
    switchDps: String,
    keyFromRegistry: Boolean,
    connectionSaved: Boolean,
    onLogicalIdChange: (String) -> Unit,
    onLocalKeyChange: (String) -> Unit,
    onDpsChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(if (connectionSaved) Icons.Rounded.CheckCircle else if (keyFromRegistry) Icons.Rounded.CloudDone else Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pair to PlayBox", fontWeight = FontWeight.Bold)
                    Text("${lan.name} • ${lan.ipAddress}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            OutlinedTextField(
                value = selectedLogicalId,
                onValueChange = onLogicalIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("PlayBox ID") },
                placeholder = { Text("PS01") },
                singleLine = true,
                enabled = !connectionSaved
            )

            if (keyFromRegistry) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Local key ready", fontWeight = FontWeight.SemiBold)
                            Text("Diambil aman melalui Noir backend. Setelah disimpan, key berada di secure storage perangkat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                OutlinedTextField(value = localKey, onValueChange = onLocalKeyChange, modifier = Modifier.fillMaxWidth(), label = { Text("Local Key • fallback") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }

            OutlinedTextField(
                value = switchDps,
                onValueChange = onDpsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Switch DPS") },
                singleLine = true,
                enabled = !connectionSaved
            )

            if (connectionSaved) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Koneksi lokal tersimpan", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "$selectedLogicalId sudah terhubung ke ${lan.ipAddress}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Button(
                    onClick = onSave,
                    enabled = selectedLogicalId.isNotBlank() && localKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null)
                    Text("  Hubungkan ke $selectedLogicalId")
                }
            }
        }
    }
}

@Composable
private fun InlineNotice(text: String, success: Boolean, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
        Text(text = text, modifier = Modifier.padding(13.dp), color = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
    }
}
