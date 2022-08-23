package com.example.sensorstream

import com.example.sensorstream.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.test.KoinTest
import org.koin.test.inject
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.test.assertEquals


class SensorStreamingManagerTest : KoinTest, KoinComponent{

    val mockSensorDataSender : SensorDataSender = mock() {
        on { sendSensorData() }.then{}
        on { pauseSendingData() }.then{}
    }
    val state : MutableStateFlow<SensorsViewState> by inject(named("initialState"))
    val sensorStreamingManager : SensorStreamingManager by inject{
        parametersOf(mockSensorDataSender, CoroutineScope(Dispatchers.Default), state,
            { testState : StartButtonState ->
                state.update { state.value.copy(startButtonState = testState) }
            },
            { testState : StreamMode ->
                state.update { state.value.copy(streamMode = testState) }
            })
    }


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
    fun streamModeChangeTest(){
        state.update { state.value.copy(streamMode = StreamMode.ON_TOUCH) }
        sensorStreamingManager.streamModeCheckChanged(true)
        assertEquals(expected = StreamMode.CONSTANT, actual = state.value.streamMode)
        verify(mockSensorDataSender, times(1)).pauseSendingData()
        sensorStreamingManager.streamModeCheckChanged(false)
        verify(mockSensorDataSender, times(2)).pauseSendingData()
        assertEquals(expected = StreamMode.ON_TOUCH, actual = state.value.streamMode)
    }

    @Test
    fun startButtonClicked(){
        state.update { state.value.copy(
            streamMode = StreamMode.CONSTANT, startButtonState = StartButtonState.START) }
        sensorStreamingManager.startButtonClicked()
        verify(mockSensorDataSender, times(1)).sendSensorData()
        sensorStreamingManager.startButtonClicked()
        verify(mockSensorDataSender, times(1)).pauseSendingData()
    }

    @Test
    fun updateStartButtonTest(){
        state.update { state.value.copy(
            streamMode = StreamMode.CONSTANT, startButtonState = StartButtonState.START) }
        sensorStreamingManager.startButtonClicked()
        state.update { state.value.copy(transmissionState = TransmissionState.ON) }
        runBlocking { delay(2000) }
        assertEquals(expected = StartButtonState.STOP, actual = state.value.startButtonState)
        state.update { state.value.copy(transmissionState = TransmissionState.OFF) }
        runBlocking { delay(500) }
        assertEquals(expected = StartButtonState.START, actual = state.value.startButtonState)
    }
}
