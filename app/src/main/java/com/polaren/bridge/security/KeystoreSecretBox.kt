package com.polaren.bridge.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Protects small secrets (pairing secret, relay token) at rest using a non-exportable Android
 * Keystore key. This key is never used for relay payloads, since the iOS app could not obtain it.
 * Output layout: iv (12 bytes) || ciphertext.
 */
object KeystoreSecretBox {
    private const val KEY_ALIAS = "polaren_bridge_storage_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val IV_LENGTH_BYTES = 12

    fun seal(plaintext: ByteArray): ByteArray {
        val blob = CryptoHelper.encrypt(getOrCreateKey(), plaintext)
        return blob.iv + blob.ciphertext
    }

    fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > IV_LENGTH_BYTES) { "Sealed data too short" }
        val iv = sealed.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = sealed.copyOfRange(IV_LENGTH_BYTES, sealed.size)
        return CryptoHelper.decrypt(getOrCreateKey(), iv, ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }
}
