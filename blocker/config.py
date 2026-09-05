from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONFIG_DIR = ROOT / "config"
BLOCKLIST_FILE = CONFIG_DIR / "blocklist.txt"
WHITELIST_FILE = CONFIG_DIR / "whitelist.txt"
HOST = "127.0.0.1"
PORT = 5353
UPSTREAM_DNS = (1, 1, 1, 1)
DNS_TIMEOUT = 3.0
