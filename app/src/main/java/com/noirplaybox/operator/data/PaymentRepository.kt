package com.noirplaybox.operator.data

import org.json.JSONObject

data class PaymentOrder(
    val orderId: String,
    val packageId: String,
    val packageName: String,
    val durationMinutes: Int,
    val amount: Int,
    val status: String,
    val midtransTransactionStatus: String? = null,
    val qrUrl: String? = null,
    val environment: String? = null
)

class PaymentRepository(
    private val api: NoirApiClient
) {
    suspend fun create(packageId: String): PaymentOrder {
        val json = api.request(
            path = "/api/payments/create",
            method = "POST",
            body = JSONObject().put("packageId", packageId)
        )
        return parsePayment(json.getJSONObject("payment"))
    }

    suspend fun status(orderId: String): PaymentOrder {
        val safeOrderId = java.net.URLEncoder.encode(orderId, Charsets.UTF_8.name())
        val json = api.get("/api/payments/status/$safeOrderId")
        return parsePayment(json.getJSONObject("payment"))
    }

    private fun parsePayment(json: JSONObject): PaymentOrder = PaymentOrder(
        orderId = json.optString("orderId"),
        packageId = json.optString("packageId"),
        packageName = json.optString("packageName"),
        durationMinutes = json.optInt("durationMinutes", 0),
        amount = json.optInt("amount", 0),
        status = json.optString("status", "PENDING").uppercase(),
        midtransTransactionStatus = json.optString("midtransTransactionStatus").takeIf { it.isNotBlank() },
        qrUrl = json.optString("qrUrl").takeIf { it.isNotBlank() },
        environment = json.optString("environment").takeIf { it.isNotBlank() }
    )
}
