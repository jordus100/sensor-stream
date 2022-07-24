package com.example.sensorstream

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

interface DataSender {
    suspend fun sendData(dataFlow: MutableStateFlow<SensorsData>)
}

fun SensorsData.format() : String{
    val prefix = "["; val postfix = "]"; val separator = " ; "
    var data = accelVals.joinToString(separator, prefix, postfix)
    data = data + " " + gyroVals.joinToString(separator, prefix, postfix)
    return data
}

class SocketDataSender (val host : String, val port : Int, val delay : Long) : DataSender{
    val client = HttpClient {
        install(WebSockets)
    }
    override suspend fun sendData(dataFlow: MutableStateFlow<SensorsData>) {
        while(true) {
            try {
                client.webSocket(method = HttpMethod.Get, host = host, port = port, path = "") {
                    this.let {
                        launch { receiveData(it) }
                    }
                    dataFlow.sample(delay).collect { sensorsRead: SensorsData ->
                        val myMessage = sensorsRead.format()
                        println("OUTGOING: " + myMessage)
                        send(myMessage)
                    }
                }
            } catch (e : Exception) {

            }
        }
    }
    private suspend fun receiveData(socket: DefaultWebSocketSession) {
        while (true) {
            val incoming = socket.incoming.receive() as? Frame.Text
            println("INCOMING: " + incoming?.readText())
        }
    }
}