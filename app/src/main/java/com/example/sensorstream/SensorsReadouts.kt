package com.example.sensorstream

import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.*
import com.example.sensorstream.databinding.SensorsReadoutsBinding
import org.koin.android.ext.android.get
import org.koin.core.component.KoinComponent
import org.koin.core.parameter.parametersOf
import java.text.DecimalFormat


data class SensorsData(var gyroVals : Array<Float> = arrayOf(0.0f, 0.0f, 0.0f),
                       var accelVals : Array<Float> = arrayOf(0.0f, 0.0f, 0.0f)) {
    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false

        other as SensorsData

        if (!gyroVals.contentEquals(other.gyroVals)) return false
        if (!accelVals.contentEquals(other.accelVals)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = gyroVals.contentHashCode()
        result = 31 * result + accelVals.contentHashCode()
        return result
    }
}

class SensorsReadouts : AppCompatActivity(), KoinComponent {
    private lateinit var sensorsViewModel: SensorsReadoutsVM
    private lateinit var sensorManager: SensorManager
    private lateinit var uiBinding: SensorsReadoutsBinding

    private val sensorsDataObserver = Observer<SensorsData> { sensorsData ->
        updateSensorsUI(sensorsData)
    }
    private val connectionDataObserver = Observer<CONNECTION> { connection ->
        updateConnectionStatusUI(connection)
    }
    private val transmissionDataObserver = Observer<TRANSMISSION> { transmission ->
        updateTransmissionStatusUI(transmission)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiBinding = SensorsReadoutsBinding.inflate(layoutInflater)
        setContentView(uiBinding.root)
    }

    private fun setEventHandlers(){
        uiBinding.root.setOnTouchListener { _, event ->
            uiBinding.root.performClick()
            sensorsViewModel.onRootTouch(event)
        }
    }

    private fun retrieveIntentBundledData() : List<Comparable<*>>{
        val extras = intent.extras
        val retrievedData = arrayListOf<Comparable<*>>()
        val constantStreamMode = ((extras?.get("streamMode") ?: STREAM_MODE.ON_TOUCH ) as STREAM_MODE)
        retrievedData.add(constantStreamMode)
        return retrievedData
    }

    override fun onStart(){
        super.onStart()
        val retrievedData = retrieveIntentBundledData()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorsViewModel = get { parametersOf(sensorManager, retrievedData[0] as STREAM_MODE) }
        sensorsViewModel.sensorsDataLive.observe(this, sensorsDataObserver)
        sensorsViewModel.connectionDataLive.observe(this, connectionDataObserver)
        sensorsViewModel.transmissionDataLive.observe(this, transmissionDataObserver)
        setEventHandlers()
    }

    private fun updateSensorsUI(sensorsData: SensorsData) {
        val df = DecimalFormat("0.000000")
        df.maximumFractionDigits = 6
        uiBinding.accelX.text = "x : " + df.format(sensorsData.accelVals[0]).toString()
        uiBinding.accelY.text = "y : " + df.format(sensorsData.accelVals[1]).toString()
        uiBinding.accelZ.text = "z : " + df.format(sensorsData.accelVals[2]).toString()
        uiBinding.gyroX.text = "x : " + df.format(sensorsData.gyroVals[0]).toString()
        uiBinding.gyroY.text = "y : " + df.format(sensorsData.gyroVals[1]).toString()
        uiBinding.gyroZ.text = "z : " + df.format(sensorsData.gyroVals[2]).toString()
    }
    private fun updateConnectionStatusUI(connection : CONNECTION){
        when(connection){
            CONNECTION.ESTABLISHED -> uiBinding.statusText.text = getString(R.string.connection_good)
            CONNECTION.NOT_ESTABLISHED -> uiBinding.statusText.text = getString(R.string.connection_bad)
        }
    }
    private fun updateTransmissionStatusUI(transmission : TRANSMISSION){
        when(transmission){
            TRANSMISSION.ON -> uiBinding.transmissionStatusText.text = getString(R.string.transmission_status_on)
            TRANSMISSION.OFF -> uiBinding.transmissionStatusText.text = getString(R.string.transmission_status_off)
        }
    }

}

