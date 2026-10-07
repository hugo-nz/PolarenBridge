package com.polaren.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.car.intent.action.RECEIVER_AUTO_START") {
            Log.i("BootReceiver", "Starting CarSyncService from boot")
            val serviceIntent = Intent(context, CarSyncService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
