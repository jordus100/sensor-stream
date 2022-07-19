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
import java.text.DecimalFormat

const val SENSOR_READ_INTERVAL : Long = 0

data class sensorsData(var gyroVals : Array<Float>, var accelVals : Array<Float>)

class SensorsReadouts : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var sensorsData  = sensorsData(arrayOf(0.0f, 0.0f, 0.0f), arrayOf(0.0f, 0.0f, 0.0f))
    private lateinit var binding: SensorsReadoutsBinding
//    private val model: SensorsReadoutsVM by viewModels()
//    private lateinit var sensorsDataSource : SensorsDataSource

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = SensorsReadoutsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        /*val sensorsDataObserver = Observer<sensorsData> { sensorsData ->
            updateSensorsValues(sensorsData)
        }
        model.sensorsDataLive.observe(this, sensorsDataObserver)*/
    }

    override fun onStart() {
        super.onStart()
        println("START")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        /*SensorsDataSource.accelSensor = accelSensor
        SensorsDataSource.gyroSensor = gyroSensor*/
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun updateSensorsValues(sensorsData : sensorsData) {

        val df = DecimalFormat("0")
        df.maximumFractionDigits = 6
        binding.gyroX.text = df.format(sensorsData.gyroVals[0]).toString();
        binding.gyroY.text = df.format(sensorsData.gyroVals[1]).toString();
        binding.gyroZ.text = df.format(sensorsData.gyroVals[2]).toString();

        binding.accelX.text = df.format(sensorsData.accelVals[0]).toString();
        binding.accelY.text = df.format(sensorsData.accelVals[1]).toString();
        binding.accelZ.text = df.format(sensorsData.accelVals[2]).toString();
    }

    override fun onSensorChanged(event: SensorEvent?) {
        println("SENSOR CHANGED")
        var values : FloatArray
        if(event?.values != null) {
            values = (event?.values!!)
        }
        else return
        when(event?.sensor?.type){
            Sensor.TYPE_GYROSCOPE -> run {
                sensorsData.gyroVals = values.toList().toTypedArray()
            }
            Sensor.TYPE_ACCELEROMETER -> run {
                sensorsData.accelVals = values.toList().toTypedArray()
            }
            null -> println("null")
            else -> println("cos innego")
        }
        updateSensorsValues(sensorsData)
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }
}
/*
class SensorsReadoutsVM () : ViewModel() {
    private val sensorsDataSource: SensorsDataSource = SensorsDataSource
    var sensorsData = sensorsData(arrayOf(0.0, 0.0, 0.0), arrayOf(0.0, 0.0, 0.0))
    val sensorsDataLive: MutableLiveData<sensorsData> by lazy {
        MutableLiveData<sensorsData>()
    }
    *//*
    init {
        viewModelScope.launch {
            sensorsDataSource.sensorsRead.collect { sensorsRead : sensorsData ->
                sensorsData = sensorsRead
                sensorsDataLive.setValue(sensorsData)
            }
        }
    }
    *//*
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
