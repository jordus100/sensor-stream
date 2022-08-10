package com.example.sensorstream.model

import androidx.lifecycle.MutableLiveData
import com.example.sensorstream.SensorDataSender
import com.example.sensorstream.viewmodel.StartButtonState
import com.example.sensorstream.viewmodel.TransmissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SensorStreamingManager(val sensorDataSender: SensorDataSender, var streamMode: StreamMode,
val transmissionState: MutableLiveData<TransmissionState>) {
    val startButtonLabelDataLive = MutableLiveData(StartButtonState.INACTIVE)

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
            when (startButtonLabelDataLive.value) {
                StartButtonState.START -> {
                    sensorDataSender.sendSensorData()
                    startButtonLabelDataLive.value = StartButtonState.STOP
                }
                StartButtonState.STOP -> {
                    sensorDataSender.pauseSendingData()
                    startButtonLabelDataLive.value = StartButtonState.START
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
            startButtonLabelDataLive.value = StartButtonState.START
        }
        else{
            sensorDataSender.pauseSendingData()
            streamMode = StreamMode.ON_TOUCH
            startButtonLabelDataLive.value = StartButtonState.INACTIVE
        }
    }

    private suspend fun updateStartButton(){
        while(true){
            if(transmissionState.value == TransmissionState.OFF &&
            startButtonLabelDataLive.value != StartButtonState.INACTIVE)
                startButtonLabelDataLive.postValue(StartButtonState.START)
            delay(300)
        }
    }

}