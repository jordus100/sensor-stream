package com.example.sensorstream.model


enum class ConnectionStatus {
    ESTABLISHED, NOT_ESTABLISHED
}
enum class StreamMode {
    CONSTANT, ON_TOUCH
}
enum class TransmissionState {
    ON, OFF
}
enum class StartButtonState {
    START, STOP, INACTIVE
}

data class SensorsViewState(val connectionStatus: ConnectionStatus,
                            val transmissionState : TransmissionState,
                            val sensorsData: SensorsData,
                            val startButtonState : StartButtonState,
                            val streamMode: StreamMode)