package com.example.sensorstream

import com.example.sensorstream.model.SensorStreamingManager
import com.example.sensorstream.model.appModule
import org.junit.Before
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.inject

class SensorStreamingManagerTest : KoinTest, KoinComponent{
    val sensorDataSender : SensorDataSender by inject()
    val sensorStreamingManager : SensorStreamingManager by inject{ parametersOf()}

    @Before
    fun setUp() {
        GlobalContext.startKoin {
            modules(appModule)
        }
    }
}