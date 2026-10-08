package com.polaren.bridge.tracking

/**
 * Detects trip boundaries from plain values (no Car API dependency, so it is JVM-testable).
 *
 * A trip starts when speed reaches [startSpeedMps]. It ends when the vehicle stays below that speed
 * for [stopTimeoutMs] (reported at the time it last moved), or immediately when the ignition goes off.
 * Call [tick] periodically so the stop timeout fires even when no new samples arrive.
 */
class TripSessionTracker(
    private val sink: SessionEventSink,
    private val extras: () -> Map<String, Any?> = { emptyMap() },
    private val startSpeedMps: Float = 2f,
    private val stopTimeoutMs: Long = 5 * 60_000L,
    private val maxIntegrationGapMs: Long = 60_000L
) {
    private var active = false
    private var startMs = 0L
    private var lastMovingMs = 0L
    private var lastSampleMs = 0L
    private var lastSpeedMps = 0f
    private var integratedMeters = 0.0
    private var startOdometerKm: Float? = null
    private var startEnergyWh: Float? = null

    private var odometerKm: Float? = null
    private var energyWh: Float? = null

    val isActive: Boolean @Synchronized get() = active

    @Synchronized
    fun onOdometer(km: Float) {
        odometerKm = km
    }

    @Synchronized
    fun onBatteryEnergy(wh: Float) {
        energyWh = wh
    }

    @Synchronized
    fun onSpeed(speedMps: Float, nowMs: Long) {
        val moving = speedMps >= startSpeedMps
        if (!active) {
            if (moving) startTrip(nowMs, speedMps)
            return
        }

        val gap = nowMs - lastSampleMs
        if (gap in 1..maxIntegrationGapMs) {
            integratedMeters += lastSpeedMps * (gap / 1000.0)
        }
        lastSampleMs = nowMs
        lastSpeedMps = speedMps
        if (moving) {
            lastMovingMs = nowMs
        } else {
            endIfIdle(nowMs)
        }
    }

    @Synchronized
    fun onIgnition(on: Boolean, nowMs: Long) {
        if (!on && active) endTrip(nowMs)
    }

    @Synchronized
    fun tick(nowMs: Long) {
        if (active) endIfIdle(nowMs)
    }

    private fun endIfIdle(nowMs: Long) {
        if (nowMs - lastMovingMs >= stopTimeoutMs) endTrip(lastMovingMs)
    }

    private fun startTrip(nowMs: Long, speedMps: Float) {
        active = true
        startMs = nowMs
        lastMovingMs = nowMs
        lastSampleMs = nowMs
        lastSpeedMps = speedMps
        integratedMeters = 0.0
        startOdometerKm = odometerKm
        startEnergyWh = energyWh
        sink(
            SessionEvent(
                SessionEventType.TRIP_START, nowMs,
                buildMap {
                    startOdometerKm?.let { put("odometerKm", it) }
                    startEnergyWh?.let { put("batteryEnergyWh", it) }
                }
            )
        )
    }

    private fun endTrip(endMs: Long) {
        val endOdometer = odometerKm
        val endEnergy = energyWh
        val data = buildMap<String, Any?> {
            put("startMs", startMs)
            put("durationMs", (endMs - startMs).coerceAtLeast(0))
            val odoStart = startOdometerKm
            if (odoStart != null && endOdometer != null) {
                put("startOdometerKm", odoStart)
                put("endOdometerKm", endOdometer)
                put("distanceKm", (endOdometer - odoStart).toDouble())
            } else {
                put("distanceKm", integratedMeters / 1000.0)
            }
            val energyStart = startEnergyWh
            if (energyStart != null && endEnergy != null) {
                put("startBatteryEnergyWh", energyStart)
                put("endBatteryEnergyWh", endEnergy)
                put("energyUsedWh", (energyStart - endEnergy).toDouble())
            }
            putAll(extras())
        }
        active = false
        sink(SessionEvent(SessionEventType.TRIP_END, endMs, data))
    }
}
