package com.example.sensorstream

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

const val SENSOR_READ_DELAY : Long = 1

class SensorsReadoutsVM (sensorManager: SensorManager) : ViewModel() {
    private var sensorsData = SensorsData()
    private val sensorsDataSource = SensorsDataSource(sensorManager)
    val sensorsDataLive: MutableLiveData<SensorsData> by lazy {
        MutableLiveData<SensorsData>()
    }
    init {
        viewModelScope.launch {
            sensorsDataSource.sensorsRead.collect { sensorsRead : SensorsData ->
                sensorsData = sensorsRead
                sensorsDataLive.value = sensorsData
            }
        }
    }
}

class SensorsDataSource (private val sensorManager: SensorManager): DefaultLifecycleObserver,
    SensorEventListener {

    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var sensorsData = SensorsData()

    init {
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        var values : FloatArray
        if(event?.values != null) {
            values = (event.values!!)
        }
        else return
        when(event.sensor?.type){
            Sensor.TYPE_GYROSCOPE -> run {
                sensorsData.gyroVals = values.toList().toTypedArray()
            }
            Sensor.TYPE_ACCELEROMETER -> run {
                sensorsData.accelVals = values.toList().toTypedArray()
            }
            null -> println("null")
            else -> println("cos innego")
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        return
    }

    val sensorsRead: Flow<SensorsData> = flow {
        while (true) {
            if (accelSensor != null && gyroSensor != null) {
                emit(sensorsData)
            }
            delay(SENSOR_READ_DELAY)
        }
    }
}