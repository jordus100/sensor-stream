package com.example.sensorstream

import com.example.sensorstream.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.junit.Before
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.get
import org.koin.test.inject
import kotlin.test.assertEquals

val testSensorFlow = MutableStateFlow(SensorsData())


open class SensorDataSenderTest : KoinTest {

    val state : MutableStateFlow<SensorsViewState> by inject(named("initialState"))
    val sensorDataSender : SensorDataSender by inject { parametersOf(
        testSensorFlow,
        CoroutineScope(Dispatchers.Default),
        state,
        {
            testState : TransmissionState ->
            state.update { state.value.copy(transmissionState = testState) }
        },
        {
            testState : ConnectionStatus ->
            state.update { state.value.copy(connectionStatus = testState) }
        }) }


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

    @Test fun socketConnectionControlTest(){
        runBlocking {
            sensorDataSender.sendSensorData()
            delay(5000)
            sensorDataSender.sendSensorData()
            testSensorFlow.update {
                it.copy(
                    Point3F(0.1f, 0.0f, 0.0f),
                    Point3F(0.1f, 0.0f, 0.0f)
                )
            }
            delay(1000)
            assertEquals(TransmissionState.ON, state.value.transmissionState)
            sensorDataSender.pauseSendingData()
            delay(1000)
            assertEquals(TransmissionState.OFF, state.value.transmissionState)
        }
    }

    @Test fun socketDataTransmittingTest(){
        runBlocking {
            sensorDataSender.sendSensorData()
            delay(5000)
            testDataTransmit(1)
            testDataTransmit(0)
        }
    }

    private fun CoroutineScope.testDataTransmit(messageCounter : Int){
        sensorDataSender.sendSensorData()
        var counter = 0
        var message = ""
        val collectJob = launch {
            sensorDataSender.receivedFlow.collect {
                if (counter == messageCounter)
                    message = it
                else
                    counter++
            }
        }
        testSensorFlow.update {
            it.copy(
                Point3F(0.1f, 0.0f, 0.0f),
                Point3F(0.1f, 0.0f, 0.0f)
            )
        }
        runBlocking {  delay(2000) }
        assertEquals(expected = "[0.1; 0.0; 0.0] [0.1; 0.0; 0.0]", actual = message)
        assertEquals(messageCounter, counter)
        sensorDataSender.pauseSendingData()
        runBlocking { collectJob.cancelAndJoin() }
    }

}