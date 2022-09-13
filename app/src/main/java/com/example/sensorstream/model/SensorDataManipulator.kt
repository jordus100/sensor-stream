package com.example.sensorstream.model

import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import java.lang.Math.abs
import java.time.Duration
import java.util.*
import javax.vecmath.Vector3d

operator fun Vector3d.times(other : Double) : Vector3d{
    var copy = Vector3d(this.x, this.y, this.z)
    copy.scale(other)
    return copy
}
operator fun Vector3d.plus(other: Vector3d) : Vector3d{
    var copy = Vector3d(this.x, this.y, this.z)
    copy.add(other)
    return copy
}

class SensorDataManipulator(private val sensorDataFlow : StateFlow<SensorsViewState>,
                            scope: CoroutineScope) {
    private var rotationQArr = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
    private var referenceQArr = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
    private var positionVec = Vector3d()
    private var position = Vector3d()
    private var speed = Vector3d()
    private val meanDepth = 10
    private var accelFiltered = Vector3d()

    init{
        scope.launch {
            val tendency = calcGreatestDriftComponents(Duration.ofSeconds(10))
            println(tendency)
            calcPosition(tendency)
        }
    }

    val formatSensorData : (SensorsData) -> String = {
        SensorManager.getQuaternionFromVector(rotationQArr,
        floatArrayOf(it.rotationVector.x, it.rotationVector.y, it.rotationVector.z))
        SensorManager.getQuaternionFromVector(referenceQArr,
            floatArrayOf(it.referencePoint.x, it.referencePoint.y , it.referencePoint.z))

        "" + rotationQArr[1] + " " + rotationQArr[2] + " " + rotationQArr[3] + " " +
        rotationQArr[0] + " ; " + referenceQArr[1] + " " + referenceQArr[2] + " " +
        referenceQArr[3] + " " + referenceQArr[0] + " ; " + it.accel.x + " " + it.accel.y +
        " " + it.accel.z + " ; " + accelFiltered.x + " " + accelFiltered.y + " " + + accelFiltered.z +
        " ; " + speed.x + " " + speed.y + " " + speed.z + " ; " + positionVec.x + " " +
        positionVec.y + " " + positionVec.z
    }
    private suspend fun calcPosition(tendency : Vector3d) {
        var delta: Double
        var prevTimestamp = 0L
        var lastReadings = ArrayDeque<Vector3d>()
        sensorDataFlow.collect {
            if (it.sensorsData.timestamp != 0L && prevTimestamp != 0L
                && it.sensorsData.timestamp != prevTimestamp) {
                accelFiltered = Vector3d(it.sensorsData.accel.x.toDouble(),
                    it.sensorsData.accel.y.toDouble(), it.sensorsData.accel.z.toDouble())
                delta = (it.sensorsData.timestamp - prevTimestamp) / 1000000000.toDouble()
                lastReadings.add(accelFiltered)
                accelFiltered = calcVectorMean(lastReadings)
                if(lastReadings.size >= meanDepth)
                {
                    var sign: Double
                    if(abs(accelFiltered.x) <= tendency.x) {
                        println("ZEROING")
                        accelFiltered.x *= 0.9
                        speed.x *= 0.95
                    }
                    else{
                        if(accelFiltered.x < 0)
                            sign = 1.0
                        else sign = -1.0
                        accelFiltered.x += tendency.x * sign
                        //speed.x += tendency.x * -1 * delta
                    }
                    if(abs(accelFiltered.y) <= tendency.y) {
                        accelFiltered.y = 0.0
                        speed.y = 0.0
                    }
                    else{
                        if(accelFiltered.y < 0)
                            sign = 1.0
                        else sign = -1.0
                        accelFiltered.y += tendency.y * sign
                        //speed.y += tendency.y * -1 * delta
                    }
                    if(abs(accelFiltered.z) <= tendency.z) {
                        accelFiltered.z = 0.0
                        speed.z = 0.0
                    }
                    else{
                        if(accelFiltered.z < 0)
                        sign = 1.0
                        else sign = -1.0
                        accelFiltered.z += tendency.z * sign
                        //speed.z += tendency.z * -1 * delta
                    }
                    position += (speed * delta) + (accelFiltered * 0.5 * (delta * delta))
                    positionVec = position
                    speed += accelFiltered * delta
                    prevTimestamp = it.sensorsData.timestamp
                    //println(position)
                    lastReadings.removeLast()
                }
            } else if (it.sensorsData.timestamp != 0L)
                prevTimestamp = it.sensorsData.timestamp
        }
    }

    private suspend fun calcGreatestDriftComponents(duration : Duration) : Vector3d{
        val startTimeMillis = System.currentTimeMillis()
        var maxX = 0f
        var maxY = 0f
        var maxZ = 0f
        sensorDataFlow.takeWhile{
            System.currentTimeMillis() - startTimeMillis <= duration.toMillis() }.collect {
            if(abs(it.sensorsData.accel.x) > maxX)
                maxX = abs(it.sensorsData.accel.x)
            if(abs(it.sensorsData.accel.y) > maxY)
                maxY = abs(it.sensorsData.accel.y)
            if(abs(it.sensorsData.accel.z) > maxZ)
                maxZ = abs(it.sensorsData.accel.z)
        }
        return Vector3d(maxX.toDouble(), maxY.toDouble(), maxZ.toDouble())
    }

    private fun calcVectorMean(vecs : ArrayDeque<Vector3d>) : Vector3d{
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        val qty = vecs.size
        vecs.forEach {
            sumX += it.x
            sumY += it.y
            sumZ += it.z
        }
        return Vector3d(sumX/qty, sumY/qty, sumZ/qty)
    }

}