/*
package com.example.sensorstream

import com.example.sensorstream.model.ConnectionStatus
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

//WARNING: tests in this class must be executed with no internet connection on the testing machine
class SensorDataSenderNoInternetTest{

    @Test
    fun socketConnectionNoInternetTest(){
        runBlocking {
            assertEquals(TransmissionState.OFF, sensorDataSender.transmissionStateFlow.value)
            val connectionJob = launch {sensorDataSender.sendSensorData() }
            delay(3000)
            assertEquals(TransmissionState.OFF, sensorDataSender.transmissionStateFlow.value)
            assertEquals(ConnectionStatus.NOT_ESTABLISHED,
                sensorDataSender.connectionStateFlow.value)
            sensorDataSender.pauseSendingData()
            delay(1000)
            assertEquals(TransmissionState.OFF, sensorDataSender.transmissionStateFlow.value)
            connectionJob.cancelAndJoin()
        }
    }
}*/