package com.noirplaybox.operator.util

import com.noirplaybox.operator.data.ApiException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun friendlyError(error: Throwable): String {
    val raw = error.message.orEmpty()
    return when {
        error is SocketTimeoutException -> "Koneksi timeout. Periksa Wi-Fi dan coba lagi."
        error is UnknownHostException || error is ConnectException -> "Backend tidak dapat dijangkau. Periksa koneksi internet."
        error is ApiException && error.statusCode == 401 -> "Sesi login berakhir. Silakan login ulang."
        error is ApiException && error.statusCode == 403 -> "Akun ini tidak memiliki izin untuk aksi tersebut."
        raw.contains("offline", ignoreCase = true) -> "Device offline atau tidak merespons di jaringan lokal."
        raw.contains("timed out", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) -> "Device tidak merespons tepat waktu. Cek daya dan Wi-Fi."
        raw.contains("local key", ignoreCase = true) -> "Local key device belum tersedia atau tidak valid. Buka Devices untuk sinkronisasi ulang."
        raw.contains("protocol", ignoreCase = true) -> "Protocol TinyTuya tidak valid. Scan ulang device agar versi protocol terdeteksi."
        raw.isNotBlank() -> raw
        else -> "Terjadi kesalahan. Coba lagi."
    }
}
