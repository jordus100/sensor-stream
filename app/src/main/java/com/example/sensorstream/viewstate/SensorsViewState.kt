package com.example.sensorstream.viewstate

import com.example.sensorstream.model.ConnectionStatus
import com.example.sensorstream.model.SensorsData
import com.example.sensorstream.viewmodel.StartButtonState
import com.example.sensorstream.viewmodel.TransmissionState

data class SensorsViewState(val connectionStatus: ConnectionStatus,
                            val transmissionState : TransmissionState,
                            val sensorsData: SensorsData,
                            val startButtonState : StartButtonState)