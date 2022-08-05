package com.example.sensorstream

import android.accounts.NetworkErrorException
import com.example.sensorstream.model.ConnectionStatus
import com.example.sensorstream.model.SensorsData
import com.example.sensorstream.model.StreamMode
import com.example.sensorstream.viewmodel.TRANSMISSION
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.events.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import java.time.LocalDateTime

interface SensorDataSender {
    val connectionStateFlow: MutableStateFlow<ConnectionStatus>
    val transmissionStateFlow : MutableStateFlow<TRANSMISSION>
    fun sendSensorData()
    fun pauseSendingData()
}

fun SensorsData.format() = "$accel $gyro"

class SocketDataSender (
    val host : String, val port : Int, val delay : Long,
    val dataFlow: MutableStateFlow<SensorsData>,
    val streamMode: StreamMode
    ) : SensorDataSender{

    override val connectionStateFlow = MutableStateFlow(ConnectionStatus.NOT_ESTABLISHED)
    override val transmissionStateFlow = MutableStateFlow(TRANSMISSION.OFF)

    private val websocketConnection = WebsocketConnection(host, port, connectionStateFlow,
        transmissionStateFlow)

    override fun sendSensorData() {
        if(streamMode == StreamMode.ON_TOUCH)
            websocketConnection.getTransmitCoroutineScope().launch { transmit() }
        else if(streamMode == StreamMode.CONSTANT){
            CoroutineScope(Dispatchers.Default).launch{ sendSensorDataContinuously() }
        }
    }

    private suspend fun sendSensorDataContinuously(){
        while(streamMode == StreamMode.CONSTANT) {
            coroutineScope {
                val transmitJob = websocketConnection.getTransmitCoroutineScope().launch { transmit() }
                transmitJob.join()
            }
            delay(100)
        }
    }

    override fun pauseSendingData(){
        websocketConnection.getTransmitCoroutineScope().coroutineContext.cancelChildren()
        transmissionStateFlow.value = TRANSMISSION.OFF
    }

    private suspend fun transmit() {
        try {
            launchSendingAndReceiving()
        } catch (e: Throwable) {
            transmissionStateFlow.value = TRANSMISSION.OFF
            yield()
            throw e
        }
    }

    private suspend fun launchSendingAndReceiving() {
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
                    websocketConnection.getWebsocketConnection().send(myMessage)
                }
            }
        }
        collectJob.join()
    }

    private suspend fun receiveData() {
        var current = LocalDateTime.now()
        var counter = 0
        var incoming: Frame.Text
        while (true) {
            withContext(websocketConnection.getTransmitCoroutineScope().coroutineContext) {
                incoming =
                    websocketConnection.getWebsocketConnection().incoming.receive() as Frame.Text
            }
            transmissionStateFlow.value = TRANSMISSION.ON
            handleIncoming(incoming)
            if (current.second != LocalDateTime.now().second) {
                current = LocalDateTime.now()
            } else
                counter++
        }
    }
    private fun handleIncoming( message : Frame.Text? ){
//        println("INCOMING: " + message?.readText())
    }
}

class WebsocketConnection(val host: String, val port: Int,
                          val connectionState : MutableStateFlow<ConnectionStatus>,
                          val transmissionState : MutableStateFlow<TRANSMISSION>) {
    private val client = HttpClient {
        install(WebSockets)
    }
    private val handler = CoroutineExceptionHandler { _, e ->
        transmitScope.coroutineContext.cancelChildren()
        transmissionState.value = TRANSMISSION.OFF
    }

    private suspend fun handleConnection() {
        while (true) {
            try {
                coroutineScope {
                    launch {
                        getWebsocketConnection()
                        monitorConnection()
                    }
                }
            } catch (e: Exception) {
            }
            delay(50)
        }
    }

    init {
        CoroutineScope(Dispatchers.Default).launch { handleConnection() }
    }

    private lateinit var websocketConnection: DefaultClientWebSocketSession
    private lateinit var transmitScope: CoroutineScope

    fun getTransmitCoroutineScope(): CoroutineScope {
        if (!::transmitScope.isInitialized || !transmitScope.isActive)
            transmitScope = CoroutineScope(Dispatchers.Default + handler)
        return transmitScope
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getWebsocketConnection(): DefaultClientWebSocketSession {
        if (!(::websocketConnection.isInitialized) || !websocketConnection.isActive
            || websocketConnection.incoming.isClosedForReceive)
        {
            try {
                websocketConnection = client.webSocketSession(
                    method = HttpMethod.Get, host,
                    port, path = ""
                )
                return websocketConnection
            } catch (e: Exception) {
                throw NetworkErrorException()
            }
        } else return websocketConnection
    }

    private suspend fun monitorConnection() {
        while(true){
            val command = "ping -c 1 -W 1 google.com";
            val pingProcess = Runtime.getRuntime().exec(command)
            var result = 0
            coroutineScope {
                launch { result = pingProcess.waitFor() }
            }
            if(result != 0)
                connectionState.value = ConnectionStatus.NOT_ESTABLISHED
            else {
                getWebsocketConnection()
                connectionState.value = ConnectionStatus.ESTABLISHED
            }
            delay(100)
        }
    }
}