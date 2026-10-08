package com.polaren.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.polaren.bridge.car.CarPropertyMonitor
import com.polaren.bridge.relay.RelayScheduler
import com.polaren.bridge.security.PairingState
import com.polaren.bridge.tracking.SessionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CarSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var carPropertyMonitor: CarPropertyMonitor? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pairingState = PairingState(this)
        startForeground(
            NOTIFICATION_ID,
            buildNotification(pairingState.isPaired, pairingState.wasRevoked),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        if (!started) {
            started = true
            val monitor = CarPropertyMonitor(this).also { carPropertyMonitor = it }
            SessionCoordinator(this, monitor, scope).start()
            monitor.connect()
            RelayScheduler.schedulePeriodic(this)

            // Keep the notification in sync with pairing status (e.g. when the backend revokes it).
            scope.launch {
                PairingState.paired.collect { paired ->
                    getSystemService(NotificationManager::class.java).notify(
                        NOTIFICATION_ID,
                        buildNotification(paired, PairingState(this@CarSyncService).wasRevoked)
                    )
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        carPropertyMonitor?.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(paired: Boolean, revoked: Boolean): Notification {
        val text = when {
            paired -> "Connected to Polaren"
            revoked -> "Pairing lost — tap to reconnect"
            else -> "Not paired — tap to reconnect"
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PairingActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Polaren Bridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Vehicle Sync", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keeps Polaren connected to vehicle data"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val TAG = "CarSyncService"
        const val CHANNEL_ID = "polaren_sync_channel"
        const val NOTIFICATION_ID = 1001
    }
}
