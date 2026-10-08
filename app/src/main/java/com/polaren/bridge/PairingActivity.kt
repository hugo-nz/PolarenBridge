package com.polaren.bridge

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.polaren.bridge.network.NetworkModule
import com.polaren.bridge.pairing.PairingOutcome
import com.polaren.bridge.pairing.PairingRepository
import com.polaren.bridge.pairing.QrCode
import com.polaren.bridge.relay.RelayScheduler
import com.polaren.bridge.security.PairingState
import com.polaren.bridge.ui.theme.PolarenBridgeTheme
import kotlinx.coroutines.CancellationException

class PairingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make sure the background service is running even on first launch after install.
        startForegroundService(Intent(this, CarSyncService::class.java))

        setContent {
            PolarenBridgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PairingScreen(onPairingComplete = { finish() })
                }
            }
        }
    }
}

private sealed interface PairingUiState {
    data object Loading : PairingUiState
    data object Paired : PairingUiState
    data class ShowQr(val modules: Array<BooleanArray>) : PairingUiState
    data class Failed(val message: String) : PairingUiState
}

@Composable
fun PairingScreen(onPairingComplete: () -> Unit) {
    val context = LocalContext.current
    val pairingState = remember { PairingState(context) }
    val repository = remember {
        PairingRepository(NetworkModule.relayApi, pairingState)
    }
    var attempt by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf<PairingUiState>(if (pairingState.isPaired) PairingUiState.Paired else PairingUiState.Loading) }

    LaunchedEffect(attempt) {
        if (pairingState.isPaired) {
            uiState = PairingUiState.Paired
            return@LaunchedEffect
        }
        uiState = PairingUiState.Loading
        try {
            val offer = repository.createOffer()
            uiState = PairingUiState.ShowQr(QrCode.encode(offer.toQrPayload(NetworkModule.BASE_URL)))
            when (repository.awaitConfirmation(offer)) {
                PairingOutcome.Confirmed -> {
                    RelayScheduler.triggerNow(context)
                    onPairingComplete()
                }
                PairingOutcome.Expired -> attempt++ // Offer expired: show a fresh code.
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PairingScreen", "Pairing failed (base URL ${NetworkModule.BASE_URL})", e)
            uiState = PairingUiState.Failed("Couldn't reach Polaren. Check the car's connection.")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Polaren Bridge", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        if (uiState !is PairingUiState.Paired) {
            Text(
                text = "In the Polaren iOS app, choose Add car and scan this code.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(Modifier.height(24.dp))

        when (val state = uiState) {
            PairingUiState.Loading -> CircularProgressIndicator()
            PairingUiState.Paired -> {
                Text("This car is paired with Polaren.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    pairingState.clear()
                    attempt++
                }) {
                    Text("Unpair")
                }
            }
            is PairingUiState.ShowQr -> QrImage(state.modules)
            is PairingUiState.Failed -> {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { attempt++ }) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun QrImage(modules: Array<BooleanArray>) {
    Canvas(modifier = Modifier.size(280.dp).background(Color.White)) {
        val count = modules.size
        val cell = size.width / count
        for (y in 0 until count) {
            for (x in 0 until count) {
                if (modules[y][x]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f)
                    )
                }
            }
        }
    }
}
