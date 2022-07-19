package com.example.sensorstream

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sensorstream.databinding.SensorsReadoutsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

const val SENSOR_READ_INTERVAL : Long = 0

data class sensorsData(var gyroVals : Array<Double>, var accelVals : Array<Double>)

class SensorsReadouts : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: SensorsReadoutsBinding
    private val model: SensorsReadoutsVM by viewModels()
    private lateinit var sensorsDataSource : SensorsDataSource

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = SensorsReadoutsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        val sensorsDataObserver = Observer<sensorsData> { sensorsData ->
            updateSensorsValues(sensorsData)
        }
        model.sensorsDataLive.observe(this, sensorsDataObserver)
    }

    override fun onStart() {
        super.onStart()
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        SensorsDataSource.accelSensor = accelSensor
        SensorsDataSource.gyroSensor = gyroSensor
    }

    fun updateSensorsValues(sensorsData : sensorsData) {
        binding.gyroX.text = sensorsData.gyroVals[0].toString();
        binding.gyroY.text = sensorsData.gyroVals[1].toString();
        binding.gyroZ.text = sensorsData.gyroVals[2].toString();

        binding.accelX.text = sensorsData.accelVals[0].toString();
        binding.accelY.text = sensorsData.accelVals[1].toString();
        binding.accelZ.text = sensorsData.accelVals[2].toString();
    }


}

class SensorsReadoutsVM () : ViewModel() {
    private val sensorsDataSource: SensorsDataSource = SensorsDataSource
    var sensorsData = sensorsData(arrayOf(0.0, 0.0, 0.0), arrayOf(0.0, 0.0, 0.0))
    val sensorsDataLive: MutableLiveData<sensorsData> by lazy {
        MutableLiveData<sensorsData>()
    }

    init {
        viewModelScope.launch {
            sensorsDataSource.sensorsRead.collect { sensorsRead : sensorsData ->
                sensorsData = sensorsRead
                sensorsDataLive.setValue(sensorsData)
            }
        }
    }
}

object SensorsDataSource {
    var accelSensor : Sensor? = null
    var gyroSensor : Sensor? = null

    val sensorsRead: Flow<sensorsData> = flow {
        while(true) {
            if(accelSensor != null && gyroSensor != null) {
                val sensorsData = getSensorsData()
                emit(sensorsData) // Emits the result of the request to the flow
                delay(SENSOR_READ_INTERVAL) // Suspends the coroutine for some time
            }
        }
    }
    private fun getSensorsData() : sensorsData{
        return sensorsData(arrayOf(0.0, 0.0, 0.0), arrayOf(0.0, 0.0, 0.0))
    }
}