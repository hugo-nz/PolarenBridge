package com.polaren.bridge.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {
    @Insert
    suspend fun insertEvent(event: TelemetryEvent)

    @Query("SELECT * FROM telemetry_outbox ORDER BY timestampMs ASC LIMIT :limit")
    suspend fun getPendingEvents(limit: Int = 50): List<TelemetryEvent>

    @Query("SELECT * FROM telemetry_outbox ORDER BY timestampMs ASC")
    fun observePendingEvents(): Flow<List<TelemetryEvent>>

    @Query("DELETE FROM telemetry_outbox WHERE id IN (:ids)")
    suspend fun deleteEvents(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM telemetry_outbox")
    suspend fun count(): Int
}