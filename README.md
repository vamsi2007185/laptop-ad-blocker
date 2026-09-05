# Laptop Ad Blocker

A lightweight DNS-based ad and tracker blocker for a Windows laptop.

## Features

- Local DNS proxy for laptops on `127.0.0.1:5354` (configurable via `DNS_PORT`)
- Native Android app using zero-root local `VpnService` DNS sinkhole
- Domain-based hierarchical ad & tracker blocking
- Separate blocklist and whitelist
- Real-time statistics and query logs
- No HTTPS interception or external traffic tunneling
- Pure standard-library Python core & modern Kotlin Jetpack Compose Android app

## Project structure

```text
laptop-ad-blocker/
├── android/                         # Android Ad Blocker application
│   ├── app/                         # Native Kotlin & Jetpack Compose app
│   │   ├── src/main/java/           # VpnService, DNS packet parser & UI
│   │   └── src/test/java/           # Unit tests
│   ├── build.gradle.kts
│   └── README.md                    # Android setup and build instructions
├── blocker/                         # Laptop Python DNS proxy
│   ├── __init__.py
│   ├── config.py
│   ├── blocklist.py
│   ├── logger.py
│   └── dns_server.py
├── config/                          # Shared domain rule lists
│   ├── blocklist.txt
│   └── whitelist.txt
├── tests/                           # Python unit tests
│   ├── test_blocklist.py
│   └── test_dns_server.py
├── requirements.txt
├── run_blocker.bat                  # One-click Windows launcher
├── .gitignore
└── README.md
```

## Android App

See the dedicated [**Android README**](android/README.md) for architecture, building with Android Studio/Gradle, and Termux terminal instructions.

## Requirements

Python 3.10+ on Windows, macOS, or Linux. The first version uses only Python's standard library, so no third-party package is required.

## Run

From the repository root:

```bash
python -m blocker.dns_server
```

The DNS server listens on `127.0.0.1:5354` by default (port 5354 avoids port conflicts with OS mDNS services on port 5353). You can configure `DNS_HOST`, `DNS_PORT`, `UPSTREAM_DNS`, and `DNS_TIMEOUT` via environment variables. Test it locally first, then configure your operating system DNS forwarding setup if you want system-wide filtering.

## Important limitation

This project blocks DNS lookups for known ad/tracker domains. It does not inspect or decrypt HTTPS traffic and cannot remove every ad that is served from the same domain as normal content.

## Tests

```bash
python -m unittest discover -s tests -v
```

## License

MIT