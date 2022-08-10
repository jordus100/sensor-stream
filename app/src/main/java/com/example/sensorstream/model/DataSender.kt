package com.example.sensorstream

import com.example.sensorstream.model.ConnectionStatus
import com.example.sensorstream.model.SensorsData
import com.example.sensorstream.viewmodel.TransmissionState
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import java.time.LocalDateTime
import kotlin.coroutines.coroutineContext

interface SensorDataSender {
    val connectionStateFlow: MutableStateFlow<ConnectionStatus>
    val transmissionStateFlow : MutableStateFlow<TransmissionState>
    val receivedFlow : MutableStateFlow<String>
    fun sendSensorData()
    fun pauseSendingData()
}

fun SensorsData.format() = "$accel $gyro"

class SocketDataSender (
    val host : String, val port : Int, val dataFlow: MutableStateFlow<SensorsData>)
    : SensorDataSender, KoinComponent {

    override val connectionStateFlow = MutableStateFlow(ConnectionStatus.NOT_ESTABLISHED)
    override val transmissionStateFlow = MutableStateFlow(TransmissionState.OFF)
    override val receivedFlow = MutableStateFlow(String())
    private val websocketConnection : WebsocketConnection = get() {
        parametersOf(connectionStateFlow, transmissionStateFlow)
    }


    override fun sendSensorData() {
        if(connectionStateFlow.value == ConnectionStatus.ESTABLISHED)
            websocketConnection.getTransmitCoroutineScope().launch { transmit() }
    }

    override fun pauseSendingData(){
        websocketConnection.getTransmitCoroutineScope().cancel()
        transmissionStateFlow.value = TransmissionState.OFF
    }

    private suspend fun transmit() {
        val sendAndReceiveJob = websocketConnection.getTransmitCoroutineScope().launch {
            async { sendData(dataFlow) }
            async { receiveData() }
        }
        sendAndReceiveJob.join()
    }

    suspend fun sendData(dataFlow: MutableStateFlow<SensorsData>) {
        val collectJob = websocketConnection.getTransmitCoroutineScope().launch {
            dataFlow.sample(1L).collect {
                val myMessage = it.format()
                withContext(websocketConnection.getTransmitCoroutineScope().coroutineContext) {
                    ensureActive()
                    websocketConnection.getWebsocketConnection().send(myMessage)
                }
            }
        }
        collectJob.join()
    }

    private suspend fun receiveData() {
        var incoming: Frame.Text
        while (true) {
            withContext(websocketConnection.getTransmitCoroutineScope().coroutineContext) {
                ensureActive()
                incoming =
                    websocketConnection.getWebsocketConnection().incoming.receive() as Frame.Text
            }
            coroutineContext.ensureActive()
            transmissionStateFlow.value = TransmissionState.ON
            receivedFlow.emit(incoming.readText())
        }
    }
}

open class WebsocketConnection(val host: String, val port: Int,
                               val connectionState : MutableStateFlow<ConnectionStatus>,
                               val transmissionState : MutableStateFlow<TransmissionState>) {
    private val client = HttpClient {
        install(WebSockets){ }
    }
    private lateinit var websocketConnection: DefaultClientWebSocketSession
    private lateinit var transmitScope: CoroutineScope
    private val websocketConnectionMutex = Mutex()

    private val handler = CoroutineExceptionHandler { _, e ->
        transmitScope.coroutineContext.cancel()
        transmissionState.value = TransmissionState.OFF
    }

    init {
        CoroutineScope(Dispatchers.Default).launch { handleConnection() }
    }

    private suspend fun handleConnection() {
        while (true) {
            coroutineScope {
                try {
                        getWebsocketConnection()
                        monitorConnection()
                    } catch (e: Exception) { }
            }
            delay(100)
        }
    }

    fun getTransmitCoroutineScope(): CoroutineScope {
        if (!::transmitScope.isInitialized || !transmitScope.isActive)
            transmitScope = CoroutineScope(Dispatchers.Default + handler)
        return transmitScope
    }

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

    protected open suspend fun monitorConnection() {
        while(true){
            if(transmissionState.value == TransmissionState.OFF){
                coroutineScope {
                    try {
                        withTimeoutOrNull(1000) {
                            websocketConnection.send("") // not many better solutions than
                            //to send a test message and wait for an exception
                        }
                    } catch (e: Exception) {
                        withContext(NonCancellable) {
                            connectionState.value = ConnectionStatus.NOT_ESTABLISHED
                            kotlin.runCatching {
                                getWebsocketConnection()
                            }
                        }
                    }
                }
            }
            delay(500)
        }
    }

}