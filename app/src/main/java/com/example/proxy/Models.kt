package com.example.proxy

import java.util.UUID

data class ProxyConnection(
    val id: String = UUID.randomUUID().toString(),
    val clientIp: String,
    val targetHost: String,
    val protocol: String, // "HTTP" or "SOCKS5"
    val startTime: Long = System.currentTimeMillis(),
    val bytesUploaded: Long = 0,
    val bytesDownloaded: Long = 0
)

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val isError: Boolean = false
)
