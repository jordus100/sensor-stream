package com.example.sensorstream.model

sealed class SensorsViewEvents(){
    object ScreenPressed : SensorsViewEvents()
    object ScreenReleased : SensorsViewEvents()
    object StartButtonClicked : SensorsViewEvents()
    class StreamModeCheckboxChanged(val isChecked : Boolean) : SensorsViewEvents()
}