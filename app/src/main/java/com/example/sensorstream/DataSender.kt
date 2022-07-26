package com.example.sensorstream

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import java.time.LocalDateTime
import kotlin.coroutines.coroutineContext

interface SensorDataSender {
    val dataFlow: MutableStateFlow<SensorsData>
    val connectionStateFlow: MutableStateFlow<CONNECTION>
    suspend fun sendSensorData()
}

fun SensorsData.format() : String{
    val prefix = "["; val postfix = "]"; val separator = " ; "
    var data = accelVals.joinToString(separator, prefix, postfix)
    data = data + " " + gyroVals.joinToString(separator, prefix, postfix)
    return data
}

class SocketDataSender (val host : String, val port : Int, val delay : Long,
                        override val dataFlow: MutableStateFlow<SensorsData>) : SensorDataSender{
    override val connectionStateFlow: MutableStateFlow<CONNECTION> =
        MutableStateFlow<CONNECTION>(CONNECTION.NOT_ESTABLISHED)
    val client = HttpClient {
        install(WebSockets)
    }
    val handleConnection : suspend DefaultClientWebSocketSession.() -> Unit  = {
        val socket = this
        coroutineScope { try {
            connectionStateFlow.value = CONNECTION.ESTABLISHED
            val receiveJob = launch { receiveData(socket) }
            val sendJob = launch { sendData(socket, dataFlow) }
            receiveJob.join()
            sendJob.join()
            } catch(e:Exception){
                println("lolllllll")
                throw Exception()
            }
        }
    }
    val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
    }
    override suspend fun sendSensorData() {
        while(true) {
            try {
                with(CoroutineScope(coroutineContext)) {
                    launch {
                    try {
                        client.webSocket(
                            method = HttpMethod.Get,
                            host,
                            port,
                            path = "",
                            block = handleConnection
                        )
                    } catch(e : Exception){
                        println("ASDASKDALSDHBAHLSHBljusaflHUDSLualesarlif")
                    }
                } }
                while(true){
                    delay(1000)
                    yield()
                }
            } catch (e: Exception) {
                when(e) {
                    is CancellationException -> yield()
                    else -> {
                        println("CONNECTION BROKEN")
                        connectionStateFlow.value = CONNECTION.NOT_ESTABLISHED
                    }
                }
            }
        }
    }
    suspend fun sendData(socket: DefaultClientWebSocketSession, dataFlow: MutableStateFlow<SensorsData>){
        with(CoroutineScope(coroutineContext)) {
            launch {dataFlow.sample(delay).collect { sensorsRead: SensorsData ->
                val myMessage = sensorsRead.format()
                println("OUTGOING: " + myMessage)
                socket.send(myMessage)
                }
            }
        }
    }
    private suspend fun receiveData(socket: DefaultClientWebSocketSession) {
        throw Exception()
        var current = LocalDateTime.now()
        var counter = 0
        while (true) {
            yield()
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