package com.example.sensorstream

import com.example.sensorstream.model.ConnectionStatus
import com.example.sensorstream.model.SensorsData
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import java.time.LocalDateTime

interface SensorDataSender {
    val dataFlow: MutableStateFlow<SensorsData>
    val connectionStateFlow: MutableStateFlow<ConnectionStatus>
    var sendingData : Boolean
    suspend fun sendSensorData()
    fun stopSendingData()
    fun resumeSendingData()
}

fun SensorsData.format() = "$accel $gyro"

class SocketDataSender (val host : String, val port : Int, val delay : Long,
                        override val dataFlow: MutableStateFlow<SensorsData>) : SensorDataSender{

    override var sendingData = false

    override fun stopSendingData() {
        sendingData = false
    }
    override fun resumeSendingData(){
        sendingData = true
    }
    override val connectionStateFlow: MutableStateFlow<ConnectionStatus> = MutableStateFlow(ConnectionStatus.NOT_ESTABLISHED)

    val client = HttpClient {
        install(WebSockets)
    }
    suspend fun handleConnection(socket : DefaultClientWebSocketSession) {
        connectionStateFlow.value = ConnectionStatus.ESTABLISHED
        coroutineScope {
            val receiveJob = launch { receiveData(socket) }
            val sendJob = launch { sendData(socket, dataFlow) }
            launch { receiveJob.join(); sendJob.join() }
        }
    }
    override suspend fun sendSensorData() {
        sendingData = true
        while (true) {
            delay(100)
            if (sendingData) {
                connectAndTransmit()
            }
        }
    }

    private suspend fun connectAndTransmit() {
        try {
            val websocketConnection =
                client.webSocketSession(
                    method = HttpMethod.Get,
                    host,
                    port,
                    path = ""
                )
            val handler = CoroutineExceptionHandler { _, e ->
                //nothing, just don't crash
            }
            val websocketJob = CoroutineScope(Dispatchers.Default).launch(handler) {
                handleConnection(websocketConnection)
            }
            while (true) {
                if (!websocketJob.isActive)
                    throw InterruptedException()
                if(!sendingData){
                    websocketJob.cancelAndJoin()
                    throw InterruptedException()
                }

                delay(100)
            }
        } catch (e: Throwable) {
            when (e) {
                is CancellationException -> yield()
                else -> connectionStateFlow.value = ConnectionStatus.NOT_ESTABLISHED
            }
        }
    }

    suspend fun sendData(socket: DefaultClientWebSocketSession, dataFlow: MutableStateFlow<SensorsData>){
        coroutineScope {
            launch {
                dataFlow.sample(delay).collect { sensorsRead: SensorsData ->
                    val myMessage = sensorsRead.format()
//                    println("OUTGOING: " + myMessage)
                    socket.send(myMessage)
                }
            }
        }
    }
    private suspend fun receiveData(socket: DefaultClientWebSocketSession) {
        coroutineScope {
            launch {
                var current = LocalDateTime.now()
                var counter = 0
                while (true) {
                    val incoming = socket.incoming.receive() as? Frame.Text
                    handleIncoming(incoming)
                    if (current.second != LocalDateTime.now().second) {
                        println(counter.toString() + " MESSAGES PER SECOND")
                        current = LocalDateTime.now()
                    } else
                        counter++
                }
            }
        }
    }
    private fun handleIncoming( message : Frame.Text? ){
//        println("INCOMING: " + message?.readText())
    }
}