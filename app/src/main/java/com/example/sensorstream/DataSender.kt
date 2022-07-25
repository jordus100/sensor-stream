package com.example.sensorstream

import androidx.lifecycle.MutableLiveData
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.time.LocalDateTime

interface SensorDataSender {
    val dataFlow: MutableStateFlow<SensorsData>
    val connectionDataLive: MutableLiveData<CONNECTION>
    suspend fun sendSensorData()
}

fun SensorsData.format() : String{
    val prefix = "["; val postfix = "]"; val separator = " ; "
    var data = accelVals.joinToString(separator, prefix, postfix)
    data = data + " " + gyroVals.joinToString(separator, prefix, postfix)
    return data
}

class SocketDataSender (val host : String, val port : Int, val delay : Long, override val dataFlow: MutableStateFlow<SensorsData>) : SensorDataSender{
    override val connectionDataLive: MutableLiveData<CONNECTION> = MutableLiveData<CONNECTION>()
    init {
        connectionDataLive.value = CONNECTION.NOT_ESTABLISHED
    }
    val client = HttpClient {
        install(WebSockets)
    }
    val handleConnection : suspend DefaultClientWebSocketSession.() -> Unit  = {
        connectionDataLive.value = CONNECTION.ESTABLISHED
        val socket = this
        val receiveJob = launch { receiveData(socket) }
        val sendJob = launch { sendData(socket, dataFlow) }
        receiveJob.join()
        sendJob.join()
    }
    override suspend fun sendSensorData() {
        try {
            openWebsocketConnection(host, port, block = handleConnection)
        } catch (e: Exception) {
            println("CONNECTION BROKEN")
            connectionDataLive.value = CONNECTION.NOT_ESTABLISHED
            return
        }
    }
    suspend fun openWebsocketConnection(host : String, port : Int, block : suspend DefaultClientWebSocketSession.() -> Unit) {
        client.webSocket(method = HttpMethod.Get, host = host, port = port, path = "", block = block)
    }
    suspend fun sendData(socket: DefaultClientWebSocketSession, dataFlow: MutableStateFlow<SensorsData>){
        dataFlow.sample(delay).collect { sensorsRead: SensorsData ->
            val myMessage = sensorsRead.format()
            println("OUTGOING: " + myMessage)
            socket.send(myMessage)
            delay(delay)
        }
    }
    private suspend fun receiveData(socket: DefaultClientWebSocketSession) {
        var current = LocalDateTime.now()
        var counter = 0
        while (true) {
            val incoming = socket.incoming.receive() as? Frame.Text
            handleIncoming(incoming)
            if(current.second != LocalDateTime.now().second) {
                println(counter.toString() + " MESSAGES PER SECOND")
                current = LocalDateTime.now()
            } else
                counter++
        }
    }
    private fun handleIncoming( message : Frame.Text? ){
        println("INCOMING: " + message?.readText())
    }
}