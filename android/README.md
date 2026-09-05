# Android Ad Blocker

A lightweight, zero-root, privacy-focused DNS ad and tracker blocker for Android devices.

## How it works

On Android, applications cannot modify system hosts files without root access. Instead, this app utilizes Android's native `VpnService` API to establish a local, on-device virtual network interface (`tun0`) that intercepts only DNS queries (UDP port 53).

```text
Android Applications / Browsers
              |
              v (DNS queries on port 53)
     Local TUN Interface (10.0.0.2:53)
              |
              v
       DomainFilter (Blocklist / Whitelist)
         /          \
  [BLOCKED]        [ALLOWED]
       |                |
  Craft local      Forward query to upstream
   NXDOMAIN         DNS (1.1.1.1 / 8.8.8.8)
   response             |
       \          /
        v        v
   Return response to app
```

> **100% On-Device & Private**: This is a local DNS proxy. No web browsing traffic, credentials, or personal data are routed to any external VPN server.

---

## Features

- **Zero Root Required**: Operates via Android's standard `VpnService` API.
- **System-Wide Filtering**: Blocks ads and trackers across all apps and browsers.
- **Hierarchical Domain Matching**: Blocking `example.com` automatically blocks all subdomains (e.g. `ads.example.com`).
- **Whitelist Priority**: Whitelisted domains always take precedence over blocklist rules.
- **Custom Domain Rules**: Add your own custom domains to the blocklist or whitelist directly in the UI.
- **Selectable Upstream Resolvers**: Choose between Cloudflare (`1.1.1.1`), Google (`8.8.8.8`), Quad9 (`9.9.9.9`), or AdGuard DNS (`94.140.14.14`).
- **Live Statistics & Query Log**: Real-time counter of blocked, allowed, and total DNS requests, plus a live log of recent domains.
- **Battery Efficient**: Only intercepts 12-byte UDP DNS headers. Regular network payload packets bypass the filtering loop.

---

## Project Structure

```text
android/
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/example/androidadblocker/
│   │   │   │   ├── MainActivity.kt           # App UI entrypoint & VPN permissions
│   │   │   │   ├── dns/
│   │   │   │   │   ├── DnsPacket.kt          # IP/UDP/DNS packet parser & NXDOMAIN generator
│   │   │   │   │   └── DnsResolver.kt        # Upstream DNS socket forwarder
│   │   │   │   ├── filter/
│   │   │   │   │   └── DomainFilter.kt       # Domain matching & normalization logic
│   │   │   │   ├── model/
│   │   │   │   │   └── BlockerStats.kt       # Live stats and query logs
│   │   │   │   ├── service/
│   │   │   │   │   └── AdBlockerVpnService.kt# Background VpnService & TUN packet loop
│   │   │   │   └── ui/main/
│   │   │   │       └── MainScreen.kt         # Jetpack Compose Dashboard UI
│   │   │   └── res/raw/
│   │   │       ├── default_blocklist.txt     # Bundled starter ad & tracker domains
│   │   │       └── default_whitelist.txt     # Bundled starter allowed exceptions
│   │   └── test/java/com/example/androidadblocker/
│   │       ├── DomainFilterTest.kt           # Filter & subdomain unit tests
│   │       └── DnsPacketTest.kt              # DNS packet parser & checksum unit tests
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── README.md
```

---

## Building and Installing

### Option 1: Using Android Studio (Recommended)
1. Open Android Studio.
2. Select **Open** and choose the `android/` directory inside this repository.
3. Allow Gradle to sync dependencies.
4. Connect an Android device (via USB debugging) or start an Android emulator.
5. Click the green **Run (▶)** button or press `Shift + F10`.

### Option 2: Using the Command Line
From the `android/` directory:

```bash
# Build the debug APK
./gradlew assembleDebug

# Install directly to a connected device
./gradlew installDebug
```

The compiled APK will be located at:
```text
android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Alternative: Running the Python Blocker on Android via Termux

If you have a terminal environment like [Termux](https://termux.dev/) installed on your Android phone, you can also run the core Python DNS server directly:

1. In Termux, install Python and Git:
   ```bash
   pkg update && pkg install python git
   ```
2. Clone this repository:
   ```bash
   git clone https://github.com/vamsi2007185/laptop-ad-blocker.git
   cd laptop-ad-blocker
   ```
3. Run the blocker:
   ```bash
   python -m blocker.dns_server
   ```
