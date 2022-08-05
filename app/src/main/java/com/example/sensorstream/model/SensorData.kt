package com.example.sensorstream.model

import android.hardware.SensorManager

enum class ConnectionStatus {
    ESTABLISHED, NOT_ESTABLISHED
}
enum class StreamMode {
    CONSTANT, ON_TOUCH
}

data class Point3F(val x: Float = 0.0f, val y: Float = 0.0f, val z: Float = 0.0f) {
    companion object {
        fun from(array: FloatArray) = Point3F(
            array.getOrNull(0) ?: 0.0f,
            array.getOrNull(1) ?: 0.0f,
            array.getOrNull(2) ?: 0.0f
        )
    }
    override fun toString() = "[$x; $y; $z]"
}

data class SensorsData(var gyro : Point3F = Point3F(), var accel : Point3F = Point3F())