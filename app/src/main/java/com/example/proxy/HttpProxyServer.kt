package com.example.proxy

import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors

object HttpProxyServer {
    private var serverSocket: ServerSocket? = null
    private var threadPool = Executors.newCachedThreadPool()
    private var isRunning = false
    private var serverThread: Thread? = null

    fun start(port: Int) {
        if (isRunning) return
        isRunning = true
        threadPool = Executors.newCachedThreadPool()

        serverThread = Thread {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                ProxyManager.log("HTTP Proxy Server listening on port $port")
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.tcpNoDelay = true // Disable Nagle's algorithm for low latency!
                    threadPool.execute {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    ProxyManager.log("HTTP server error: ${e.message}", true)
                }
            }
        }.apply {
            name = "HttpServerMain"
            start()
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverSocket = null
        serverThread?.interrupt()
        serverThread = null

        threadPool.shutdownNow()
        ProxyManager.log("HTTP Proxy Server stopped.")
    }

    private fun handleClient(clientSocket: Socket) {
        val clientIp = clientSocket.inetAddress?.hostAddress ?: "unknown"
        var targetHost = "unknown"
        val connectionId = java.util.UUID.randomUUID().toString()
        var targetSocket: Socket? = null

        try {
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            // Read the first line of HTTP request (Request-Line)
            val requestLine = readLine(clientIn) ?: return
            if (requestLine.trim().isEmpty()) {
                clientSocket.close()
                return
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                clientSocket.close()
                return
            }

            val method = parts[0].uppercase()
            val uri = parts[1]
            val httpVersion = if (parts.size > 2) parts[2] else "HTTP/1.1"

            // Read headers
            val headers = mutableListOf<String>()
            while (true) {
                val line = readLine(clientIn) ?: break
                if (line.trim().isEmpty()) break
                headers.add(line)
            }

            // Authentication check
            if (ProxyManager.useAuth.value) {
                var isAuthorized = false
                val expectedUser = ProxyManager.username.value
                val expectedPass = ProxyManager.password.value

                for (header in headers) {
                    if (header.startsWith("Proxy-Authorization:", ignoreCase = true)) {
                        val authValue = header.substringAfter("Proxy-Authorization:").trim()
                        if (authValue.startsWith("Basic", ignoreCase = true)) {
                            val base64Credentials = authValue.substringAfter("Basic").trim()
                            try {
                                val credentials = String(Base64.decode(base64Credentials, Base64.DEFAULT), Charsets.UTF_8)
                                val credParts = credentials.split(":")
                                if (credParts.size == 2 && credParts[0] == expectedUser && credParts[1] == expectedPass) {
                                    isAuthorized = true
                                }
                            } catch (e: Exception) {
                                // Decode error
                            }
                        }
                    }
                }

                if (!isAuthorized) {
                    val realmString = "ProxyShare"
                    val response = "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                            "Proxy-Authenticate: Basic realm=\"$realmString\"\r\n" +
                            "Content-Length: 33\r\n" +
                            "Content-Type: text/plain\r\n\r\n" +
                            "Proxy Authentication Required.\r\n"
                    clientOut.write(response.toByteArray(Charsets.UTF_8))
                    clientOut.flush()
                    clientSocket.close()
                    ProxyManager.log("HTTP: Auth attempt failed from $clientIp for URI: $uri", true)
                    return
                }
            }

            if (method == "CONNECT") {
                // HTTPS CONNECT Tunnel
                // URI style: host:port
                val hostParts = uri.split(":")
                val host = hostParts[0]
                val port = if (hostParts.size > 1) hostParts[1].toInt() else 443
                targetHost = "$host:$port"

                ProxyManager.log("HTTP: Connecting CONNECT to $targetHost (client: $clientIp)")

                try {
                    targetSocket = Socket(host, port)
                    targetSocket.tcpNoDelay = true
                } catch (e: Exception) {
                    ProxyManager.log("HTTP: Target CONNECT connection failed to $targetHost: ${e.message}", true)
                    val errorResponse = "$httpVersion 502 Bad Gateway\r\nConnection: close\r\n\r\n"
                    clientOut.write(errorResponse.toByteArray(Charsets.UTF_8))
                    clientOut.flush()
                    clientSocket.close()
                    return
                }

                // Send success response
                val successResponse = "$httpVersion 200 Connection Established\r\n" +
                        "Proxy-Agent: VPN-Tether-Proxy/1.0\r\n\r\n"
                clientOut.write(successResponse.toByteArray(Charsets.UTF_8))
                clientOut.flush()

                // Register connection
                val connection = ProxyConnection(
                    id = connectionId,
                    clientIp = clientIp,
                    targetHost = targetHost,
                    protocol = "HTTP(CONNECT)"
                )
                ProxyManager.addConnection(connection)

                // Pipe streams
                val targetIn = targetSocket.getInputStream()
                val targetOut = targetSocket.getOutputStream()

                val t1 = Thread { bridgeStreams(clientIn, targetOut, connectionId, true) }
                val t2 = Thread { bridgeStreams(targetIn, clientOut, connectionId, false) }

                t1.start()
                t2.start()

                t1.join()
                t2.join()

            } else {
                // Standard HTTP Proxying
                // URI style: http://host[:port]/path
                val urlObj = try {
                    URL(uri)
                } catch (e: Exception) {
                    null
                }

                if (urlObj == null) {
                    val badRequest = "$httpVersion 400 Bad Request\r\nConnection: close\r\n\r\nInvalid URL format."
                    clientOut.write(badRequest.toByteArray(Charsets.UTF_8))
                    clientOut.flush()
                    clientSocket.close()
                    return
                }

                val host = urlObj.host
                val port = if (urlObj.port != -1) urlObj.port else 80
                targetHost = "$host:$port"

                ProxyManager.log("HTTP: Connecting GET/POST to $targetHost (client: $clientIp)")

                try {
                    targetSocket = Socket(host, port)
                    targetSocket.tcpNoDelay = true
                } catch (e: Exception) {
                    ProxyManager.log("HTTP: Target connection failed to $targetHost: ${e.message}", true)
                    val errorResponse = "$httpVersion 502 Bad Gateway\r\nConnection: close\r\n\r\n"
                    clientOut.write(errorResponse.toByteArray(Charsets.UTF_8))
                    clientOut.flush()
                    clientSocket.close()
                    return
                }

                // Register connection
                val connection = ProxyConnection(
                    id = connectionId,
                    clientIp = clientIp,
                    targetHost = targetHost,
                    protocol = "HTTP($method)"
                )
                ProxyManager.addConnection(connection)

                val targetOut = targetSocket.getOutputStream()
                val targetIn = targetSocket.getInputStream()

                // Forward recalculated request to the target
                val pathAndQuery = if (urlObj.query != null) "${urlObj.path}?${urlObj.query}" else urlObj.path.ifEmpty { "/" }
                val newRequestLine = "$method $pathAndQuery $httpVersion\r\n"
                targetOut.write(newRequestLine.toByteArray(Charsets.UTF_8))

                // Forward headers (excluding proxy auth headers)
                for (header in headers) {
                    if (!header.startsWith("Proxy-Authorization:", ignoreCase = true) &&
                        !header.startsWith("Proxy-Connection:", ignoreCase = true)) {
                        targetOut.write("$header\r\n".toByteArray(Charsets.UTF_8))
                    }
                }
                // Write empty line to terminate headers
                targetOut.write("\r\n".toByteArray(Charsets.UTF_8))
                targetOut.flush()

                // Pipe remaining streams bi-directionally (e.g., POST data and response data)
                val t1 = Thread { bridgeStreams(clientIn, targetOut, connectionId, true) }
                val t2 = Thread { bridgeStreams(targetIn, clientOut, connectionId, false) }

                t1.start()
                t2.start()

                t1.join()
                t2.join()
            }

        } catch (e: Exception) {
            ProxyManager.log("HTTP bridge closed for $targetHost with exception: ${e.message}")
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {}
            try {
                targetSocket?.close()
            } catch (e: Exception) {}
            ProxyManager.removeConnection(connectionId)
        }
    }

    private fun readLine(input: InputStream): String? {
        val bos = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c == -1) {
                if (bos.size() == 0) return null
                break
            }
            if (c == '\n'.toInt()) {
                break
            }
            if (c != '\r'.toInt()) {
                bos.write(c)
            }
        }
        return bos.toString("UTF-8")
    }

    private fun bridgeStreams(input: InputStream, output: OutputStream, connectionId: String, isUpload: Boolean) {
        val buffer = ByteArray(32768) // 32KB buffer for ultra-high speed throughput!
        val bufIn = BufferedInputStream(input, 32768)
        val bufOut = BufferedOutputStream(output, 32768)
        try {
            var read: Int
            while (bufIn.read(buffer).also { read = it } != -1) {
                bufOut.write(buffer, 0, read)
                bufOut.flush()
                if (isUpload) {
                    ProxyManager.updateConnectionBytes(connectionId, read.toLong(), 0L)
                } else {
                    ProxyManager.updateConnectionBytes(connectionId, 0L, read.toLong())
                }
            }
        } catch (e: Exception) {
            // normal termination on connection close
        } finally {
            try {
                input.close()
            } catch (e: Exception) {}
            try {
                output.close()
            } catch (e: Exception) {}
        }
    }
}
