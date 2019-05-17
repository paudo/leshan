package org.eclipse.leshan.client.demo

import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import java.util.Random
import java.util.TimeZone
import java.util.Timer
import java.util.TimerTask

import org.eclipse.leshan.client.request.ServerIdentity
import org.eclipse.leshan.client.resource.BaseInstanceEnabler
import org.eclipse.leshan.core.model.ObjectModel
import org.eclipse.leshan.core.model.ResourceModel.Type
import org.eclipse.leshan.core.node.LwM2mResource
import org.eclipse.leshan.core.response.ExecuteResponse
import org.eclipse.leshan.core.response.ReadResponse
import org.eclipse.leshan.core.response.WriteResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MyDevice : BaseInstanceEnabler() {

    private val manufacturer: String
        get() = "Leshan Demo Device"

    private val modelNumber: String
        get() = "Model 500"

    private val serialNumber: String
        get() = "LT-500-000-0001"

    private val firmwareVersion: String
        get() = "1.0.0"

    private val errorCode: Long
        get() = 0

    private val batteryLevel: Int
        get() = RANDOM.nextInt(101)

    private val memoryFree: Long
        get() = Runtime.getRuntime().freeMemory() / 1024

    private val currentTime: Date
        get() = Date()

    private var utcOffset = SimpleDateFormat("X").format(Calendar.getInstance().time)

    private var timezone = TimeZone.getDefault().id

    private val supportedBinding: String
        get() = "U"

    private val deviceType: String
        get() = "Demo"

    private val hardwareVersion: String
        get() = "1.0.1"

    private val softwareVersion: String
        get() = "1.0.2"

    private val batteryStatus: Int
        get() = RANDOM.nextInt(7)

    private val memoryTotal: Long
        get() = Runtime.getRuntime().totalMemory() / 1024

    init {
        // notify new date each 5 second
        val timer = Timer("Device-Current Time")
        timer.schedule(object : TimerTask() {
            override fun run() {
                fireResourcesChange(13)
            }
        }, 5000, 5000)
    }

    override fun read(identity: ServerIdentity, resourceid: Int): ReadResponse {
        LOG.info("Read on Device Resource $resourceid")
        when (resourceid) {
            0 -> return ReadResponse.success(resourceid, manufacturer)
            1 -> return ReadResponse.success(resourceid, modelNumber)
            2 -> return ReadResponse.success(resourceid, serialNumber)
            3 -> return ReadResponse.success(resourceid, firmwareVersion)
            9 -> return ReadResponse.success(resourceid, batteryLevel.toLong())
            10 -> return ReadResponse.success(resourceid, memoryFree)
            11 -> {
                val errorCodes = HashMap<Int, Long>()
                errorCodes[0] = errorCode
                return ReadResponse.success(resourceid, errorCodes, Type.INTEGER)
            }
            13 -> return ReadResponse.success(resourceid, currentTime)
            14 -> return ReadResponse.success(resourceid, utcOffset)
            15 -> return ReadResponse.success(resourceid, timezone)
            16 -> return ReadResponse.success(resourceid, supportedBinding)
            17 -> return ReadResponse.success(resourceid, deviceType)
            18 -> return ReadResponse.success(resourceid, hardwareVersion)
            19 -> return ReadResponse.success(resourceid, softwareVersion)
            20 -> return ReadResponse.success(resourceid, batteryStatus.toLong())
            21 -> return ReadResponse.success(resourceid, memoryTotal)
            else -> return super.read(identity, resourceid)
        }
    }

    override fun execute(identity: ServerIdentity, resourceid: Int, params: String?): ExecuteResponse {
        LOG.info("Execute on Device resource $resourceid")
        if (params != null && params.length != 0) {
            println("\t params $params")
        }
        return ExecuteResponse.success()
    }

    override fun write(identity: ServerIdentity, resourceid: Int, value: LwM2mResource): WriteResponse {
        LOG.info("Write on Device Resource $resourceid value $value")
        when (resourceid) {
            13 -> return WriteResponse.notFound()
            14 -> {
                utcOffset = value.value as String
                fireResourcesChange(resourceid)
                return WriteResponse.success()
            }
            15 -> {
                timezone = value.value as String
                fireResourcesChange(resourceid)
                return WriteResponse.success()
            }
            else -> return super.write(identity, resourceid, value)
        }
    }

    override fun getAvailableResourceIds(model: ObjectModel): List<Int> {
        return supportedResources
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MyDevice::class.java!!)

        private val RANDOM = Random()
        private val supportedResources = Arrays
                .asList(0, 1, 2, 3, 9, 10, 11, 13, 14, 15, 16, 17, 18,
                        19, 20, 21)
    }
}
