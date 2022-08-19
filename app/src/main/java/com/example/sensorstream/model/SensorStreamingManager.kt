package com.example.sensorstream.model

import com.example.sensorstream.SensorDataSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SensorStreamingManager(val sensorDataSender: SensorDataSender,
                             val state : StateFlow<SensorsViewState>,
                             val startButtonStateUpdate : (StartButtonState) -> Unit,
                             val streamModeUpdate : (StreamMode) -> Unit) {

    init {
        CoroutineScope(Dispatchers.Default).launch{
            updateStartButton()
        }
    }

    fun screenPressed() {
        if (state.value.streamMode == StreamMode.ON_TOUCH)
            sensorDataSender.sendSensorData()
    }

    fun screenTouchReleased() {
        if (state.value.streamMode == StreamMode.ON_TOUCH)
            sensorDataSender.pauseSendingData()
    }

    fun startButtonClicked() {
        if (state.value.streamMode == StreamMode.CONSTANT) {
            when (state.value.startButtonState) {
                StartButtonState.START -> {
                    sensorDataSender.sendSensorData()
                    startButtonStateUpdate(StartButtonState.STOP)
                }
                StartButtonState.STOP -> {
                    sensorDataSender.pauseSendingData()
                    startButtonStateUpdate(StartButtonState.START)
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
        sensorDataSender.pauseSendingData()
        if(newStreamMode == StreamMode.CONSTANT){
            startButtonStateUpdate(StartButtonState.START)
            streamModeUpdate(StreamMode.CONSTANT)
        }
        else{
            startButtonStateUpdate(StartButtonState.INACTIVE)
            streamModeUpdate(StreamMode.ON_TOUCH)
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun updateStartButton(){
        state.sample(300L).collect{
            if(it.transmissionState == TransmissionState.OFF
            && state.value.startButtonState != StartButtonState.INACTIVE)
                startButtonStateUpdate(StartButtonState.START)
            if(it.transmissionState == TransmissionState.ON
                && state.value.startButtonState != StartButtonState.INACTIVE)
                startButtonStateUpdate(StartButtonState.STOP)
        }
    }

}