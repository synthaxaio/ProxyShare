package com.example.proxy

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

object Socks5ProxyServer {
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
                ProxyManager.log("SOCKS5 Proxy Server listening on port $port")
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.tcpNoDelay = true // Disable Nagle's algorithm for low latency!
                    threadPool.execute {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    ProxyManager.log("SOCKS5 server error: ${e.message}", true)
                }
            }
        }.apply {
            name = "Socks5ServerMain"
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
        ProxyManager.log("SOCKS5 Proxy Server stopped.")
    }

    private fun handleClient(clientSocket: Socket) {
        val clientIp = clientSocket.inetAddress?.hostAddress ?: "unknown"
        var targetHost = "unknown"
        val connectionId = java.util.UUID.randomUUID().toString()
        var targetSocket: Socket? = null

        try {
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            // 1. Handshake version check
            val version = clientIn.read()
            if (version == -1) return
            if (version != 0x05) {
                ProxyManager.log("SOCKS5: Unsupported protocol version: $version from $clientIp", true)
                clientSocket.close()
                return
            }

            // Read supported auth methods
            val numMethods = clientIn.read()
            if (numMethods == -1) return
            val methods = ByteArray(numMethods)
            var readMethods = 0
            while (readMethods < numMethods) {
                val read = clientIn.read(methods, readMethods, numMethods - readMethods)
                if (read == -1) return
                readMethods += read
            }

            // Select authentication method
            val useAuth = ProxyManager.useAuth.value
            val selectedMethod = if (useAuth) 0x02 else 0x00 // 0x02 is Username/Password, 0x00 is No Auth

            var methodSupported = false
            for (m in methods) {
                if (m.toInt() == selectedMethod) {
                    methodSupported = true
                    break
                }
            }

            if (!methodSupported) {
                // Return 0xFF: no acceptable method
                clientOut.write(byteArrayOf(0x05.toByte(), 0xFF.toByte()))
                clientOut.flush()
                clientSocket.close()
                return
            }

            // Return selected method
            clientOut.write(byteArrayOf(0x05.toByte(), selectedMethod.toByte()))
            clientOut.flush()

            // 2. Perform authentication subnegotiation if Username/Password selected
            if (useAuth) {
                val subAuthVersion = clientIn.read()
                if (subAuthVersion == -1) return
                if (subAuthVersion != 0x01) {
                    clientSocket.close()
                    return
                }

                // Read username
                val uLen = clientIn.read()
                if (uLen == -1) return
                val uBytes = ByteArray(uLen)
                var uRead = 0
                while (uRead < uLen) {
                    val read = clientIn.read(uBytes, uRead, uLen - uRead)
                    if (read == -1) return
                    uRead += read
                }
                val rcvUser = String(uBytes, Charsets.UTF_8)

                // Read password
                val pLen = clientIn.read()
                if (pLen == -1) return
                val pBytes = ByteArray(pLen)
                var pRead = 0
                while (pRead < pLen) {
                    val read = clientIn.read(pBytes, pRead, pLen - pRead)
                    if (read == -1) return
                    pRead += read
                }
                val rcvPass = String(pBytes, Charsets.UTF_8)

                val expectedUser = ProxyManager.username.value
                val expectedPass = ProxyManager.password.value

                if (rcvUser == expectedUser && rcvPass == expectedPass) {
                    // Auth success (reply version 0x01, status 0x00)
                    clientOut.write(byteArrayOf(0x01.toByte(), 0x00.toByte()))
                    clientOut.flush()
                } else {
                    // Auth fail (reply version 0x01, status 0x01 or other non-zero)
                    clientOut.write(byteArrayOf(0x01.toByte(), 0x01.toByte()))
                    clientOut.flush()
                    clientSocket.close()
                    ProxyManager.log("SOCKS5 Auth failed for $rcvUser from $clientIp", true)
                    return
                }
            }

            // 3. Connection Request
            val reqVer = clientIn.read()
            if (reqVer == -1) return
            if (reqVer != 0x05) {
                clientSocket.close()
                return
            }

            val cmd = clientIn.read()
            if (cmd != 0x01) { // 0x01 is CONNECT command
                // Command not supported
                clientOut.write(byteArrayOf(0x05.toByte(), 0x07.toByte(), 0x00.toByte(), 0x01.toByte(), 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                clientSocket.close()
                return
            }

            clientIn.read() // Skip reserved byte

            val addrType = clientIn.read()
            val destIp: String
            when (addrType) {
                0x01 -> { // IPv4
                    val ipBytes = ByteArray(4)
                    clientIn.read(ipBytes)
                    destIp = InetAddress.getByAddress(ipBytes).hostAddress ?: "0.0.0.0"
                }
                0x03 -> { // Domain name
                    val domainLen = clientIn.read()
                    val domainBytes = ByteArray(domainLen)
                    var domainRead = 0
                    while (domainRead < domainLen) {
                        val read = clientIn.read(domainBytes, domainRead, domainLen - domainRead)
                        if (read == -1) return
                        domainRead += read
                    }
                    destIp = String(domainBytes, Charsets.UTF_8)
                }
                0x04 -> { // IPv6
                    val ipBytes = ByteArray(16)
                    clientIn.read(ipBytes)
                    destIp = "[" + InetAddress.getByAddress(ipBytes).hostAddress + "]"
                }
                else -> {
                    // Address type not supported
                    clientOut.write(byteArrayOf(0x05.toByte(), 0x08.toByte(), 0x00.toByte(), 0x01.toByte(), 0, 0, 0, 0, 0, 0))
                    clientOut.flush()
                    clientSocket.close()
                    return
                }
            }

            // Port (2 bytes, big-endian)
            val p1 = clientIn.read()
            val p2 = clientIn.read()
            if (p1 == -1 || p2 == -1) return
            val destPort = (p1 shl 8) or p2

            targetHost = "$destIp:$destPort"
            ProxyManager.log("SOCKS5: Connecting to $targetHost (client: $clientIp)")

            // Open connection to destination
            try {
                targetSocket = Socket(destIp, destPort)
                targetSocket.tcpNoDelay = true // Enable low latency!
            } catch (e: Exception) {
                ProxyManager.log("SOCKS5: Target connection failed to $targetHost: ${e.message}", true)
                // Host unreachable reply (0x04)
                clientOut.write(byteArrayOf(0x05.toByte(), 0x04.toByte(), 0x00.toByte(), 0x01.toByte(), 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                clientSocket.close()
                return
            }

            // Send Connect Success Response
            // Response: SOCKS5 (0x05) | Success (0x00) | Reserved (0x00) | IPv4 type (0x01) | IP 0.0.0.0 (4 bytes) | Port 0 (2 bytes)
            clientOut.write(byteArrayOf(0x05.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0, 0, 0, 0, 0, 0))
            clientOut.flush()

            // Register connection with manager
            val connection = ProxyConnection(
                id = connectionId,
                clientIp = clientIp,
                targetHost = targetHost,
                protocol = "SOCKS5"
            )
            ProxyManager.addConnection(connection)

            // Start bridging
            val targetIn = targetSocket.getInputStream()
            val targetOut = targetSocket.getOutputStream()

            // Run forwarding threads
            val t1 = Thread {
                bridgeStreams(clientIn, targetOut, connectionId, true)
            }
            val t2 = Thread {
                bridgeStreams(targetIn, clientOut, connectionId, false)
            }

            t1.start()
            t2.start()

            // Wait until both streams finish
            t1.join()
            t2.join()

        } catch (e: Exception) {
            // handle connection failure
            ProxyManager.log("SOCKS5 bridge closed for $targetHost with exception: ${e.message}")
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
            // connection broken or socket closed intentionally, normal flow
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
