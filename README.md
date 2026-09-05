# Laptop Ad Blocker

A lightweight DNS-based ad and tracker blocker for a Windows laptop.

## Features

- Local DNS proxy on `127.0.0.1:5353`
- Domain-based blocking
- Separate blocklist and whitelist
- Runtime statistics
- Simple logging
- No HTTPS interception
- Standard-library Python implementation

## Project structure

```text
laptop-ad-blocker/
├── blocker/
│   ├── __init__.py
│   ├── config.py
│   ├── blocklist.py
│   ├── logger.py
│   └── dns_server.py
├── config/
│   ├── blocklist.txt
│   └── whitelist.txt
├── tests/
│   └── test_blocklist.py
├── requirements.txt
├── .gitignore
└── README.md
```

## Requirements

Python 3.10+ on Windows, macOS, or Linux. The first version uses only Python's standard library, so no third-party package is required.

## Run

From the repository root:

```bash
python -m blocker.dns_server
```

The DNS server listens on `127.0.0.1:5353` by default. This version is intentionally not a privileged system-wide DNS installer. Test it locally first, then configure your operating system DNS forwarding setup if you want system-wide filtering.

## Important limitation

This project blocks DNS lookups for known ad/tracker domains. It does not inspect or decrypt HTTPS traffic and cannot remove every ad that is served from the same domain as normal content.

## Tests

```bash
python -m unittest discover -s tests -v
```

## License

MIT