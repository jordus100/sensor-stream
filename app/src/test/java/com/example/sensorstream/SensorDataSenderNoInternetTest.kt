package com.example.sensorstream

import com.example.sensorstream.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.assertEquals

//WARNING: tests in this class must be executed with no internet connection on the testing machine
class SensorDataSenderNoInternetTest : KoinTest {

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
        GlobalContext.startKoin {
            modules(appModule)
        }
    }

    @After
    fun tearDown() {
        GlobalContext.stopKoin()
    }

    @Test
    fun socketConnectionNoInternetTest(){
        runBlocking {
            sensorDataSender.sendSensorData()
            delay(3000)
            sensorDataSender.sendSensorData()
            testSensorFlow.update {
                it.copy(
                    Point3F(0.1f, 0.0f, 0.0f),
                    Point3F(0.1f, 0.0f, 0.0f)
                )
            }
            delay(1000)
            assertEquals(TransmissionState.OFF, state.value.transmissionState)
            assertEquals(ConnectionStatus.NOT_ESTABLISHED, state.value.connectionStatus)
            sensorDataSender.pauseSendingData()
            delay(1000)
            assertEquals(TransmissionState.OFF, state.value.transmissionState)
        }
    }
}
