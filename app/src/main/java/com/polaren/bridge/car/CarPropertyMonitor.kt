package com.polaren.bridge.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CarPropertyMonitor(context: Context) {
    private val TAG = "CarPropertyMonitor"

    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null

    // State flows to emit changes to the service
    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed

    private val _evBatteryLevel = MutableStateFlow(0f)
    val evBatteryLevel: StateFlow<Float> = _evBatteryLevel

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging

    init {
        connectToCar(context)
    }

    private fun connectToCar(context: Context) {
        if (context.packageManager.hasSystemFeature("android.hardware.type.automotive")) {
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER) { connectedCar, isReady ->
                if (isReady) {
                    Log.i(TAG, "Car API is ready.")
                    carPropertyManager = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    registerListeners()
                } else {
                    Log.w(TAG, "Car API is disconnected.")
                    carPropertyManager = null
                }
            }
        } else {
            Log.e(TAG, "Not an automotive device, Car API unavailable.")
        }
    }

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                    val speedVal = value.value as? Float ?: 0f
                    _speed.value = speedVal
                    Log.d(TAG, "Speed updated: $speedVal m/s")
                }
                VehiclePropertyIds.EV_BATTERY_LEVEL -> {
                    val batteryLevel = value.value as? Float ?: 0f
                    _evBatteryLevel.value = batteryLevel
                    Log.d(TAG, "EV Battery Level updated: $batteryLevel")
                }
                VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED -> {
                    val connected = value.value as? Boolean ?: false
                    _isCharging.value = connected
                    Log.d(TAG, "EV Charge Port Connected: $connected")
                }
            }
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.e(TAG, "Error reading property: $propertyId in zone $zone")
        }
    }

    private fun registerListeners() {
        val manager = carPropertyManager ?: return
        Log.i(TAG, "Registering Car Property Listeners")

        try {
            // Speed (Requires android.car.permission.CAR_SPEED)
            manager.registerCallback(
                propertyCallback,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                CarPropertyManager.SENSOR_RATE_NORMAL
            )

            // EV Battery Level (Requires android.car.permission.CAR_ENERGY)
            manager.registerCallback(
                propertyCallback,
                VehiclePropertyIds.EV_BATTERY_LEVEL,
                CarPropertyManager.SENSOR_RATE_NORMAL
            )

            // Charge Port Connected (Requires android.car.permission.CAR_ENERGY)
            manager.registerCallback(
                propertyCallback,
                VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED,
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException registering car properties. Check permissions.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register car properties", e)
        }
    }

    fun disconnect() {
        carPropertyManager?.unregisterCallback(propertyCallback)
        car?.disconnect()
        car = null
        carPropertyManager = null
    }
}
