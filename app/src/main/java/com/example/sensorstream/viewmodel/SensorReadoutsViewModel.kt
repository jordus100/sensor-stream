package com.example.sensorstream.viewmodel

import android.hardware.SensorManager
import androidx.lifecycle.*
import com.example.sensorstream.SensorDataSender
import com.example.sensorstream.model.SensorStreamingManager
import com.example.sensorstream.model.*
import com.example.sensorstream.viewstate.SensorsViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.sample
import org.koin.core.parameter.parametersOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val SENSOR_READ_DELAY : Long = 1

enum class TransmissionState {
    ON, OFF
}
enum class StartButtonState {
    START, STOP, INACTIVE
}

const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_FASTEST

class SensorsReadoutsViewModel (val sensorManager: SensorManager)
    : ViewModel(), KoinComponent {

    private val _state = MutableStateFlow(
        SensorsViewState(
            ConnectionStatus.NOT_ESTABLISHED, TransmissionState.OFF,
            SensorsData(), StartButtonState.INACTIVE))
    val state : StateFlow<SensorsViewState>
        get() = _state

    private val sensorsDataSource: SensorsDataSource by inject { parametersOf(sensorManager) }
    private val sensorDataSender: SensorDataSender by inject {
        parametersOf( sensorsDataSource.sensorDataFlow)
    }

    private val sensorStreamingManager : SensorStreamingManager by inject {
        parametersOf(sensorDataSender, state) }

    init {
        viewModelScope.launch {
            sensorsDataSource.sensorDataFlow.sample(SENSOR_READ_DELAY).collect {
                _state.value = _state.value.copy(sensorsData = it)
            }
        }
        viewModelScope.launch {
            sensorDataSender.transmissionStateFlow.collect {
                _state.value = _state.value.copy(transmissionState = it)
            }
        }
        viewModelScope.launch {
            sensorDataSender.connectionStateFlow.collect {
                _state.value = _state.value.copy(connectionStatus = it)
            }
        }
        viewModelScope.launch {
            sensorStreamingManager.startButtonStateFlow.sample(SENSOR_READ_DELAY).collect {
                _state.value = _state.value.copy(startButtonState = it)
            }
        }
    }

    fun screenPressed() {
        sensorStreamingManager.screenPressed()
    }

    fun screenTouchReleased() {
        sensorStreamingManager.screenTouchReleased()
    }

    fun startButtonClicked() {
        sensorStreamingManager.startButtonClicked()
    }

    fun streamModeCheckChanged(isChecked : Boolean) {
        sensorStreamingManager.streamModeCheckChanged(isChecked)
    }

}


