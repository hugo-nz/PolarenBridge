package com.polaren.bridge.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface RelayApiService {
    @POST("v1/relay/{pairingId}/telemetry")
    suspend fun sendTelemetry(
        @Path("pairingId") pairingId: String,
        @Header("Authorization") token: String,
        @Body payload: TelemetryPayload
    ): Response<Unit>
}

data class TelemetryPayload(
    val iv: String, // Base64 encoded IV
    val encryptedData: String // Base64 encoded encrypted JSON payload
)
