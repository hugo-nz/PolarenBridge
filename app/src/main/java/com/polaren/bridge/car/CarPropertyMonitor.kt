package com.polaren.bridge.car

import android.car.Car
import android.car.VehicleIgnitionState
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.EvChargeState
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.polaren.bridge.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridges the AAOS Car API to plain Kotlin flows. Every property is registered independently so a
 * SecurityException (OEM allow-listing) or an unsupported property only disables that signal.
 *
 * Units follow VehiclePropertyIds: speed m/s, odometer km, EV battery level Wh, temperatures Celsius.
 */
class CarPropertyMonitor(private val context: Context) {
    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null

    private val _speedMps = MutableStateFlow(0f)
    val speedMps: StateFlow<Float> = _speedMps

    private val _batteryEnergyWh = MutableStateFlow<Float?>(null)
    val batteryEnergyWh: StateFlow<Float?> = _batteryEnergyWh

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging

    private val _ignitionOn = MutableStateFlow<Boolean?>(null)
    val ignitionOn: StateFlow<Boolean?> = _ignitionOn

    private val _odometerKm = MutableStateFlow<Float?>(null)
    val odometerKm: StateFlow<Float?> = _odometerKm

    private val _lowVoltageBatteryVolts = MutableStateFlow<Float?>(null)
    val lowVoltageBatteryVolts: StateFlow<Float?> = _lowVoltageBatteryVolts

    private val _batteryTemperatureC = MutableStateFlow<Float?>(null)
    val batteryTemperatureC: StateFlow<Float?> = _batteryTemperatureC

    // EV_CHARGE_STATE is authoritative; the port-connected signal is only a fallback.
    private var chargeStateSupported = false
    private var chargePortConnected = false

    fun connect() {
        if (car != null) return
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.e(TAG, "Not an automotive device, Car API unavailable.")
            return
        }
        car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER) { connectedCar, isReady ->
            if (isReady) {
                Log.i(TAG, "Car service ready")
                carPropertyManager = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                registerListeners()
            } else {
                Log.w(TAG, "Car service disconnected")
                carPropertyManager = null
            }
        }
    }

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        @Suppress("DEPRECATION")
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> (value.value as? Float)?.let { _speedMps.value = it }
                VehiclePropertyIds.EV_BATTERY_LEVEL -> (value.value as? Float)?.let { _batteryEnergyWh.value = it }
                VehiclePropertyIds.PERF_ODOMETER -> (value.value as? Float)?.let { _odometerKm.value = it }
                VehiclePropertyIds.IGNITION_STATE -> (value.value as? Int)?.let {
                    _ignitionOn.value = it == VehicleIgnitionState.ON || it == VehicleIgnitionState.START
                }
                VehiclePropertyIds.EV_CHARGE_STATE -> (value.value as? Int)?.let {
                    _isCharging.value = it == EvChargeState.STATE_CHARGING
                }
                VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED -> (value.value as? Boolean)?.let {
                    chargePortConnected = it
                    if (!chargeStateSupported) _isCharging.value = it
                }
                else -> onVendorEvent(value)
            }
        }

        private fun onVendorEvent(value: CarPropertyValue<*>) {
            val reading = value.value as? Float ?: return
            if (value.propertyId == BuildConfig.VENDOR_LV_BATTERY_VOLTAGE_PROPERTY_ID) {
                _lowVoltageBatteryVolts.value = reading
            } else if (value.propertyId == BuildConfig.VENDOR_BATTERY_TEMPERATURE_PROPERTY_ID) {
                _batteryTemperatureC.value = reading
            }
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "Error reading property $propertyId in zone $zone")
        }
    }

    private fun registerListeners() {
        val manager = carPropertyManager ?: return

        // Core signals for session detection.
        register(manager, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        register(manager, VehiclePropertyIds.EV_BATTERY_LEVEL, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        register(manager, VehiclePropertyIds.PERF_ODOMETER, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        register(manager, VehiclePropertyIds.IGNITION_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        chargeStateSupported =
            register(manager, VehiclePropertyIds.EV_CHARGE_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        register(manager, VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED, CarPropertyManager.SENSOR_RATE_ONCHANGE)

        // Secondary telemetry: no standard AAOS property exists, so IDs come from build config.
        if (BuildConfig.VENDOR_LV_BATTERY_VOLTAGE_PROPERTY_ID != 0) {
            register(manager, BuildConfig.VENDOR_LV_BATTERY_VOLTAGE_PROPERTY_ID, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }
        if (BuildConfig.VENDOR_BATTERY_TEMPERATURE_PROPERTY_ID != 0) {
            register(manager, BuildConfig.VENDOR_BATTERY_TEMPERATURE_PROPERTY_ID, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }
    }

    private fun register(manager: CarPropertyManager, propertyId: Int, rate: Float): Boolean {
        return try {
            if (manager.getCarPropertyConfig(propertyId) == null) {
                Log.w(TAG, "Property $propertyId not supported by this vehicle")
                false
            } else {
                manager.registerCallback(propertyCallback, propertyId, rate)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Property $propertyId needs a permission this app lacks (OEM allow-list?)", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register property $propertyId", e)
            false
        }
    }

    fun disconnect() {
        runCatching { carPropertyManager?.unregisterCallback(propertyCallback) }
        car?.disconnect()
        car = null
        carPropertyManager = null
    }

    private companion object {
        const val TAG = "CarPropertyMonitor"
    }
}
