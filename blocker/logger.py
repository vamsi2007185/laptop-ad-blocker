from dataclasses import dataclass
from datetime import datetime, timezone


@dataclass
class Stats:
    total: int = 0
    blocked: int = 0
    allowed: int = 0

    def record(self, blocked: bool) -> None:
        self.total += 1
        if blocked:
            self.blocked += 1
        else:
            self.allowed += 1

    def summary(self) -> str:
        now = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
        return f"[{now}] total={self.total} blocked={self.blocked} allowed={self.allowed}"
