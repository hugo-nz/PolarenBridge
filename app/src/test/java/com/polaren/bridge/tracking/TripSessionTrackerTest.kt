package com.polaren.bridge.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSessionTrackerTest {
    private val events = mutableListOf<SessionEvent>()
    private val tracker = TripSessionTracker(sink = { events += it }, stopTimeoutMs = 300_000)

    @Test
    fun startsTripWhenSpeedExceedsThreshold() {
        tracker.onSpeed(0.5f, 1_000)
        assertTrue(events.isEmpty())
        tracker.onSpeed(5f, 2_000)
        assertEquals(listOf(SessionEventType.TRIP_START), events.map { it.type })
        assertEquals(2_000L, events[0].timestampMs)
    }

    @Test
    fun endsTripAfterStopTimeoutAtLastMovingTime() {
        tracker.onSpeed(10f, 0)
        tracker.onSpeed(10f, 10_000)
        tracker.onSpeed(0f, 20_000)
        tracker.tick(200_000)
        assertTrue(tracker.isActive)
        tracker.tick(10_000 + 300_000)

        assertFalse(tracker.isActive)
        val end = events.last()
        assertEquals(SessionEventType.TRIP_END, end.type)
        assertEquals(10_000L, end.timestampMs)
        assertEquals(10_000L, end.data["durationMs"])
    }

    @Test
    fun brieflyStoppedTripContinues() {
        tracker.onSpeed(10f, 0)
        tracker.onSpeed(0f, 30_000)
        tracker.onSpeed(10f, 60_000)
        tracker.tick(120_000)
        assertEquals(1, events.size)
        assertTrue(tracker.isActive)
    }

    @Test
    fun ignitionOffEndsTripImmediately() {
        tracker.onSpeed(10f, 0)
        tracker.onIgnition(false, 5_000)
        assertEquals(SessionEventType.TRIP_END, events.last().type)
        assertEquals(5_000L, events.last().timestampMs)
    }

    @Test
    fun reportsOdometerAndEnergyDeltas() {
        tracker.onOdometer(1000f)
        tracker.onBatteryEnergy(50_000f)
        tracker.onSpeed(10f, 0)
        tracker.onOdometer(1012.5f)
        tracker.onBatteryEnergy(48_000f)
        tracker.onIgnition(false, 60_000)

        val data = events.last().data
        assertEquals(12.5, data["distanceKm"] as Double, 1e-6)
        assertEquals(2000.0, data["energyUsedWh"] as Double, 1e-6)
    }

    @Test
    fun fallsBackToIntegratedSpeedWithoutOdometer() {
        tracker.onSpeed(10f, 0)
        tracker.onSpeed(10f, 10_000)
        tracker.onIgnition(false, 10_000)
        assertEquals(0.1, events.last().data["distanceKm"] as Double, 1e-6)
    }
}
