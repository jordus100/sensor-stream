package com.example.sensorstream

import com.example.sensorstream.view.appModule
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertEquals

const val WEBSOCKET_SERVER_URL = "echo.websocket.events"
const val WEBSOCKET_SERVER_PORT = 80


class SensorDataSenderTest : KoinTest {
    val testSensorLiveData = MutableStateFlow(SensorsData())
    val sensorDataSender : SensorDataSender by lazy {
        get<SensorDataSender> { parametersOf(WEBSOCKET_SERVER_URL, WEBSOCKET_SERVER_PORT, 1L, testSensorLiveData) }
    }


    @Before
    fun setUp() {
        startKoin {
            modules(appModule)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }


    /*@Test fun socketConnectionTest(){
        runBlocking {
            val connectionJob = launch {sensorDataSender.sendSensorData() }
            delay(5000)
            connectionJob.cancelAndJoin()
            assertEquals(CONNECTION.ESTABLISHED, sensorDataSender.connectionStateFlow.value)
        }
    }*/

    @Test fun socketConnectionControlTest(){
        runBlocking {
            assertEquals(TRANSMISSION.OFF, sensorDataSender.transmissionStateFlow.value)
            val connectionJob = launch {sensorDataSender.sendSensorData() }
            delay(3000)
            assertEquals(TRANSMISSION.ON, sensorDataSender.transmissionStateFlow.value)
            sensorDataSender.pauseSendingData()
            delay(1000)
            assertEquals(TRANSMISSION.OFF, sensorDataSender.transmissionStateFlow.value)
            connectionJob.cancelAndJoin()
        }
    }

}