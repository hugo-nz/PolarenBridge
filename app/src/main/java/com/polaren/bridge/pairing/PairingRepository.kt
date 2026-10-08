package com.polaren.bridge.pairing

import com.polaren.bridge.network.PairingStatusResponse
import com.polaren.bridge.network.RelayApiService
import com.polaren.bridge.network.bearer
import com.polaren.bridge.security.PairingState
import kotlinx.coroutines.delay
import java.io.IOException

sealed interface PairingOutcome {
    data object Confirmed : PairingOutcome
    data object Expired : PairingOutcome
}

class PairingRepository(
    private val api: RelayApiService,
    private val state: PairingState,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pollIntervalMs: Long = 2_000L
) {
    /** Requests a pairing session and generates the shared secret locally. */
    suspend fun createOffer(): PairingOffer {
        val response = api.createPairingOffer()
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            throw IOException("Pairing offer rejected: HTTP ${response.code()}")
        }
        return PairingOffer(
            pairingId = body.pairingId,
            relayToken = body.relayToken,
            expiresAtMs = body.expiresAtMs,
            secret = PairingOffer.newSecret()
        )
    }

    /** Polls until the iOS app confirms the offer or it expires; persists the pairing on success. */
    suspend fun awaitConfirmation(offer: PairingOffer): PairingOutcome {
        while (clock() < offer.expiresAtMs) {
            try {
                val response = api.getPairingStatus(offer.pairingId, bearer(offer.relayToken))
                when (response.body()?.status) {
                    PairingStatusResponse.CONFIRMED -> {
                        state.savePairing(offer.pairingId, offer.relayToken, offer.secret)
                        return PairingOutcome.Confirmed
                    }
                    PairingStatusResponse.EXPIRED, PairingStatusResponse.REVOKED -> return PairingOutcome.Expired
                }
            } catch (e: IOException) {
                // Transient network error; keep polling until the offer expires.
            }
            delay(pollIntervalMs)
        }
        return PairingOutcome.Expired
    }
}
