package com.noirplaybox.operator.data

import com.google.firebase.auth.FirebaseAuth
import com.noirplaybox.operator.BuildConfig
import com.noirplaybox.operator.util.NoirServerClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NoirApiClient(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val baseUrl: String = BuildConfig.NOIR_API_BASE_URL.trimEnd('/')
) {
    suspend fun get(path: String): JSONObject = request(path = path, method = "GET")

    suspend fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        forceRefreshToken: Boolean = false
    ): JSONObject {
        val token = getIdToken(forceRefresh = forceRefreshToken)
        val result = executeRequest(path, method, body, token)

        if (result.serverDateEpochMs > 0L) {
            NoirServerClock.update(result.serverDateEpochMs)
        }

        if (result.statusCode == 401 && !forceRefreshToken) {
            return request(
                path = path,
                method = method,
                body = body,
                forceRefreshToken = true
            )
        }

        val json = runCatching { JSONObject(result.body.ifBlank { "{}" }) }
            .getOrElse {
                throw IllegalStateException(
                    "Response backend tidak valid (HTTP ${result.statusCode})."
                )
            }

        val success = json.optBoolean("success", result.statusCode in 200..299)

        if (result.statusCode !in 200..299 || !success) {
            throw ApiException(
                statusCode = result.statusCode,
                message = json.optString("error").ifBlank {
                    "Request backend gagal (HTTP ${result.statusCode})"
                }
            )
        }

        return json
    }

    private suspend fun getIdToken(forceRefresh: Boolean): String {
        val user = auth.currentUser
            ?: throw IllegalStateException("User Firebase belum login.")

        return suspendCancellableCoroutine { continuation ->
            user.getIdToken(forceRefresh)
                .addOnSuccessListener { result ->
                    val token = result.token
                    if (token.isNullOrBlank()) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Firebase ID token kosong.")
                            )
                        }
                    } else if (continuation.isActive) {
                        continuation.resume(token)
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
        }
    }

    private suspend fun executeRequest(
        path: String,
        method: String,
        body: JSONObject?,
        token: String
    ): HttpResult = withContext(Dispatchers.IO) {
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        val connection = (
            URL("$baseUrl$normalizedPath").openConnection() as HttpURLConnection
        )

        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                }
            }

            val statusCode = connection.responseCode
            val serverDate = connection.getHeaderFieldDate("Date", -1L)

            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = if (stream == null) {
                ""
            } else {
                BufferedReader(
                    InputStreamReader(stream, Charsets.UTF_8)
                ).use { reader ->
                    reader.readText()
                }
            }

            HttpResult(
                statusCode = statusCode,
                body = responseBody,
                serverDateEpochMs = serverDate
            )
        } finally {
            connection.disconnect()
        }
    }
}

data class HttpResult(
    val statusCode: Int,
    val body: String,
    val serverDateEpochMs: Long = -1L
)

class ApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)
