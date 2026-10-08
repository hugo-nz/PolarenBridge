package com.polaren.bridge.security
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingStateInstrumentedTest {
    @Test
    fun testPairingState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = PairingState(context)
        state.clear()
        state.savePairing("id123", "token123", "secret123".toByteArray())
        assertTrue(state.isPaired)
        assertEquals("id123", state.pairingId)
        assertEquals("token123", state.relayToken)
        assertNotNull(state.relayKey())
    }
}
