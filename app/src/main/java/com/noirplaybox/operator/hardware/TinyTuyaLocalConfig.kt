package com.noirplaybox.operator.hardware

data class TinyTuyaLocalConfig(
    val logicalDeviceId: String,
    val tuyaDeviceId: String,
    val ipAddress: String,
    val localKey: String,
    val protocolVersion: String = "3.3",
    val switchDps: Int = 1
) {
    fun validate(): String? {
        if (logicalDeviceId.isBlank()) return "Logical device ID kosong."
        if (tuyaDeviceId.isBlank()) return "Tuya Device ID wajib diisi."
        if (ipAddress.isBlank()) return "IP BARDI wajib diisi."
        if (localKey.isBlank()) return "Local key wajib diisi."
        if (protocolVersion !in setOf("3.1", "3.2", "3.3", "3.4", "3.5")) {
            return "Protocol harus 3.1, 3.2, 3.3, 3.4, atau 3.5."
        }
        if (switchDps <= 0) return "Switch DPS tidak valid."
        return null
    }
}
