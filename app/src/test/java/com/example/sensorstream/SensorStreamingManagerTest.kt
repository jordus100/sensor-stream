package com.example.sensorstream

import androidx.lifecycle.MutableLiveData
import com.example.sensorstream.model.SensorStreamingManager
import com.example.sensorstream.model.appModule
import com.example.sensorstream.viewmodel.TransmissionState
import org.junit.After
import org.junit.Before
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.inject
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.mock


class SensorStreamingManagerTest : KoinTest, KoinComponent{
    val testTransmissionDataLive = MutableLiveData(TransmissionState.OFF)
    val sensorDataSender : SensorDataSender by inject{ parametersOf(testSensorFlow)}
    val sensorStreamingManager : SensorStreamingManager
    by inject{ parametersOf(testTransmissionDataLive)}
    val mockSensorDataSender : SensorDataSender = mock()

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

    fun startButtonPressedTest(){
        testTransmissionDataLive.value = TransmissionState.OFF

    }

}