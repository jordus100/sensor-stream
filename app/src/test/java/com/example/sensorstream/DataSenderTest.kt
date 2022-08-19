package com.example.sensorstream

import com.example.sensorstream.model.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.junit.Before
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.dsl.module
import org.koin.test.inject
import kotlin.test.assertEquals

val testModule = module {
    single<SensorDataSender> { params -> SocketDataSender(params.get(0)) }
    single<WebsocketConnection> { params -> WebsocketConnection(BuildConfig.WEBSOCKET_SERVER,
        BuildConfig.WEBSOCKET_SERVER_PORT, params.get(0))}
}
val testSensorFlow = MutableStateFlow(SensorsData())


open class SensorDataSenderTest : KoinTest {

    val sensorDataSender : SensorDataSender by inject{ parametersOf(testSensorFlow) }


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