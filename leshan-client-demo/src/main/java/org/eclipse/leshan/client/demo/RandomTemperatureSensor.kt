package org.eclipse.leshan.client.demo

import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.eclipse.leshan.client.request.ServerIdentity
import org.eclipse.leshan.client.resource.BaseInstanceEnabler
import org.eclipse.leshan.core.model.ObjectModel
import org.eclipse.leshan.core.response.ExecuteResponse
import org.eclipse.leshan.core.response.ReadResponse
import org.eclipse.leshan.util.NamedThreadFactory

class RandomTemperatureSensor : BaseInstanceEnabler() {
    private val scheduler: ScheduledExecutorService
    private var currentTemp: Double = 0.toDouble()
    private var minMeasuredValue = currentTemp
    private var maxMeasuredValue = currentTemp
    private var first = true

    init {
        this.scheduler = Executors
                .newSingleThreadScheduledExecutor(NamedThreadFactory("Temperature Sensor"))
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
    override fun execute(identity: ServerIdentity, resourceId: Int,
                         params: String): ExecuteResponse {
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

        try {
            val urlForGetRequest = URL("http://127.0.0.1:5000/get-temperature")
            var readLine: String? = null
            val conection = urlForGetRequest.openConnection() as HttpURLConnection
            conection.requestMethod = "GET"
            val responseCode = conection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val `in` = BufferedReader(
                        InputStreamReader(conection.inputStream))
                val response = StringBuilder()
                while ((`in`.readLine()) != null) {
                    response.append(readLine)
                }
                `in`.close()
                val root = JsonParser().parse(response.toString())
                currentTemp = root.asJsonObject.get("temperature").asDouble
            } else {
                println("GET NOT WORKED")
            }
        } catch (e: Exception) {
            println(e.message)
        }

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
        } else if (newTemperature < minMeasuredValue || first) {
            first = false
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
        private val supportedResources = Arrays
                .asList(SENSOR_VALUE, UNITS, MAX_MEASURED_VALUE,
                        MIN_MEASURED_VALUE, RESET_MIN_MAX_MEASURED_VALUES)
    }
}
