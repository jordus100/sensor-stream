package com.example.sensorstream.model

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.sensorstream.viewmodel.SENSOR_DELAY
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class SensorsDataSource(sensorManager: SensorManager) : SensorEventListener {
    var sensorDataFlow = MutableStateFlow(SensorsData())
        private set
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    init {
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        sensorManager.registerListener(this, gyroSensor, SENSOR_DELAY)
        sensorManager.registerListener(this, accelSensor, SENSOR_DELAY)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.values == null) return

        when(event.sensor?.type){
            Sensor.TYPE_GYROSCOPE -> sensorDataFlow.update {
                it.copy(gyro = Point3F.from(event.values)) }
            Sensor.TYPE_ACCELEROMETER -> sensorDataFlow.update {
                it.copy(accel = Point3F.from(event.values)) }
            null -> println("null")
            else -> println("cos innego")
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        return
    }
}
