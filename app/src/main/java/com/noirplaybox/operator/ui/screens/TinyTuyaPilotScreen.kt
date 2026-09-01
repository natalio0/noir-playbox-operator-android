package com.noirplaybox.operator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var armed by remember { mutableStateOf(false) }
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

    fun runPower(on: Boolean) {
        val config = formConfig() ?: return
        if (!armed) {
            error = "Centang konfirmasi LOCAL CONTROL terlebih dahulu."
            return
        }
        if (busy) return

        scope.launch {
            busy = true
            error = null
            message = null
            try {
                renderResult(
                    bridge.setPower(config, on),
                    if (on) {
                        "Command LOCAL ON berhasil."
                    } else {
                        "Command LOCAL OFF berhasil."
                    }
                )
            } catch (t: Throwable) {
                error = t.message ?: "Command TinyTuya gagal."
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
            "TinyTuya ${json.optString("tinytuya", "?")} · " +
                "${json.optString("crypto", "?")} · LAN ONLY"
        }.getOrElse {
            "TinyTuya runtime belum siap: ${it.message.orEmpty()}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .widthIn(max = 900.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "TINYTUYA LOCAL PILOT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    logicalDeviceId.uppercase(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    info,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onBack,
                enabled = !busy
            ) {
                Text("Kembali")
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Pilot ini TIDAK mengubah billing/session Firebase dan belum mengganti controller production. " +
                "Ia hanya menguji komunikasi BARDI langsung lewat Wi-Fi/LAN.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        Text(
            "Konfigurasi lokal",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Masukkan data ${logicalDeviceId.uppercase()} langsung di perangkat. Jangan kirim local_key ke chat.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = tuyaDeviceId,
            onValueChange = { tuyaDeviceId = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tuya Device ID") },
            singleLine = true,
            enabled = !busy
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("IP BARDI di LAN") },
            placeholder = { Text("192.168.x.x") },
            singleLine = true,
            enabled = !busy
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = localKey,
            onValueChange = { localKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Local Key") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = protocolVersion,
                onValueChange = { protocolVersion = it },
                modifier = Modifier.weight(1f),
                label = { Text("Protocol") },
                placeholder = { Text("3.3") },
                singleLine = true,
                enabled = !busy
            )

            OutlinedTextField(
                value = switchDpsText,
                onValueChange = { switchDpsText = it.filter(Char::isDigit) },
                modifier = Modifier.weight(1f),
                label = { Text("Switch DPS") },
                placeholder = { Text("1") },
                singleLine = true,
                enabled = !busy
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val config = formConfig() ?: return@Button
                    runCatching {
                        store.save(config)
                    }.onSuccess {
                        configured = true
                        message = "Konfigurasi ${logicalDeviceId.uppercase()} disimpan terenkripsi."
                        error = null
                    }.onFailure {
                        error = it.message ?: "Gagal menyimpan konfigurasi."
                    }
                },
                enabled = !busy
            ) {
                Text(if (configured) "UPDATE CONFIG" else "SAVE CONFIG")
            }

            OutlinedButton(
                onClick = {
                    store.delete(logicalDeviceId)
                    tuyaDeviceId = ""
                    ipAddress = ""
                    localKey = ""
                    protocolVersion = "3.3"
                    switchDpsText = "1"
                    configured = false
                    armed = false
                    lastResult = null
                    message = "Konfigurasi lokal dihapus dari perangkat."
                    error = null
                },
                enabled = !busy
            ) {
                Text("HAPUS CONFIG")
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        Text(
            "LAN Test",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = ::runStatus,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "MEMPROSES..." else "TEST STATUS VIA LAN")
        }

        Spacer(Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = armed,
                onCheckedChange = { armed = it },
                enabled = !busy
            )
            Text(
                "Saya paham tombol di bawah mengontrol monitor ${logicalDeviceId.uppercase()} langsung tanpa Tuya Cloud."
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { runPower(true) },
                enabled = !busy && armed,
                modifier = Modifier.weight(1f)
            ) {
                Text("MONITOR ON · LOCAL")
            }

            Button(
                onClick = { runPower(false) },
                enabled = !busy && armed,
                modifier = Modifier.weight(1f)
            ) {
                Text("MONITOR OFF · LOCAL")
            }
        }

        message?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }

        lastResult?.let { result ->
            Spacer(Modifier.height(22.dp))
            HorizontalDivider()
            Spacer(Modifier.height(18.dp))

            Text(
                "Hasil DPS",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Switch: ${when (result.switchOn) {
                    true -> "ON"
                    false -> "OFF"
                    null -> "UNKNOWN"
                }}",
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(10.dp))
            Text(
                result.dpsText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Raw response",
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                result.rawText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}
