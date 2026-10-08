package com.polaren.bridge.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException

class KeyDerivationTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun hkdf_matchesRfc5869TestCase1() {
        val okm = KeyDerivation.hkdfSha256(
            ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42
        )
        assertArrayEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            okm
        )
    }

    @Test
    fun relayKey_isDeterministicAndPairingBound() {
        val secret = ByteArray(32) { it.toByte() }
        val a = KeyDerivation.deriveRelayKey(secret, "pair-1")
        assertArrayEquals(a.encoded, KeyDerivation.deriveRelayKey(secret, "pair-1").encoded)
        assertEquals(32, a.encoded.size)
        assert(!a.encoded.contentEquals(KeyDerivation.deriveRelayKey(secret, "pair-2").encoded))
    }

    @Test
    fun encryptDecrypt_roundTripWithAad() {
        val key = KeyDerivation.deriveRelayKey(ByteArray(32) { 7 }, "pair-1")
        val plaintext = """{"type":"TRIP_START"}""".toByteArray()
        val aad = "pair-1".toByteArray()

        val blob = CryptoHelper.encrypt(key, plaintext, aad)
        assertArrayEquals(plaintext, CryptoHelper.decrypt(key, blob.iv, blob.ciphertext, aad))
    }

    @Test
    fun decrypt_failsWithWrongAad() {
        val key = KeyDerivation.deriveRelayKey(ByteArray(32) { 7 }, "pair-1")
        val blob = CryptoHelper.encrypt(key, "x".toByteArray(), "pair-1".toByteArray())
        assertThrows(GeneralSecurityException::class.java) {
            CryptoHelper.decrypt(key, blob.iv, blob.ciphertext, "pair-2".toByteArray())
        }
    }
}
