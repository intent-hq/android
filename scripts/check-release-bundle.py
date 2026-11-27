#!/usr/bin/env python3
"""Fail when a release bundle lacks Tailcat or has native code that Google Play rejects."""
from pathlib import Path
import struct
import sys
import zipfile

root = Path(__file__).resolve().parent.parent
bundle = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "app/build/outputs/bundle/release/app-release.aab"
REQUIRED_ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64")
PAGE_SIZE = 16 * 1024
PT_LOAD = 1


def load_alignments(elf: bytes) -> list[int]:
    if elf[:4] != b"\x7fELF" or elf[4] != 2:
        raise ValueError("not a 64-bit ELF file")
    endian = "<" if elf[5] == 1 else ">"
    phoff, = struct.unpack_from(endian + "Q", elf, 0x20)
    phentsize, phnum = struct.unpack_from(endian + "HH", elf, 0x36)
    alignments = []
    for index in range(phnum):
        offset = phoff + index * phentsize
        kind, = struct.unpack_from(endian + "I", elf, offset)
        if kind == PT_LOAD:
            alignments.append(struct.unpack_from(endian + "Q", elf, offset + 0x30)[0])
    return alignments


errors = []
with zipfile.ZipFile(bundle) as archive:
    libraries = [name for name in archive.namelist() if name.startswith("base/lib/") and name.endswith(".so")]
    for abi in REQUIRED_ABIS:
        if f"base/lib/{abi}/libgojni.so" not in libraries:
            errors.append(f"Tailcat library is missing for {abi}")
    for name in sorted(libraries):
        if name.split("/")[2] not in ("arm64-v8a", "x86_64"):
            continue
        alignments = load_alignments(archive.read(name))
        if not alignments or min(alignments) < PAGE_SIZE:
            errors.append(f"{name} LOAD segments are not 16 KB aligned: {alignments}")

if errors:
    raise SystemExit("\n".join([f"{bundle} is not ready for Google Play:"] + errors))
print(f"{bundle.name}: Tailcat present for {', '.join(REQUIRED_ABIS)}; 64-bit libraries are 16 KB aligned")
