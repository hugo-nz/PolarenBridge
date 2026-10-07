package com.polaren.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.polaren.bridge.car.CarPropertyMonitor

class CarSyncService : Service() {

    private var carPropertyMonitor: CarPropertyMonitor? = null
    private var relayWorker: RelayWorker? = null

    companion object {
        private const val TAG = "CarSyncService"
        private const val CHANNEL_ID = "polaren_sync_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Service starting")
        createNotificationChannel()
        carPropertyMonitor = CarPropertyMonitor(this)
        relayWorker = RelayWorker(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Starting foreground")

        // Ensure the pairing activity can be launched from the notification
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PairingActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Polaren Bridge")
            .setContentText("Syncing with vehicle data")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        relayWorker?.start()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: Service stopped")
        carPropertyMonitor?.disconnect()
        relayWorker?.stop()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Vehicle Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains connection with vehicle telemetry for Polaren"
            setShowBadge(false)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
