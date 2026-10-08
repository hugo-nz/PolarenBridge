package com.polaren.bridge.security
import org.junit.Test
import org.junit.Assert.*
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PairingStateTest {
    @Test
    fun testPairingState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = PairingState(context)
        state.savePairing("id", "token", "secret".toByteArray())
        assertTrue(state.isPaired)
    }
}
