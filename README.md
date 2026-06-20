# ProxyShare 🌐🔒

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Compose-M3-green.svg?style=flat&logo=jetpack%20compose)](https://developer.android.com/jetpack/compose)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://www.android.com)
[![Build Tool](https://img.shields.io/badge/Build-Gradle-02303A.svg?style=flat&logo=gradle)](https://gradle.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**ProxyShare** is a high-performance, lightweight Android application that enables you to share your VPN or local internet connection with other devices (PCs, laptops, game consoles, or other phones). It works over **USB Tethering**, **Wi-Fi Hotspot**, or **Bluetooth Tethering** by running secure, local **HTTP and SOCKS5 proxy servers** directly on your Android device.

By routing traffic through this app, you bypass standard mobile hotspot limitations and carrier tethering blocks, sharing your native or VPN-encrypted tunnel seamlessly.

---

## ✨ Features

*   **Dual-Protocol Engine:** Run a high-throughput **HTTP Proxy** (default port `8080`) and a **SOCKS5 Proxy** (default port `1080`) simultaneously.
*   **Authentication Shield:** Optional username and password credential enforcement to keep unauthorized clients off your shared servers.
*   **Real-time Traffic Monitor:** Dynamic speedometer tracking live upload and download speeds, along with cumulative bandwidth counters.
*   **Active Connections Inspector:** Live monitoring pane showing connected client IP addresses, target host destinations, and transferred bandwidth.
*   **Sliding Diagnostic Logs:** Interactive terminal console holding the last 100 system events, client handshakes, proxy pipeline outcomes, and error reports.
*   **Polished Material 3 UI:** Fluid animations, responsive states, clean layout dividers, clipboard copy-to-click controls, and a gorgeous dark-accented visual theme.
*   **Extensive Unit/Screenshot Tests:** Fully integrated local JVM test suite leveraging **Robolectric** and **Roborazzi** for robust, emulator-free visual verification.

---

## 📂 Project Structure

```text
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt        # Primary Main/Dashboard UI layout
│   │   │   │   ├── ProxyService.kt        # Background life-cycle service
│   │   │   │   ├── proxy/
│   │   │   │   │   ├── HttpProxyServer.kt  # High-performance HTTP tunneling server
│   │   │   │   │   ├── Socks5ProxyServer.kt# Multithreaded SOCKS5 proxy server
│   │   │   │   │   ├── ProxyManager.kt     # Shared state, stats, speed calculations
│   │   │   │   │   └── Models.kt           # Data and payload models
│   │   │   ├── res/                        # Graphics, layouts, and XML resources
│   │   └── test/                           # Robolectric unit and UI screenshot tests
```

---

## 📱 How to Use (Sharing your VPN connection)

### Step 1: Establish Physical Connection
Connect your client device (e.g., Windows/macOS PC) to your Android device using one of the following methods:
*   **USB Tethering:** Enable *USB Tethering* in Android Settings.
*   **Wi-Fi Hotspot:** Turn on your phone's *Personal Hotspot* and connect the client laptop to your Wi-Fi network.

### Step 2: Configure and Start the Proxy
1. Launch the **ProxyShare** app on your phone.
2. If desired, configure custom port values for HTTP (`8080`) and SOCKS5 (`1080`).
3. (Optional) Turn on **Require Auth** toggle and set a custom username & password.
4. Tap **Start Servers**.

### Step 3: Find your Host Server IP
The app dashboard displays your active network interface IPs. Usually:
*   For **USB Tethering**: Typically `192.168.42.129` or similar IP assigned to your phone's tethering interface.
*   For **Wi-Fi Hotspot**: Typically `192.168.43.1` or `192.168.49.1`.

### Step 4: Configure Client Proxy Settings
Configure your client device (Windows, Mac, or web browsers) to route internet traffic through your phone's server.

#### For HTTP Proxy:
*   **Host/Proxy IP Address:** (Your phone interface IP, e.g., `192.168.43.1`)
*   **Port:** `8080` (or your custom HTTP port)

#### For SOCKS5 Proxy (Best for games or full-system UDP):
*   **SOCKS Host/Server:** (Your phone interface IP, e.g., `192.168.43.1`)
*   **Port:** `1080` (or your custom SOCKS5 port)
*   **Protocol:** SOCKS v5

*If you enforced authentication, input your configured Username and Password on your client client prompts.*

---

## 📝 License

Distributed under the MIT License. See `LICENSE` or contact for information.
