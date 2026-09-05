from pathlib import Path


def load_domains(path: Path) -> set[str]:
    domains: set[str] = set()
    if not path.exists():
        return domains
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip().lower()
        if not line or line.startswith("#"):
            continue
        # Accept plain domains and simple hosts-file style entries.
        parts = line.split()
        domain = parts[-1].lstrip(".") if parts else ""
        if domain and domain not in {"localhost", "localhost.localdomain", "broadcasthost"}:
            domains.add(domain.rstrip("."))
    return domains


class DomainFilter:
    def __init__(self, blocklist_path: Path, whitelist_path: Path):
        self.blocked = load_domains(blocklist_path)
        self.allowed = load_domains(whitelist_path)

    @staticmethod
    def normalize(domain: str) -> str:
        return domain.strip().lower().rstrip(".")

    def is_blocked(self, domain: str) -> bool:
        domain = self.normalize(domain)
        if not domain:
            return False
        labels = domain.split(".")
        for i in range(len(labels)):
            candidate = ".".join(labels[i:])
            if candidate in self.allowed:
                return False
            if candidate in self.blocked:
                return True
        return False
