package com.example.sensorstream.view

import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import androidx.lifecycle.*
import com.example.sensorstream.*
import com.example.sensorstream.databinding.SensorsReadoutsBinding
import com.example.sensorstream.model.ConnectionStatus
import com.example.sensorstream.model.SensorsData
import com.example.sensorstream.model.StartButtonState
import com.example.sensorstream.model.TransmissionState
import com.example.sensorstream.viewmodel.SensorsReadoutsViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.core.component.KoinComponent
import org.koin.core.parameter.parametersOf
import java.text.DecimalFormat


class SensorsReadoutsActivity : AppCompatActivity(), KoinComponent {
    private lateinit var sensorsViewModel: SensorsReadoutsViewModel
    private lateinit var sensorManager: SensorManager
    private lateinit var uiBinding: SensorsReadoutsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiBinding = SensorsReadoutsBinding.inflate(layoutInflater)
        setContentView(uiBinding.root)
    }

    override fun onStart() {
        super.onStart()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorsViewModel = get { parametersOf(sensorManager) }
        lifecycleScope.launch {
            sensorsViewModel.state.collect {
                updateSensorsUI(it.sensorsData)
                updateConnectionStatusUI(it.connectionStatus)
                updateTransmissionStateStatusUI(it.transmissionState)
                updateStartButton(it.startButtonState)
            }
        }
        setEventHandlers()
    }

    private fun setEventHandlers(){
        uiBinding.root.setOnTouchListener { _, event ->
            uiBinding.root.performClick()
            if(event.pointerCount == 1) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sensorsViewModel.screenPressed()
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_UP -> {
                        sensorsViewModel.screenTouchReleased()
                        return@setOnTouchListener true
                    }
                    else -> return@setOnTouchListener false
                }
            }
            return@setOnTouchListener false
        }

        uiBinding.startButton.setOnClickListener{ _ ->
            sensorsViewModel.startButtonClicked()
        }

        uiBinding.streamModeCheckBox.setOnCheckedChangeListener{ _, isChecked ->
            sensorsViewModel.streamModeCheckChanged(isChecked)
        }
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
            ConnectionStatus.ESTABLISHED -> uiBinding.statusText.text = getString(
                R.string.connection_good)
            ConnectionStatus.NOT_ESTABLISHED -> uiBinding.statusText.text = getString(
                R.string.connection_bad)
        }
    }
    private fun updateTransmissionStateStatusUI(transmissionState : TransmissionState){
        when(transmissionState){
            TransmissionState.ON -> uiBinding.transmissionStatusText.text = getString(
                R.string.transmission_status_on)
            TransmissionState.OFF -> uiBinding.transmissionStatusText.text = getString(
                R.string.transmission_status_off)
        }
    }

    fun updateStartButton(startButtonState: StartButtonState){
        when(startButtonState){
            StartButtonState.START -> {
                uiBinding.startButton.isEnabled = true
                uiBinding.startButton.text = getString(R.string.start)
            }
            StartButtonState.STOP -> uiBinding.startButton.text = getString(R.string.stop)
            StartButtonState.INACTIVE -> {
                uiBinding.startButton.text = getString(R.string.start)
                uiBinding.startButton.isEnabled = false
            }
        }
    }

}

