import tempfile
import unittest
from pathlib import Path

from blocker.blocklist import DomainFilter


class DomainFilterTests(unittest.TestCase):
    def make_filter(self, blocked: str, allowed: str = "") -> DomainFilter:
        directory = Path(tempfile.mkdtemp())
        block = directory / "blocklist.txt"
        allow = directory / "whitelist.txt"
        block.write_text(blocked, encoding="utf-8")
        allow.write_text(allowed, encoding="utf-8")
        return DomainFilter(block, allow)

    def test_exact_domain_is_blocked(self):
        f = self.make_filter("ads.example.com\n")
        self.assertTrue(f.is_blocked("ads.example.com"))

    def test_subdomain_is_blocked(self):
        f = self.make_filter("example.com\n")
        self.assertTrue(f.is_blocked("cdn.example.com"))

    def test_whitelist_overrides_parent_block(self):
        f = self.make_filter("example.com\n", "safe.example.com\n")
        self.assertFalse(f.is_blocked("safe.example.com"))
        self.assertTrue(f.is_blocked("ads.example.com"))

    def test_unknown_domain_is_allowed(self):
        f = self.make_filter("ads.example.com\n")
        self.assertFalse(f.is_blocked("openai.com"))

    def test_inline_comments_stripped(self):
        f = self.make_filter("0.0.0.0 badtracker.com # track user ads\n")
        self.assertTrue(f.is_blocked("badtracker.com"))
        self.assertFalse(f.is_blocked("track"))

    def test_hosts_file_syntax(self):
        f = self.make_filter("127.0.0.1 telemetry.example.com\n")
        self.assertTrue(f.is_blocked("telemetry.example.com"))


if __name__ == "__main__":
    unittest.main()
