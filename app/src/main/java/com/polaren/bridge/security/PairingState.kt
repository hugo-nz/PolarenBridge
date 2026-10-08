package com.polaren.bridge.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.crypto.SecretKey

/** Pairing identity. The shared secret and relay token are sealed with a Keystore key at rest. */
class PairingState(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("polaren_pairing", Context.MODE_PRIVATE)

    init {
        _paired.value = readIsPaired()
    }

    val isPaired: Boolean get() = readIsPaired()

    val pairingId: String? get() = prefs.getString(KEY_PAIRING_ID, null)

    /** Bearer token the backend issued to this car for relay calls. */
    val relayToken: String?
        get() = readSealed(KEY_RELAY_TOKEN)?.toString(Charsets.UTF_8)

    /** True when a previously valid pairing was rejected by the backend and needs re-pairing. */
    val wasRevoked: Boolean get() = prefs.getBoolean(KEY_REVOKED, false)

    /** Encryption key for relay payloads, derived from the pairing secret. */
    fun relayKey(): SecretKey? {
        val id = pairingId ?: return null
        val secret = readSealed(KEY_SECRET) ?: return null
        return KeyDerivation.deriveRelayKey(secret, id)
    }

    fun savePairing(pairingId: String, relayToken: String, sharedSecret: ByteArray) {
        prefs.edit()
            .putString(KEY_PAIRING_ID, pairingId)
            .putString(KEY_RELAY_TOKEN, seal(relayToken.toByteArray(Charsets.UTF_8)))
            .putString(KEY_SECRET, seal(sharedSecret))
            .putBoolean(KEY_REVOKED, false)
            .commit()
        _paired.value = true
        Log.d(TAG, "savePairing completed")
    }

    fun clear() {
        Log.d(TAG, "clear called")
        prefs.edit().clear().commit()
        _paired.value = false
    }

    /** Clears credentials but remembers that the user must re-pair. */
    fun markRevoked() {
        Log.d(TAG, "markRevoked called")
        prefs.edit().clear().putBoolean(KEY_REVOKED, true).commit()
        _paired.value = false
    }

    private fun readIsPaired(): Boolean =
        prefs.contains(KEY_PAIRING_ID) && prefs.contains(KEY_SECRET) && prefs.contains(KEY_RELAY_TOKEN)

    private fun seal(bytes: ByteArray): String =
        Base64.encodeToString(KeystoreSecretBox.seal(bytes), Base64.NO_WRAP)

    private fun readSealed(key: String): ByteArray? {
        val encoded = prefs.getString(key, null) ?: return null
        return try {
            KeystoreSecretBox.open(Base64.decode(encoded, Base64.NO_WRAP))
        } catch (e: Exception) {
            // Keystore key lost (e.g. head unit reset): the pairing is unusable.
            Log.e(TAG, "Unable to unseal $key; clearing pairing", e)
            markRevoked()
            null
        }
    }

    companion object {
        private const val TAG = "PairingState"
        private const val KEY_PAIRING_ID = "pairing_id"
        private const val KEY_RELAY_TOKEN = "relay_token_sealed"
        private const val KEY_SECRET = "shared_secret_sealed"
        private const val KEY_REVOKED = "revoked"

        private val _paired = MutableStateFlow(false)

        /** Process-wide pairing status, used to keep the foreground notification current. */
        val paired: StateFlow<Boolean> = _paired
    }
}
