package com.example.sensorstream

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.viewbinding.ViewBinding
import com.example.sensorstream.databinding.SensorsReadoutsBinding
import com.example.sensorstream.databinding.SensorsReadoutsPorBinding
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
    private var accelValsText : Array<TextView?> = arrayOfNulls(3)
    private var gyroValsText : Array<TextView?> = arrayOfNulls(3)
    private var sensorsData  = sensorsData(arrayOf(0.0f, 0.0f, 0.0f), arrayOf(0.0f, 0.0f, 0.0f))
    private lateinit var porBinding : SensorsReadoutsPorBinding
    private lateinit var horBinding: SensorsReadoutsBinding
//    private val model: SensorsReadoutsVM by viewModels()
//    private lateinit var sensorsDataSource : SensorsDataSource

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        if (getResources().getConfiguration().orientation === Configuration.ORIENTATION_LANDSCAPE) {
            println("ORIENTACJA")
            horBinding = SensorsReadoutsBinding.inflate(layoutInflater)
            val view = horBinding.root
            setContentView(view)
            configUIRefs()
        } else if (getResources().getConfiguration().orientation === Configuration.ORIENTATION_PORTRAIT) {
            porBinding = SensorsReadoutsPorBinding.inflate(layoutInflater)
            val view = porBinding.root
            setContentView(view)
            configUIRefs()
        }
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

    private fun configUIRefs(){
        accelValsText[0] = findViewById<TextView>(R.id.accelX)
        accelValsText[1] = findViewById<TextView>(R.id.accelY)
        accelValsText[2] = findViewById<TextView>(R.id.accelZ)
        gyroValsText[0] = findViewById<TextView>(R.id.gyroX)
        gyroValsText[1] = findViewById<TextView>(R.id.gyroY)
        gyroValsText[2] = findViewById<TextView>(R.id.gyroZ)
    }
    private fun updateSensorsValues(sensorsData : sensorsData) {
        val df = DecimalFormat("0")
        df.maximumFractionDigits = 6
        for(i in 0..2){
            accelValsText[i]?.text =  df.format(sensorsData.accelVals[i]).toString()
        }
        for(i in 0..2){
            gyroValsText[i]?.text = df.format(sensorsData.gyroVals[i]).toString()
        }
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
