package com.example.sensorstream.model

import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.vecmath.Quat4d
import javax.vecmath.Quat4f
import javax.vecmath.Vector3d
import kotlin.math.abs

operator fun Vector3d.times(other : Double) : Vector3d {
    var copy = Vector3d(this.x, this.y, this.z)
    copy.scale(other)
    return copy
}
operator fun Vector3d.plus(other: Vector3d) : Vector3d {
    var copy = Vector3d(this.x, this.y, this.z)
    copy.add(other)
    return copy
}

operator fun Vector3d.get(index : Int) : Double{
    return when(index){
        0 -> x
        1 -> y
        2 -> z
        else -> throw IndexOutOfBoundsException()
    }
}

operator fun Vector3d.set(index : Int, value : Double){
    when(index){
        0 -> x = value
        1 -> y = value
        2 -> z = value
        else -> throw IndexOutOfBoundsException()
    }
}

enum class MotionDirection{
    POSITIVE, NEGATIVE, NONE
}
enum class MotionPhase{
    ACCEL, OVERSHOOT, STILL
}

data class CalibrationData(val maxNoise : Vector3d = Vector3d(), val minNoise : Vector3d = Vector3d(),
                           val meanNoise : Vector3d = Vector3d(), val rotationVec: Vector3d = Vector3d()
)

