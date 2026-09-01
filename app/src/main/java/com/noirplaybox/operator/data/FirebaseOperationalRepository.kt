package com.noirplaybox.operator.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.noirplaybox.operator.model.DeviceState
import com.noirplaybox.operator.model.OperatorSession
import com.noirplaybox.operator.model.PlayboxDevice
import com.noirplaybox.operator.model.RentalPackage

class FirebaseOperationalRepository(
    private val context: Context
) {
    val firebaseConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    private val auth: FirebaseAuth?
        get() = if (firebaseConfigured) FirebaseAuth.getInstance() else null

    private val firestore: FirebaseFirestore?
        get() = if (firebaseConfigured) FirebaseFirestore.getInstance() else null

    fun restoreSession(callback: (Result<OperatorSession?>) -> Unit) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            callback(Result.success(null))
            return
        }

        val user = firebaseAuth.currentUser
        if (user == null) {
            callback(Result.success(null))
            return
        }

        validateOperationalProfile(
            uid = user.uid,
            email = user.email.orEmpty()
        ) { result ->
            result
                .onSuccess { profile ->
                    callback(Result.success(profile))
                }
                .onFailure { error ->
                    firebaseAuth.signOut()
                    callback(Result.failure(error))
                }
        }
    }

    fun login(
        email: String,
        password: String,
        callback: (Result<OperatorSession>) -> Unit
    ) {
        if (!firebaseConfigured) {
            callback(
                Result.failure(
                    IllegalStateException(
                        "Firebase belum dikonfigurasi. Letakkan google-services.json di folder app/."
                    )
                )
            )
            return
        }

        if (email.isBlank() || password.isBlank()) {
            callback(
                Result.failure(
                    IllegalArgumentException("Email dan password wajib diisi.")
                )
            )
            return
        }

        val firebaseAuth = auth
            ?: return callback(
                Result.failure(
                    IllegalStateException("Firebase Auth tidak tersedia.")
                )
            )

        firebaseAuth
            .signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user == null) {
                    callback(
                        Result.failure(
                            IllegalStateException("User Firebase tidak ditemukan.")
                        )
                    )
                    return@addOnSuccessListener
                }

                validateOperationalProfile(
                    uid = user.uid,
                    email = user.email.orEmpty()
                ) { profileResult ->
                    profileResult
                        .onSuccess { profile ->
                            callback(Result.success(profile))
                        }
                        .onFailure { error ->
                            // Admin / role lain tidak boleh mempertahankan
                            // session di APK operational.
                            firebaseAuth.signOut()
                            callback(Result.failure(error))
                        }
                }
            }
            .addOnFailureListener { error ->
                callback(
                    Result.failure(
                        IllegalStateException(
                            error.localizedMessage ?: "Login Firebase gagal."
                        )
                    )
                )
            }
    }

    private fun validateOperationalProfile(
        uid: String,
        email: String,
        callback: (Result<OperatorSession>) -> Unit
    ) {
        val db = firestore
            ?: return callback(
                Result.failure(
                    IllegalStateException("Firestore tidak tersedia.")
                )
            )

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    callback(
                        Result.failure(
                            IllegalStateException(
                                "Profil users/$uid tidak ditemukan."
                            )
                        )
                    )
                    return@addOnSuccessListener
                }

                val role = document.getString("role")
                    ?.trim()
                    ?.lowercase()
                    .orEmpty()

                if (role != "operational") {
                    callback(
                        Result.failure(
                            IllegalAccessException(
                                "Akun ini memiliki role '${role.ifBlank { "unknown" }}'. " +
                                    "APK ini hanya untuk role operational."
                            )
                        )
                    )
                    return@addOnSuccessListener
                }

                val cafeId = firstString(
                    document,
                    "cafeId",
                    "cafe_id"
                )

                if (cafeId.isBlank()) {
                    callback(
                        Result.failure(
                            IllegalStateException(
                                "Akun operational belum memiliki cafeId."
                            )
                        )
                    )
                    return@addOnSuccessListener
                }

                val displayName = firstString(
                    document,
                    "name",
                    "displayName",
                    "nama"
                ).ifBlank {
                    email.substringBefore("@").ifBlank { "Operational" }
                }

                val cafeName = firstString(
                    document,
                    "cafeName",
                    "cafe_name"
                ).ifBlank {
                    cafeId
                        .split("-", "_")
                        .joinToString(" ") { part ->
                            part.replaceFirstChar { char -> char.uppercase() }
                        }
                }

                callback(
                    Result.success(
                        OperatorSession(
                            uid = uid,
                            displayName = displayName,
                            email = email,
                            role = role,
                            cafeId = cafeId,
                            cafeName = cafeName
                        )
                    )
                )
            }
            .addOnFailureListener { error ->
                callback(
                    Result.failure(
                        IllegalStateException(
                            error.localizedMessage
                                ?: "Gagal membaca profil operational."
                        )
                    )
                )
            }
    }

    fun loadDevices(
        cafeId: String,
        callback: (Result<List<PlayboxDevice>>) -> Unit
    ) {
        val db = firestore
            ?: return callback(
                Result.failure(
                    IllegalStateException("Firestore tidak tersedia.")
                )
            )

        db.collection("devices")
            .whereEqualTo("cafeId", cafeId)
            .get()
            .addOnSuccessListener { snapshot ->
                val devices = snapshot.documents
                    .map { document -> mapDevice(document, cafeId) }
                    .sortedBy { it.id }

                callback(Result.success(devices))
            }
            .addOnFailureListener { primaryError ->
                db.collection("devices")
                    .whereEqualTo("cafe_id", cafeId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val devices = snapshot.documents
                            .map { document -> mapDevice(document, cafeId) }
                            .sortedBy { it.id }

                        callback(Result.success(devices))
                    }
                    .addOnFailureListener {
                        callback(
                            Result.failure(
                                IllegalStateException(
                                    primaryError.localizedMessage
                                        ?: "Gagal membaca daftar device cafe."
                                )
                            )
                        )
                    }
            }
    }

    private fun mapDevice(
        document: DocumentSnapshot,
        cafeId: String
    ): PlayboxDevice {
        val id = document.id
        val name = firstString(
            document,
            "name",
            "deviceName",
            "label"
        ).ifBlank { id }

        val rawState = firstString(
            document,
            "state",
            "status",
            "rentalState"
        )

        val enabled = document.getBoolean("enabled") ?: true
        val online = document.getBoolean("online")
            ?: document.getBoolean("isOnline")
            ?: true

        val state = when {
            !enabled || !online -> DeviceState.OFFLINE
            rawState.contains("PREPAR", ignoreCase = true) ->
                DeviceState.PREPARING
            rawState.contains("ACTIVE", ignoreCase = true) ->
                DeviceState.ACTIVE
            rawState.contains("RENT", ignoreCase = true) ->
                DeviceState.ACTIVE
            rawState.contains("SHUTDOWN", ignoreCase = true) ->
                DeviceState.SHUTDOWN
            rawState.contains("OFFLINE", ignoreCase = true) ->
                DeviceState.OFFLINE
            else -> DeviceState.READY
        }

        val remainingSeconds = (
            document.getLong("remainingSeconds")
                ?: document.getLong("remaining_seconds")
                ?: 0L
        ).coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        val preparingMinutes = (
            document.getLong("preparingMinutes")
                ?: document.getLong("preparing_minutes")
                ?: 0L
        ).coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        return PlayboxDevice(
            id = id,
            name = name,
            cafeId = cafeId,
            state = state,
            connected = online,
            connectionLabel = "Firebase",
            remainingSeconds = remainingSeconds,
            preparingMinutes = preparingMinutes
        )
    }

    fun logout() {
        auth?.signOut()
    }

    fun packages(): List<RentalPackage> = listOf(
        RentalPackage("1h", "1 Jam", 60, 12_000),
        RentalPackage("2h", "2 Jam", 120, 22_000),
        RentalPackage("3h", "3 Jam", 180, 30_000),
        RentalPackage("5h", "5 Jam", 300, 45_000),
        RentalPackage("10h", "10 Jam", 600, 80_000),
    )

    private fun firstString(
        document: DocumentSnapshot,
        vararg keys: String
    ): String {
        for (key in keys) {
            val value = document.getString(key)?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return ""
    }
}
