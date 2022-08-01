package com.example.sensorstream

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import com.example.sensorstream.databinding.MainBinding
import com.example.sensorstream.databinding.SensorsReadoutsBinding

class MainActivity : AppCompatActivity() {

    private lateinit var uiBinding: MainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiBinding = MainBinding.inflate(layoutInflater)
        setContentView(uiBinding.root)
    }

    fun onStartBtnClicked(view: View){
        val sensorReadoutsScreenIntent = Intent(this, SensorsReadouts::class.java)
        val streamMode =  if (uiBinding.streamModeCheckBox?.isChecked() == true) STREAM_MODE.CONSTANT
                else STREAM_MODE.ON_TOUCH
        sensorReadoutsScreenIntent.putExtra("streamMode", streamMode)
        startActivity(sensorReadoutsScreenIntent)
    }
}

