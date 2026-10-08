package com.polaren.bridge.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedBlob(val iv: ByteArray, val ciphertext: ByteArray)

/**
 * AES-256-GCM helper. The caller supplies the key (relay payloads use the pairing-derived key from
 * [KeyDerivation]); this class holds no key material of its own.
 */
object CryptoHelper {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128
    private val secureRandom = SecureRandom()

    fun encrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray? = null): EncryptedBlob {
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        return EncryptedBlob(iv, cipher.doFinal(plaintext))
    }

    fun decrypt(key: SecretKey, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(ciphertext)
    }
}
