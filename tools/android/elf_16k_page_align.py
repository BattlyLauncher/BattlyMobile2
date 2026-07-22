#!/usr/bin/env python3
"""Patch or verify Android ELF shared libraries for 16 KB page-size compatibility.

For already-congruent binaries this only raises PT_LOAD ``p_align``. For older
prebuilt libraries it can also insert file padding between load segments and
rewrite every affected program/section offset. Virtual addresses and segment
contents stay unchanged, so relocations and the runtime ABI are preserved.
"""

from __future__ import annotations

import argparse
import os
import shutil
import struct
import sys
import zipfile
from pathlib import Path

PT_LOAD = 1
PAGE_SIZE_16K = 0x4000


class ElfError(Exception):
    pass


def iter_so_files(paths: list[Path], allow_missing: bool) -> list[Path]:
    files: list[Path] = []
    for path in paths:
        if not path.exists():
            if allow_missing:
                continue
            raise FileNotFoundError(path)
        if path.is_file():
            if path.suffix == ".so":
                files.append(path)
            continue
        for root, _, names in os.walk(path):
            for name in names:
                if name.endswith(".so"):
                    files.append(Path(root, name))
    return files


def prune_abis(paths: list[Path], keep_abis: set[str], allow_missing: bool) -> None:
    if not keep_abis:
        return
    for path in paths:
        if not path.exists():
            if allow_missing:
                continue
            raise FileNotFoundError(path)
        for root, dirs, _ in os.walk(path):
            for dirname in list(dirs):
                if dirname in {"armeabi-v7a", "arm64-v8a", "x86", "x86_64"} and dirname not in keep_abis:
                    shutil.rmtree(Path(root, dirname))
                    dirs.remove(dirname)
                    print(f"removed ABI {dirname}: {Path(root, dirname)}")


def parse_elf_header(data: bytes) -> tuple[str, str, int, int, int, int, int, int]:
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise ElfError("not an ELF file")

    elf_class = data[4]
    endian_byte = data[5]
    if elf_class not in (1, 2):
        raise ElfError(f"unsupported ELF class {elf_class}")
    if endian_byte == 1:
        endian = "<"
    elif endian_byte == 2:
        endian = ">"
    else:
        raise ElfError(f"unsupported ELF endian {endian_byte}")

    if elf_class == 1:
        e_phoff = struct.unpack_from(endian + "I", data, 28)[0]
        e_phentsize = struct.unpack_from(endian + "H", data, 42)[0]
        e_phnum = struct.unpack_from(endian + "H", data, 44)[0]
        e_shoff = struct.unpack_from(endian + "I", data, 32)[0]
        e_shentsize = struct.unpack_from(endian + "H", data, 46)[0]
        e_shnum = struct.unpack_from(endian + "H", data, 48)[0]
        return "elf32", endian, e_phoff, e_phentsize, e_phnum, e_shoff, e_shentsize, e_shnum

    e_phoff = struct.unpack_from(endian + "Q", data, 32)[0]
    e_phentsize = struct.unpack_from(endian + "H", data, 54)[0]
    e_phnum = struct.unpack_from(endian + "H", data, 56)[0]
    e_shoff = struct.unpack_from(endian + "Q", data, 40)[0]
    e_shentsize = struct.unpack_from(endian + "H", data, 58)[0]
    e_shnum = struct.unpack_from(endian + "H", data, 60)[0]
    return "elf64", endian, e_phoff, e_phentsize, e_phnum, e_shoff, e_shentsize, e_shnum


def offset_delta(offset: int, padding: list[tuple[int, int]]) -> int:
    return sum(amount for position, amount in padding if position <= offset)


