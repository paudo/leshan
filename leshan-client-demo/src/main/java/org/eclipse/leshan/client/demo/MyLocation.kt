package org.eclipse.leshan.client.demo

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Arrays
import java.util.Date

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.eclipse.leshan.client.request.ServerIdentity
import org.eclipse.leshan.client.resource.BaseInstanceEnabler
import org.eclipse.leshan.core.model.ObjectModel
import org.eclipse.leshan.core.response.ReadResponse
import org.eclipse.leshan.util.NamedThreadFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MyLocation : BaseInstanceEnabler() {

    var latitude: Float = 0.toFloat()
        private set
    var longitude: Float = 0.toFloat()
        private set
    private val scaleFactor: Float
    var timestamp: Date? = null
        private set
    private val scheduler: ScheduledExecutorService

    init {
        this.latitude = latitude
        this.longitude = longitude
        this.scaleFactor = 1.0f
        this.timestamp = Date()

        this.scheduler = Executors
                .newSingleThreadScheduledExecutor(NamedThreadFactory("Temperature Sensor"))
        scheduler.scheduleAtFixedRate({ adjustLocation() }, 2, 2, TimeUnit.SECONDS)
    }

    override fun read(identity: ServerIdentity, resourceid: Int): ReadResponse {
        LOG.info("Read on Location Resource $resourceid")
        when (resourceid) {
            0 -> return ReadResponse.success(resourceid, latitude.toDouble())
            1 -> return ReadResponse.success(resourceid, longitude.toDouble())
            5 -> return ReadResponse.success(resourceid, timestamp)
            else -> return super.read(identity, resourceid)
        }
    }

    @Synchronized
    private fun adjustLocation() {
        setLatitude()
        setLongitude()
    }

    fun setLatitude() {
        val tempLatitude = myHttpRequest("latitude")

        if (tempLatitude != this.latitude) {
            this.latitude = tempLatitude
            this.timestamp = Date()
            fireResourcesChange(0, 5)
        }
    }

    fun setLongitude() {
        val tempLongitude = myHttpRequest("longitude")

        if (tempLongitude != this.latitude) {
            this.longitude = tempLongitude
            this.timestamp = Date()
            fireResourcesChange(1, 5)
        }
    }

    override fun getAvailableResourceIds(model: ObjectModel): List<Int> {
        return supportedResources
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MyLocation::class.java!!)

        private val supportedResources = Arrays.asList(0, 1, 5)

        private fun myHttpRequest(dataType: String): Float {
            try {
                val urlForGetRequest = URL("http://127.0.0.1:5000/get-location")
                var readLine: String? = null
                val conection = urlForGetRequest.openConnection() as HttpURLConnection
                conection.requestMethod = "GET"
                val responseCode = conection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val `in` = BufferedReader(
                            InputStreamReader(conection.inputStream))
                    val response = StringBuilder()
                    while ((`in`.readLine()) != null) {
                        readLine = `in`.readLine()
                        response.append(readLine)
                    }
                    `in`.close()
                    val root = JsonParser().parse(response.toString())

                    return root.asJsonObject.get(dataType).asFloat
                } else {
                    println("GET NOT WORKED")
                }
            } catch (e: Exception) {
                println(e.message)
            }

            return 0f
        }
    }
}