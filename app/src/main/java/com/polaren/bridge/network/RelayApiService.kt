package com.polaren.bridge.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Relay backend contract (see docs/PAIRING_PROTOCOL.md). The backend only brokers pairing and
 * relays opaque ciphertext; it never holds the payload key.
 */
interface RelayApiService {
    /** Starts a pairing session. Returns a pairing ID and the relay token for this car. */
    @POST("v1/pairing/offers")
    suspend fun createPairingOffer(): Response<PairingOfferResponse>

    @GET("v1/pairing/{pairingId}")
    suspend fun getPairingStatus(
        @Path("pairingId") pairingId: String,
        @Header("Authorization") authorization: String
    ): Response<PairingStatusResponse>

    @POST("v1/relay/{pairingId}/telemetry")
    suspend fun sendTelemetry(
        @Path("pairingId") pairingId: String,
        @Header("Authorization") authorization: String,
        @Body payload: TelemetryPayload
    ): Response<Unit>
}

data class PairingOfferResponse(
    val pairingId: String,
    val relayToken: String,
    val expiresAtMs: Long
)

data class PairingStatusResponse(
    val status: String // PENDING, CONFIRMED, EXPIRED or REVOKED
) {
    companion object {
        const val PENDING = "PENDING"
        const val CONFIRMED = "CONFIRMED"
        const val EXPIRED = "EXPIRED"
        const val REVOKED = "REVOKED"
    }
}

data class TelemetryPayload(
    val version: Int = 1,
    val iv: String, // Base64 AES-GCM nonce
    val encryptedData: String // Base64 ciphertext || tag of the JSON event
)

fun bearer(token: String) = "Bearer $token"
