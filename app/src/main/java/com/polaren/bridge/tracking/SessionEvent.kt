package com.polaren.bridge.tracking

enum class SessionEventType { TRIP_START, TRIP_END, CHARGE_START, CHARGE_END }

/** A discrete session boundary event; [data] holds only JSON-friendly values. */
data class SessionEvent(
    val type: SessionEventType,
    val timestampMs: Long,
    val data: Map<String, Any?>
)

typealias SessionEventSink = (SessionEvent) -> Unit
