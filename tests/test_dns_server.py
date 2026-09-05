import struct
import unittest
from unittest.mock import MagicMock, patch

from blocker.dns_server import (
    blocked_response,
    forward,
    question_end,
    read_qname,
    servfail_response,
)


class DnsServerTests(unittest.TestCase):
    def test_read_qname(self):
        # 3www 7example 3com 0
        raw = b"\x03www\x07example\x03com\x00"
        domain, offset = read_qname(raw, 0)
        self.assertEqual(domain, "www.example.com")
        self.assertEqual(offset, len(raw))

    def test_question_end_valid(self):
        # Header (12 bytes) + question
        header = struct.pack("!HHHHHH", 0x1234, 0x0100, 1, 0, 0, 0)
        qname = b"\x06google\x03com\x00"
        qtype_qclass = struct.pack("!HH", 1, 1)  # A record, IN class
        packet = header + qname + qtype_qclass

        domain, qtype, q_end = question_end(packet)
        self.assertEqual(domain, "google.com")
        self.assertEqual(qtype, 1)
        self.assertEqual(q_end, len(packet))

    def test_question_end_too_short(self):
        with self.assertRaises(ValueError):
            question_end(b"\x00" * 10)

    def test_blocked_response(self):
        header = struct.pack("!HHHHHH", 0xABCD, 0x0100, 1, 0, 0, 0)
        qname = b"\x03ads\x07example\x03com\x00"
        qtype_qclass = struct.pack("!HH", 1, 1)
        packet = header + qname + qtype_qclass

        resp = blocked_response(packet, len(packet))

        # Check transaction ID
        tx_id, flags, qdcount, ancount, nscount, arcount = struct.unpack("!HHHHHH", resp[:12])
        self.assertEqual(tx_id, 0xABCD)
        self.assertEqual(flags, 0x8183)  # Response, Recursion Available, NXDOMAIN
        self.assertEqual(qdcount, 1)
        self.assertEqual(ancount, 0)
        self.assertEqual(nscount, 0)
        self.assertEqual(arcount, 0)
        # Check question section preserved
        self.assertEqual(resp[12:], qname + qtype_qclass)

    def test_blocked_response_with_edns0_truncation(self):
        header = struct.pack("!HHHHHH", 0x1234, 0x0100, 1, 0, 0, 1)
        qname = b"\x07tracker\x03com\x00"
        qtype_qclass = struct.pack("!HH", 1, 1)
        opt_rr = b"\x00\x00\x29\x10\x00\x00\x00\x00\x00\x00\x00"  # EDNS0 OPT RR
        packet = header + qname + qtype_qclass + opt_rr
        q_end = 12 + len(qname) + len(qtype_qclass)

        resp = blocked_response(packet, q_end)
        self.assertEqual(len(resp), q_end)

    def test_servfail_response(self):
        header = struct.pack("!HHHHHH", 0x5678, 0x0100, 1, 0, 0, 0)
        qname = b"\x04test\x03com\x00"
        packet = header + qname + struct.pack("!HH", 1, 1)

        resp = servfail_response(packet, len(packet))
        _, flags, _, _, _, _ = struct.unpack("!HHHHHH", resp[:12])
        self.assertEqual(flags, 0x8182)  # Response, SERVFAIL

    @patch("socket.socket")
    def test_forward_sends_to_pair(self, mock_socket_cls):
        mock_socket = MagicMock()
        mock_socket_cls.return_value.__enter__.return_value = mock_socket
        mock_socket.recvfrom.return_value = (b"mock_dns_response", ("1.1.1.1", 53))

        query = b"\x12\x34query"
        res = forward(query)
        self.assertEqual(res, b"mock_dns_response")

        # Verify address sent to was a (host: str, port: int) 2-tuple
        call_args = mock_socket.sendto.call_args
        target_addr = call_args[0][1]
        self.assertIsInstance(target_addr, tuple)
        self.assertEqual(len(target_addr), 2)
        self.assertIsInstance(target_addr[0], str)
        self.assertIsInstance(target_addr[1], int)


if __name__ == "__main__":
    unittest.main()
