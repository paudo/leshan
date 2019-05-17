package org.eclipse.leshan.client.demo

import org.eclipse.leshan.LwM2mId.*
import org.eclipse.leshan.client.`object`.Security.*

import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.Arrays

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.eclipse.californium.core.network.config.NetworkConfig
import org.eclipse.leshan.LwM2m
import org.eclipse.leshan.client.californium.LeshanClient
import org.eclipse.leshan.client.californium.LeshanClientBuilder
import org.eclipse.leshan.client.`object`.Server
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler
import org.eclipse.leshan.client.resource.ObjectsInitializer
import org.eclipse.leshan.core.model.LwM2mModel
import org.eclipse.leshan.core.model.ObjectLoader
import org.eclipse.leshan.core.model.ObjectModel
import org.eclipse.leshan.core.request.BindingMode
import org.eclipse.leshan.util.Hex
import org.eclipse.leshan.util.SecurityUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object LeshanClientDemo {

    private val LOG = LoggerFactory.getLogger(LeshanClientDemo::class.java!!)

    private val modelPaths = arrayOf("3303.xml")

    private val OBJECT_ID_TEMPERATURE_SENSOR = 3303
    private val DEFAULT_ENDPOINT = "LeshanClientDemo"
    private val USAGE = "java -jar leshan-client-demo.jar [OPTION]\n\n"

    private var locationInstance: MyLocation? = null

    @JvmStatic
    fun main(args: Array<String>) {

        // Define options for command line tools
        val options = Options()

        val PSKChapter = StringBuilder()
        PSKChapter.append("\n .")
        PSKChapter.append("\n .")
        PSKChapter
                .append("\n ================================[ PSK ]=================================")
        PSKChapter
                .append("\n | By default Leshan demo use non secure connection.                    |")
        PSKChapter
                .append("\n | To use PSK, -i and -p options should be used together.               |")
        PSKChapter
                .append("\n ------------------------------------------------------------------------")

        val RPKChapter = StringBuilder()
        RPKChapter.append("\n .")
        RPKChapter.append("\n .")
        RPKChapter
                .append("\n ================================[ RPK ]=================================")
        RPKChapter
                .append("\n | By default Leshan demo use non secure connection.                    |")
        RPKChapter
                .append("\n | To use RPK, -cpubk -cprik -spubk options should be used together.    |")
        RPKChapter
                .append("\n | To get helps about files format and how to generate it, see :        |")
        RPKChapter
                .append("\n | See https://github.com/eclipse/leshan/wiki/Credential-files-format   |")
        RPKChapter
                .append("\n ------------------------------------------------------------------------")

        val X509Chapter = StringBuilder()
        X509Chapter.append("\n .")
        X509Chapter.append("\n .")
        X509Chapter
                .append("\n ================================[X509]==================================")
        X509Chapter
                .append("\n | By default Leshan demo use non secure connection.                    |")
        X509Chapter
                .append("\n | To use X509, -ccert -cprik -scert options should be used together.   |")
        X509Chapter
                .append("\n | To get helps about files format and how to generate it, see :        |")
        X509Chapter
                .append("\n | See https://github.com/eclipse/leshan/wiki/Credential-files-format   |")
        X509Chapter
                .append("\n ------------------------------------------------------------------------")

        options.addOption("h", "help", false, "Display help information.")
        options.addOption("n", true, String.format(
                "Set the endpoint name of the Client.\nDefault: the local hostname or '%s' if any.",
                DEFAULT_ENDPOINT))
        options.addOption("b", false, "If present use bootstrap.")
        options.addOption("lh", true,
                "Set the local CoAP address of the Client.\n  Default: any local address.")
        options.addOption("lp", true,
                "Set the local CoAP port of the Client.\n  Default: A valid port value is between 0 and 65535.")
        options.addOption("u", true,
                String.format("Set the LWM2M or Bootstrap server URL.\nDefault: localhost:%d.",
                        LwM2m.DEFAULT_COAP_PORT))
        options.addOption("pos", true,
                "Set the initial location (latitude, longitude) of the device to be reported by the Location object.\n Format: lat_float:long_float")
        options.addOption("sf", true,
                "Scale factor to apply when shifting position.\n Default is 1.0.$PSKChapter")
        options.addOption("i", true, "Set the LWM2M or Bootstrap server PSK identity in ascii.")
        options.addOption("p", true,
                "Set the LWM2M or Bootstrap server Pre-Shared-Key in hexa.$RPKChapter")
        options.addOption("cpubk", true,
                "The path to your client public key file.\n The public Key should be in SubjectPublicKeyInfo format (DER encoding).")
        options.addOption("cprik", true,
                "The path to your client private key file.\nThe private key should be in PKCS#8 format (DER encoding).")
        options.addOption("spubk", true,
                "The path to your server public key file.\n The public Key should be in SubjectPublicKeyInfo format (DER encoding).$X509Chapter")
        options.addOption("ccert", true,
                "The path to your client certificate file.\n The certificate Common Name (CN) should generaly be equal to the client endpoint name (see -n option).\nThe certificate should be in X509v3 format (DER encoding).")
        options.addOption("scert", true,
                "The path to your server certificate file.\n The certificate should be in X509v3 format (DER encoding).")

        val formatter = HelpFormatter()
        formatter.width = 90
        formatter.optionComparator = null

        // Parse arguments
        val cl: CommandLine
        try {
            cl = DefaultParser().parse(options, args)
        } catch (e: ParseException) {
            System.err.println("Parsing failed.  Reason: " + e.message)
            formatter.printHelp(USAGE, options)
            return
        }

        // Print help
        if (cl.hasOption("help")) {
            formatter.printHelp(USAGE, options)
            return
        }

        // Abort if unexpected options
        if (cl.args.size > 0) {
            System.err.println("Unexpected option or arguments : " + cl.argList)
            formatter.printHelp(USAGE, options)
            return
        }

        // Abort if PSK config is not complete
        if (cl.hasOption("i") && !cl.hasOption("p") || !cl.hasOption("i") && cl.hasOption("p")) {
            System.err
                    .println(
                            "You should precise identity (-i) and Pre-Shared-Key (-p) if you want to connect in PSK")
            formatter.printHelp(USAGE, options)
            return
        }

        // Abort if all RPK config is not complete
        var rpkConfig = false
        if (cl.hasOption("cpubk") || cl.hasOption("spubk")) {
            if (!cl.hasOption("cpubk") || !cl.hasOption("cprik") || !cl.hasOption("spubk")) {
                System.err.println("cpubk, cprik and spubk should be used together to connect using RPK")
                formatter.printHelp(USAGE, options)
                return
            } else {
                rpkConfig = true
            }
        }

        // Abort if all X509 config is not complete
        var x509config = false
        if (cl.hasOption("ccert") || cl.hasOption("scert")) {
            if (!cl.hasOption("ccert") || !cl.hasOption("cprik") || !cl.hasOption("scert")) {
                System.err.println("ccert, cprik and scert should be used together to connect using X509")
                formatter.printHelp(USAGE, options)
                return
            } else {
                x509config = true
            }
        }

        // Abort if cprik is used without complete RPK or X509 config
        if (cl.hasOption("cprik")) {
            if (!x509config && !rpkConfig) {
                System.err.println(
                        "cprik should be used with ccert and scert for X509 config OR cpubk and spubk for RPK config")
                formatter.printHelp(USAGE, options)
                return
            }
        }

        // Get endpoint name
        var endpoint: String
        if (cl.hasOption("n")) {
            endpoint = cl.getOptionValue("n")
        } else {
            try {
                endpoint = InetAddress.getLocalHost().hostName
            } catch (e: UnknownHostException) {
                endpoint = DEFAULT_ENDPOINT
            }

        }

        // Get server URI
        val serverURI: String
        if (cl.hasOption("u")) {
            if (cl.hasOption("i") || cl.hasOption("cpubk")) {
                serverURI = "coaps://" + cl.getOptionValue("u")
            } else {
                serverURI = "coap://" + cl.getOptionValue("u")
            }
        } else {
            if (cl.hasOption("i") || cl.hasOption("cpubk") || cl.hasOption("ccert")) {
                serverURI = "coaps://localhost:" + LwM2m.DEFAULT_COAP_SECURE_PORT
            } else {
                serverURI = "coap://localhost:" + LwM2m.DEFAULT_COAP_PORT
            }
        }

        // get PSK info
        var pskIdentity: ByteArray? = null
        var pskKey: ByteArray? = null
        if (cl.hasOption("i")) {
            pskIdentity = cl.getOptionValue("i").toByteArray()
            pskKey = Hex.decodeHex(cl.getOptionValue("p").toCharArray())
        }

        // get RPK info
        var clientPublicKey: PublicKey? = null
        var clientPrivateKey: PrivateKey? = null
        var serverPublicKey: PublicKey? = null
        if (cl.hasOption("cpubk")) {
            try {
                clientPrivateKey = SecurityUtil.privateKey.readFromFile(cl.getOptionValue("cprik"))
                clientPublicKey = SecurityUtil.publicKey.readFromFile(cl.getOptionValue("cpubk"))
                serverPublicKey = SecurityUtil.publicKey.readFromFile(cl.getOptionValue("spubk"))
            } catch (e: Exception) {
                System.err.println("Unable to load RPK files : " + e.message)
                e.printStackTrace()
                formatter.printHelp(USAGE, options)
                return
            }

        }

        // get X509 info
        var clientCertificate: X509Certificate? = null
        var serverCertificate: X509Certificate? = null
        if (cl.hasOption("ccert")) {
            try {
                clientPrivateKey = SecurityUtil.privateKey.readFromFile(cl.getOptionValue("cprik"))
                clientCertificate = SecurityUtil.certificate.readFromFile(cl.getOptionValue("ccert"))
                serverCertificate = SecurityUtil.certificate.readFromFile(cl.getOptionValue("scert"))
            } catch (e: Exception) {
                System.err.println("Unable to load X509 files : " + e.message)
                e.printStackTrace()
                formatter.printHelp(USAGE, options)
                return
            }

        }

        // get local address
        var localAddress: String? = null
        var localPort = 0
        if (cl.hasOption("lh")) {
            localAddress = cl.getOptionValue("lh")
        }
        if (cl.hasOption("lp")) {
            localPort = Integer.parseInt(cl.getOptionValue("lp"))
        }

        var latitude: Float? = null
        var longitude: Float? = null
        var scaleFactor: Float? = 1.0f
        // get initial Location
        if (cl.hasOption("pos")) {
            try {
                val pos = cl.getOptionValue("pos")
                val colon = pos.indexOf(':')
                if (colon == -1 || colon == 0 || colon == pos.length - 1) {
                    System.err.println(
                            "Position must be a set of two floats separated by a colon, e.g. 48.131:11.459")
                    formatter.printHelp(USAGE, options)
                    return
                }
                latitude = java.lang.Float.valueOf(pos.substring(0, colon))
                longitude = java.lang.Float.valueOf(pos.substring(colon + 1))
            } catch (e: NumberFormatException) {
                System.err.println(
                        "Position must be a set of two floats separated by a colon, e.g. 48.131:11.459")
                formatter.printHelp(USAGE, options)
                return
            }

        }
        if (cl.hasOption("sf")) {
            try {
                scaleFactor = java.lang.Float.valueOf(cl.getOptionValue("sf"))
            } catch (e: NumberFormatException) {
                System.err.println("Scale factor must be a float, e.g. 1.0 or 0.01")
                formatter.printHelp(USAGE, options)
                return
            }

        }
        try {
            createAndStartClient(endpoint, localAddress, localPort, cl.hasOption("b"), serverURI,
                    pskIdentity, pskKey,
                    clientPrivateKey, clientPublicKey, serverPublicKey, clientCertificate, serverCertificate)
        } catch (e: Exception) {
            System.err.println("Unable to create and start client ...")
            e.printStackTrace()
        }

    }

    @Throws(CertificateEncodingException::class)
    fun createAndStartClient(endpoint: String, localAddress: String?, localPort: Int,
                             needBootstrap: Boolean,
                             serverURI: String, pskIdentity: ByteArray?, pskKey: ByteArray?, clientPrivateKey: PrivateKey?,
                             clientPublicKey: PublicKey?,
                             serverPublicKey: PublicKey?, clientCertificate: X509Certificate?,
                             serverCertificate: X509Certificate?) {

        //    locationInstance = new MyLocation(latitude, longitude, scaleFactor);
        locationInstance = MyLocation()

        // Initialize model
        val models = ObjectLoader.loadDefault()
        models.addAll(ObjectLoader.loadDdfResources("/models", modelPaths))

        // Initialize object list
        val initializer = ObjectsInitializer(LwM2mModel(models))
        if (needBootstrap) {
            if (pskIdentity != null) {
                initializer.setInstancesForObject(SECURITY, pskBootstrap(serverURI, pskIdentity, pskKey!!))
                initializer.setClassForObject(SERVER, Server::class.java)
            } else if (clientPublicKey != null) {
                initializer
                        .setInstancesForObject(SECURITY, rpkBootstrap(serverURI, clientPublicKey.encoded,
                                clientPrivateKey!!.encoded, serverPublicKey!!.encoded))
                initializer.setClassForObject(SERVER, Server::class.java)
            } else if (clientCertificate != null) {
                initializer.setInstancesForObject(SECURITY,
                        x509Bootstrap(serverURI, clientCertificate.encoded,
                                clientPrivateKey!!.encoded, serverCertificate!!.encoded))
                initializer.setInstancesForObject(SERVER, Server(123, 30, BindingMode.U, false))
            } else {
                initializer.setInstancesForObject(SECURITY, noSecBootstap(serverURI))
                initializer.setClassForObject(SERVER, Server::class.java)
            }
        } else {
            if (pskIdentity != null) {
                initializer.setInstancesForObject(SECURITY, psk(serverURI, 123, pskIdentity, pskKey!!))
                initializer.setInstancesForObject(SERVER, Server(123, 30, BindingMode.U, false))
            } else if (clientPublicKey != null) {
                initializer
                        .setInstancesForObject(SECURITY, rpk(serverURI, 123, clientPublicKey.encoded,
                                clientPrivateKey!!.encoded, serverPublicKey!!.encoded))
                initializer.setInstancesForObject(SERVER, Server(123, 30, BindingMode.U, false))
            } else if (clientCertificate != null) {
                initializer
                        .setInstancesForObject(SECURITY, x509(serverURI, 123, clientCertificate.encoded,
                                clientPrivateKey!!.encoded, serverCertificate!!.encoded))
                initializer.setInstancesForObject(SERVER, Server(123, 30, BindingMode.U, false))
            } else {
                initializer.setInstancesForObject(SECURITY, noSec(serverURI, 123))
                initializer.setInstancesForObject(SERVER, Server(123, 30, BindingMode.U, false))
            }
        }
        initializer.setInstancesForObject(DEVICE, MyDevice())
        initializer.setInstancesForObject(LOCATION, locationInstance)
        initializer.setInstancesForObject(OBJECT_ID_TEMPERATURE_SENSOR, RandomTemperatureSensor())
        val enablers = initializer.createAll()

        // Create CoAP Config
        val coapConfig: NetworkConfig
        val configFile = File(NetworkConfig.DEFAULT_FILE_NAME)
        if (configFile.isFile) {
            coapConfig = NetworkConfig()
            coapConfig.load(configFile)
        } else {
            coapConfig = LeshanClientBuilder.createDefaultNetworkConfig()
            coapConfig.store(configFile)
        }

        // Create client
        val builder = LeshanClientBuilder(endpoint)
        builder.setLocalAddress(localAddress, localPort)
        builder.setObjects(enablers)
        builder.setCoapConfig(coapConfig)
        val client = builder.build()

        // Display client public key to easily add it in demo servers.
        if (clientPublicKey != null) {
            if (clientPublicKey is ECPublicKey) {
                val ecPublicKey = clientPublicKey as ECPublicKey?
                // Get x coordinate
                var x = ecPublicKey!!.w.affineX.toByteArray()
                if (x[0].toInt() == 0) {
                    x = Arrays.copyOfRange(x, 1, x.size)
                }

                // Get Y coordinate
                var y = ecPublicKey.w.affineY.toByteArray()
                if (y[0].toInt() == 0) {
                    y = Arrays.copyOfRange(y, 1, y.size)
                }

                // Get Curves params
                val params = ecPublicKey.params.toString()

                LOG.info(
                        "Client uses RPK : \n Elliptic Curve parameters  : {} \n Public x coord : {} \n Public y coord : {} \n Public Key (Hex): {} \n Private Key (Hex): {}",
                        params, Hex.encodeHexString(x), Hex.encodeHexString(y),
                        Hex.encodeHexString(clientPublicKey.encoded),
                        Hex.encodeHexString(clientPrivateKey!!.encoded))

            } else {
                throw IllegalStateException(
                        "Unsupported Public Key Format (only ECPublicKey supported).")
            }
        }
        // Display X509 credentials to easily at it in demo servers.
        if (clientCertificate != null) {
            LOG.info("Client uses X509 : \n X509 Certificate (Hex): {} \n Private Key (Hex): {}",
                    Hex.encodeHexString(clientCertificate.encoded),
                    Hex.encodeHexString(clientPrivateKey!!.encoded))
        }

        LOG.info("Press 'w','a','s','d' to change reported Location ({},{}).",
                locationInstance!!.latitude,
                locationInstance!!.longitude)

        // Start the client
        client.start()

        // De-register on shutdown and stop client.
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                client.destroy(true) // send de-registration request before destroy
            }
        })
    }
}
