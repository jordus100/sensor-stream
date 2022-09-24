package com.example.sensorstream.model

import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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

class SensorDataManipulator(private val sensorDataFlow : StateFlow<SensorsViewState>,
                            scope: CoroutineScope) {

    private var position = Vector3d()
    private var velocity = Vector3d()
    private var accelFiltered = Vector3d()
    private val meanDepth = 2
    private val stationaryDetectionSensitivity = 100
    private val overshootFactor = 0.1
    private val calibrationWindow = 0.05
    private val calibrationDuration = Duration.ofSeconds(1)
    private val motionVelThreshold = 0.05

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
            val noise = calcGreatestDriftComponents(dataFlow) {
                System.currentTimeMillis() - startTimeMillis <= duration.toMillis() }
            println(noise.third)
            calcPosition(noise.first, noise.second, noise.third)
        }
    }

    val formatSensorData : (SensorsData) -> String = {
        var rotationQArr = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
        var referenceQArr = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
        SensorManager.getQuaternionFromVector(rotationQArr,
        floatArrayOf(it.rotationVector.x, it.rotationVector.y, it.rotationVector.z))
        SensorManager.getQuaternionFromVector(referenceQArr,
            floatArrayOf(it.referencePoint.x, it.referencePoint.y , it.referencePoint.z))

        "" + rotationQArr[1] + " " + rotationQArr[2] + " " + rotationQArr[3] + " " +
        rotationQArr[0] + " ; " + referenceQArr[1] + " " + referenceQArr[2] + " " +
        referenceQArr[3] + " " + referenceQArr[0] + " ; " + it.accel.x + " " + it.accel.y +
        " " + it.accel.z + " ; " + accelFiltered.x + " " + accelFiltered.y + " " + + accelFiltered.z +
        " ; " + velocity.x + " " + velocity.y + " " + velocity.z + " ; " + position.x + " " +
        position.y + " " + position.z
    }

    private suspend fun calcPosition(maxNoiseParam : Vector3d,
                                     minNoiseParam : Vector3d, meanNoiseParam : Vector3d) {
        var delta: Double
        var prevTimestamp = 0L
        var prevAccel = Vector3d()
        var prevVel = Vector3d()
        val lastReadings = ArrayDeque<Vector3d>()
        val stationaryReadingsCount = arrayOf(
            AtomicInteger(0), AtomicInteger(0), AtomicInteger(0))
        val motionDirections = arrayOf(MotionDirection.NONE, MotionDirection.NONE, MotionDirection.NONE)
        val motionPhases = arrayOf(MotionPhase.STILL, MotionPhase.STILL, MotionPhase.STILL)
        var maxNoise = maxNoiseParam
        var minNoise = minNoiseParam
        var meanNoise = meanNoiseParam
        val overshootVelSum = Vector3d()
        val accelVelSum = Vector3d()
        val overshootTimestamp = arrayOf(
            AtomicLong(0), AtomicLong(0), AtomicLong(0))
        var noiseCalibrationResult = Triple(maxNoise, minNoise, meanNoise)
        val calibrationFlow = MutableStateFlow(SensorsData())

        sensorDataFlow.collect {
            if (it.sensorsData.timestamp != 0L && prevTimestamp != 0L
            && it.sensorsData.timestamp != prevTimestamp) {
                var accel = Vector3d(it.sensorsData.accel.x.toDouble(),
                it.sensorsData.accel.y.toDouble(), it.sensorsData.accel.z.toDouble())
                val gravity = Vector3d(it.sensorsData.accelerationVector.x.toDouble(),
                    it.sensorsData.accelerationVector.y.toDouble(),
                    it.sensorsData.accelerationVector.z.toDouble())
                //accel += (gravity * -1.0)
                lastReadings.add(accel)
                if(lastReadings.size >= meanDepth) {
                    delta = (it.sensorsData.timestamp - prevTimestamp) / 1000000000.toDouble()
                    filterAccelData(lastReadings, accel, accelVelSum, overshootVelSum,
                                    maxNoise, minNoise, meanNoise, calibrationFlow,
                                    stationaryReadingsCount, motionDirections, motionPhases)
                    noiseCalibrationResult = handleCalibration(calibrationFlow,
                        stationaryReadingsCount, noiseCalibrationResult)
                    maxNoise = noiseCalibrationResult.first
                    minNoise = noiseCalibrationResult.second
                    meanNoise = noiseCalibrationResult.third
                    val (velocityAdd, velocitySum) = calculateData(delta, prevVel, prevAccel)
                    detectMotionDirection(motionDirections, motionPhases, velocitySum, velocityAdd,
                                          overshootVelSum, accelVelSum)
                    handleMotion(motionDirections, motionPhases, velocity, velocitySum, velocityAdd,
                                 position, overshootVelSum, accelVelSum, it.sensorsData.timestamp,
                                 overshootTimestamp)
                    prevTimestamp = it.sensorsData.timestamp
                    lastReadings.removeLast()
                    prevAccel = accelFiltered
                    prevVel = velocity
                    println("velocityAdd: " + velocityAdd[0])
                    println("velocitySum: " + velocitySum[0])
                    println("accelVelSum: " + accelVelSum[0])
                    println("overshootVelSum: " + overshootVelSum[0])
                    if(motionDirections[0] == MotionDirection.POSITIVE)
                        println("positive")
                    else if(motionDirections[0] == MotionDirection.NEGATIVE)
                        println("negative")
                    else println("none")
                    if(motionPhases[0] == MotionPhase.ACCEL)
                        println("accel")
                    else if(motionPhases[0] == MotionPhase.OVERSHOOT)
                        println("overshoot")
                    else println("still")
                }
            } else if (it.sensorsData.timestamp != 0L)
                prevTimestamp = it.sensorsData.timestamp
        }
    }

    private fun calculateData(delta: Double, prevVel: Vector3d, prevAccel: Vector3d)
                                : Pair<Vector3d, Vector3d> {
        position += integrateVec(prevVel, velocity, delta)
        val velocityAdd = integrateVec(prevAccel, accelFiltered, delta)
        val velocitySum = velocity + velocityAdd
        return Pair(velocityAdd, velocitySum)
    }

    private fun handleCalibration(calibrationFlow: MutableStateFlow<SensorsData>,
                                    stationaryReadingsCount: Array<AtomicInteger>,
                                    noiseCalibrationResult: Triple<Vector3d, Vector3d, Vector3d>)
                                    : Triple<Vector3d, Vector3d, Vector3d> {
        var noiseCalibrationResult1 = noiseCalibrationResult
        CoroutineScope(Dispatchers.Main).launch {
            val result =
                calibrate(calibrationFlow, calibrationDuration, stationaryReadingsCount)
            if (result.third.x != 0.0)
                noiseCalibrationResult1 = result
        }
        return noiseCalibrationResult1
    }

    private fun filterAccelData(lastReadings: ArrayDeque<Vector3d>, accel: Vector3d, accelVelSum: Vector3d,
                                overshootVelSum: Vector3d, maxNoise: Vector3d, minNoise: Vector3d,
                                meanNoise: Vector3d, calibrationFlow: MutableStateFlow<SensorsData>,
                                stationaryReadingsCount: Array<AtomicInteger>,
                                motionDirections: Array<MotionDirection>,
                                motionPhases: Array<MotionPhase>) {
        accelFiltered = calcVectorMean(lastReadings)
        accelFiltered = eliminateNoiseWindow(accel, maxNoise, minNoise, meanNoise)
        updateCalibrationFlow(calibrationFlow, accel, accelFiltered)
        detectStationary(
            stationaryReadingsCount, accelFiltered, velocity, accelVelSum, overshootVelSum,
            motionDirections, motionPhases
        )
    }

    private fun eliminateNoiseWindow(accel : Vector3d, maxNoise : Vector3d,
                                     minNoise : Vector3d, meanNoise : Vector3d) : Vector3d{
        val resultVec = Vector3d()
        for(i in 0..2){
            if((accel[i]) < maxNoise[i] && accel[i] > minNoise[i]) {
                resultVec[i] = 0.0
            }
            else{
                if(accel[i] > maxNoise[i]) {
                    resultVec[i] = accel[i] + (-1.0 * (maxNoise[i] - meanNoise[i]))
                }
                if(accel[i] < minNoise[i]) {
                    resultVec[i] = accel[i] + (meanNoise[i] - minNoise[i])
                }
            }
        }
        return resultVec
    }

    private fun updateCalibrationFlow(calibrationFlow : MutableStateFlow<SensorsData>,
                                      accelRaw : Vector3d, accelDenoised : Vector3d){
        var count = 0
        for(i in 0..2){
            if(abs(accelDenoised[i]) <= calibrationWindow) {
                count++
            }
        }
        if(count == 3){
            calibrationFlow.update{ it.copy(accel = Point3F(accelRaw.x.toFloat(),
                                    accelRaw.y.toFloat(), accelRaw.z.toFloat())) }
            }
    }

    private suspend fun calibrate(calibrationFlow: StateFlow<SensorsData>, duration: Duration,
                          stationaryReadingsCount: Array<AtomicInteger>)
                          : Triple<Vector3d, Vector3d, Vector3d> {
        var calibrationResult = Triple(Vector3d(), Vector3d(), Vector3d())
        val calibrationStartThreshold = stationaryDetectionSensitivity * 3
        if(stationaryReadingsCount[0].get() >= calibrationStartThreshold
        && stationaryReadingsCount[1].get() >= calibrationStartThreshold
        && stationaryReadingsCount[2].get() >= calibrationStartThreshold)
            if(stationaryReadingsCount[0].get() == calibrationStartThreshold
            || stationaryReadingsCount[1].get() == calibrationStartThreshold
            || stationaryReadingsCount[2].get() == calibrationStartThreshold) {
                val startTimeMillis = System.currentTimeMillis()
                calibrationResult = calcGreatestDriftComponents(calibrationFlow) {
                    System.currentTimeMillis() - startTimeMillis <= duration.toMillis()
                }
            }
        return calibrationResult
    }

    private fun detectStationary(stationaryReadingsCount : Array<AtomicInteger>,
                                 accelFiltered : Vector3d, velocity : Vector3d, accelVelSum: Vector3d,
                                 overshootVelSum: Vector3d, motionDirection: Array<MotionDirection>,
                                 motionPhase: Array<MotionPhase>){
        for(i in 0..2){
            if(accelFiltered[i] == 0.0) {
                stationaryReadingsCount[i].addAndGet(1)
            }
            else{
                stationaryReadingsCount[i].set(0)
            }
            if(stationaryReadingsCount[i].get() >= stationaryDetectionSensitivity){
                velocity[i] = 0.0
                motionDirection[i] = MotionDirection.NONE
                motionPhase[i] = MotionPhase.STILL
                accelVelSum[i] = 0.0
                overshootVelSum[i] = 0.0
                if(i == 0)
                    println("SPD INTV")
            }
        }
    }

    private fun setNewAcceleration(motionDirectionArray : Array<MotionDirection>,
                                   newMotionDirection : MotionDirection, axisIndex : Int,
                                   motionPhaseArray : Array<MotionPhase>, overshootVelSum : Vector3d,
                                   accelVelSum : Vector3d){
        motionDirectionArray[axisIndex] = newMotionDirection
        motionPhaseArray[axisIndex] = MotionPhase.ACCEL
        overshootVelSum[axisIndex] = 0.0
        accelVelSum[axisIndex] = 0.0
    }

    private fun detectMotionDirection(motionDirections: Array<MotionDirection>,
                                      motionPhases: Array<MotionPhase>, velocitySum : Vector3d,
                                      velocityAdd : Vector3d, overshootVelSum: Vector3d,
                                      accelVelSum: Vector3d){
        for(i in 0..2){
            if(motionDirections[i] == MotionDirection.NONE && motionPhases[i] == MotionPhase.STILL){
                if(velocityAdd[i] > 0.0) {
                    setNewAcceleration(motionDirections, MotionDirection.POSITIVE, i, motionPhases,
                                       overshootVelSum, accelVelSum)
                }
                if(velocityAdd[i] < 0.0) {
                    setNewAcceleration(motionDirections, MotionDirection.NEGATIVE, i, motionPhases,
                                       overshootVelSum, accelVelSum)
                }
            }
        }
    }

    private fun handleMotion(motionDirections: Array<MotionDirection>,
                             motionPhases: Array<MotionPhase>, velocity : Vector3d,
                             velocitySum: Vector3d, velocityAdd : Vector3d, position : Vector3d,
                             overshootVelSum: Vector3d, accelVelSum: Vector3d, timestamp : Long,
                             overshootTimestamp : Array<AtomicLong>){
        for(i in 0..2){
            when(motionPhases[i]){
                MotionPhase.ACCEL -> {
                    handleAcceleration(motionDirections, i, velocitySum, velocityAdd, motionPhases,
                                       overshootVelSum, overshootTimestamp, timestamp, accelVelSum,
                                       velocity, position)
                    return
                }
                MotionPhase.OVERSHOOT -> {
                    handleOvershoot(motionDirections, i, velocityAdd, velocity, accelVelSum,
                                    velocitySum, motionPhases, timestamp, overshootTimestamp,
                                    overshootVelSum)
                    return
                }
                else -> {}
            }
        }
    }

    private fun handleOvershoot(motionDirections: Array<MotionDirection>, i: Int,
                                velocityAdd: Vector3d, velocity: Vector3d, accelVelSum: Vector3d,
                                velocitySum: Vector3d, motionPhases: Array<MotionPhase>,
                                timestamp : Long, overshootTimestamp : Array<AtomicLong>,
                                overshootVelSum: Vector3d) {
        when (motionDirections[i]) {
            MotionDirection.POSITIVE -> {
                handleOvershootEnding(
                    velocityAdd, i, velocity, accelVelSum, overshootVelSum,
                    motionPhases, motionDirections, MotionDirection.POSITIVE
                )
                if(motionPhases[i] != MotionPhase.STILL)
                    detectDirectionChange(overshootVelSum, i, accelVelSum, timestamp,
                        overshootTimestamp, velocity, velocitySum, position, motionPhases,
                        motionDirections, MotionDirection.NEGATIVE)
                return
            }
            MotionDirection.NEGATIVE -> {
                handleOvershootEnding(
                    velocityAdd, i, velocity, accelVelSum, overshootVelSum,
                    motionPhases, motionDirections, MotionDirection.NEGATIVE
                )
                if(motionPhases[i] != MotionPhase.STILL)
                    detectDirectionChange(overshootVelSum, i, accelVelSum, timestamp,
                        overshootTimestamp, velocity, velocitySum, position, motionPhases,
                        motionDirections, MotionDirection.POSITIVE)
                return
            }
            else -> {}
        }
    }

    private fun handleAcceleration(motionDirections: Array<MotionDirection>, i: Int,
                                   velocitySum: Vector3d, velocityAdd :Vector3d,
                                   motionPhases: Array<MotionPhase>,
                                    overshootVelSum: Vector3d, overshootTimestamp: Array<AtomicLong>,
                                    timestamp: Long, accelVelSum: Vector3d, velocity: Vector3d,
                                    position: Vector3d){
        when (motionDirections[i]) {
            MotionDirection.POSITIVE -> {
                detectOvershoot(velocitySum, i, motionPhases, overshootVelSum, overshootTimestamp,
                                timestamp, accelVelSum, velocity, velocityAdd, position, motionDirections,
                                MotionDirection.NEGATIVE)
                return
            }
            MotionDirection.NEGATIVE -> {
                detectOvershoot(velocitySum, i, motionPhases, overshootVelSum, overshootTimestamp,
                                timestamp, accelVelSum, velocity, velocityAdd,position, motionDirections,
                                MotionDirection.POSITIVE)
                return
            }
            else -> {}
        }
    }

    private fun handleOvershootEnding(velocityAdd: Vector3d, axisIndex: Int, velocity: Vector3d,
                                      accelVelSum: Vector3d, overshootVelSum: Vector3d,
                                      motionPhases: Array<MotionPhase>,
                                      motionDirections: Array<MotionDirection>,
                                      motionDirection: MotionDirection) {
        var velocityCondition = false
        if((motionDirection == MotionDirection.POSITIVE && velocityAdd[axisIndex] >= 0.0)
        || (motionDirection == MotionDirection.NEGATIVE && velocityAdd[axisIndex] <= 0.0))
           velocityCondition = true
        if (velocityCondition) {
            if(axisIndex == 0)
                println("overshoot ending")
            accelVelSum[axisIndex] = 0.0
            overshootVelSum[axisIndex] = 0.0
            //velocity[axisIndex] = velocitySum[axisIndex]
            motionPhases[axisIndex] = MotionPhase.STILL
            motionDirections[axisIndex] = MotionDirection.NONE
        } else
            velocity[axisIndex] = 0.0
    }

    private fun detectOvershoot(velocitySum: Vector3d, axisIndex: Int, motionPhases: Array<MotionPhase>,
                                overshootVelSum: Vector3d, overshootTimestamp: Array<AtomicLong>,
                                timestamp: Long, accelVelSum: Vector3d, velocity: Vector3d,
                                velocityAdd: Vector3d, position: Vector3d,
                                motionDirections: Array<MotionDirection>,
                                newMotionDirection: MotionDirection) {
        var velocitySumCondition = false
        if((velocitySum[axisIndex] < 0 && newMotionDirection == MotionDirection.NEGATIVE)
        || (velocitySum[axisIndex] > 0 && newMotionDirection == MotionDirection.POSITIVE))
            velocitySumCondition = true
        if (velocitySumCondition) {
            motionPhases[axisIndex] = MotionPhase.OVERSHOOT
            if(axisIndex == 0)
                println("OVERSHOOT DETECTED")
            overshootVelSum[axisIndex] += velocitySum[axisIndex]
            overshootTimestamp[axisIndex].set(timestamp)
        } else {
            velocity[axisIndex] = velocitySum[axisIndex]
            if((newMotionDirection == MotionDirection.POSITIVE && velocityAdd[axisIndex] < 0)
            || (newMotionDirection == MotionDirection.NEGATIVE && velocityAdd[axisIndex] > 0)) {
                accelVelSum[axisIndex] += velocityAdd[axisIndex]
                /*if (axisIndex == 0) {
                    println("velocity add: " + velocityAdd[axisIndex])
                    println("accelVelSum: " + accelVelSum[axisIndex])
                }*/
            }
        }
    }

    private fun detectDirectionChange(overshootVelSum: Vector3d, axisIndex: Int, accelVelSum: Vector3d,
                                        timestamp: Long, overshootTimestamp: Array<AtomicLong>,
                                        velocity: Vector3d, velocitySum: Vector3d, position: Vector3d,
                                        motionPhases: Array<MotionPhase>,
                                        motionDirections: Array<MotionDirection>,
                                        newMotionDirection: MotionDirection) {
        /*if(axisIndex == 0) {
            println("overshootVelSum: " + overshootVelSum[0])
            println("accelVelSum: " + accelVelSum[0])
        }*/
        if (abs(overshootVelSum[axisIndex]) >= abs(accelVelSum[axisIndex] * overshootFactor)) {
            if(axisIndex == 0)
                println("DIRECTION CHANGE")
            val overshootDelta =
                (timestamp - overshootTimestamp[axisIndex].get()) / 1000000000.toDouble()
            velocity[axisIndex] += overshootVelSum[axisIndex]
            position[axisIndex] += integrateVec(
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

    private suspend fun calcGreatestDriftComponents(dataFlow : StateFlow<SensorsData>,
                                                    whileCondition : () -> Boolean) :
            Triple<Vector3d, Vector3d, Vector3d>{
        val maxVec = Vector3d(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
        val minVec = Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
        val mean : Vector3d
        val vectors = ArrayDeque<Vector3d>()
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
        for(i in 0..2)
            vel[i] = ((prevVec[i] + vec[i]) * delta) / 2.0
        return vel
    }

}