package org.eclipse.leshan.client.demo

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Arrays
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import org.eclipse.leshan.client.request.ServerIdentity
import org.eclipse.leshan.client.resource.BaseInstanceEnabler
import org.eclipse.leshan.core.model.ObjectModel
import org.eclipse.leshan.core.response.ExecuteResponse
import org.eclipse.leshan.core.response.ReadResponse
import org.eclipse.leshan.util.NamedThreadFactory

class RandomTemperatureSensor : BaseInstanceEnabler() {
    private val scheduler: ScheduledExecutorService
    private val rng = Random()
    private var currentTemp = 20.0
    private var minMeasuredValue = currentTemp
    private var maxMeasuredValue = currentTemp

    init {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(NamedThreadFactory("Temperature Sensor"))
        scheduler.scheduleAtFixedRate({ adjustTemperature() }, 2, 2, TimeUnit.SECONDS)
    }

    @Synchronized
    override fun read(identity: ServerIdentity, resourceId: Int): ReadResponse {
        when (resourceId) {
            MIN_MEASURED_VALUE -> return ReadResponse.success(resourceId, getTwoDigitValue(minMeasuredValue))
            MAX_MEASURED_VALUE -> return ReadResponse.success(resourceId, getTwoDigitValue(maxMeasuredValue))
            SENSOR_VALUE -> return ReadResponse.success(resourceId, getTwoDigitValue(currentTemp))
            UNITS -> return ReadResponse.success(resourceId, UNIT_CELSIUS)
            else -> return super.read(identity, resourceId)
        }
    }

    @Synchronized
    override fun execute(identity: ServerIdentity, resourceId: Int, params: String): ExecuteResponse {
        when (resourceId) {
            RESET_MIN_MAX_MEASURED_VALUES -> {
                resetMinMaxMeasuredValues()
                return ExecuteResponse.success()
            }
            else -> return super.execute(identity, resourceId, params)
        }
    }

    private fun getTwoDigitValue(value: Double): Double {
        val toBeTruncated = BigDecimal.valueOf(value)
        return toBeTruncated.setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    @Synchronized
    private fun adjustTemperature() {
        val delta = (rng.nextInt(20) - 10) / 10f
        currentTemp += delta.toDouble()
        val changedResource = adjustMinMaxMeasuredValue(currentTemp)
        if (changedResource != null) {
            fireResourcesChange(SENSOR_VALUE, changedResource)
        } else {
            fireResourcesChange(SENSOR_VALUE)
        }
    }

    private fun adjustMinMaxMeasuredValue(newTemperature: Double): Int? {

        if (newTemperature > maxMeasuredValue) {
            maxMeasuredValue = newTemperature
            return MAX_MEASURED_VALUE
        } else if (newTemperature < minMeasuredValue) {
            minMeasuredValue = newTemperature
            return MIN_MEASURED_VALUE
        } else {
            return null
        }
    }

    private fun resetMinMaxMeasuredValues() {
        minMeasuredValue = currentTemp
        maxMeasuredValue = currentTemp
    }

    override fun getAvailableResourceIds(model: ObjectModel): List<Int> {
        return supportedResources
    }

    companion object {

        private val UNIT_CELSIUS = "cel"
        private val SENSOR_VALUE = 5700
        private val UNITS = 5701
        private val MAX_MEASURED_VALUE = 5602
        private val MIN_MEASURED_VALUE = 5601
        private val RESET_MIN_MAX_MEASURED_VALUES = 5605
        private val supportedResources = Arrays.asList(SENSOR_VALUE, UNITS, MAX_MEASURED_VALUE,
                MIN_MEASURED_VALUE, RESET_MIN_MAX_MEASURED_VALUES)
    }
}
