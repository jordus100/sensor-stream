package com.example.sensorstream.model

import com.example.sensorstream.SensorDataSender
import com.example.sensorstream.viewmodel.StartButtonState
import com.example.sensorstream.viewmodel.TransmissionState
import com.example.sensorstream.viewstate.SensorsViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class SensorStreamingManager(val sensorDataSender: SensorDataSender, var streamMode: StreamMode,
val state : StateFlow<SensorsViewState>
) {
    private val _startButtonStateFlow = MutableStateFlow(StartButtonState.INACTIVE)
    val startButtonStateFlow : StateFlow<StartButtonState>
        get() = _startButtonStateFlow

    init{
        CoroutineScope(Dispatchers.Default).launch{
            updateStartButton()
        }
    }

    fun screenPressed() {
        if (streamMode == StreamMode.ON_TOUCH)
            sensorDataSender.sendSensorData()
    }

    fun screenTouchReleased() {
        if (streamMode == StreamMode.ON_TOUCH)
            sensorDataSender.pauseSendingData()
    }

    fun startButtonClicked() {
        if (streamMode == StreamMode.CONSTANT) {
            when (state.value.startButtonState) {
                StartButtonState.START -> {
                    sensorDataSender.sendSensorData()
                    _startButtonStateFlow.value = StartButtonState.STOP
                }
                StartButtonState.STOP -> {
                    sensorDataSender.pauseSendingData()
                    _startButtonStateFlow.value = StartButtonState.START
                }
                else -> return
            }
        }
    }

    fun streamModeCheckChanged(isChecked : Boolean) {
        if(DEFAULT_STREAM_MODE == StreamMode.ON_TOUCH)
            if(isChecked)
                changeStreamMode(StreamMode.CONSTANT)
            else
                changeStreamMode(StreamMode.ON_TOUCH)
        else{
            if(isChecked)
                changeStreamMode(StreamMode.ON_TOUCH)
            else
                changeStreamMode(StreamMode.CONSTANT)
        }
    }

    private fun changeStreamMode(newStreamMode: StreamMode){
        if(newStreamMode == StreamMode.CONSTANT){
            sensorDataSender.pauseSendingData()
            streamMode = StreamMode.CONSTANT
            _startButtonStateFlow.value = StartButtonState.START
        }
        else{
            sensorDataSender.pauseSendingData()
            streamMode = StreamMode.ON_TOUCH
            _startButtonStateFlow.value = StartButtonState.INACTIVE
        }
    }

    private suspend fun updateStartButton(){
        state.sample(300L).collect{
            if(it.transmissionState == TransmissionState.OFF
                && state.value.startButtonState != StartButtonState.INACTIVE)
                _startButtonStateFlow.value = StartButtonState.START
        }
    }

}