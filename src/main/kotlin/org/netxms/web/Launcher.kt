package org.netxms.web

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import org.eclipse.jetty.server.*
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.eclipse.jetty.webapp.WebAppContext

fun main(args: Array<String>) {
    val parser = ArgParser("netxms-web-launcher")

    val httpPort by parser.option(
        ArgType.Int,
        description = "Listen Port for HTTP connector, 0 will disable connector"
    ).default(8080)

    val httpsPort by parser.option(
        ArgType.Int,
        description = "Listen Port for HTTPS connector, 0 will disable connector"
    ).default(0)

    val keystore by parser.option(
        ArgType.String,
        description = "Location of the keystore file with server's certificate and private key"
    ).default("/var/lib/netxms/nxmc.jks")

    val keystorePassword by parser.option(
        ArgType.String,
        description = "Keystore file password"
    )

    val war by parser.option(
        ArgType.String,
        description = "nxmc.war file location"
    ).default("/var/lib/netxms/nxmc.war")

    parser.parse(args)

    val threadPool = QueuedThreadPool()
    threadPool.name = "server"

    val httpConfig = HttpConfiguration()
    httpConfig.addCustomizer(SecureRequestCustomizer())
    val http = HttpConnectionFactory(httpConfig)
    val server = Server(threadPool)

    if (httpPort != 0) {
        val connector = ServerConnector(server, http)
        connector.port = httpPort
        server.addConnector(connector)
    }

    if (httpsPort != 0) {
        val sslContextFactory = SslContextFactory.Server()
        sslContextFactory.keyStorePath = keystore
        sslContextFactory.setKeyStorePassword(keystorePassword)
        val tls = SslConnectionFactory(sslContextFactory, http.protocol)
        val sslConnector = ServerConnector(server, tls, http)
        sslConnector.port = httpsPort
        server.addConnector(sslConnector)
    }

    val context = WebAppContext()
    context.setWar(war)
    context.contextPath = "/"
    server.handler = context

    server.start()
}