def repack_load_segments(data: bytearray) -> tuple[bytearray, list[str]]:
    (
        elf_kind,
        endian,
        e_phoff,
        e_phentsize,
        e_phnum,
        e_shoff,
        e_shentsize,
        e_shnum,
    ) = parse_elf_header(data)
    is_32 = elf_kind == "elf32"
    offset_format = endian + ("I" if is_32 else "Q")
    offset_size = 4 if is_32 else 8

    program_headers: list[dict[str, int]] = []
    load_segments: list[dict[str, int]] = []
    for index in range(e_phnum):
        header_offset = e_phoff + index * e_phentsize
        if header_offset + e_phentsize > len(data):
            raise ElfError("program header table points outside file")
        p_type = struct.unpack_from(endian + "I", data, header_offset)[0]
        if is_32:
            p_offset = struct.unpack_from(endian + "I", data, header_offset + 4)[0]
            p_vaddr = struct.unpack_from(endian + "I", data, header_offset + 8)[0]
            p_filesz = struct.unpack_from(endian + "I", data, header_offset + 16)[0]
            p_offset_field = header_offset + 4
            p_align_field = header_offset + 28
        else:
            p_offset = struct.unpack_from(endian + "Q", data, header_offset + 8)[0]
            p_vaddr = struct.unpack_from(endian + "Q", data, header_offset + 16)[0]
            p_filesz = struct.unpack_from(endian + "Q", data, header_offset + 32)[0]
            p_offset_field = header_offset + 8
            p_align_field = header_offset + 48
        header = {
            "index": index,
            "type": p_type,
            "offset": p_offset,
            "vaddr": p_vaddr,
            "filesz": p_filesz,
            "offset_field": p_offset_field,
            "align_field": p_align_field,
        }
        program_headers.append(header)
        if p_type == PT_LOAD:
            load_segments.append(header)

    load_segments.sort(key=lambda header: header["offset"])
    previous_end = 0
    padding: list[tuple[int, int]] = []
    cumulative = 0
    notes: list[str] = []
    handled_offsets: set[int] = set()
    for segment in load_segments:
        original_offset = segment["offset"]
        if original_offset < previous_end and original_offset != 0:
            raise ElfError(
                f"PT_LOAD[{segment['index']}] overlaps the previous file segment; cannot repack safely"
            )
        previous_end = max(previous_end, original_offset + segment["filesz"])
        if original_offset in handled_offsets:
            continue
        handled_offsets.add(original_offset)
        current_offset = original_offset + cumulative
        desired_remainder = segment["vaddr"] % PAGE_SIZE_16K
        amount = (desired_remainder - current_offset) % PAGE_SIZE_16K
        if amount:
            if original_offset == 0:
                raise ElfError("first PT_LOAD cannot be padded before the ELF header")
            padding.append((original_offset, amount))
            cumulative += amount
            notes.append(
                f"inserted 0x{amount:x} bytes before PT_LOAD file offset 0x{original_offset:x}"
            )

    if not padding:
        return data, notes

    rebuilt = bytearray()
    cursor = 0
    for position, amount in padding:
        rebuilt.extend(data[cursor:position])
        rebuilt.extend(b"\0" * amount)
        cursor = position
    rebuilt.extend(data[cursor:])

    # Program headers live in the first PT_LOAD, before any inserted padding.
    for header in program_headers:
        new_offset = header["offset"] + offset_delta(header["offset"], padding)
        struct.pack_into(offset_format, rebuilt, header["offset_field"], new_offset)

    new_shoff = e_shoff + offset_delta(e_shoff, padding) if e_shoff else 0
    if is_32:
        struct.pack_into(endian + "I", rebuilt, 32, new_shoff)
        section_offset_field_delta = 16
    else:
        struct.pack_into(endian + "Q", rebuilt, 40, new_shoff)
        section_offset_field_delta = 24

    if e_shoff and e_shentsize and e_shnum:
        for index in range(e_shnum):
            old_header_offset = e_shoff + index * e_shentsize
            new_header_offset = new_shoff + index * e_shentsize
            if old_header_offset + e_shentsize > len(data):
                raise ElfError("section header table points outside file")
            old_section_offset = struct.unpack_from(
                offset_format, data, old_header_offset + section_offset_field_delta
            )[0]
            if old_section_offset:
                new_section_offset = old_section_offset + offset_delta(old_section_offset, padding)
                struct.pack_into(
                    offset_format,
                    rebuilt,
                    new_header_offset + section_offset_field_delta,
                    new_section_offset,
                )

    # Guard against a malformed rewrite before the result ever reaches packaging.
    for header in load_segments:
        new_offset = header["offset"] + offset_delta(header["offset"], padding)
        if (new_offset - header["vaddr"]) % PAGE_SIZE_16K != 0:
            raise ElfError(f"PT_LOAD[{header['index']}] is still not 16KB congruent after repack")
    return rebuilt, notes


