package com.example.sensorstream.model

import android.hardware.SensorManager
import androidx.core.math.MathUtils.clamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
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

enum class MotionDirection{
    POSITIVE, NEGATIVE, STILL
}
enum class MotionPhase{
    ACCEL, OVERSHOOT, STILL
}

class SensorDataManipulator(private val sensorDataFlow : StateFlow<SensorsViewState>,
                            scope: CoroutineScope) {
    private var rotationQArr = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
    private var referenceQArr = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
    private var position = Vector3d()
    private var speed = Vector3d()
    private var accelFilter = Vector3d()
    private var meanDepth = 2
    private var stationaryDetectionSensitivity = 15
    private var overshootFactor = 0.5

    init{
        scope.launch {
            val startTimeMillis = System.currentTimeMillis()
            val duration = Duration.ofSeconds(7)
            val dataFlow = sensorDataFlow.transform { emit(it.sensorsData) }.stateIn(this)
            val noise = calcGreatestDriftComponents(dataFlow) {
                System.currentTimeMillis() - startTimeMillis <= duration.toMillis() }
            println(noise.third)
            calcPosition(noise.first, noise.second, noise.third)
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
        " " + it.accel.z + " ; " + accelFilter.x + " " + accelFilter.y + " " + + accelFilter.z +
        " ; " + speed.x + " " + speed.y + " " + speed.z + " ; " + position.x + " " +
        position.y + " " + position.z
    }
    private suspend fun calcPosition(maxNoiseParam : Vector3d,
                                     minNoiseParam : Vector3d, offsetParam : Vector3d) {
        var delta: Double
        var prevTimestamp = 0L
        var prevAccel = Vector3d()
        var prevVel = Vector3d()
        var lastReadings = ArrayDeque<Vector3d>()
        var stationaryReadings = 0
        var motionDirection  = MotionDirection.STILL
        var motionPhase = MotionPhase.STILL
        var maxNoise = maxNoiseParam
        var minNoise = minNoiseParam
        var offset = offsetParam
        var overshootSumVel = Vector3d()
        var accelSumVel = Vector3d()
        var overshootTimestamp : Long
        var noiseCalibration : Triple<Vector3d, Vector3d, Vector3d>? = null
        var dataFlow = MutableStateFlow(SensorsData())
        sensorDataFlow.collect {
            if (it.sensorsData.timestamp != 0L && prevTimestamp != 0L
            && it.sensorsData.timestamp != prevTimestamp) {

                var accel = Vector3d(it.sensorsData.accel.x.toDouble(),
                it.sensorsData.accel.y.toDouble(), it.sensorsData.accel.z.toDouble())
                lastReadings.add(accel)
                if(lastReadings.size >= meanDepth) {
                    accelFilter = calcVectorMean(lastReadings)
                    if((accelFilter.x) < maxNoise.x && accelFilter.x > minNoise.x) {
                        dataFlow.update { it.copy(accel = Point3F(accelFilter.x.toFloat(),
                            accelFilter.y.toFloat(), accelFilter.z.toFloat())) }
                        accelFilter.x = 0.0
                    }
                    else{
                        val accelFilterCopy = Vector3d(accelFilter.x, accelFilter.y, accelFilter.z)
                        if(accelFilter.x > maxNoise.x) {
                            accelFilter.x += -1.0 * (maxNoise.x - offset.x)
                            if(abs(accelFilter.x) < 0.05){
                                dataFlow.update { sensorsData -> sensorsData.copy(
                                accel = Point3F(accelFilterCopy.x.toFloat(),
                                accelFilterCopy.y.toFloat(), accelFilterCopy.z.toFloat())) }
                            }
                        }
                        if(accelFilter.x < minNoise.x) {
                            accelFilter.x += (offset.x - minNoise.x)
                            if(abs(accelFilter.x) < 0.05){
                                dataFlow.update { sensorsData -> sensorsData.copy(
                                accel = Point3F(accelFilterCopy.x.toFloat(),
                                accelFilterCopy.y.toFloat(), accelFilterCopy.z.toFloat())) }
                            }
                        }
                    }
                    println(accelFilter)
                    if(accelFilter.x == 0.0) {
                        stationaryReadings++
                        if (stationaryReadings == stationaryDetectionSensitivity) {
                            CoroutineScope(Dispatchers.Default).launch {
                                val startTimeMillis = System.currentTimeMillis()
                                val duration = Duration.ofSeconds(1)
                                println("START CALIBRATING")
                                noiseCalibration = calcGreatestDriftComponents(dataFlow){
                                System.currentTimeMillis() - startTimeMillis <= duration.toMillis() }
                            }
                            println("CALIBRATION DONE")
                        }
                        if(stationaryReadings >= stationaryDetectionSensitivity
                            && noiseCalibration != null) {
                            maxNoise = noiseCalibration!!.first
                            minNoise = noiseCalibration!!.second
                            offset = noiseCalibration!!.third
                        }
                    }
                    else stationaryReadings = 0
                    delta = (it.sensorsData.timestamp - prevTimestamp) / 1000000000.toDouble()
                    //println("accel: " + accelFilter.x)
                    if(stationaryReadings >= stationaryDetectionSensitivity){
                        speed.x = 0.0
                        motionDirection = MotionDirection.STILL
                        motionPhase = MotionPhase.STILL
                    }
                    //println("velocity: " + speed.x)
                    position += integrateVec(prevVel, speed, delta)
                    val speedAdd = integrateVec(prevAccel, accelFilter, delta)
                    val speedXSum = speed.x + speedAdd.x
                    if(motionDirection == MotionDirection.STILL && motionPhase == MotionPhase.STILL){
                        if(speedXSum > 0.0){
                            speed += speedAdd
                            motionDirection = MotionDirection.POSITIVE
                            //println("ACCEL")
                            motionPhase = MotionPhase.ACCEL
                            overshootSumVel = Vector3d()
                            accelSumVel = Vector3d()
                        }
                        if(speedXSum < 0.0){
                            speed += speedAdd
                            motionDirection = MotionDirection.NEGATIVE
                            //println("ACCEL")
                            motionPhase = MotionPhase.ACCEL
                            overshootSumVel = Vector3d()
                            accelSumVel = Vector3d()
                        }
                    }
                    when(motionPhase){
                        MotionPhase.ACCEL -> {
                            when(motionDirection){
                                MotionDirection.POSITIVE -> {
                                    if(speedXSum < 0){
                                        motionPhase = MotionPhase.OVERSHOOT
                                        overshootSumVel.x += speedXSum
                                        overshootTimestamp = it.sensorsData.timestamp
                                        if(abs(overshootSumVel.x) >= abs(accelSumVel.x * overshootFactor)){
                                            //println("REVERSE")
                                            val overshootDelta =
                                                (it.sensorsData.timestamp - overshootTimestamp) /
                                                        1000000000.toDouble()
                                            speed += overshootSumVel
                                            position += integrateVec(
                                                Vector3d(), overshootSumVel, overshootDelta)
                                            motionPhase = MotionPhase.ACCEL
                                            motionDirection = MotionDirection.NEGATIVE
                                            overshootSumVel = Vector3d()
                                            accelSumVel = overshootSumVel
                                        }
                                        //println("OVERSHOOT")
                                    }
                                    else{
                                        speed.x = speedXSum
                                        accelSumVel.x = speedXSum
                                    }
                                }
                                MotionDirection.NEGATIVE -> {
                                    if(speedXSum > 0){
                                        motionPhase = MotionPhase.OVERSHOOT
                                        overshootSumVel.x += speedXSum
                                        overshootTimestamp = it.sensorsData.timestamp
                                        if(abs(overshootSumVel.x) >= abs(accelSumVel.x * overshootFactor)){
                                            //println("REVERSE")
                                            val overshootDelta =
                                                (it.sensorsData.timestamp - overshootTimestamp) /
                                                        1000000000.toDouble()
                                            position += integrateVec(
                                                Vector3d(), overshootSumVel, overshootDelta)
                                            speed += overshootSumVel
                                            motionPhase = MotionPhase.ACCEL
                                            motionDirection = MotionDirection.POSITIVE
                                            overshootSumVel = Vector3d()
                                            accelSumVel = overshootSumVel
                                        }
                                        //println("OVERSHOOT")
                                    }
                                    else{
                                        speed.x = speedXSum
                                        accelSumVel.x = speedXSum
                                    }
                                }
                                else -> {}
                            }
                        }
                        MotionPhase.OVERSHOOT -> {
                            when(motionDirection){
                                MotionDirection.POSITIVE -> {
                                    if(speedAdd.x >= 0.0 || speed.x == 0.0){
                                        //println("RESET")
                                        accelSumVel = Vector3d()
                                        speed.x = speedXSum
                                        motionPhase = MotionPhase.STILL
                                        motionDirection = MotionDirection.STILL
                                    }
                                    else speed.x = 0.0
                                }
                                MotionDirection.NEGATIVE -> {
                                    if(speedAdd.x <= 0.0 || speed.x == 0.0){
                                        //println("RESET")
                                        accelSumVel = Vector3d()
                                        speed.x = speedXSum
                                        motionPhase = MotionPhase.STILL
                                        motionDirection = MotionDirection.STILL
                                    }
                                    else speed.x = 0.0
                                }
                                else -> {}
                            }
                        }
                        else -> {}
                    }
                    prevTimestamp = it.sensorsData.timestamp
                    lastReadings.removeLast()
                    //println("speed" + speed)
                    prevAccel = accelFilter
                    prevVel = speed
                }
            } else if (it.sensorsData.timestamp != 0L)
                prevTimestamp = it.sensorsData.timestamp
        }
    }


    private suspend fun calcGreatestDriftComponents(dataFlow : StateFlow<SensorsData>,
                                                    whileCondition : () -> Boolean) :
            Triple<Vector3d, Vector3d, Vector3d>{
        var maxVec = Vector3d(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
        var minVec = Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
        var mean : Vector3d
        var vectors = ArrayDeque<Vector3d>()
        dataFlow.takeWhile{ whileCondition() }.collect {
            if(it.accel.x > maxVec.x)
                maxVec.x = it.accel.x.toDouble()
            if(it.accel.x < minVec.x)
                minVec.x = it.accel.x.toDouble()
            if(it.accel.y > maxVec.y)
                maxVec.y = it.accel.y.toDouble()
            if(it.accel.y < minVec.y)
                minVec.y = it.accel.y.toDouble()
            if(it.accel.z > maxVec.z)
                maxVec.z = it.accel.z.toDouble()
            if(it.accel.y < minVec.z)
                minVec.z = it.accel.z.toDouble()
            vectors.add(Vector3d(it.accel.x.toDouble(), it.accel.y.toDouble(),
                it.accel.z.toDouble()))
        }
        mean = calcVectorMean(vectors)
        return Triple(maxVec, minVec, mean)
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

    private fun integrateVec(prevVec : Vector3d, vec : Vector3d, delta : Double) : Vector3d{
        val vel = Vector3d()
        vel.x = ((prevVec.x + vec.x) * delta) / 2.0
        return vel
    }

}