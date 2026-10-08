# Pairing & relay protocol (v1)

Contract between the car app, the relay backend and the iOS app. The backend endpoints below do not
exist yet in `polestar_power_app_v1`; this is the shape the car app implements.

## Pairing

1. Car: `POST /v1/pairing/offers` → `{ pairingId, relayToken, expiresAtMs }`.
   `relayToken` is a bearer token for this car only.
2. Car generates a 32-byte random `secret` locally (never sent to the backend) and shows a QR code:
   `{"v":1,"pid":"<pairingId>","sec":"<base64url secret, no padding>","api":"<base URL>"}`.
3. iOS scans the QR code and confirms the pairing with the backend (endpoint TBD, iOS-authenticated).
4. Car polls `GET /v1/pairing/{pairingId}` with `Authorization: Bearer <relayToken>` →
   `{ status: PENDING | CONFIRMED | EXPIRED | REVOKED }`. On `CONFIRMED` it stores `pairingId`,
   `relayToken` and `secret` (sealed with an Android Keystore key).

## Key derivation (both sides)

`key = HKDF-SHA256(ikm = secret, salt = UTF-8(pairingId), info = UTF-8("polaren-relay-v1"), L = 32)`

## Relay

`POST /v1/relay/{pairingId}/telemetry` with `Authorization: Bearer <relayToken>`:

```json
{ "version": 1, "iv": "<base64 12-byte nonce>", "encryptedData": "<base64 ciphertext||16-byte tag>" }
```

AES-256-GCM, AAD = UTF-8(pairingId). The backend relays this blob to iOS and discards it. HTTP 401/403
means the pairing was revoked: the car clears its pairing and shows "Pairing lost — tap to reconnect".

## Decrypted event JSON

Common fields: `eventId` (UUID, for de-duplication), `type`, `timestampMs`.

| type | extra fields (all optional) |
|---|---|
| `TRIP_START` | `odometerKm`, `batteryEnergyWh` |
| `TRIP_END` | `startMs`, `durationMs`, `distanceKm`, `startOdometerKm`, `endOdometerKm`, `startBatteryEnergyWh`, `endBatteryEnergyWh`, `energyUsedWh`, `lowVoltageBatteryVolts`, `batteryTemperatureC` |
| `CHARGE_START` | `batteryEnergyWh` |
| `CHARGE_END` | `startMs`, `durationMs`, `startBatteryEnergyWh`, `endBatteryEnergyWh`, `energyAddedWh`, `lowVoltageBatteryVolts`, `batteryTemperatureC` |

Units: km, Wh, volts, °C, epoch milliseconds.

## Build configuration

Gradle properties: `polaren.relayBaseUrl`, and (OEM-specific, 0 = disabled)
`polaren.lvBatteryVoltagePropertyId`, `polaren.batteryTemperaturePropertyId`.
