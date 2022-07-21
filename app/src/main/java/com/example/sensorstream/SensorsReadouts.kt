package com.example.sensorstream

import android.content.res.Configuration
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.*
import com.example.sensorstream.databinding.SensorsReadoutsBinding
import com.example.sensorstream.databinding.SensorsReadoutsPorBinding
import java.text.DecimalFormat


data class SensorsData(var gyroVals : Array<Float> = arrayOf(0.0f, 0.0f, 0.0f), var accelVals : Array<Float> = arrayOf(0.0f, 0.0f, 0.0f)) {
    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false

        other as SensorsData

        if (!gyroVals.contentEquals(other.gyroVals)) return false
        if (!accelVals.contentEquals(other.accelVals)) return false
        println("WTF")
        return true
    }

    override fun hashCode(): Int {
        var result = gyroVals.contentHashCode()
        result = 31 * result + accelVals.contentHashCode()
        return result
    }
}

class SensorsReadouts : AppCompatActivity() {

    private lateinit var sensorsViewModel: SensorsReadoutsVM
    private lateinit var sensorManager: SensorManager
    private var accelValsText: Array<TextView?> = arrayOfNulls(3)
    private var gyroValsText: Array<TextView?> = arrayOfNulls(3)
    private lateinit var porBinding: SensorsReadoutsPorBinding
    private lateinit var horBinding: SensorsReadoutsBinding

    private val sensorsDataObserver = Observer<SensorsData> { sensorsData ->
        updateSensorsUI(sensorsData)
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            horBinding = SensorsReadoutsBinding.inflate(layoutInflater)
            val view = horBinding.root
            setContentView(view)
            configUIRefs()
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            porBinding = SensorsReadoutsPorBinding.inflate(layoutInflater)
            val view = porBinding.root
            setContentView(view)
            configUIRefs()
        }
    }

    override fun onStart(){
        super.onStart()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorsViewModel = SensorsReadoutsVM(sensorManager)
        sensorsViewModel.sensorsDataLive.observe(this, sensorsDataObserver)
    }

    private fun configUIRefs() {
        accelValsText[0] = findViewById<TextView>(R.id.accelX)
        accelValsText[1] = findViewById<TextView>(R.id.accelY)
        accelValsText[2] = findViewById<TextView>(R.id.accelZ)
        gyroValsText[0] = findViewById<TextView>(R.id.gyroX)
        gyroValsText[1] = findViewById<TextView>(R.id.gyroY)
        gyroValsText[2] = findViewById<TextView>(R.id.gyroZ)
    }

    private fun updateSensorsUI(sensorsData: SensorsData) {
        val df = DecimalFormat("0")
        df.maximumFractionDigits = 6
        for (i in 0..2) {
            accelValsText[i]?.text = df.format(sensorsData.accelVals[i]).toString()
        }
        for (i in 0..2) {
            gyroValsText[i]?.text = df.format(sensorsData.gyroVals[i]).toString()
        }
    }

}

