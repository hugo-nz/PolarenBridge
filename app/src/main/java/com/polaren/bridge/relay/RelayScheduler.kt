package com.polaren.bridge.relay

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RelayScheduler {
    private const val PERIODIC_NAME = "polaren_outbox_relay_periodic"
    private const val IMMEDIATE_NAME = "polaren_outbox_relay_now"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Safety-net drain that also retries anything left over from failed attempts. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<OutboxRelayWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Drains the outbox as soon as the network allows, e.g. right after a new event is queued. */
    fun triggerNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutboxRelayWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
