package com.example.sensorstream

import android.widget.TextView

//const val SENSOR_READ_INTERVAL : Long = 0

//    private val model: SensorsReadoutsVM by viewModels()
//    private lateinit var sensorsDataSource : SensorsDataSource

/*SensorsDataSource.accelSensor = accelSensor
        SensorsDataSource.gyroSensor = gyroSensor*/

/*val sensorsDataObserver = Observer<sensorsData> { sensorsData ->
            updateSensorsValues(sensorsData)
        }
        model.sensorsDataLive.observe(this, sensorsDataObserver)*/

/*
class SensorsReadoutsVM () : ViewModel() {
    private val sensorsDataSource: SensorsDataSource = SensorsDataSource
    var sensorsData = sensorsData(arrayOf(0.0, 0.0, 0.0), arrayOf(0.0, 0.0, 0.0))

}*/
/*

class SensorsDataSource() : SensorEventListener{



    val sensorsRead: Flow<sensorsData> = flow {
        while(true) {
            if(accelSensor != null && gyroSensor != null) {
                val sensorsData = getSensorsData()
                emit(sensorsData) // Emits the result of the request to the flow
                delay(SENSOR_READ_INTERVAL) // Suspends the coroutine for some time
            }
        }



}*/

/*
private fun configUIRefs() {
    accelValsText[0] = findViewById<TextView>(R.id.accelX)
    accelValsText[1] = findViewById<TextView>(R.id.accelY)
    accelValsText[2] = findViewById<TextView>(R.id.accelZ)
    gyroValsText[0] = findViewById<TextView>(R.id.gyroX)
    gyroValsText[1] = findViewById<TextView>(R.id.gyroY)
    gyroValsText[2] = findViewById<TextView>(R.id.gyroZ)
}*/
