#!/usr/bin/env python3
"""Fail when Battly UI strings lose localization or bypass Android resources."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RES_DIR = ROOT / "app_pojavlauncher" / "src" / "main" / "res"
JAVA_DIR = ROOT / "app_pojavlauncher" / "src" / "main" / "java"
KEY_FILE = Path(__file__).with_name("battly_string_keys.txt")
LOCALES = (
    "af",
    "ar",
    "az-rAZ",
    "ba",
    "bn-rBD",
    "bn-rIN",
    "ca",
    "cs",
    "da",
    "de",
    "el",
    "et-rEE",
    "fa-rIR",
    "fi",
    "es",
    "fil",
    "fr",
    "hi",
    "hu",
    "in",
    "it",
    "iw",
    "ja",
    "kk",
    "ko",
    "la",
    "lt",
    "mn-rMN",
    "ms",
    "nl",
    "no",
    "pl",
    "pt",
    "pt-rBR",
    "ro",
    "ru",
    "sk-rSK",
    "sr",
    "sr-rCS",
    "sv",
    "th",
    "tr",
    "tt",
    "uk",
    "vi",
    "zh-rCN",
    "zh-rTW",
)
FORMAT_PATTERN = re.compile(
    r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z%]"
)
UNPOSITIONED_FORMAT_PATTERN = re.compile(
    r"(?<!%)%(?!\d+\$)[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]"
)
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
VISIBLE_XML_ATTRIBUTES = ("text", "hint", "title", "summary", "contentDescription")
NON_LANGUAGE_XML = {
    "◀",
    "▶",
    "▲",
    "▼",
    "x",
    "+",
    "100%",
    "Battly",
    "AngelAuraMC",
    "PojavLauncherTeam",
    "Tran Khanh Duy",
    "Boardwalk",
    "1 / 1",
    ".",
    ".minecraft",
    "Java 8",
    "Java 17",
    "Java 21",
    "Java 25",
}
DIRECT_LITERAL_PATTERN = re.compile(
    r"\b(?:setText|setTitle|setMessage|setHint|setContentDescription|setPositiveButton|"
    r"setNegativeButton)\s*\(\s*\"([^\"\\]*(?:\\.[^\"\\]*)*)\""
)
TOAST_LITERAL_PATTERN = re.compile(
    r"\bToast\.makeText\s*\([^,]+,\s*\"([^\"\\]*(?:\\.[^\"\\]*)*)\""
)
NON_LANGUAGE_JAVA = {
    "",
    "0",
    "!",
    "%08X",
    "%s - %s",
    "%s%s",
    " · ",
    "Minecraft ",
    "\\u2193 ",
    "\\u2665 ",
}


def parse_strings(path: Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    return {
        element.attrib["name"]: "".join(element.itertext()).strip()
        for element in root.findall("string")
    }


def placeholders(value: str) -> list[str]:
    return sorted(FORMAT_PATTERN.findall(value))


def audit_coverage(errors: list[str]) -> None:
    keys = [
        line.strip()
        for line in KEY_FILE.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    ]
    base = parse_strings(RES_DIR / "values" / "strings.xml")
    for key, value in base.items():
        if len(UNPOSITIONED_FORMAT_PATTERN.findall(value)) > 1:
            errors.append(f"base: multiple non-positional placeholders in {key}")
    for key in keys:
        if key not in base:
            errors.append(f"Unknown Battly string key: {key}")
    for locale in LOCALES:
        path = RES_DIR / f"values-{locale}" / "strings.xml"
        localized = parse_strings(path)
        for key in keys:
            if key not in localized:
                errors.append(f"{locale}: missing {key}")
                continue
            if key in base and placeholders(localized[key]) != placeholders(base[key]):
                errors.append(
                    f"{locale}: placeholder mismatch for {key}: "
                    f"{placeholders(localized[key])} != {placeholders(base[key])}"
                )
            if (
                key in base
                and "battly" in base[key].casefold()
                and "battly" not in localized[key].casefold()
            ):
                errors.append(f"{locale}: Battly brand was translated in {key}")
            if "ZXQPH" in localized[key] or "<span" in localized[key]:
                errors.append(f"{locale}: translation token leaked into {key}")


def audit_layouts(errors: list[str]) -> None:
    for path in RES_DIR.rglob("*.xml"):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for element in root.iter():
            for attribute in VISIBLE_XML_ATTRIBUTES:
                value = element.attrib.get(ANDROID_NS + attribute)
                if (
                    value
                    and not value.startswith(("@", "?", "%"))
                    and value.strip()
                    and value not in NON_LANGUAGE_XML
                ):
                    errors.append(
                        f"{path.relative_to(ROOT)}: hardcoded android:{attribute}={value!r}"
                    )


def audit_java(errors: list[str]) -> None:
    for path in JAVA_DIR.rglob("*.java"):
        source = path.read_text(encoding="utf-8", errors="ignore")
        for pattern in (DIRECT_LITERAL_PATTERN, TOAST_LITERAL_PATTERN):
            for match in pattern.finditer(source):
                value = match.group(1)
                if value not in NON_LANGUAGE_JAVA:
                    line = source.count("\n", 0, match.start()) + 1
                    errors.append(
                        f"{path.relative_to(ROOT)}:{line}: hardcoded UI text {value!r}"
                    )


def main() -> int:
    errors: list[str] = []
    audit_coverage(errors)
    audit_layouts(errors)
    audit_java(errors)
    if errors:
        print(f"Battly i18n audit failed with {len(errors)} issue(s):", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    key_count = sum(
        1
        for line in KEY_FILE.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    )
    print(
        f"Battly i18n audit passed: {key_count} strings x {len(LOCALES)} locales; "
        "no translatable UI literals detected."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
