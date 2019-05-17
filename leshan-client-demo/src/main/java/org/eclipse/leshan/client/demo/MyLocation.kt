package org.eclipse.leshan.client.demo

import java.util.Arrays
import java.util.Date
import java.util.Random

import org.eclipse.leshan.client.request.ServerIdentity
import org.eclipse.leshan.client.resource.BaseInstanceEnabler
import org.eclipse.leshan.core.model.ObjectModel
import org.eclipse.leshan.core.response.ReadResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MyLocation @JvmOverloads constructor(latitude: Float? = null, longitude: Float? = null, private val scaleFactor: Float = 1.0f) : BaseInstanceEnabler() {

    private var latitude: Float = 0.toFloat()
    private var longitude: Float = 0.toFloat()
    var timestamp: Date? = null
        private set

    init {
        if (latitude != null) {
            this.latitude = latitude + 90f
        } else {
            this.latitude = RANDOM.nextInt(180).toFloat()
        }
        if (longitude != null) {
            this.longitude = longitude + 180f
        } else {
            this.longitude = RANDOM.nextInt(360).toFloat()
        }
        timestamp = Date()
    }

    override fun read(identity: ServerIdentity, resourceid: Int): ReadResponse {
        LOG.info("Read on Location Resource $resourceid")
        when (resourceid) {
            0 -> return ReadResponse.success(resourceid, getLatitude().toDouble())
            1 -> return ReadResponse.success(resourceid, getLongitude().toDouble())
            5 -> return ReadResponse.success(resourceid, timestamp)
            else -> return super.read(identity, resourceid)
        }
    }

    fun moveLocation(nextMove: String) {
        when (nextMove[0]) {
            'w' -> moveLatitude(1.0f)
            'a' -> moveLongitude(-1.0f)
            's' -> moveLatitude(-1.0f)
            'd' -> moveLongitude(1.0f)
        }
    }

    private fun moveLatitude(delta: Float) {
        latitude = latitude + delta * scaleFactor
        timestamp = Date()
        fireResourcesChange(0, 5)
    }

    private fun moveLongitude(delta: Float) {
        longitude = longitude + delta * scaleFactor
        timestamp = Date()
        fireResourcesChange(1, 5)
    }

    fun getLatitude(): Float {
        return latitude - 90.0f
    }

    fun getLongitude(): Float {
        return longitude - 180f
    }

    override fun getAvailableResourceIds(model: ObjectModel): List<Int> {
        return supportedResources
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MyLocation::class.java!!)

        private val supportedResources = Arrays.asList(0, 1, 5)
        private val RANDOM = Random()
    }
}