package com.example.sensorstream.model

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.sensorstream.viewmodel.SENSOR_DELAY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SensorsDataSource(sensorManager: SensorManager, private val state: StateFlow<SensorsViewState>,
                        externalScope: CoroutineScope,
                        private val sensorDataUpdate : (SensorsData) -> Unit) : SensorEventListener {
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var rotationVector: Sensor? = null
    private var accelVector: Sensor? = null

    init {
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        accelVector = sensorManager.getDefaultSensor(Sensor.TYPE_POSE_6DOF)
        sensorManager.registerListener(this, gyroSensor, SENSOR_DELAY)
        sensorManager.registerListener(this, accelSensor, SENSOR_DELAY)
        sensorManager.registerListener(this, rotationVector, SENSOR_DELAY)
        sensorManager.registerListener(this, accelVector, SENSOR_DELAY)
        externalScope.launch{
            setSensorReferencePoint()
        }
    }

    private suspend fun setSensorReferencePoint(){
        var prevState : TransmissionState? = null
        state.collect {
            if (prevState != it.transmissionState && it.transmissionState == TransmissionState.ON) {
                sensorDataUpdate(state.value.sensorsData.copy(
                    referencePoint = state.value.sensorsData.rotationVector,
                    accelRefPoint = state.value.sensorsData.accel))
            }
            prevState = it.transmissionState
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.values == null) return

        when(event.sensor?.type){
            Sensor.TYPE_LINEAR_ACCELERATION ->
                sensorDataUpdate(state.value.sensorsData.copy(accel = Point3F.from(event.values),
                    timestamp = event.timestamp))
            Sensor.TYPE_GYROSCOPE ->
                sensorDataUpdate(state.value.sensorsData.copy(gyro = Point3F.from(event.values)))
            Sensor.TYPE_GAME_ROTATION_VECTOR ->
                sensorDataUpdate(state.value.sensorsData.copy(
                    rotationVector = Point3F.from(event.values)))
            Sensor.TYPE_POSE_6DOF ->
                sensorDataUpdate(state.value.sensorsData.copy(
                    accelerationVector = Point3F.from(
                        floatArrayOf(event.values[0], event.values[1], event.values[2]))))
            null -> println("null")
            else -> println("cos innego")
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        return
    }
}
