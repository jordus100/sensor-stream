package com.example.sensorstream.viewmodel

import android.hardware.SensorManager
import androidx.lifecycle.*
import com.example.sensorstream.BuildConfig
import com.example.sensorstream.SensorDataSender
import com.example.sensorstream.model.*
import com.example.sensorstream.view.DEFAULT_STREAM_MODE
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

    var streamMode = DEFAULT_STREAM_MODE
    val websocketServerUrl = BuildConfig.WEBSOCKET_SERVER
    val websocketServerPort = BuildConfig.WEBSOCKET_SERVER_PORT
    private val sensorsDataSource: SensorsDataSource by inject { parametersOf(sensorManager) }
    private val sensorDataSender: SensorDataSender by inject {
        parametersOf(
            websocketServerUrl, websocketServerPort, 1000L,
            sensorsDataSource.sensorDataFlow
        )
    }
    val sensorsDataLive: MutableLiveData<SensorsData> by lazy {
        MutableLiveData<SensorsData>()
    }

    val connectionDataLive = MutableLiveData(ConnectionStatus.NOT_ESTABLISHED)
    val transmissionDataLive = MutableLiveData(TransmissionState.OFF)
    private val sensorStreamingManager : SensorStreamingManager by inject {
        parametersOf(sensorDataSender, streamMode, transmissionDataLive) }
    val startButtonLabelDataLive = sensorStreamingManager.startButtonLabelDataLive

    init {
        viewModelScope.launch {
            sensorsDataSource.sensorDataFlow.sample(SENSOR_READ_DELAY).collect {
                sensorsDataLive.value = it
            }
        }
        viewModelScope.launch {
            sensorDataSender.transmissionStateFlow.collect {
                transmissionDataLive.value = it
            }
        }
        viewModelScope.launch {
            sensorDataSender.connectionStateFlow.collect {
                connectionDataLive.value = it
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


