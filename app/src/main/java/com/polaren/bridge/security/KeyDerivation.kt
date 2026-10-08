package com.polaren.bridge.security

import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Derives the relay encryption key from the pairing shared secret. The iOS app performs the
 * identical derivation, so both ends hold the same key while the backend never sees it.
 */
object KeyDerivation {
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val HASH_LENGTH = 32
    const val RELAY_KEY_INFO = "polaren-relay-v1"
    const val SECRET_LENGTH_BYTES = 32

    /** HKDF-SHA256 (RFC 5869). */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * HASH_LENGTH)) { "Invalid HKDF output length" }
        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LENGTH) else salt
        val prk = hmac(effectiveSalt, ikm)

        val okm = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val toCopy = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, okm, offset, toCopy)
            offset += toCopy
            counter++
        }
        return okm
    }

    /** AES-256 key bound to this pairing: HKDF(secret, salt = pairingId, info = "polaren-relay-v1"). */
    fun deriveRelayKey(sharedSecret: ByteArray, pairingId: String): SecretKey {
        val keyBytes = hkdfSha256(
            ikm = sharedSecret,
            salt = pairingId.toByteArray(Charsets.UTF_8),
            info = RELAY_KEY_INFO.toByteArray(Charsets.UTF_8),
            length = 32
        )
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(data)
    }
}