def patch_elf(path: Path, fix: bool) -> tuple[bool, list[str]]:
    data = bytearray(path.read_bytes())
    if fix:
        data, repack_notes = repack_load_segments(data)
    else:
        repack_notes = []
    elf_kind, endian, e_phoff, e_phentsize, e_phnum, _, _, _ = parse_elf_header(data)
    changed = False
    notes: list[str] = list(repack_notes)

    for index in range(e_phnum):
        phoff = e_phoff + index * e_phentsize
        if phoff + e_phentsize > len(data):
            raise ElfError("program header table points outside file")

        if elf_kind == "elf32":
            p_type = struct.unpack_from(endian + "I", data, phoff)[0]
            if p_type != PT_LOAD:
                continue
            p_offset = struct.unpack_from(endian + "I", data, phoff + 4)[0]
            p_vaddr = struct.unpack_from(endian + "I", data, phoff + 8)[0]
            p_align_offset = phoff + 28
            p_align = struct.unpack_from(endian + "I", data, p_align_offset)[0]
            pack_format = endian + "I"
        else:
            p_type = struct.unpack_from(endian + "I", data, phoff)[0]
            if p_type != PT_LOAD:
                continue
            p_offset = struct.unpack_from(endian + "Q", data, phoff + 8)[0]
            p_vaddr = struct.unpack_from(endian + "Q", data, phoff + 16)[0]
            p_align_offset = phoff + 48
            p_align = struct.unpack_from(endian + "Q", data, p_align_offset)[0]
            pack_format = endian + "Q"

        if (p_offset - p_vaddr) % PAGE_SIZE_16K != 0:
            raise ElfError(
                f"PT_LOAD[{index}] offset/vaddr are not 16KB congruent "
                f"(offset=0x{p_offset:x}, vaddr=0x{p_vaddr:x})"
            )
        if p_align < PAGE_SIZE_16K:
            notes.append(f"PT_LOAD[{index}] p_align 0x{p_align:x} -> 0x{PAGE_SIZE_16K:x}")
            if fix:
                struct.pack_into(pack_format, data, p_align_offset, PAGE_SIZE_16K)
                changed = True

    if repack_notes:
        changed = True
    if changed:
        path.write_bytes(data)
    return changed, notes


def verify_zip(path: Path) -> int:
    failures = 0
    with zipfile.ZipFile(path, "r") as archive:
        for name in archive.namelist():
            if not name.endswith(".so"):
                continue
            data = archive.read(name)
            try:
                elf_kind, endian, e_phoff, e_phentsize, e_phnum, _, _, _ = parse_elf_header(data)
                for index in range(e_phnum):
                    phoff = e_phoff + index * e_phentsize
                    if elf_kind == "elf32":
                        p_type = struct.unpack_from(endian + "I", data, phoff)[0]
                        if p_type != PT_LOAD:
                            continue
                        p_offset = struct.unpack_from(endian + "I", data, phoff + 4)[0]
                        p_vaddr = struct.unpack_from(endian + "I", data, phoff + 8)[0]
                        p_align = struct.unpack_from(endian + "I", data, phoff + 28)[0]
                    else:
                        p_type = struct.unpack_from(endian + "I", data, phoff)[0]
                        if p_type != PT_LOAD:
                            continue
                        p_offset = struct.unpack_from(endian + "Q", data, phoff + 8)[0]
                        p_vaddr = struct.unpack_from(endian + "Q", data, phoff + 16)[0]
                        p_align = struct.unpack_from(endian + "Q", data, phoff + 48)[0]
                    if p_align < PAGE_SIZE_16K or (p_offset - p_vaddr) % PAGE_SIZE_16K != 0:
                        print(f"FAIL {name}: PT_LOAD[{index}] align=0x{p_align:x}", file=sys.stderr)
                        failures += 1
            except Exception as exc:
                print(f"FAIL {name}: {exc}", file=sys.stderr)
                failures += 1
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fix", action="store_true", help="patch files in place")
    parser.add_argument("--allow-missing", action="store_true")
    parser.add_argument("--best-effort", action="store_true", help="warn instead of failing for non-patchable files")
    parser.add_argument("--keep-abi", action="append", default=[], help="remove other ABI directories before checking")
    parser.add_argument("--zip", action="store_true", help="verify a packaged APK/AAB zip instead of patching files")
    parser.add_argument("paths", nargs="+", type=Path)
    args = parser.parse_args()

    if args.zip:
        failures = sum(verify_zip(path) for path in args.paths)
        return 1 if failures else 0

    failures = 0
    patched = 0
    checked = 0
    prune_abis(args.paths, set(args.keep_abi), args.allow_missing)
    for path in iter_so_files(args.paths, args.allow_missing):
        try:
            changed, notes = patch_elf(path, args.fix)
            checked += 1
            if changed:
                patched += 1
                print(f"patched {path}")
            elif notes and not args.fix:
                print(f"needs patch {path}: {'; '.join(notes)}")
        except ElfError as exc:
            failures += 1
            print(f"FAIL {path}: {exc}", file=sys.stderr)

    print(f"ELF 16KB page-size check: checked={checked} patched={patched} failures={failures}")
    if failures and args.best_effort:
        print("ELF 16KB page-size check: non-patchable files were left unchanged", file=sys.stderr)
        return 0
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