class PositionCalculator(private val sensorDataFlow : StateFlow<SensorsViewState>,
                         scope: CoroutineScope){

    private var _position = Vector3d()
    val position
        get() = _position
    private var _velocity = Vector3d()
    val velocity
        get() = _velocity
    private var _accelFiltered = Vector3d()
    val accelFiltered
        get() = _accelFiltered
    private var accel = Vector3d()
    private var velocityAdd = Vector3d()
    private var velocitySum = Vector3d()
    private val meanDepth = 2
    private val stationaryDetectionSensitivity = 100
    private val overshootFactor = 0.3
    private val calibrationWindow = 0.05
    private val calibrationDuration = Duration.ofSeconds(1)
    private var timestamp = 0L
    private var delta: Double = 0.0
    private var prevTimestamp = 0L
    private var prevAccel = Vector3d()
    private var prevVel = Vector3d()
    private val lastReadings = ArrayDeque<Vector3d>()
    private val stationaryReadingsCount = arrayOf(
        AtomicInteger(0), AtomicInteger(0), AtomicInteger(0)
    )
    private val motionDirections = arrayOf(MotionDirection.NONE, MotionDirection.NONE, MotionDirection.NONE)
    private val motionPhases = arrayOf(MotionPhase.STILL, MotionPhase.STILL, MotionPhase.STILL)
    private val overshootVelSum = Vector3d()
    private val accelVelSum = Vector3d()
    private val overshootTimestamp = arrayOf(
        AtomicLong(0), AtomicLong(0), AtomicLong(0)
    )
    private var noiseCalibrationResult = CalibrationData()
    private val calibrationFlow = MutableStateFlow(SensorsData())

    init{
        scope.launch {
            /*emit(it.sensorsData.copy(
                accel = Point3F(it.sensorsData.accel.x - it.sensorsData.accelerationVector.x,
                    it.sensorsData.accel.y - it.sensorsData.accelerationVector.y,
                    it.sensorsData.accel.z - it.sensorsData.accelerationVector.z)))*/
            val startTimeMillis = System.currentTimeMillis()
            val duration = Duration.ofSeconds(7)
            val dataFlow = sensorDataFlow.transform {
                emit(it.sensorsData)
            }.stateIn(this)
            val result = calcNoiseComponents(dataFlow) {
                System.currentTimeMillis() - startTimeMillis <= duration.toMillis() }
            println(result.meanNoise)
            calcPosition()
        }
    }

    suspend fun calcPosition() {
        sensorDataFlow.collect {
            if (it.sensorsData.timestamp != 0L && prevTimestamp != 0L
                && it.sensorsData.timestamp != prevTimestamp) {
                accel = Vector3d(it.sensorsData.accel.x.toDouble(),
                    it.sensorsData.accel.y.toDouble(), it.sensorsData.accel.z.toDouble())
                val rotationVec = Vector3d(it.sensorsData.rotationVector.x.toDouble(),
                    it.sensorsData.rotationVector.y.toDouble(), it.sensorsData.rotationVector.z.toDouble())
                accel = eliminateGravity(accel, rotationVec)
                println("accel: " + accel.toString())
                lastReadings.add(accel)
                if(lastReadings.size >= meanDepth) {
                    delta = (it.sensorsData.timestamp - prevTimestamp) / 1000000000.toDouble()
                    filterAccelData()
                    handleCalibration()
                    val calcResult = calculateData()
                    velocityAdd = calcResult.first
                    velocitySum = calcResult.second
                    detectMotionDirection()
                    handleMotion()
                    prevTimestamp = it.sensorsData.timestamp
                    lastReadings.removeLast()
                    prevAccel = _accelFiltered
                    prevVel = _velocity
                }
            } else if (it.sensorsData.timestamp != 0L)
                prevTimestamp = it.sensorsData.timestamp
        }
    }

    private fun eliminateGravity(accel : Vector3d, rotationVec : Vector3d) : Vector3d{
        val rotationQArr = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
        val calibratedRotationQArr = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
        SensorManager.getQuaternionFromVector(rotationQArr,
            floatArrayOf(rotationVec.x.toFloat(), rotationVec.y.toFloat(), rotationVec.z.toFloat()))
        SensorManager.getQuaternionFromVector(calibratedRotationQArr,
            floatArrayOf(noiseCalibrationResult.rotationVec.x.toFloat(),
                noiseCalibrationResult.rotationVec.y.toFloat(),
                noiseCalibrationResult.rotationVec.z.toFloat()))
        val rotationQ = Quat4d(rotationQArr.map{ it.toDouble() }.toDoubleArray())
        val calibratedRotationQ = Quat4d(calibratedRotationQArr.map{ it.toDouble() }.toDoubleArray())
        rotationQ.mulInverse(calibratedRotationQ)
        val rotationDiffQ = rotationQ
        rotationDiffQ.inverse()
        val accelQ = Quat4d(accel.x, accel.y, accel.z, 0.0)
        val rotationDiffQCopy = Quat4d(rotationDiffQ)
        rotationDiffQ.mul(accelQ)
        rotationDiffQ.mulInverse(rotationDiffQCopy)
        return Vector3d(rotationDiffQ.x, rotationDiffQ.y, rotationDiffQ.z)
    }

    private fun calculateData()
            : Pair<Vector3d, Vector3d> {
        _position += integrateVec(prevVel, _velocity, delta)
        val velocityAdd = integrateVec(prevAccel, _accelFiltered, delta)
        val velocitySum = _velocity + velocityAdd
        return Pair(velocityAdd, velocitySum)
    }

    private fun handleCalibration()
            : CalibrationData {
        CoroutineScope(Dispatchers.Main).launch {
            val result =
                calibrate()
            if (result.meanNoise.x != 0.0)
                noiseCalibrationResult = result
        }
        return noiseCalibrationResult
    }

    private fun filterAccelData() {
        _accelFiltered = calcVectorMean(lastReadings)
        _accelFiltered = eliminateNoiseWindow(_accelFiltered)
        updateCalibrationFlow()
        detectStationary()
    }

    private fun eliminateNoiseWindow(accel : Vector3d) : Vector3d{
        val resultVec = Vector3d()
        for(i in 0..2){
            if((accel[i]) < noiseCalibrationResult.maxNoise[i] &&
                accel[i] > noiseCalibrationResult.minNoise[i]) {
                resultVec[i] = 0.0
            }
            else{
                if(accel[i] > noiseCalibrationResult.maxNoise[i]) {
                    resultVec[i] = accel[i] + (-1.0 *
                            (noiseCalibrationResult.maxNoise[i] - noiseCalibrationResult.meanNoise[i]))
                }
                if(accel[i] < noiseCalibrationResult.minNoise[i]) {
                    resultVec[i] = accel[i] +
                            (noiseCalibrationResult.meanNoise[i] - noiseCalibrationResult.minNoise[i])
                }
            }
        }
        return resultVec
    }

    private fun updateCalibrationFlow(){
        var count = 0
        for(i in 0..2){
            if(abs(_accelFiltered[i]) <= calibrationWindow) {
                count++
            }
        }
        if(count == 3){
            calibrationFlow.update{ it.copy(accel = Point3F(accel.x.toFloat(),
                accel.y.toFloat(), accel.z.toFloat())) }
        }
    }

    private suspend fun calibrate()
            : CalibrationData {
        var calibrationResult = CalibrationData()
        val calibrationStartThreshold = stationaryDetectionSensitivity * 3
        if(stationaryReadingsCount[0].get() >= calibrationStartThreshold
            && stationaryReadingsCount[1].get() >= calibrationStartThreshold
            && stationaryReadingsCount[2].get() >= calibrationStartThreshold)
            if(stationaryReadingsCount[0].get() == calibrationStartThreshold
                || stationaryReadingsCount[1].get() == calibrationStartThreshold
                || stationaryReadingsCount[2].get() == calibrationStartThreshold) {
                val startTimeMillis = System.currentTimeMillis()
                calibrationResult = calcNoiseComponents(calibrationFlow) {
                    System.currentTimeMillis() - startTimeMillis <= calibrationDuration.toMillis()
                }
            }
        return calibrationResult
    }

    private fun detectStationary(){
        for(i in 0..2){
            if(_accelFiltered[i] == 0.0) {
                stationaryReadingsCount[i].addAndGet(1)
            }
            else{
                stationaryReadingsCount[i].set(0)
            }
            if(stationaryReadingsCount[i].get() >= stationaryDetectionSensitivity){
                _velocity[i] = 0.0
                motionDirections[i] = MotionDirection.NONE
                motionPhases[i] = MotionPhase.STILL
                accelVelSum[i] = 0.0
                overshootVelSum[i] = 0.0
            }
        }
    }

    private fun setNewAcceleration(axisIndex: Int, newMotionDirection : MotionDirection){
        motionDirections[axisIndex] = newMotionDirection
        motionPhases[axisIndex] = MotionPhase.ACCEL
        overshootVelSum[axisIndex] = 0.0
        accelVelSum[axisIndex] = 0.0
    }

    private fun detectMotionDirection(){
        for(i in 0..2){
            if(motionDirections[i] == MotionDirection.NONE && motionPhases[i] == MotionPhase.STILL){
                if(velocityAdd[i] > 0.0) {
                    setNewAcceleration(i, MotionDirection.POSITIVE)
                }
                if(velocityAdd[i] < 0.0) {
                    setNewAcceleration(i, MotionDirection.NEGATIVE)
                }
            }
        }
    }

    private fun handleMotion(){
        for(i in 0..2){
            when(motionPhases[i]){
                MotionPhase.ACCEL -> {
                    handleAcceleration(i)
                    return
                }
                MotionPhase.OVERSHOOT -> {
                    handleOvershoot(i)
                    return
                }
                else -> {}
            }
        }
    }

    private fun handleOvershoot(axisIndex: Int) {
        when (motionDirections[axisIndex]) {
            MotionDirection.POSITIVE -> {
                handleOvershootEnding(axisIndex)
                if(motionPhases[axisIndex] != MotionPhase.STILL)
                    detectDirectionChange(axisIndex, MotionDirection.NEGATIVE)
                return
            }
            MotionDirection.NEGATIVE -> {
                handleOvershootEnding(axisIndex)
                if(motionPhases[axisIndex] != MotionPhase.STILL)
                    detectDirectionChange(axisIndex, MotionDirection.POSITIVE)
                return
            }
            else -> {}
        }
    }

    private fun handleAcceleration(axisIndex: Int){
        when (motionDirections[axisIndex]) {
            MotionDirection.POSITIVE -> {
                detectOvershoot(axisIndex, MotionDirection.NEGATIVE)
                return
            }
            MotionDirection.NEGATIVE -> {
                detectOvershoot(axisIndex, MotionDirection.POSITIVE)
                return
            }
            else -> {}
        }
    }

    private fun handleOvershootEnding(axisIndex : Int) {
        var velocityCondition = false
        if((motionDirections[axisIndex] == MotionDirection.POSITIVE && velocityAdd[axisIndex] >= 0.0)
            || (motionDirections[axisIndex] == MotionDirection.NEGATIVE && velocityAdd[axisIndex] <= 0.0))
            velocityCondition = true
        if (velocityCondition) {
            accelVelSum[axisIndex] = 0.0
            overshootVelSum[axisIndex] = 0.0
            motionPhases[axisIndex] = MotionPhase.STILL
            motionDirections[axisIndex] = MotionDirection.NONE
        } else
            _velocity[axisIndex] = 0.0
    }

    private fun detectOvershoot(axisIndex : Int, newMotionDirection : MotionDirection) {
        var velocitySumCondition = false
        if((velocitySum[axisIndex] < 0 && newMotionDirection == MotionDirection.NEGATIVE)
            || (velocitySum[axisIndex] > 0 && newMotionDirection == MotionDirection.POSITIVE))
            velocitySumCondition = true
        if (velocitySumCondition) {
            motionPhases[axisIndex] = MotionPhase.OVERSHOOT
            overshootVelSum[axisIndex] += velocitySum[axisIndex]
            overshootTimestamp[axisIndex].set(timestamp)
        } else {
            _velocity[axisIndex] = velocitySum[axisIndex]
            if((newMotionDirection == MotionDirection.POSITIVE && velocityAdd[axisIndex] < 0)
                || (newMotionDirection == MotionDirection.NEGATIVE && velocityAdd[axisIndex] > 0)) {
                accelVelSum[axisIndex] += velocityAdd[axisIndex]
            }
        }
    }

    private fun detectDirectionChange(axisIndex : Int, newMotionDirection : MotionDirection) {
        if (abs(overshootVelSum[axisIndex]) >= abs(accelVelSum[axisIndex] * overshootFactor)) {
            val overshootDelta =
                (timestamp - overshootTimestamp[axisIndex].get()) / 1000000000.toDouble()
            _velocity[axisIndex] += overshootVelSum[axisIndex]
            _position[axisIndex] += integrateVec(
                Vector3d(), overshootVelSum, overshootDelta
            )[axisIndex]
            motionPhases[axisIndex] = MotionPhase.ACCEL
            motionDirections[axisIndex] = newMotionDirection
            accelVelSum[axisIndex] = overshootVelSum[axisIndex]
            overshootVelSum[axisIndex] = 0.0
        } else{
            overshootVelSum[axisIndex] += velocitySum[axisIndex]
        }
    }

    private suspend fun calcNoiseComponents(dataFlow : StateFlow<SensorsData>,
                                            whileCondition : () -> Boolean) : CalibrationData {
        val maxVec = Vector3d(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
        val minVec = Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
        val meanVec : Vector3d
        val meanRotation : Vector3d
        val vectors = ArrayDeque<Vector3d>()
        val rotations = ArrayDeque<Vector3d>()
        if(whileCondition())
            dataFlow.takeWhile{ whileCondition() }.collect {
                for(i in 0..2){
                    if(it.accel[i] > maxVec[i])
                        maxVec[i] = it.accel[i].toDouble()
                    if(it.accel[i] < minVec[i])
                        minVec[i] = it.accel[i].toDouble()
                }
                vectors.add(Vector3d(it.accel.x.toDouble(), it.accel.y.toDouble(),
                    it.accel.z.toDouble()))
                vectors.add(Vector3d(it.rotationVector.x.toDouble(), it.rotationVector.y.toDouble(),
                    it.rotationVector.z.toDouble()))
            }
        meanVec = calcVectorMean(vectors)
        meanRotation = calcVectorMean(rotations)
        return CalibrationData(maxVec, minVec, meanVec, meanRotation)
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
        for(i in 0..2)
            vel[i] = ((prevVec[i] + vec[i]) * delta) / 2.0
        return vel
    }

}