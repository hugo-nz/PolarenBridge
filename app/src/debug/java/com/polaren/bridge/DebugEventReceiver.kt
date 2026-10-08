package com.polaren.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.polaren.bridge.data.AppDatabase
import com.polaren.bridge.data.TelemetryEvent
import com.polaren.bridge.relay.RelayScheduler
import com.polaren.bridge.tracking.SessionEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * adb shell am broadcast -n com.polaren.bridge/.DebugEventReceiver --es type CHARGE_START
 * Queues a synthetic event in the outbox and triggers the relay.
 */
class DebugEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type")?.takeIf { t -> SessionEventType.values().any { it.name == t } }
            ?: SessionEventType.CHARGE_START.name
        val now = System.currentTimeMillis()
        val payload = linkedMapOf<String, Any?>(
            "eventId" to UUID.randomUUID().toString(),
            "type" to type,
            "timestampMs" to now,
            "debug" to true
        )
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getDatabase(context.applicationContext).telemetryDao().insertEvent(
                    TelemetryEvent(eventType = type, timestampMs = now, payloadJson = Gson().toJson(payload))
                )
                Log.i("DebugEventReceiver", "Queued $type")
                RelayScheduler.triggerNow(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
