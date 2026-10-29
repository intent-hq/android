#!/usr/bin/env python3
"""Fail when the managed-device run did not export every smoke screenshot."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parent.parent
output = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "app/build/outputs/managed_device_android_test_additional_output"
screens = ("pairing", "paired-workspaces", "workspaces", "note-tasks", "comments", "conversation")
for screen in screens:
    for theme in ("light", "dark"):
        name = f"{screen}-{theme}.png"
        files = list(output.rglob(name))
        if not files or any(file.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n" for file in files):
            raise SystemExit(f"Missing or invalid UI screenshot: {name}")
print("All 12 light/dark smoke screenshots were exported")
