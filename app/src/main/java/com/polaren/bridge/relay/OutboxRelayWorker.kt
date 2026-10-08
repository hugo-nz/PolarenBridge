package com.polaren.bridge.relay

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.polaren.bridge.data.AppDatabase
import com.polaren.bridge.network.NetworkModule
import com.polaren.bridge.network.TelemetryPayload
import com.polaren.bridge.network.bearer
import com.polaren.bridge.security.CryptoHelper
import com.polaren.bridge.security.PairingState

/** Encrypts queued events with the pairing-derived key and relays them in order. */
class OutboxRelayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pairing = PairingState(applicationContext)
        val pairingId = pairing.pairingId
        val token = pairing.relayToken
        val key = pairing.relayKey()
        if (pairingId == null || token == null || key == null) {
            Log.d(TAG, "Not paired; leaving outbox untouched")
            return Result.success()
        }

        val dao = AppDatabase.getDatabase(applicationContext).telemetryDao()
        val api = NetworkModule.relayApi
        val aad = pairingId.toByteArray(Charsets.UTF_8)

        while (true) {
            val batch = dao.getPendingEvents(BATCH_SIZE)
            if (batch.isEmpty()) return Result.success()

            val sentIds = mutableListOf<Long>()
            var outcome: Result? = null
            for (event in batch) {
                val blob = CryptoHelper.encrypt(key, event.payloadJson.toByteArray(Charsets.UTF_8), aad)
                val payload = TelemetryPayload(
                    iv = Base64.encodeToString(blob.iv, Base64.NO_WRAP),
                    encryptedData = Base64.encodeToString(blob.ciphertext, Base64.NO_WRAP)
                )
                try {
                    val response = api.sendTelemetry(pairingId, bearer(token), payload)
                    val code = response.code()
                    when {
                        response.isSuccessful -> sentIds += event.id
                        code == 401 || code == 403 -> {
                            if (runAttemptCount < 3) {
                                Log.w(TAG, "Pairing rejected (HTTP $code), retrying to account for backend eventual consistency")
                                outcome = Result.retry()
                            } else {
                                Log.e(TAG, "Pairing rejected (HTTP $code); clearing pairing")
                                pairing.markRevoked()
                                outcome = Result.success()
                            }
                        }
                        code == 408 || code == 429 || code >= 500 -> {
                            Log.w(TAG, "Transient relay failure HTTP $code")
                            outcome = Result.retry()
                        }
                        else -> {
                            // Permanent rejection of this event; drop it so it cannot block the queue.
                            Log.e(TAG, "Dropping event ${event.id}: HTTP $code")
                            sentIds += event.id
                        }
                    }
                } catch (e: java.io.IOException) {
                    Log.w(TAG, "Network error relaying event ${event.id}", e)
                    outcome = Result.retry()
                }
                if (outcome != null) break
            }

            if (sentIds.isNotEmpty()) dao.deleteEvents(sentIds)
            outcome?.let { return it }
        }
    }

    private companion object {
        const val TAG = "OutboxRelayWorker"
        const val BATCH_SIZE = 50
    }
}
