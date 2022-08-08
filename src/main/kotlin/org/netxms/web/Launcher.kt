package org.netxms.web

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import org.eclipse.jetty.http.HttpMethod
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.server.*
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.eclipse.jetty.webapp.WebAppContext
import java.util.*

val ALLOWED_METHODS: EnumSet<HttpMethod> = EnumSet.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST)

fun main(args: Array<String>) {
    val parser = ArgParser("netxms-web-launcher")

    val httpPort by parser.option(
        ArgType.Int, description = "Listen Port for HTTP connector, 0 will disable connector"
    ).default(8080)
    val httpsPort by parser.option(
        ArgType.Int, description = "Listen Port for HTTPS connector, 0 will disable connector"
    ).default(0)

    val keystore by parser.option(
        ArgType.String, description = "Location of the keystore file with server's certificate and private key"
    ).default("/var/lib/netxms/nxmc.pkcs12")
    var keystorePassword by parser.option(
        ArgType.String,
        description = "Keystore file password. If environment variable exists with this name - it's value will be used instead"
    ).default("")

    val war by parser.option(
        ArgType.String,
        description = "nxmc.war file location",
    )

    val log by parser.option(
        ArgType.String, description = "Log file name or special names \"System.err\"/\"System.out\""
    ).default("System.err")
    val logLevel by parser.option(
        ArgType.String, description = "Verbosity level"
    ).default("INFO")
    val accessLog by parser.option(
        ArgType.String, description = "Write access log to separate file, otherwise will be sent to common log"
    )

    parser.parse(args)

    if (System.getenv().containsKey(keystorePassword)) {
        keystorePassword = System.getenv(keystorePassword)
    }

    System.setProperty(org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, logLevel)
    System.setProperty(org.slf4j.simple.SimpleLogger.LOG_FILE_KEY, log)

    startServer(httpPort, httpsPort, keystore, keystorePassword, war, accessLog)
}

private fun startServer(
    httpPort: Int, httpsPort: Int, keystore: String, keystorePassword: String?, war: String?, accessLog: String?
) {
    val httpConfig = HttpConfiguration()
    httpConfig.addCustomizer(SecureRequestCustomizer())
    httpConfig.addCustomizer { _, _, request ->
        val method = HttpMethod.fromString(request.method)
        if (!ALLOWED_METHODS.contains(method)) {
            request.isHandled = true
            request.response.status = HttpStatus.METHOD_NOT_ALLOWED_405
        }
    }
    val http = HttpConnectionFactory(httpConfig)

    val threadPool = QueuedThreadPool()
    threadPool.name = "server"
    val server = Server(threadPool)

    if (httpPort != 0) {
        val connector = ServerConnector(server, http)
        connector.port = httpPort
        server.addConnector(connector)
    }

    if (httpsPort != 0) {
        val sslContextFactory = SslContextFactory.Server()
        sslContextFactory.keyStorePath = keystore
        sslContextFactory.keyStorePassword = keystorePassword
        val tls = SslConnectionFactory(sslContextFactory, http.protocol)
        val sslConnector = ServerConnector(server, tls, http)
        sslConnector.port = httpsPort
        server.addConnector(sslConnector)
    }

    val context = WebAppContext()


    if (war != null) {
        context.war = war
    } else {
        context.setWarResource(Resource.newClassPathResource("/META-INF/webapps/nxmc"))
    }
    context.contextPath = "/"
    server.handler = context

    val logWriter: RequestLog.Writer = if (accessLog != null) {
        RequestLogWriter(accessLog)
    } else {
        Slf4jRequestLogWriter()
    }
    server.requestLog = CustomRequestLog(logWriter, CustomRequestLog.EXTENDED_NCSA_FORMAT)

    if (context.war == null) {
        println("ERROR: WAR file is not available. Set location with '--war' argument.")
    } else {
        server.start()
    }
}
