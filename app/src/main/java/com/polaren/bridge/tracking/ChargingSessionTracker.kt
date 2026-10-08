package com.polaren.bridge.tracking

/**
 * Detects charging-session boundaries from plain values (JVM-testable).
 *
 * A session starts on the first "charging" sample and ends once charging has been absent for
 * [endDebounceMs], which absorbs brief drops from charger handshakes or load balancing.
 * Call [tick] periodically so the debounce can expire without new samples.
 */
class ChargingSessionTracker(
    private val sink: SessionEventSink,
    private val extras: () -> Map<String, Any?> = { emptyMap() },
    private val endDebounceMs: Long = 60_000L
) {
    private var active = false
    private var startMs = 0L
    private var notChargingSinceMs: Long? = null
    private var startEnergyWh: Float? = null
    private var energyWh: Float? = null

    val isActive: Boolean @Synchronized get() = active

    @Synchronized
    fun onBatteryEnergy(wh: Float) {
        energyWh = wh
    }

    @Synchronized
    fun onCharging(charging: Boolean, nowMs: Long) {
        if (charging) {
            notChargingSinceMs = null
            if (!active) {
                active = true
                startMs = nowMs
                startEnergyWh = energyWh
                sink(
                    SessionEvent(
                        SessionEventType.CHARGE_START, nowMs,
                        buildMap { startEnergyWh?.let { put("batteryEnergyWh", it) } }
                    )
                )
            }
        } else if (active) {
            if (notChargingSinceMs == null) notChargingSinceMs = nowMs
            tick(nowMs)
        }
    }

    @Synchronized
    fun tick(nowMs: Long) {
        val since = notChargingSinceMs ?: return
        if (active && nowMs - since >= endDebounceMs) endSession(since)
    }

    private fun endSession(endMs: Long) {
        val energyStart = startEnergyWh
        val energyEnd = energyWh
        val data = buildMap<String, Any?> {
            put("startMs", startMs)
            put("durationMs", (endMs - startMs).coerceAtLeast(0))
            if (energyStart != null && energyEnd != null) {
                put("startBatteryEnergyWh", energyStart)
                put("endBatteryEnergyWh", energyEnd)
                put("energyAddedWh", (energyEnd - energyStart).toDouble())
            }
            putAll(extras())
        }
        active = false
        notChargingSinceMs = null
        sink(SessionEvent(SessionEventType.CHARGE_END, endMs, data))
    }
}
