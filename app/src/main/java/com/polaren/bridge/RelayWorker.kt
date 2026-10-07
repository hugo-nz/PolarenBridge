package com.polaren.bridge

import android.content.Context
import android.util.Base64
import android.util.Log
import com.polaren.bridge.data.AppDatabase
import com.polaren.bridge.data.TelemetryEvent
import com.polaren.bridge.network.NetworkModule
import com.polaren.bridge.network.TelemetryPayload
import com.polaren.bridge.security.CryptoHelper
import com.polaren.bridge.security.PairingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RelayWorker(private val context: Context) {
    private val TAG = "RelayWorker"
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val database = AppDatabase.getDatabase(context)
    private val pairingState = PairingState(context)
    private val api = NetworkModule.relayApi

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    processOutbox()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in RelayWorker loop", e)
                }
                delay(60_000) // Poll every minute. In production use WorkManager or JobScheduler for battery efficiency
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun processOutbox() {
        if (!pairingState.isPaired) {
            Log.d(TAG, "Not paired, skipping relay.")
            return
        }

        val pairingId = pairingState.pairingId ?: return
        val events = database.telemetryDao().getPendingEvents(50)

        if (events.isEmpty()) return

        Log.i(TAG, "Processing ${events.size} outbox events")
        val successfullySentIds = mutableListOf<Long>()

        for (event in events) {
            try {
                // Ensure the payload is encrypted before sending
                val payloadToSend = if (!event.isEncrypted) {
                    val (iv, encryptedBytes) = CryptoHelper.encrypt(event.payloadJson.toByteArray())
                    val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                    val dataBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

                    TelemetryPayload(ivBase64, dataBase64)
                } else {
                    // If we somehow stored it already encrypted (not typical for this outbox design, but for safety)
                    Log.w(TAG, "Event ${event.id} is already encrypted in DB, skipping or handle differently")
                    continue
                }

                val response = api.sendTelemetry(pairingId, "Bearer temp_token", payloadToSend)
                if (response.isSuccessful) {
                    successfullySentIds.add(event.id)
                } else {
                    Log.e(TAG, "Failed to send event ${event.id}: HTTP ${response.code()}")
                    if (response.code() == 401 || response.code() == 403) {
                         Log.e(TAG, "Pairing revoked or expired. Stopping relay.")
                         pairingState.clear()
                         // Need to update UI/Notification here
                         break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception sending event ${event.id}", e)
                break // Stop processing this batch on network error to preserve order/avoid spamming
            }
        }

        if (successfullySentIds.isNotEmpty()) {
            database.telemetryDao().deleteEvents(successfullySentIds)
            Log.i(TAG, "Deleted ${successfullySentIds.size} successfully sent events from outbox")
        }
    }
}
