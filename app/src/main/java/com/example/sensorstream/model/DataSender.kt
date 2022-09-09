package com.example.sensorstream.model

import android.hardware.SensorManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
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
    val receivedFlow : StateFlow<String>
    fun sendSensorData()
    fun pauseSendingData()
}


class SocketDataSender (formatSensorData : (SensorsData) -> String,
                        private val externalScope: CoroutineScope,
                        private val state : StateFlow<SensorsViewState>,
                        private val transmissionStateUpdate : (TransmissionState) -> Unit,
                        private val connectionStatusUpdate : (ConnectionStatus) -> Unit)
    : SensorDataSender, KoinComponent {

    override val receivedFlow : StateFlow<String>
        get() = _receivedFlow
    private val _receivedFlow = MutableStateFlow(String())

    private val websocketConnection : WebsocketConnection = get() {
        parametersOf(connectionStatusUpdate)
    }
    private val handler = CoroutineExceptionHandler { _, _ ->
        transmissionActive = false
        transmissionStateUpdate(TransmissionState.OFF)
        connectionStatusUpdate(ConnectionStatus.NOT_ESTABLISHED)
        transmit()
    }
    private var transmissionActive = false
    private val sensorDataFlowTransformed = state
        .filter{ transmissionActive }
        .map{ formatSensorData(it.sensorsData) }
        .buffer( 64, BufferOverflow.DROP_LATEST)

    private var pingMessage = ""
    private val connectionSustainer = flow {
        while(true){
            delay(1000)
            emit(pingMessage)
        }
    }.filter{!transmissionActive}

    override fun sendSensorData() {
        if(state.value.connectionStatus == ConnectionStatus.ESTABLISHED) {
            transmissionActive = true
        }
    }

    override fun pauseSendingData(){
        transmissionActive = false
        transmissionStateUpdate(TransmissionState.OFF)
    }

    init {
        transmit()
    }

    private fun transmit() {
        externalScope.launch(handler) {
            launch { sendData() }
            launch { receiveData() }
        }
    }

    @OptIn(FlowPreview::class)
    fun CoroutineScope.sendData() {
        launch {
            sensorDataFlowTransformed.sample(1L)
            .filter{ transmissionActive }
            .collect {
                yield()
                websocketConnection.getWebsocketConnection().send(it)
                if(transmissionActive)
                    transmissionStateUpdate(TransmissionState.ON)
            }
        }
        launch {
            connectionSustainer.filter{!transmissionActive}.collect {
                yield()
                if( !transmissionActive ) {
                    websocketConnection.getWebsocketConnection().send(it)
                }
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
                if(incomingTxt != pingMessage) {
                    _receivedFlow.emit(incomingTxt)
                    println(incomingTxt)
                }
            }
        }
    }

}

open class WebsocketConnection(private val host: String, private val port: Int,
                               private val path : String,
                               val connectionStatusUpdate : (ConnectionStatus) -> Unit){

    private val client = HttpClient(CIO) {
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
                        port, path = path
                    )
                    connectionStatusUpdate(ConnectionStatus.ESTABLISHED)
                    return websocketConnection
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw e
                }
            } else return websocketConnection
        }
    }

}