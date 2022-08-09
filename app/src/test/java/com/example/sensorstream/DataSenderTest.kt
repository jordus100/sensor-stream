package com.example.sensorstream

import com.example.sensorstream.model.*
import com.example.sensorstream.viewmodel.SensorsReadoutsViewModel
import com.example.sensorstream.viewmodel.TransmissionState
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.get
import org.junit.Before
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals

const val WEBSOCKET_SERVER_URL = "echo.websocket.events"
const val WEBSOCKET_SERVER_PORT = 80


class TestWebsocketConnection(host : String, port : Int,
                              connectionState : MutableStateFlow<ConnectionStatus>,
                              transmissionState: MutableStateFlow<TransmissionState>)
    : WebsocketConnection(host = host, port, connectionState, transmissionState) {

    override suspend fun monitorConnection(){
        while(true)
            delay(2000)
    }
}

open class SensorDataSenderTest : KoinTest {
    private val testSensorFlow = MutableStateFlow(SensorsData())

    val sensorDataSender : SensorDataSender by lazy {
        get<SensorDataSender> { parametersOf(WEBSOCKET_SERVER_URL, WEBSOCKET_SERVER_PORT, 1L,
            testSensorFlow, StreamMode.ON_TOUCH) }
    }

    val testModule = module {
        single<SensorDataSender> { params -> SocketDataSender(host = params.get(), port = params.get(),
            delay = params.get(), dataFlow = params.get()) }
        single<WebsocketConnection> { params -> TestWebsocketConnection(params.get(), params.get(),
            params.get(), params.get(3))}
    }

    @Before
    fun setUp() {
        startKoin {
            modules(testModule)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test fun socketConnectionControlTest(){
        runBlocking {
            assertEquals(TransmissionState.OFF, sensorDataSender.transmissionStateFlow.value)
            val connectionJob = launch {sensorDataSender.sendSensorData() }
            delay(3000)
            assertEquals(TransmissionState.ON, sensorDataSender.transmissionStateFlow.value)
            sensorDataSender.pauseSendingData()
            delay(1000)
            assertEquals(TransmissionState.OFF, sensorDataSender.transmissionStateFlow.value)
            connectionJob.cancelAndJoin()
        }
    }

    @Test fun socketDataTransmittingTest(){
        runBlocking {
            launch { sensorDataSender.sendSensorData() }
            val collectJob = launch {
                var counter = 0
                sensorDataSender.receivedFlow.collect {
                    if(counter == 2)
                        assertEquals(expected = "[0.0; 0.0; 0.0] [0.0; 0.0; 0.0]", actual = it)
                    else
                        counter++
                }
            }
            testSensorFlow.update {
                it.copy(
                    Point3F(0.0f, 0.0f, 0.0f),
                    Point3F(0.0f, 0.0f, 0.0f)
                )
            }
            delay(3000)
            sensorDataSender.pauseSendingData()
            collectJob.cancelAndJoin()
        }
    }

}