import socket
import struct

from .blocklist import DomainFilter
from .config import (
    BLOCKLIST_FILE,
    DNS_TIMEOUT,
    HOST,
    PORT,
    UPSTREAM_DNS,
    UPSTREAM_PORT,
    WHITELIST_FILE,
)
from .logger import Stats


def read_qname(packet: bytes, offset: int) -> tuple[str, int]:
    labels = []
    while offset < len(packet):
        length = packet[offset]
        offset += 1
        if length == 0:
            break
        if length & 0xC0:
            # Compression in a question name is unusual; stop safely.
            offset += 1
            break
        if offset + length > len(packet):
            raise ValueError("Invalid DNS packet")
        labels.append(packet[offset:offset + length].decode("ascii", errors="ignore"))
        offset += length
    return ".".join(labels), offset


def question_end(packet: bytes) -> tuple[str, int, int]:
    if len(packet) < 12:
        raise ValueError("Invalid DNS packet: header too short")
    domain, offset = read_qname(packet, 12)
    if offset + 4 > len(packet):
        raise ValueError("Incomplete DNS question")
    qtype, qclass = struct.unpack("!HH", packet[offset:offset + 4])
    return domain, qtype, offset + 4


def blocked_response(query: bytes, q_end: int | None = None) -> bytes:
    # NXDOMAIN: preserve ID, set response + recursion available, return one question.
    flags = 0x8183
    q_bytes = query[12:q_end] if q_end is not None else query[12:]
    return query[:2] + struct.pack("!H", flags) + query[4:6] + b"\x00\x00\x00\x00\x00\x00" + q_bytes


def servfail_response(query: bytes, q_end: int | None = None) -> bytes:
    # SERVFAIL: preserve ID, set response + recursion available, return one question.
    flags = 0x8182
    q_bytes = query[12:q_end] if q_end is not None else query[12:]
    return query[:2] + struct.pack("!H", flags) + query[4:6] + b"\x00\x00\x00\x00\x00\x00" + q_bytes


def forward(query: bytes) -> bytes:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as upstream:
        upstream.settimeout(DNS_TIMEOUT)
        upstream.sendto(query, (UPSTREAM_DNS, UPSTREAM_PORT))
        response, _ = upstream.recvfrom(4096)
        return response


def run() -> None:
    domain_filter = DomainFilter(BLOCKLIST_FILE, WHITELIST_FILE)
    stats = Stats()
    server = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    server.bind((HOST, PORT))
    print(f"Laptop Ad Blocker DNS server listening on {HOST}:{PORT}", flush=True)
    print(f"Forwarding to upstream DNS: {UPSTREAM_DNS}:{UPSTREAM_PORT}", flush=True)
    print(f"Blocklist: {BLOCKLIST_FILE}", flush=True)

    try:
        while True:
            packet, client = server.recvfrom(4096)
            try:
                domain, qtype, q_end = question_end(packet)
                blocked = domain_filter.is_blocked(domain)
                stats.record(blocked)
                if blocked:
                    print(f"BLOCK  {domain}", flush=True)
                    response = blocked_response(packet, q_end)
                    server.sendto(response, client)
                else:
                    print(f"ALLOW  {domain} (type={qtype})", flush=True)
                    try:
                        response = forward(packet)
                        server.sendto(response, client)
                    except (TimeoutError, socket.timeout) as exc:
                        print(f"Upstream timeout for {domain}: {exc}", flush=True)
                        server.sendto(servfail_response(packet, q_end), client)
            except (ValueError, OSError) as exc:
                print(f"Request error: {exc}", flush=True)
            except Exception as exc:
                print(f"Unexpected request error: {exc}", flush=True)
    except KeyboardInterrupt:
        print("\nStopping DNS server.", flush=True)
        print(stats.summary(), flush=True)
    finally:
        server.close()


if __name__ == "__main__":
    run()
