package com.example.sensorstream

import com.example.sensorstream.model.ConnectionStatus
import com.example.sensorstream.model.SensorsData
import com.example.sensorstream.model.TransmissionState
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

interface SensorDataSender {
    val connectionStateFlow: MutableStateFlow<ConnectionStatus>
    val transmissionStateFlow : MutableStateFlow<TransmissionState>
    val receivedFlow : MutableStateFlow<String>
    fun sendSensorData()
    fun pauseSendingData()
}

fun SensorsData.format() = "$accel $gyro"

class SocketDataSender (sensorMutableDataFlow: MutableStateFlow<SensorsData>)
    : SensorDataSender, KoinComponent {

    override val connectionStateFlow = MutableStateFlow(ConnectionStatus.NOT_ESTABLISHED)
    override val transmissionStateFlow = MutableStateFlow(TransmissionState.OFF)
    override val receivedFlow = MutableStateFlow(String())
    private val websocketConnection : WebsocketConnection = get() {
        parametersOf(connectionStateFlow, transmissionStateFlow)
    }
    private val handler = CoroutineExceptionHandler { _, _ ->
        transmissionStateFlow.value = TransmissionState.OFF
        connectionStateFlow.value = ConnectionStatus.NOT_ESTABLISHED
        transmit()
    }
    private var transmittingSensorDataNow = false
    private val sensorDataFlow = sensorMutableDataFlow
        .filter{transmittingSensorDataNow}
        .map{ it.format() }
        .buffer( 64, BufferOverflow.DROP_LATEST)

    private val connectionSustainer = flow {
        while(true){
            delay(200)
            emit("")
        }
    }.filter{!transmittingSensorDataNow}

    override fun sendSensorData() {
        if(connectionStateFlow.value == ConnectionStatus.ESTABLISHED) {
            transmittingSensorDataNow = true
        }
    }

    override fun pauseSendingData(){
        transmittingSensorDataNow = false
        transmissionStateFlow.value = TransmissionState.OFF
    }

    init {
        transmit()
    }

    private fun transmit() {
        CoroutineScope(Dispatchers.Default).launch(handler) {
            launch { sendData() }
            launch { receiveData() }
        }
    }

    @OptIn(FlowPreview::class)
    fun CoroutineScope.sendData() {
        launch {
            sensorDataFlow.sample(1L)
            .filter{transmittingSensorDataNow}
            .collect {
                yield()
                websocketConnection.getWebsocketConnection().send(it)
                if(transmittingSensorDataNow)
                    transmissionStateFlow.value = TransmissionState.ON
            }
        }
        launch {
            connectionSustainer.sample(1L).filter{!transmittingSensorDataNow}.collect {
                yield()
                if(!transmittingSensorDataNow)
                    websocketConnection.getWebsocketConnection().send(it)
            }
        }
    }

    private fun CoroutineScope.receiveData() {
        launch {
            while (isActive) {
                val incoming = this.async {
                    websocketConnection.getWebsocketConnection().incoming.receive() as Frame.Text
                }
                val incomingTxt = incoming.await().readText()
                receivedFlow.emit(incomingTxt)
                println(incomingTxt)
                coroutineContext.ensureActive()
            }
        }
    }

}

open class WebsocketConnection(val host: String, val port: Int,
                               val connectionState : MutableStateFlow<ConnectionStatus>){

    private val client = HttpClient {
        install(WebSockets){ }
    }
    private lateinit var websocketConnection: DefaultClientWebSocketSession
    private val websocketConnectionMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getWebsocketConnection(): DefaultClientWebSocketSession {
        websocketConnectionMutex.withLock {
            if (!(::websocketConnection.isInitialized) || !websocketConnection.isActive
                || websocketConnection.outgoing.isClosedForSend
            ) {
                try {
                    websocketConnection = client.webSocketSession(
                        method = HttpMethod.Get, host,
                        port, path = ""
                    )
                    connectionState.value = ConnectionStatus.ESTABLISHED
                    return websocketConnection
                } catch (e: Exception) {
                    throw e
                }
            } else return websocketConnection
        }
    }

}