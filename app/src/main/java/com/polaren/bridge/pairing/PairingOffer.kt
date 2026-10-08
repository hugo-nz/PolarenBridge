package com.polaren.bridge.pairing

import com.google.gson.Gson
import java.security.SecureRandom
import java.util.Base64

/**
 * A pairing offer shown as a QR code. [secret] is generated on this device and only ever travels
 * through the QR code to the iOS app, so the backend cannot derive the payload key.
 */
class PairingOffer(
    val pairingId: String,
    val relayToken: String,
    val expiresAtMs: Long,
    val secret: ByteArray
) {
    /** QR content, read by the iOS app. Format is documented in docs/PAIRING_PROTOCOL.md. */
    fun toQrPayload(apiBaseUrl: String): String = Gson().toJson(
        linkedMapOf(
            "v" to QR_VERSION,
            "pid" to pairingId,
            "sec" to Base64.getUrlEncoder().withoutPadding().encodeToString(secret),
            "api" to apiBaseUrl
        )
    )

    companion object {
        const val QR_VERSION = 1

        fun newSecret(length: Int = 32): ByteArray =
            ByteArray(length).also { SecureRandom().nextBytes(it) }
    }
}
