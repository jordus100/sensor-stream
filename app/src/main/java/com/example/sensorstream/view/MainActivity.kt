package com.example.sensorstream.view

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.sensorstream.databinding.MainBinding
import com.example.sensorstream.model.StreamMode

class MainActivity : AppCompatActivity() {

    private lateinit var uiBinding: MainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiBinding = MainBinding.inflate(layoutInflater)
        setContentView(uiBinding.root)
    }

    fun onStartBtnClicked(view: View) {
        val sensorReadoutsScreenIntent = Intent(this, SensorsReadoutsActivity::class.java)
        val streamMode =  if (uiBinding.streamModeCheckBox?.isChecked() == true) StreamMode.CONSTANT else StreamMode.ON_TOUCH
        sensorReadoutsScreenIntent.putExtra("streamMode", streamMode)
        startActivity(sensorReadoutsScreenIntent)
    }
}

