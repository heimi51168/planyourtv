package app.orchard.tv.policy.web

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * NanoHTTPD 2.3.1 shares one input stream for all requests on a keep-alive connection. A POST
 * body that no handler reads stays there, so the next request starts with `{}GET` and the server
 * answers 400. The page sends `{}` with acquire/heartbeat/release, and those routes can return
 * early, so this pins the body-drain behaviour independent of the browser.
 */
@RunWith(RobolectricTestRunner::class)
class ParentWebAdminKeepAliveTest {
    private lateinit var context: Context
    private lateinit var server: NanoHTTPD
    private var port = 0

    private data class HttpResponse(val code: Int, val body: String)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        server = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession): Response {
                val routed = ParentWebAdmin.handle(context, session, session.uri)
                return routed ?: newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "",
                )
            }
        }
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        port = server.listeningPort
    }

    @After
    fun tearDown() {
        server.stop()
    }

    /** Reads one response using its Content-Length; both requests share the same socket. */
    private fun readResponse(input: InputStream): HttpResponse {
        val statusLine = readLine(input)
        val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: -1
        var length = 0
        while (true) {
            val line = readLine(input)
            if (line.isEmpty()) break
            val name = line.substringBefore(':').trim()
            if (name.equals("Content-Length", ignoreCase = true)) {
                length = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(body, offset, length - offset)
            if (read < 0) break
            offset += read
        }
        return HttpResponse(code, String(body, 0, offset, Charsets.UTF_8))
    }

    private fun readLine(input: InputStream): String {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) break
            if (byte == '\n'.code) break
            if (byte != '\r'.code) line.append(byte.toChar())
        }
        return line.toString()
    }

    private fun writeRequest(
        socket: Socket,
        method: String,
        path: String,
        body: String?,
        keepAlive: Boolean,
    ) {
        val payload = body.orEmpty().toByteArray()
        val head = buildString {
            append("$method $path HTTP/1.1\r\n")
            append("Host: 127.0.0.1:$port\r\n")
            append("Connection: ${if (keepAlive) "keep-alive" else "close"}\r\n")
            if (body != null) {
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${payload.size}\r\n")
            }
            append("\r\n")
        }
        socket.getOutputStream().apply {
            write(head.toByteArray())
            write(payload)
            flush()
        }
    }

    @Test
    fun builtInPortraitRouteServesJpegBytes() {
        val connection = URL("http://127.0.0.1:$port/parent-avatar?preset=dad")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        val bytes = connection.inputStream.use { it.readBytes() }

        assertEquals(200, connection.responseCode)
        assertTrue(connection.contentType.startsWith("image/jpeg"))
        assertTrue(bytes.size > 1_000)
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun anEarlyReturnPostLeavesTheNextRequestReadableOnTheSameConnection() {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5_000
            val input = socket.getInputStream()

            // A cached page still calls the old login route; it returns 410 without reading
            // the body, which is exactly the case that used to poison the stream.
            writeRequest(socket, "POST", "/api/parent/unlock", "{}", keepAlive = true)
            val unlock = readResponse(input)
            assertEquals(410, unlock.code)
            assertTrue(unlock.body.contains("LOCAL_WEB_LOGIN"))

            writeRequest(socket, "GET", "/api/parent/bootstrap", null, keepAlive = false)
            val bootstrap = readResponse(input)
            assertEquals(200, bootstrap.code)
            assertTrue(bootstrap.body.contains("\"webAvailable\""))
        }
    }
}
