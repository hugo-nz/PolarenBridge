package com.polaren.bridge.pairing

import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PairingOfferTest {
    @Test
    fun qrPayloadCarriesSecretButNotRelayToken() {
        val secret = ByteArray(32) { it.toByte() }
        val offer = PairingOffer("pid-1", "token-xyz", 123L, secret)
        val payload = offer.toQrPayload("https://api.example.com/")

        assertTrue("token-xyz" !in payload)
        val json = JsonParser.parseString(payload).asJsonObject
        assertEquals(1, json["v"].asInt)
        assertEquals("pid-1", json["pid"].asString)
        assertEquals("https://api.example.com/", json["api"].asString)
        assertArrayEquals(secret, Base64.getUrlDecoder().decode(json["sec"].asString))
    }

    @Test
    fun qrEncodesToSquareMatrix() {
        val m = QrCode.encode("hello")
        assertTrue(m.isNotEmpty())
        assertTrue(m.all { it.size == m.size })
    }

    @Test
    fun newSecretsAreRandom() {
        assertTrue(!PairingOffer.newSecret().contentEquals(PairingOffer.newSecret()))
    }
}
