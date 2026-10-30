package org.netxms.web

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private const val SECRET = "secret-detail"

private class FailingServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        throw IllegalArgumentException(SECRET)
    }
}

private class SendErrorServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, SECRET)
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenericErrorHandlerTest {
    private val server = Server(0)
    private val client = HttpClient.newHttpClient()

    @BeforeAll
    fun startServer() {
        val context = ServletContextHandler()
        context.contextPath = "/"
        context.addServlet(ServletHolder(FailingServlet()), "/fail")
        context.addServlet(ServletHolder(SendErrorServlet()), "/send-error")
        context.errorHandler = GenericErrorHandler()
        server.errorHandler = GenericErrorHandler()
        server.handler = context
        server.start()
    }

    @AfterAll
    fun stopServer() {
        server.stop()
    }

    private fun get(path: String, accept: String? = null): HttpResponse<String> {
        val port = (server.connectors[0] as ServerConnector).localPort
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        accept?.let { request.header("Accept", it) }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun assertGeneric(response: HttpResponse<String>, status: Int, path: String, reason: String) {
        assertEquals(status, response.statusCode())
        val body = response.body()
        assertTrue(body.contains(reason), body)
        assertFalse(body.contains(SECRET), body)
        assertFalse(body.contains("IllegalArgumentException"), body)
        assertFalse(body.contains(path), body)
    }

    @Test
    fun `exception is hidden from html response`() {
        assertGeneric(get("/fail", "text/html"), 500, "/fail", "Server Error")
    }

    @Test
    fun `exception is hidden from json response`() {
        val response = get("/fail", "application/json")
        assertGeneric(response, 500, "/fail", "Server Error")
        assertEquals("""{"status":"500","message":"Server Error"}""", response.body())
    }

    @Test
    fun `exception is hidden from plain text response`() {
        assertGeneric(get("/fail", "text/plain"), 500, "/fail", "Server Error")
    }

    @Test
    fun `sendError message is hidden`() {
        assertGeneric(get("/send-error"), 500, "/send-error", "Server Error")
    }

    @Test
    fun `missing page keeps standard reason`() {
        assertGeneric(get("/missing"), 404, "/missing", "Not Found")
    }
}
