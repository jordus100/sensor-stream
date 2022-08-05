package com.example.sensorstream.viewmodel

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.MotionEvent
import androidx.lifecycle.*
import com.example.sensorstream.BuildConfig
import com.example.sensorstream.SensorDataSender
import com.example.sensorstream.model.ConnectionStatus
import com.example.sensorstream.model.Point3F
import com.example.sensorstream.model.SensorsData
import com.example.sensorstream.model.StreamMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import org.koin.core.parameter.parametersOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val SENSOR_READ_DELAY : Long = 1

enum class TRANSMISSION {
    ON, OFF
}

const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_FASTEST

class SensorsReadoutsViewModel (val sensorManager: SensorManager, var streamMode: StreamMode)
    : ViewModel(), KoinComponent {

    val websocketServerUrl = BuildConfig.WEBSOCKET_SERVER
    val websocketServerPort = BuildConfig.WEBSOCKET_SERVER_PORT
    private val sensorsDataSource : SensorsDataSource by inject { parametersOf(sensorManager)}
    private val sensorDataSender : SensorDataSender by inject { parametersOf(websocketServerUrl,
        websocketServerPort, 1000L, sensorsDataSource.sensorDataFlow, streamMode) }
    val sensorsDataLive: MutableLiveData<SensorsData> by lazy {
        MutableLiveData<SensorsData>()
    }

    val connectionDataLive = MutableLiveData<ConnectionStatus>(ConnectionStatus.NOT_ESTABLISHED)
    val transmissionDataLive = MutableLiveData<TRANSMISSION>(TRANSMISSION.OFF)
    lateinit var event : MotionEvent

    init {
        viewModelScope.launch {
            sensorsDataSource.sensorDataFlow.sample(SENSOR_READ_DELAY).collect { sensorsDataLive.value = it }
        }
        viewModelScope.launch {
            sensorDataSender.transmissionStateFlow.collect {
                transmissionDataLive.value = it
            }
        }
        viewModelScope.launch { sensorDataSender.connectionStateFlow.collect {
            connectionDataLive.value = it }
        }
        if (streamMode == StreamMode.CONSTANT) viewModelScope.launch {
            sensorDataSender.sendSensorData() }
    }

    fun onRootTouch(event : MotionEvent) : Boolean {
        if (streamMode == StreamMode.ON_TOUCH) {
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    if (event.pointerCount == 1) {
                        sensorDataSender.sendSensorData()
                        this.event = event
                    }
                MotionEvent.ACTION_UP -> if(event.pointerCount == 1) sensorDataSender.pauseSendingData()
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
    override var sensorDataFlow = MutableStateFlow(SensorsData())
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

