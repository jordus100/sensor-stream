package com.example.sensorstream

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample

const val SENSOR_READ_DELAY : Long = 1

class SensorsReadoutsVM (sensorManager: SensorManager) : ViewModel() {
    val websocketServerUrl = BuildConfig.WEBSOCKET_SERVER
    val websocketServerPort = BuildConfig.WEBSOCKET_SERVER_PORT
    private val sensorsDataSource : SensorsDataSource = SensorsDataSourceImpl(sensorManager)
    private val dataSender = SocketDataSender(sensorsDataSource.sensorDataFlow, websocketServerUrl, websocketServerPort, 1)
    val sensorsDataLive: MutableLiveData<SensorsData> by lazy {
        MutableLiveData<SensorsData>()
    }
    init {
        viewModelScope.launch {
            sensorsDataSource.sensorDataFlow.sample(SENSOR_READ_DELAY).collect { sensorsRead : SensorsData ->
                sensorsDataLive.value = sensorsRead
            }
        }
        viewModelScope.launch{
            dataSender.sendData()
        }
    }
}
interface SensorsDataSource {
    val sensorDataFlow : MutableStateFlow<SensorsData>
}
class SensorsDataSourceImpl(private val sensorManager: SensorManager) :
    SensorsDataSource, SensorEventListener {
    override var sensorDataFlow = MutableStateFlow<SensorsData>(SensorsData())
        private set
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
        while(true) {
            try {
                client.webSocket(method = HttpMethod.Get, host = host, port = port, path = "") {
                    this.let {
                        launch { receiveData(it) }
                    }
                    sensorDataFlow.sample(delay).collect { sensorsRead: SensorsData ->
                        val myMessage = sensorsRead.format()
                        println("OUTGOING: " + myMessage)
                        send(myMessage)
                    }
                }
            } catch (e : Exception) {

            }
        }
    }
    suspend fun receiveData(socket: DefaultWebSocketSession) {
        while (true) {
            val incoming = socket.incoming.receive() as? Frame.Text
            println("INCOMING: " + incoming?.readText())
        }
    }
}

fun SensorsData.format() : String{
    val prefix = "["; val postfix = "]"; val separator = " ; "
    var data = accelVals.joinToString(separator, prefix, postfix)
    data = data + " " + gyroVals.joinToString(separator, prefix, postfix)
    return data
}