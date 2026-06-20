package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.proxy.LogEntry
import com.example.proxy.ProxyConnection
import com.example.proxy.ProxyManager
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false) {
                Scaffold(modifier = Modifier.fillMaxSize().testTag("main_scaffold")) { innerPadding ->
                    ProxyDashboardScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun CopyIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFF005AC1)) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // draw background square
            drawRect(
                color = tint.copy(alpha = 0.4f),
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.25f),
                size = androidx.compose.ui.geometry.Size(w * 0.65f, h * 0.65f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
            // draw foreground square
            drawRect(
                color = tint,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.05f, h * 0.05f),
                size = androidx.compose.ui.geometry.Size(w * 0.65f, h * 0.65f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
fun ProxyDashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Observe global proxy states
    val isRunning by ProxyManager.isRunning.collectAsState()
    val uploadSpeed by ProxyManager.uploadSpeed.collectAsState()
    val downloadSpeed by ProxyManager.downloadSpeed.collectAsState()
    val totalBytesUploaded by ProxyManager.totalBytesUploaded.collectAsState()
    val totalBytesDownloaded by ProxyManager.totalBytesDownloaded.collectAsState()
    val activeConnections by ProxyManager.activeConnections.collectAsState()
    val logs by ProxyManager.logs.collectAsState()

    // Preferences / Settings bindings
    val httpPortVal by ProxyManager.httpPort.collectAsState()
    val socksPortVal by ProxyManager.socksPort.collectAsState()
    val useAuthVal by ProxyManager.useAuth.collectAsState()
    val usernameVal by ProxyManager.username.collectAsState()
    val passwordVal by ProxyManager.password.collectAsState()

    // Local state for configuration inputs
    var httpPortInput by remember(httpPortVal) { mutableStateOf(httpPortVal.toString()) }
    var socksPortInput by remember(socksPortVal) { mutableStateOf(socksPortVal.toString()) }
    var usernameInput by remember(usernameVal) { mutableStateOf(usernameVal) }
    var passwordInput by remember(passwordVal) { mutableStateOf(passwordVal) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Local IP cache
    var localIps by remember { mutableStateOf(emptyList<Pair<String, String>>()) }

    fun refreshAddresses() {
        localIps = ProxyManager.getLocalIpAddresses()
    }

    LaunchedEffect(Unit) {
        refreshAddresses()
    }

    // Validation flags
    var httpPortError by remember { mutableStateOf(false) }
    var socksPortError by remember { mutableStateOf(false) }

    // Notification permission launcher for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            ProxyService.startService(context)
        } else {
            Toast.makeText(context, "Notification permission is required to run proxy service in backend.", Toast.LENGTH_LONG).show()
            ProxyService.startService(context)
        }
    }

    // Bold Typography theme color palette
    val ColorPrimary = Color(0xFF005AC1) // Royal Blue accent
    val ColorBackground = Color(0xFFF7F9FF) // Light slate-blue foundation
    val ColorCardBg = Color(0xFFFFFFFF) // Pure white card surfaces
    val ColorConfigBg = Color(0xFFE0E7F6) // Muted blue container details
    val ColorBorder = Color(0xFFE2E8F0) // Subtle border outlines
    val ColorTextDark = Color(0xFF0F172A) // slate-900 typography
    val ColorTextMuted = Color(0xFF64748B) // slate-500 subtitles

    val glowColor by animateColorAsState(
        targetValue = if (isRunning) ColorPrimary else ColorTextMuted,
        animationSpec = tween(durationMillis = 600)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground) // Crisp Off-white Background
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- MASTHEAD HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Logo icon",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ProxyShare",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = ColorTextDark
                )
                Text(
                    text = "Share VPN Gateway without Root",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = ColorTextMuted
                )
            }

            // High-contrast Status standby badge
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isRunning) ColorPrimary.copy(alpha = 0.15f) else ColorTextMuted.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (isRunning) ColorPrimary.copy(alpha = 0.3f) else ColorTextMuted.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isRunning) "ACTIVE" else "STANDBY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = if (isRunning) ColorPrimary else ColorTextMuted
                )
            }
        }

        // --- POWER TOGGLE CONTROLLER CARD ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("control_card"),
            colors = CardDefaults.cardColors(
                containerColor = ColorCardBg
            ),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Central Big Pulsing Radar Indicator
                val infiniteTransition = rememberInfiniteTransition()
                val scale by if (isRunning) {
                    infiniteTransition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                } else {
                    remember { mutableStateOf(1.0f) }
                }

                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Pulse Ring with Scale Animation
                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) ColorPrimary.copy(alpha = 0.12f) else ColorTextMuted.copy(alpha = 0.05f))
                            .border(
                                2.dp,
                                if (isRunning) ColorPrimary.copy(alpha = 0.2f) else ColorTextMuted.copy(alpha = 0.1f),
                                CircleShape
                            )
                    )

                    // Solid Inter Circle Button
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) ColorPrimary else ColorTextMuted)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Refresh else Icons.Default.PlayArrow,
                            contentDescription = "Status symbol",
                            modifier = Modifier.size(38.dp),
                            tint = Color.White
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SYSTEM STATUS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = ColorTextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRunning) "VPN SHARED" else "PROXY STANDBY",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = if (isRunning) ColorPrimary else ColorTextDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRunning) "Tether interfaces serving clients safely" else "Start proxy service to broadcast interfaces",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorTextMuted,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(color = ColorBorder)

                // Real-time Bandwidth metrics styled like the HTML grid columns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Download Speed block
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(ColorBackground)
                            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Download link",
                                tint = ColorPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DOWNLOAD",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = ColorTextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatSpeedString(downloadSpeed),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = ColorTextDark
                        )
                        Text(
                            text = "Total: ${formatBytes(totalBytesDownloaded)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextMuted
                        )
                    }

                    // Upload Speed block
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(ColorBackground)
                            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Upload link",
                                tint = Color(0xFF0369A1), // beautiful light blue detail
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "UPLOAD",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = ColorTextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatSpeedString(uploadSpeed),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = ColorTextDark
                        )
                        Text(
                            text = "Total: ${formatBytes(totalBytesUploaded)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextMuted
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isRunning) {
                            ProxyService.stopService(context)
                        } else {
                            // Validate before starting
                            val hPort = httpPortInput.toIntOrNull()
                            val sPort = socksPortInput.toIntOrNull()

                            httpPortError = hPort == null || hPort !in 1024..65535
                            socksPortError = sPort == null || sPort !in 1024..65535

                            if (httpPortError || socksPortError) {
                                Toast.makeText(context, "Please fix port errors: range 1024-65535 required.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (hPort == sPort) {
                                Toast.makeText(context, "HTTP and SOCKS5 ports cannot be identical.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // Apply configuration state updates to ProxyManager
                            ProxyManager.httpPort.value = hPort!!
                            ProxyManager.socksPort.value = sPort!!
                            ProxyManager.username.value = usernameInput
                            ProxyManager.password.value = passwordInput

                            // Start background service with post notification request
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    ProxyService.startService(context)
                                }
                            } else {
                                ProxyService.startService(context)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("toggle_servers_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFEF4444) else ColorPrimary
                    ),
                    shape = CircleShape // Beautiful pill-shape button matching rounded-full
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Clear else Icons.Default.PlayArrow,
                        contentDescription = "Trigger icon",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "STOP PROXY SERVERS" else "START PROXY SERVERS",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // --- LOCAL IP ASSIGNMENTS CARD ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("ip_card"),
            colors = CardDefaults.cardColors(
                containerColor = ColorCardBg
            ),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = "IPs info", tint = ColorPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Detected Local IPs (Use on Windows)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = ColorTextDark
                        )
                    }
                    IconButton(onClick = { refreshAddresses() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh IPs", tint = ColorTextMuted)
                    }
                }

                Text(
                    text = "When USB tethering is active, your computer connects over a local virtual adapter. Use the IP matching 'rndis0', 'usb0' or resembling '192.168.42.x' / '192.168.49.x' as your proxy server host.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextMuted,
                    lineHeight = 16.sp
                )

                if (localIps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ColorBackground, RoundedCornerShape(12.dp))
                            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No local IPs detected. Enable Wi-Fi or USB tethering.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextMuted
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        localIps.forEach { (iface, ip) ->
                            val isTetherInterface = iface.contains("rndis", ignoreCase = true) || iface.contains("usb", ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isTetherInterface) ColorPrimary.copy(alpha = 0.08f) else ColorBackground)
                                    .border(
                                        width = 1.dp,
                                        color = if (isTetherInterface) ColorPrimary.copy(alpha = 0.3f) else ColorBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isTetherInterface) ColorPrimary else ColorTextMuted)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = ip,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            color = ColorTextDark,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (isTetherInterface) "$iface (Tether Active)" else iface,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isTetherInterface) ColorPrimary else ColorTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(ip))
                                        Toast.makeText(context, "$ip copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    CopyIcon(modifier = Modifier.size(16.dp), tint = ColorPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- PORTS & CONFIGURATION CARD ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("config_card"),
            colors = CardDefaults.cardColors(
                containerColor = ColorConfigBg
            ),
            shape = RoundedCornerShape(32.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorPrimary.copy(alpha = 0.08f)),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = "Config icon", tint = ColorPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Protocol Settings",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = ColorTextDark
                        )
                    }
                    
                    // High-contrast STABLE badge matching HTML design
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.6f))
                            .border(1.dp, ColorPrimary.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "STABLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            ),
                            color = ColorPrimary
                        )
                    }
                }

                if (isRunning) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFD97706).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Locked settings", tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Settings locked while proxy is running. Stop servers to adjust.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFB45309)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // HTTP PORT
                    OutlinedTextField(
                        value = httpPortInput,
                        onValueChange = {
                            if (!isRunning) {
                                httpPortInput = it
                                val parsed = it.toIntOrNull()
                                httpPortError = parsed == null || parsed !in 1024..65535
                                if (!httpPortError && parsed != null) {
                                    ProxyManager.httpPort.value = parsed
                                }
                            }
                        },
                        label = { Text("HTTP Proxy Port") },
                        modifier = Modifier.weight(1f).testTag("http_port"),
                        enabled = !isRunning,
                        isError = httpPortError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ColorTextDark,
                            unfocusedTextColor = ColorTextDark,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.5f),
                            disabledTextColor = ColorTextDark.copy(alpha = 0.6f),
                            focusedLabelColor = ColorPrimary,
                            unfocusedLabelColor = ColorTextMuted
                        )
                    )

                    // SOCKS5 PORT
                    OutlinedTextField(
                        value = socksPortInput,
                        onValueChange = {
                            if (!isRunning) {
                                socksPortInput = it
                                val parsed = it.toIntOrNull()
                                socksPortError = parsed == null || parsed !in 1024..65535
                                if (!socksPortError && parsed != null) {
                                    ProxyManager.socksPort.value = parsed
                                }
                            }
                        },
                        label = { Text("SOCKS5 Port") },
                        modifier = Modifier.weight(1f).testTag("socks_port"),
                        enabled = !isRunning,
                        isError = socksPortError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ColorTextDark,
                            unfocusedTextColor = ColorTextDark,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.5f),
                            disabledTextColor = ColorTextDark.copy(alpha = 0.6f),
                            focusedLabelColor = ColorPrimary,
                            unfocusedLabelColor = ColorTextMuted
                        )
                    )
                }

                // Authentication Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Proxy Authentication", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold), color = ColorTextDark)
                        Text(text = "Require password check on browser", style = MaterialTheme.typography.bodySmall, color = ColorTextMuted)
                    }
                    Switch(
                        checked = useAuthVal,
                        onCheckedChange = {
                            if (!isRunning) {
                                ProxyManager.useAuth.value = it
                            }
                        },
                        enabled = !isRunning,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorPrimary,
                            checkedTrackColor = ColorPrimary.copy(alpha = 0.3f),
                            uncheckedThumbColor = ColorTextMuted,
                            uncheckedTrackColor = ColorTextMuted.copy(alpha = 0.2f)
                        )
                    )
                }

                // Custom Credentials Fields (revealed if auth toggled)
                AnimatedVisibility(visible = useAuthVal) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = {
                                if (!isRunning) {
                                    usernameInput = it
                                    ProxyManager.username.value = it
                                }
                            },
                            label = { Text("Proxy Username") },
                            modifier = Modifier.fillMaxWidth().testTag("auth_username"),
                            enabled = !isRunning,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ColorTextDark,
                                unfocusedTextColor = ColorTextDark,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedLabelColor = ColorPrimary,
                                unfocusedLabelColor = ColorTextMuted
                            )
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                if (!isRunning) {
                                    passwordInput = it
                                    ProxyManager.password.value = it
                                }
                            },
                            label = { Text("Proxy Password") },
                            modifier = Modifier.fillMaxWidth().testTag("auth_password"),
                            enabled = !isRunning,
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.Clear else Icons.Default.Lock
                                val description = if (passwordVisible) "Hide password" else "Show password"
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = description, tint = ColorTextMuted)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ColorTextDark,
                                unfocusedTextColor = ColorTextDark,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedLabelColor = ColorPrimary,
                                unfocusedLabelColor = ColorTextMuted
                            )
                        )
                    }
                }
            }
        }

        // --- WINDOWS SETUP GUIDE CARD ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("guide_card"),
            colors = CardDefaults.cardColors(
                containerColor = ColorCardBg
            ),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            var expanded by remember { mutableStateOf(false) }

            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = "Guide logo", tint = ColorPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Windows Connection Guide",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = ColorTextDark
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand info",
                        tint = ColorTextMuted
                    )
                }

                if (expanded) {
                    HorizontalDivider(color = ColorBorder)

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Your PC bypasses cell VPNs by default during tethering. Using these proxy channels forces Windows traffic to go through the active Android VPN interface.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextMuted,
                            lineHeight = 16.sp
                        )

                        // Steps list
                        val targetIp = if (localIps.isNotEmpty()) {
                            val prioritized = localIps.find { it.first.contains("rndis") || it.first.contains("usb") }
                            prioritized?.second ?: localIps.first().second
                        } else {
                            "192.168.42.129"
                        }

                        StepItem(
                            stepNumber = "1",
                            title = "Connect USB & Enable Tethering",
                            body = "Plumb USB cable to Windows PC. Go to Android Settings -> Network & Internet -> Hotspot & tethering -> Enable 'USB Tethering'."
                        )

                        StepItem(
                            stepNumber = "2",
                            title = "Choose HTTP or SOCKS5",
                            body = "HTTP proxy is ideal for web browsers. SOCKS5 handles comprehensive TCP payloads with slightly lower overhead."
                        )

                        StepItem(
                            stepNumber = "3",
                            title = "Apply Settings in Windows",
                            body = "Open Windows Settings -> 'Network & Internet' -> 'Proxy' -> Toggle 'Use a proxy server'.\n• Address: $targetIp\n• Port: $httpPortVal (HTTP) or $socksPortVal (SOCKS5)"
                        )

                        // Copyable Powershell test command inside sleek tech box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A)) // Sleek terminal slate
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Test SOCKS5 proxy via PowerShell curl:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF94A3B8), // Slate 400
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val cmd = "curl -x socks5h://$targetIp:$socksPortVal https://www.google.com -I"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = cmd,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF38BDF8), // Radiant sky blue
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(cmd))
                                        Toast.makeText(context, "Command copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    CopyIcon(modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- ACTIVE CONNECTIONS LIST CARD ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("connections_card"),
            colors = CardDefaults.cardColors(
                containerColor = ColorCardBg
            ),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh, // custom elegant client group icon
                        contentDescription = "Clients icon",
                        tint = ColorPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Active Clients (${activeConnections.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = ColorTextDark
                    )
                }

                if (activeConnections.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .background(ColorBackground, RoundedCornerShape(12.dp))
                            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active connections yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextMuted
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeConnections.take(15).forEach { conn ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ColorBackground)
                                    .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = conn.targetHost,
                                        color = ColorTextDark,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (conn.protocol.startsWith("SOCKS")) ColorPrimary else Color(0xFF0369A1),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = conn.protocol, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "from ${conn.clientIp}", style = MaterialTheme.typography.bodySmall, color = ColorTextMuted, fontSize = 11.sp)
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "↑ ${formatBytes(conn.bytesUploaded)}",
                                        color = Color(0xFF0284C7),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "↓ ${formatBytes(conn.bytesDownloaded)}",
                                        color = ColorPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (activeConnections.size > 15) {
                            Text(
                                text = "And ${activeConnections.size - 15} more connections...",
                                color = ColorTextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // --- REAL-TIME TRAFFIC CONSOLE CARD ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("console_card"),
            colors = CardDefaults.cardColors(
                containerColor = ColorCardBg
            ),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) ColorPrimary else ColorTextMuted)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Traffic Stream Console",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = ColorTextDark
                        )
                    }

                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { ProxyManager.clearLogs() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear logs", tint = ColorTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Dedicated scrollable terminal view height-bounded (monolithic dark container for tech log contrast)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A)) // slate-900 crisp CLI
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    val lazyListState = rememberLazyListState()

                    LaunchedEffect(logs.size) {
                        if (logs.isNotEmpty()) {
                            lazyListState.animateScrollToItem(0)
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs, key = { it.id }) { logItem ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = formatTimestamp(logItem.timestamp),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B) // Slate 500
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = logItem.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (logItem.isError) Color(0xFFEF4444) else Color(0xFFE2E8F0).copy(alpha = 0.9f),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (logs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Terminal idle. Activate proxy and connect Windows clients.",
                                        color = Color(0xFF64748B).copy(alpha = 0.4f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepItem(stepNumber: String, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF005AC1)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, 
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold), 
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = body, 
                style = MaterialTheme.typography.bodySmall, 
                color = Color(0xFF64748B), 
                lineHeight = 15.sp
            )
        }
    }
}

private fun formatSpeedString(bytesPerSec: Long): String {
    val kb = bytesPerSec / 1024.0
    return if (kb >= 1024.0) {
        String.format("%.1f MB/s", kb / 1024.0)
    } else {
        String.format("%.1f KB/s", kb)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

private fun formatTimestamp(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
