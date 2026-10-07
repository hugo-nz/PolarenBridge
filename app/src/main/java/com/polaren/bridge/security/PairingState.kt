package com.polaren.bridge.security

import android.content.Context
import android.content.SharedPreferences

class PairingState(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("polaren_pairing", Context.MODE_PRIVATE)

    var isPaired: Boolean
        get() = prefs.getBoolean("is_paired", false)
        set(value) = prefs.edit().putBoolean("is_paired", value).apply()

    var pairingId: String?
        get() = prefs.getString("pairing_id", null)
        set(value) = prefs.edit().putString("pairing_id", value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}