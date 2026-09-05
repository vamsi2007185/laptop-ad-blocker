import socket
import struct

from .blocklist import DomainFilter
from .config import BLOCKLIST_FILE, DNS_TIMEOUT, HOST, PORT, UPSTREAM_DNS, WHITELIST_FILE
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
    domain, offset = read_qname(packet, 12)
    if offset + 4 > len(packet):
        raise ValueError("Incomplete DNS question")
    qtype, qclass = struct.unpack("!HH", packet[offset:offset + 4])
    return domain, qtype, offset + 4


def blocked_response(query: bytes) -> bytes:
    # NXDOMAIN: preserve ID, set response + recursion available, return one question.
    flags = 0x8183
    return query[:2] + struct.pack("!H", flags) + query[4:6] + b"\x00\x00\x00\x00\x00\x00" + query[12:]


def forward(query: bytes) -> bytes:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as upstream:
        upstream.settimeout(DNS_TIMEOUT)
        upstream.sendto(query, UPSTREAM_DNS + (53,))
        response, _ = upstream.recvfrom(4096)
        return response


def run() -> None:
    domain_filter = DomainFilter(BLOCKLIST_FILE, WHITELIST_FILE)
    stats = Stats()
    server = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    server.bind((HOST, PORT))
    print(f"Laptop Ad Blocker DNS server listening on {HOST}:{PORT}")
    print(f"Blocklist: {BLOCKLIST_FILE}")

    try:
        while True:
            packet, client = server.recvfrom(4096)
            try:
                domain, qtype, _ = question_end(packet)
                blocked = domain_filter.is_blocked(domain)
                stats.record(blocked)
                if blocked:
                    print(f"BLOCK  {domain}")
                    response = blocked_response(packet)
                else:
                    print(f"ALLOW  {domain} (type={qtype})")
                    response = forward(packet)
                server.sendto(response, client)
            except (ValueError, OSError) as exc:
                print(f"Request error: {exc}")
            except Exception as exc:
                print(f"Unexpected request error: {exc}")
    except KeyboardInterrupt:
        print("\nStopping DNS server.")
        print(stats.summary())
    finally:
        server.close()


if __name__ == "__main__":
    run()
