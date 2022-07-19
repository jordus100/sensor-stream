package com.example.sensorstream

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
    }
    fun onStartBtnClicked(view: View){
        val intent = Intent(this, SensorsReadouts::class.java)
        startActivity(intent)
    }
}

