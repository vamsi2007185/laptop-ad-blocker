import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONFIG_DIR = ROOT / "config"
BLOCKLIST_FILE = CONFIG_DIR / "blocklist.txt"
WHITELIST_FILE = CONFIG_DIR / "whitelist.txt"
HOST = os.environ.get("DNS_HOST", "127.0.0.1")
PORT = int(os.environ.get("DNS_PORT", 5354))
UPSTREAM_DNS = os.environ.get("UPSTREAM_DNS", "1.1.1.1")
UPSTREAM_PORT = int(os.environ.get("UPSTREAM_PORT", 53))
DNS_TIMEOUT = float(os.environ.get("DNS_TIMEOUT", 3.0))
