package com.example.sensorstream.view

import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.*
import com.example.sensorstream.*
import com.example.sensorstream.databinding.SensorsReadoutsBinding
import com.example.sensorstream.model.ConnectionStatus
import com.example.sensorstream.model.SensorsData
import com.example.sensorstream.model.StreamMode
import com.example.sensorstream.viewmodel.SensorsReadoutsViewModel
import com.example.sensorstream.viewmodel.TRANSMISSION
import org.koin.android.ext.android.get
import org.koin.core.component.KoinComponent
import org.koin.core.parameter.parametersOf
import java.text.DecimalFormat


class SensorsReadoutsActivity : AppCompatActivity(), KoinComponent {
    private lateinit var sensorsViewModel: SensorsReadoutsViewModel
    private lateinit var sensorManager: SensorManager
    private lateinit var uiBinding: SensorsReadoutsBinding

    private val sensorsDataObserver = Observer<SensorsData> { sensorsData ->
        updateSensorsUI(sensorsData)
    }
    private val connectionDataObserver = Observer<ConnectionStatus> { connection ->
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
        val constantStreamMode = ((extras?.get("streamMode") ?: StreamMode.ON_TOUCH) as StreamMode)
        retrievedData.add(constantStreamMode)
        return retrievedData
    }

    override fun onStart(){
        super.onStart()
        val retrievedData = retrieveIntentBundledData()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorsViewModel = get { parametersOf(sensorManager, retrievedData[0] as StreamMode) }
        sensorsViewModel.sensorsDataLive.observe(this, sensorsDataObserver)
        sensorsViewModel.connectionDataLive.observe(this, connectionDataObserver)
        sensorsViewModel.transmissionDataLive.observe(this, transmissionDataObserver)
        setEventHandlers()
    }

    private fun updateSensorsUI(sensorsData: SensorsData) {
        val df = DecimalFormat("0.000000")
        df.maximumFractionDigits = 6
        uiBinding.accelX.text = "x : " + df.format(sensorsData.accel.x).toString()
        uiBinding.accelY.text = "y : " + df.format(sensorsData.accel.y).toString()
        uiBinding.accelZ.text = "z : " + df.format(sensorsData.accel.z).toString()
        uiBinding.gyroX.text = "x : " + df.format(sensorsData.gyro.x).toString()
        uiBinding.gyroY.text = "y : " + df.format(sensorsData.gyro.y).toString()
        uiBinding.gyroZ.text = "z : " + df.format(sensorsData.gyro.z).toString()
    }
    private fun updateConnectionStatusUI(connection : ConnectionStatus){
        when(connection){
            ConnectionStatus.ESTABLISHED -> uiBinding.statusText.text = getString(R.string.connection_good)
            ConnectionStatus.NOT_ESTABLISHED -> uiBinding.statusText.text = getString(R.string.connection_bad)
        }
    }
    private fun updateTransmissionStatusUI(transmission : TRANSMISSION){
        when(transmission){
            TRANSMISSION.ON -> uiBinding.transmissionStatusText.text = getString(R.string.transmission_status_on)
            TRANSMISSION.OFF -> uiBinding.transmissionStatusText.text = getString(R.string.transmission_status_off)
        }
    }

}

