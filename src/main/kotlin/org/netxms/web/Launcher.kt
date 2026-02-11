package org.netxms.web

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.int
import org.eclipse.jetty.compression.server.CompressionHandler
import org.eclipse.jetty.ee10.webapp.WebAppContext
import org.eclipse.jetty.http.HttpMethod
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.resource.ResourceFactory
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import java.util.*

val ALLOWED_METHODS: EnumSet<HttpMethod> = EnumSet.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST)

class Launcher : CliktCommand(name = "netxms-web-launcher") {
    init {
        val props = Properties()
        javaClass.getResourceAsStream("/version.properties")?.use { props.load(it) }
        versionOption(props.getProperty("version", "unknown"), names = setOf("--version", "-v")) { "nxmc-web-launcher $it" }
    }

    private val httpPort by option("--httpPort", help = "Listen Port for HTTP connector, 0 will disable connector")
        .int().default(8080)
    private val httpsPort by option("--httpsPort", help = "Listen Port for HTTPS connector, 0 will disable connector")
        .int().default(0)
    private val keystore by option("--keystore", help = "Location of the keystore file with server's certificate and private key")
        .default("/var/lib/netxms/nxmc.pkcs12")
    private val keystorePassword by option("--keystorePassword",
        help = "Keystore file password. If environment variable exists with this name - its value will be used instead"
    ).default("")
    private val war by option("--war", help = "nxmc.war file location")
    private val log by option("--log", help = "Log file name or special names \"System.err\"/\"System.out\"")
        .default("System.err")
    private val logLevel by option("--logLevel", help = "Verbosity level").default("INFO")
    private val accessLog by option("--accessLog", help = "Write access log to separate file, otherwise will be sent to common log")
    private val disableSni by option("--disableSni", help = "Disable SNI host verification").flag()

    override fun run() {
        val resolvedKeystorePassword = if (System.getenv().containsKey(keystorePassword)) {
            System.getenv(keystorePassword)
        } else {
            keystorePassword
        }

        System.setProperty(org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, logLevel)
        System.setProperty(org.slf4j.simple.SimpleLogger.LOG_FILE_KEY, log)

        startServer(httpPort, httpsPort, keystore, resolvedKeystorePassword, war, accessLog, !disableSni)
    }
}

fun main(args: Array<String>) = Launcher().main(args)

private class MethodFilterHandler(handler: Handler) : Handler.Wrapper(handler) {
    override fun handle(request: Request, response: Response, callback: Callback): Boolean {
        val method = HttpMethod.fromString(request.method)
        if (method != null && !ALLOWED_METHODS.contains(method)) {
            response.status = HttpStatus.METHOD_NOT_ALLOWED_405
            callback.succeeded()
            return true
        }
        return super.handle(request, response, callback)
    }
}

private fun startServer(
    httpPort: Int,
    httpsPort: Int,
    keystore: String,
    keystorePassword: String?,
    war: String?,
    accessLog: String?,
    enableSniHostCheck: Boolean
) {
    val httpConfig = HttpConfiguration()
    val secureRequestCustomizer = SecureRequestCustomizer()
    secureRequestCustomizer.isSniHostCheck = enableSniHostCheck
    httpConfig.addCustomizer(secureRequestCustomizer)
    httpConfig.addCustomizer { request, responseHeaders ->
        responseHeaders.put("Content-Security-Policy", "default-src 'self' 'unsafe-eval' 'unsafe-inline'")
        responseHeaders.put("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
        responseHeaders.put("X-Frame-Options", "SAMEORIGIN")
        responseHeaders.put("X-Content-Type-Options", "nosniff")
        request
    }
    httpConfig.sendServerVersion = false
    val http = HttpConnectionFactory(httpConfig)

    val threadPool = QueuedThreadPool()
    threadPool.name = "server"
    val server = Server(threadPool)

    server.errorHandler = ErrorHandler().apply { isShowStacks = false }

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

    context.errorHandler = ErrorHandler().apply { isShowStacks = false }

    if (war != null) {
        context.war = war
    } else {
        context.baseResource = ResourceFactory.of(context).newClassLoaderResource("/META-INF/webapps/nxmc")
    }
    context.contextPath = "/"

    val compressionHandler = CompressionHandler(context)
    server.handler = MethodFilterHandler(compressionHandler)

    val logWriter: RequestLog.Writer = if (accessLog != null) {
        RequestLogWriter(accessLog)
    } else {
        Slf4jRequestLogWriter()
    }
    server.requestLog = CustomRequestLog(logWriter, CustomRequestLog.EXTENDED_NCSA_FORMAT)

    if (context.war == null && context.baseResource == null) {
        println("ERROR: WAR file is not available. Set location with '--war' argument.")
    } else {
        server.start()
    }
}
