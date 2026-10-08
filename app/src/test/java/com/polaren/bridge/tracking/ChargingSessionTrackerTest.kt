package com.polaren.bridge.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingSessionTrackerTest {
    private val events = mutableListOf<SessionEvent>()
    private val tracker = ChargingSessionTracker(sink = { events += it }, endDebounceMs = 60_000)

    @Test
    fun startsAndEndsSessionWithEnergyDelta() {
        tracker.onBatteryEnergy(20_000f)
        tracker.onCharging(true, 1_000)
        tracker.onBatteryEnergy(35_000f)
        tracker.onCharging(false, 100_000)
        tracker.tick(100_000 + 60_000)

        assertEquals(listOf(SessionEventType.CHARGE_START, SessionEventType.CHARGE_END), events.map { it.type })
        val end = events.last()
        assertEquals(100_000L, end.timestampMs)
        assertEquals(15_000.0, end.data["energyAddedWh"] as Double, 1e-6)
    }

    @Test
    fun briefDropoutDoesNotEndSession() {
        tracker.onCharging(true, 0)
        tracker.onCharging(false, 10_000)
        tracker.onCharging(true, 20_000)
        tracker.tick(200_000)

        assertEquals(1, events.size)
        assertTrue(tracker.isActive)
    }
}
