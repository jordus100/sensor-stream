package com.example.sensorstream.model

sealed class SensorsViewEvents(){
    class ScreenPressed : SensorsViewEvents()
    class ScreenReleased : SensorsViewEvents()
    class StartButtonClicked() : SensorsViewEvents()
    class StreamModeCheckboxChanged(val isChecked : Boolean) : SensorsViewEvents()
}