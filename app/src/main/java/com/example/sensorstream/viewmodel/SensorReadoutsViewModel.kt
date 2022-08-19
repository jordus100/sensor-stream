package com.example.sensorstream.viewmodel

import android.hardware.SensorManager
import androidx.lifecycle.*
import com.example.sensorstream.SensorDataSender
import com.example.sensorstream.model.SensorStreamingManager
import com.example.sensorstream.model.*
import com.example.sensorstream.model.SensorsViewState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.util.Objects.toString

const val SENSOR_READ_DELAY : Long = 1
const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_FASTEST

@OptIn(FlowPreview::class)
class SensorsReadoutsViewModel (private val sensorManager: SensorManager)
    : ViewModel(), KoinComponent {

    private val _state : MutableStateFlow<SensorsViewState> by inject(named("initialState"))
    val state : StateFlow<SensorsViewState>
        get() = _state

    private val sensorsDataSource: SensorsDataSource by inject { parametersOf(sensorManager) }
    private val sensorDataSender: SensorDataSender by inject {
        parametersOf(sensorsDataSource.sensorDataFlow, state,
            { transmissionState : TransmissionState ->
                _state.update { it.copy(transmissionState = transmissionState) } },
            { connectionStatus : ConnectionStatus ->
                _state.update { it.copy(connectionStatus = connectionStatus) }
            })
    }

    private val sensorStreamingManager : SensorStreamingManager by inject {
        parametersOf(sensorDataSender, _state,
            { startButtonState : StartButtonState ->
            _state.update { it.copy(startButtonState = startButtonState) } },
            { streamMode : StreamMode ->
                _state.update { it.copy(streamMode = streamMode) }
            })
    }

    init {
        viewModelScope.launch {
            sensorsDataSource.sensorDataFlow.sample(SENSOR_READ_DELAY).collect { sensorsData ->
                _state.update { _state.value.copy(sensorsData = sensorsData) }
            }
        }
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


