package com.polaren.bridge.tracking

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.polaren.bridge.car.CarPropertyMonitor
import com.polaren.bridge.data.AppDatabase
import com.polaren.bridge.data.TelemetryEvent
import com.polaren.bridge.relay.RelayScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Feeds [CarPropertyMonitor] flows into the session trackers and persists the resulting events to
 * the outbox, then nudges the relay. Events are written in emission order via a single consumer.
 */
class SessionCoordinator(
    private val context: Context,
    private val monitor: CarPropertyMonitor,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = AppDatabase.getDatabase(context).telemetryDao()
    private val gson = Gson()
    private val events = Channel<SessionEvent>(Channel.UNLIMITED)

    private val tripTracker = TripSessionTracker(
        sink = { events.trySend(it) },
        extras = ::secondaryTelemetry
    )
    private val chargingTracker = ChargingSessionTracker(
        sink = { events.trySend(it) },
        extras = ::secondaryTelemetry
    )

    fun start() {
        scope.launch {
            for (event in events) persist(event)
        }
        scope.launch { monitor.speedMps.collect { tripTracker.onSpeed(it, clock()) } }
        scope.launch { monitor.ignitionOn.filterNotNull().collect { tripTracker.onIgnition(it, clock()) } }
        scope.launch { monitor.odometerKm.filterNotNull().collect(tripTracker::onOdometer) }
        scope.launch {
            monitor.batteryEnergyWh.filterNotNull().collect {
                tripTracker.onBatteryEnergy(it)
                chargingTracker.onBatteryEnergy(it)
            }
        }
        scope.launch { monitor.isCharging.collect { chargingTracker.onCharging(it, clock()) } }
        scope.launch {
            while (true) {
                delay(TICK_INTERVAL_MS)
                val now = clock()
                tripTracker.tick(now)
                chargingTracker.tick(now)
            }
        }
    }

    private fun secondaryTelemetry(): Map<String, Any?> = buildMap {
        monitor.lowVoltageBatteryVolts.value?.let { put("lowVoltageBatteryVolts", it) }
        monitor.batteryTemperatureC.value?.let { put("batteryTemperatureC", it) }
    }

    private suspend fun persist(event: SessionEvent) {
        val payload = LinkedHashMap<String, Any?>().apply {
            put("eventId", UUID.randomUUID().toString())
            put("type", event.type.name)
            put("timestampMs", event.timestampMs)
            putAll(event.data)
        }
        try {
            dao.insertEvent(
                TelemetryEvent(
                    eventType = event.type.name,
                    timestampMs = event.timestampMs,
                    payloadJson = gson.toJson(payload)
                )
            )
            Log.i(TAG, "Queued ${event.type}")
            RelayScheduler.triggerNow(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue ${event.type}", e)
        }
    }

    private companion object {
        const val TAG = "SessionCoordinator"
        const val TICK_INTERVAL_MS = 30_000L
    }
}
