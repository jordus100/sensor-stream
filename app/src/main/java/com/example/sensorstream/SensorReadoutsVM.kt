package com.example.sensorstream

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.MotionEvent
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
enum class TRANSMISSION {
    ON, OFF
}
enum class STREAM_MODE{
    CONSTANT, ON_TOUCH
}

const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_FASTEST

class SensorsReadoutsVM (val sensorManager: SensorManager, var streamMode: STREAM_MODE)
    : ViewModel(), KoinComponent {

    val websocketServerUrl = BuildConfig.WEBSOCKET_SERVER
    val websocketServerPort = BuildConfig.WEBSOCKET_SERVER_PORT
    private val sensorsDataSource : SensorsDataSource by inject { parametersOf(sensorManager)}
    private val sensorDataSender : SensorDataSender by inject { parametersOf(websocketServerUrl,
        websocketServerPort, 1000L, sensorsDataSource.sensorDataFlow, streamMode) }
    val sensorsDataLive: MutableLiveData<SensorsData> by lazy {
        MutableLiveData<SensorsData>()
    }
    val connectionDataLive = MutableLiveData<CONNECTION>(CONNECTION.NOT_ESTABLISHED)
    val transmissionDataLive = MutableLiveData<TRANSMISSION>(TRANSMISSION.OFF)
    lateinit var event : MotionEvent


    init {
        viewModelScope.launch {
            sensorsDataSource.sensorDataFlow.sample(SENSOR_READ_DELAY).collect {
                    sensorsRead : SensorsData ->
                sensorsDataLive.value = sensorsRead
            }
        }
        viewModelScope.launch {
            sensorDataSender.connectionStateFlow.collect { connectionStatus ->
                connectionDataLive.value = connectionStatus
            }
        }
        viewModelScope.launch {
            sensorDataSender.transmissionStateFlow.collect { transmissionStatus ->
                transmissionDataLive.value = transmissionStatus
            }
        }
        viewModelScope.launch{
            if(streamMode == STREAM_MODE.CONSTANT)
                sensorDataSender.sendSensorData()
        }
    }
    fun onRootTouch(event : MotionEvent) : Boolean {
        if (streamMode == STREAM_MODE.ON_TOUCH) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.pointerCount == 1) {
                        sensorDataSender.sendSensorData()
                        this.event = event
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if(event.pointerCount == 1) {
                        sensorDataSender.pauseSendingData()
                    }
                }
            }
        }
        return true
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
        val values : FloatArray
        if(event?.values != null) {
            values = (event.values!!)
        }
        else return
        when(event.sensor?.type){
            Sensor.TYPE_GYROSCOPE -> run {
                sensorDataFlow.value = SensorsData(gyroVals = values.toList().toTypedArray(),
                    accelVals = sensorDataFlow.value.accelVals)
            }
            Sensor.TYPE_ACCELEROMETER -> run {
                sensorDataFlow.value = SensorsData(gyroVals = sensorDataFlow.value.gyroVals,
                    accelVals = values.toList().toTypedArray())
            }
            null -> println("null")
            else -> println("cos innego")
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        return
    }
}

