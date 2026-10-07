package com.polaren.bridge.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry_outbox")
data class TelemetryEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String, // "TRIP_START", "TRIP_END", "CHARGE_START", "CHARGE_UPDATE"
    val timestampMs: Long,
    val payloadJson: String,
    val isEncrypted: Boolean = false
)