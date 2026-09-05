# Architecture

```text
Application / Browser
        |
        v
 Local DNS proxy (127.0.0.1:5353)
        |
        +--> DomainFilter --> blocklist.txt / whitelist.txt
        |       |
        |       +--> BLOCK: NXDOMAIN response
        |
        +--> ALLOW: forward DNS query to upstream resolver
```

## Components

- `blocker/dns_server.py`: UDP DNS listener, request parsing, forwarding, and blocked responses.
- `blocker/blocklist.py`: normalizes domains and applies block/allow rules.
- `blocker/config.py`: paths, bind address, port, upstream DNS, and timeout.
- `blocker/logger.py`: in-memory request counters.
- `config/blocklist.txt`: starter domains.
- `config/whitelist.txt`: explicit exceptions.

## Safety boundary

The project operates at DNS/domain level. It does not perform TLS interception, packet payload inspection, credential collection, or traffic decryption.
