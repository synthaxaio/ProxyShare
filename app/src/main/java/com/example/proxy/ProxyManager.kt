package com.example.proxy

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

object ProxyManager {
    private const val TAG = "ProxyManager"
    private val scope = CoroutineScope(Dispatchers.Default)
    private var speedTrackerJob: Job? = null

    // Preferences / Settings State
    val httpPort = MutableStateFlow(8080)
    val socksPort = MutableStateFlow(1080)
    val useAuth = MutableStateFlow(false)
    val username = MutableStateFlow("admin")
    val password = MutableStateFlow("proxy123")

    // Running State
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Real-time Traffic Metrics
    private val _uploadSpeed = MutableStateFlow(0L) // bytes per second
    val uploadSpeed: StateFlow<Long> = _uploadSpeed.asStateFlow()

    private val _downloadSpeed = MutableStateFlow(0L) // bytes per second
    val downloadSpeed: StateFlow<Long> = _downloadSpeed.asStateFlow()

    private val _totalBytesUploaded = MutableStateFlow(0L)
    val totalBytesUploaded: StateFlow<Long> = _totalBytesUploaded.asStateFlow()

    private val _totalBytesDownloaded = MutableStateFlow(0L)
    val totalBytesDownloaded: StateFlow<Long> = _totalBytesDownloaded.asStateFlow()

    // Connections map (thread-safe for concurrent updates from proxy threads)
    private val connectionsMap = ConcurrentHashMap<String, ProxyConnection>()
    private val _activeConnections = MutableStateFlow<List<ProxyConnection>>(emptyList())
    val activeConnections: StateFlow<List<ProxyConnection>> = _activeConnections.asStateFlow()

    // Logs state (rolls at max 100 entries to prevent memory leak)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // Keep track of raw counters for delta speed calculations
    private var lastUploadedBytes = 0L
    private var lastDownloadedBytes = 0L

    init {
        log("ProxyShare system initialized. Ready to share.")
    }

    fun startServers(context: Context) {
        if (_isRunning.value) return
        _isRunning.value = true
        log("Starting HTTP proxy on port ${httpPort.value} and SOCKS5 proxy on port ${socksPort.value}...")

        // Clear active stats
        connectionsMap.clear()
        _activeConnections.value = emptyList()
        _totalBytesUploaded.value = 0L
        _totalBytesDownloaded.value = 0L
        _uploadSpeed.value = 0L
        _downloadSpeed.value = 0L
        lastUploadedBytes = 0L
        lastDownloadedBytes = 0L

        // Start servers
        HttpProxyServer.start(httpPort.value)
        Socks5ProxyServer.start(socksPort.value)

        // Start speed tracking routine
        startSpeedTracker()
    }

    fun stopServers() {
        if (!_isRunning.value) return
        _isRunning.value = false
        log("Stopping HTTP and SOCKS5 proxy servers...")

        HttpProxyServer.stop()
        Socks5ProxyServer.stop()

        stopSpeedTracker()
        connectionsMap.clear()
        _activeConnections.value = emptyList()
        _uploadSpeed.value = 0L
        _downloadSpeed.value = 0L
    }

    fun addConnection(connection: ProxyConnection) {
        connectionsMap[connection.id] = connection
        updateConnectionsList()
    }

    fun updateConnectionBytes(id: String, uploadedDelta: Long, downloadedDelta: Long) {
        val conn = connectionsMap[id] ?: return
        val updated = conn.copy(
            bytesUploaded = conn.bytesUploaded + uploadedDelta,
            bytesDownloaded = conn.bytesDownloaded + downloadedDelta
        )
        connectionsMap[id] = updated
        _totalBytesUploaded.update { it + uploadedDelta }
        _totalBytesDownloaded.update { it + downloadedDelta }
        updateConnectionsList()
    }

    fun removeConnection(id: String) {
        val removed = connectionsMap.remove(id)
        if (removed != null) {
            updateConnectionsList()
        }
    }

    private fun updateConnectionsList() {
        _activeConnections.value = connectionsMap.values.toList().sortedByDescending { it.startTime }
    }

    fun log(message: String, isError: Boolean = false) {
        Log.d(TAG, message)
        scope.launch {
            _logs.update { currentLogs ->
                val newEntry = LogEntry(message = message, isError = isError)
                val updated = listOf(newEntry) + currentLogs
                if (updated.size > 150) updated.take(150) else updated
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun startSpeedTracker() {
        speedTrackerJob?.cancel()
        speedTrackerJob = scope.launch {
            while (_isRunning.value) {
                delay(1000)
                val currentUploaded = _totalBytesUploaded.value
                val currentDownloaded = _totalBytesDownloaded.value

                val uploadedDelta = currentUploaded - lastUploadedBytes
                val downloadedDelta = currentDownloaded - lastDownloadedBytes

                _uploadSpeed.value = if (uploadedDelta > 0) uploadedDelta else 0L
                _downloadSpeed.value = if (downloadedDelta > 0) downloadedDelta else 0L

                lastUploadedBytes = currentUploaded
                lastDownloadedBytes = currentDownloaded
            }
        }
    }

    private fun stopSpeedTracker() {
        speedTrackerJob?.cancel()
        speedTrackerJob = null
    }

    /**
     * Retrieves all valid local IPv4 addresses (with interface names) available on this device.
     */
    fun getLocalIpAddresses(): List<Pair<String, String>> {
        val ips = mutableListOf<Pair<String, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val element = interfaces.nextElement()
                val addresses = element.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == false) {
                        val name = element.displayName ?: element.name
                        ips.add(Pair(name, address.hostAddress ?: ""))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching local IP addresses", e)
        }
        return ips
    }
}
