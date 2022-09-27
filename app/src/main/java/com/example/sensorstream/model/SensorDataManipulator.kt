package com.example.sensorstream.model

import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import kotlin.math.abs
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.vecmath.Vector3d

class SensorDataManipulator(private val sensorDataFlow : StateFlow<SensorsViewState>,
                            scope: CoroutineScope) : KoinComponent{

    private val positionCalculator = get<PositionCalculator>{ parametersOf(sensorDataFlow, scope) }

    val formatSensorData : (SensorsData) -> String = {
        val rotationQArr = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
        val referenceQArr = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
        SensorManager.getQuaternionFromVector(rotationQArr,
        floatArrayOf(it.rotationVector.x, it.rotationVector.y, it.rotationVector.z))
        SensorManager.getQuaternionFromVector(referenceQArr,
            floatArrayOf(it.referencePoint.x, it.referencePoint.y , it.referencePoint.z))

        "" + rotationQArr[1] + " " + rotationQArr[2] + " " + rotationQArr[3] + " " +
        rotationQArr[0] + " ; " + referenceQArr[1] + " " + referenceQArr[2] + " " +
        referenceQArr[3] + " " + referenceQArr[0] + " ; " + it.accel.x + " " + it.accel.y +
        " " + it.accel.z + " ; " + positionCalculator.accelFiltered.x + " " +
        positionCalculator.accelFiltered.y + " " + positionCalculator.accelFiltered.z +
        " ; " + positionCalculator.velocity.x + " " + positionCalculator.velocity.y + " " +
        positionCalculator.velocity.z + " ; " + positionCalculator.position.x + " " +
        positionCalculator.position.y + " " + positionCalculator.position.z
    }



}