#!/usr/bin/env python3
"""Validate exact smoke selectors and print AndroidJUnitRunner's class argument."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
selectors = [line.strip() for line in (root / "scripts/ui-smoke-tests.txt").read_text().splitlines()
             if line.strip() and not line.lstrip().startswith("#")]
if not selectors or len(selectors) != len(set(selectors)):
    raise SystemExit("Smoke selectors must be nonempty and unique")
for selector in selectors:
    if not re.fullmatch(r"[\w.]+#[A-Za-z]\w*", selector):
        raise SystemExit(f"Invalid smoke selector: {selector}")
    classname, method = selector.split("#")
    source = root / "app/src/androidTest/java" / (classname.replace(".", "/") + ".kt")
    if not source.exists() or not re.search(r"@Test\s+fun\s+" + re.escape(method) + r"\s*\(", source.read_text()):
        raise SystemExit(f"Missing smoke test: {selector}")
print(",".join(selectors))
