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
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate

const val SENSOR_READ_DELAY : Long = 1

class SensorsReadoutsVM (sensorManager: SensorManager) : ViewModel() {
    val sensorDataFlow = MutableStateFlow<SensorsData>(SensorsData())
    private val sensorsDataSource = SensorsDataSource(sensorDataFlow, sensorManager)
    private val dataSender = SocketDataSender(sensorDataFlow, "echo.websocket.events", 80, 1)
    val sensorsDataLive: MutableLiveData<SensorsData> by lazy {
        MutableLiveData<SensorsData>()
    }
    init {
        viewModelScope.launch {
            sensorDataFlow.collect { sensorsRead : SensorsData ->
                sensorsDataLive.value = sensorsRead
                delay(SENSOR_READ_DELAY)
            }
        }
        viewModelScope.launch{
            dataSender.sendData()
        }
    }
}

class SensorsDataSource (val sensorDataFlow : MutableStateFlow<SensorsData>, private val sensorManager: SensorManager) :
    SensorEventListener {

    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

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

class SocketDataSender (val sensorDataFlow: MutableStateFlow<SensorsData>, var host : String, var port : Int, var delay : Long){
    val client = HttpClient {
        install(WebSockets)
    }
    suspend fun sendData() {
        client.webSocket(method = HttpMethod.Get, host = host, port = port, path = "") {
            println("websocket")
            var socket : DefaultClientWebSocketSession = this
            launch{receiveData(socket)}
            sensorDataFlow.collect { sensorsRead: SensorsData ->
                val myMessage = sensorsRead.accelVals[2].toString()
                send(myMessage)
            }
        }
    }
    suspend fun receiveData(socket : DefaultWebSocketSession){
        while(true) {
            val incoming = socket.incoming.receive() as? Frame.Text
            println(incoming?.readText())
            delay(10)
        }
    }
}