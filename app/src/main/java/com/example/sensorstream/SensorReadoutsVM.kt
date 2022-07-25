package com.example.sensorstream

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import org.koin.core.parameter.parametersOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val SENSOR_READ_DELAY : Long = 1

enum class CONNECTION {
    ESTABLISHED, NOT_ESTABLISHED
}

const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME

class SensorsReadoutsVM (sensorManager: SensorManager) : ViewModel(), KoinComponent {
    val websocketServerUrl = BuildConfig.WEBSOCKET_SERVER
    val websocketServerPort = BuildConfig.WEBSOCKET_SERVER_PORT
    private val sensorsDataSource : SensorsDataSource by inject { parametersOf(sensorManager)}
    private val sensorDataSender : SensorDataSender by inject { parametersOf(websocketServerUrl, websocketServerPort, 1L, sensorsDataSource.sensorDataFlow) }
    val sensorsDataLive: MutableLiveData<SensorsData> by lazy {
        MutableLiveData<SensorsData>()
    }
    val connectionDataLive: MutableLiveData<CONNECTION> = sensorDataSender.connectionDataLive
    init {
        viewModelScope.launch {
            sensorsDataSource.sensorDataFlow.sample(SENSOR_READ_DELAY).collect { sensorsRead : SensorsData ->
                sensorsDataLive.value = sensorsRead
            }
        }
        viewModelScope.launch{
            sensorDataSender.sendSensorData()
        }
    }
}

interface SensorsDataSource {
    val sensorDataFlow : MutableStateFlow<SensorsData>
}

class SensorsDataSourceImpl(sensorManager: SensorManager) :
    SensorsDataSource, SensorEventListener {
    override var sensorDataFlow = MutableStateFlow<SensorsData>(SensorsData())
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
        var values : FloatArray
        if(event?.values != null) {
            values = (event.values!!)
        }
        else return
        when(event.sensor?.type){
            Sensor.TYPE_GYROSCOPE -> run {
                sensorDataFlow.value = SensorsData(gyroVals = values.toList().toTypedArray(), accelVals = sensorDataFlow.value.accelVals)
            }
            Sensor.TYPE_ACCELEROMETER -> run {
                sensorDataFlow.value = SensorsData(gyroVals = sensorDataFlow.value.gyroVals, accelVals = values.toList().toTypedArray())
            }
            null -> println("null")
            else -> println("cos innego")
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        return
    }
}

