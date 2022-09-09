package com.example.sensorstream.viewmodel

import android.hardware.SensorManager
import androidx.lifecycle.*
import com.example.sensorstream.model.SensorDataSender
import com.example.sensorstream.model.SensorStreamingManager
import com.example.sensorstream.model.*
import com.example.sensorstream.model.SensorsViewState
import com.example.sensorstream.view.SensorsViewEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.qualifier.named

const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_FASTEST

class SensorsReadoutsViewModel (private val sensorManager: SensorManager)
    : ViewModel(), KoinComponent {

    private val _state : MutableStateFlow<SensorsViewState> by inject(named("initialState"))
    val state : StateFlow<SensorsViewState>
        get() = _state

    private val sensorsDataSource : SensorsDataSource = get {
        parametersOf(sensorManager, state, viewModelScope,
            { sensorData : SensorsData ->
                _state.update {
                    println(sensorData.referencePoint.x)
                    it.copy(sensorsData = sensorData) } } ) }
    private val sensorDataManipulator : SensorDataManipulator = get {
        parametersOf(state, viewModelScope)
    }
    private val sensorDataSender : SensorDataSender = get {
        parametersOf(sensorDataManipulator.formatSensorData, viewModelScope, state,
            { transmissionState : TransmissionState ->
                _state.update { it.copy(transmissionState = transmissionState) } },
            { connectionStatus : ConnectionStatus ->
                _state.update { it.copy(connectionStatus = connectionStatus) }
            })
    }

    private val sensorStreamingManager : SensorStreamingManager = get {
        parametersOf(sensorDataSender, viewModelScope, _state,
            { startButtonState: StartButtonState ->
                _state.update { it.copy(startButtonState = startButtonState) }
            },
            { streamMode: StreamMode ->
                _state.update { it.copy(streamMode = streamMode) }
            })
    }

    fun consumeUiEvents(uiEventsFlow : SharedFlow<SensorsViewEvents>) {
        viewModelScope.launch {
            uiEventsFlow.collect {
                when (it) {
                    is SensorsViewEvents.ScreenPressed -> sensorStreamingManager.screenPressed()
                    is SensorsViewEvents.ScreenReleased ->
                        sensorStreamingManager.screenTouchReleased()
                    is SensorsViewEvents.StartButtonClicked ->
                        sensorStreamingManager.startButtonClicked()
                    is SensorsViewEvents.StreamModeCheckboxChanged ->
                        sensorStreamingManager.streamModeCheckChanged(it.isChecked)
                }
            }
        }
    }

}