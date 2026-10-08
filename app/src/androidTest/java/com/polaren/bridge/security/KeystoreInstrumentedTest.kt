package com.polaren.bridge.security
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import android.util.Base64

@RunWith(AndroidJUnit4::class)
class KeystoreInstrumentedTest {
    @Test
    fun testKeystore() {
        val secret = "my_secret_token".toByteArray()
        val sealed = KeystoreSecretBox.seal(secret)
        val opened = KeystoreSecretBox.open(sealed)
        assertArrayEquals(secret, opened)
    }
}
