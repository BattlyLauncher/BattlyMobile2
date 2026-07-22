#!/usr/bin/env python3
"""Populate Battly-owned Android strings using the existing locale files.

This is a maintainer tool, not part of the Android build. Existing non-English
translations are preserved. Missing strings and values still identical to the
English source are translated in bounded batches.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RES_DIR = ROOT / "app_pojavlauncher" / "src" / "main" / "res"
BASE_FILE = RES_DIR / "values" / "strings.xml"
KEY_FILE = Path(__file__).with_name("battly_string_keys.txt")
CACHE_FILE = ROOT / "build" / "i18n" / "translations.json"

LOCALES = {
    "af": "af",
    "ar": "ar",
    "de": "de",
    "es": "es",
    "fil": "tl",
    "fr": "fr",
    "in": "id",
    "it": "it",
    "iw": "he",
    "ja": "ja",
    "ko": "ko",
    "ms": "ms",
    "pt": "pt",
    "pt-rBR": "pt",
    "ru": "ru",
    "sk-rSK": "sk",
    "tr": "tr",
    "zh-rCN": "zh-CN",
    "zh-rTW": "zh-TW",
}

TOKEN_PATTERN = re.compile(
    r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z%]"
)
BRANDS = (
    "BattlyWorlds",
    "Battly",
    "Minecraft",
    "Java",
    "NeoForge",
    "Forge",
    "OptiFine",
    "LegacyFabric",
    "Fabric",
    "Quilt",
    "Modrinth",
    "CurseForge",
    "Google Drive",
    "Google",
    "Discord",
    "Microsoft",
    "OpenGL",
    "Vulkan",
    "LWJGL",
    "Mesa",
    "MobileGlues",
    "Zink",
    "Freedreno",
    "Turnip",
    "Sodium",
    "Iris",
    "Terracotta",
    "Android",
    "Firebase",
    "GitHub",
    "VPNService",
    "OAuth",
    "PNG",
    "LAN",
)
BRAND_PATTERN = re.compile(
    "|".join(re.escape(brand) for brand in sorted(BRANDS, key=len, reverse=True)),
    flags=re.IGNORECASE,
)
PROTECTED_PATTERN = re.compile(
    rf"BattlyWorlds|Battly|{TOKEN_PATTERN.pattern}",
    flags=re.IGNORECASE,
)
STRING_PATTERN_TEMPLATE = r'(<string\s+name="{name}"(?:\s+[^>]*)?>)(.*?)(</string>)'
SPLITTER = "__BATTLY_SPLIT_9F3A__"


def parse_strings(path: Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    return {
        element.attrib["name"]: "".join(element.itertext()).strip()
        for element in root.findall("string")
        if element.attrib.get("translatable", "true") != "false"
    }


def parse_base_elements() -> dict[str, ET.Element]:
    root = ET.parse(BASE_FILE).getroot()
    return {
        element.attrib["name"]: element
        for element in root.findall("string")
        if element.attrib.get("translatable", "true") != "false"
    }


def mask_tokens(value: str) -> tuple[str, dict[str, str]]:
    replacements: dict[str, str] = {}

    def replace(match: re.Match[str]) -> str:
        token_id = f"ZXQPH{len(replacements):03d}QXZ"
        original = match.group(0)
        replacements[token_id] = original
        return token_id

    return PROTECTED_PATTERN.sub(replace, value), replacements


def restore_tokens(value: str, replacements: dict[str, str]) -> str:
    for token_id, original in replacements.items():
        if token_id not in value:
            raise ValueError(f"Translation removed required token {token_id}")
        value = value.replace(token_id, original)
    return value.strip()


def translate_request(text: str, target: str) -> str:
    query = urllib.parse.urlencode(
        {
            "client": "gtx",
            "sl": "en",
            "tl": target,
            "dt": "t",
            "q": text,
        }
    )
    request = urllib.request.Request(
        f"https://translate.googleapis.com/translate_a/single?{query}",
        headers={"User-Agent": "Battly-Mobile-i18n/2.0.1"},
    )
    last_error: Exception | None = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.load(response)
            return "".join(part[0] for part in payload[0] if part and part[0])
        except Exception as error:  # pragma: no cover - network-dependent tool
            last_error = error
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"Translation request failed for {target}") from last_error


def load_cache() -> dict[str, str]:
    if not CACHE_FILE.is_file():
        return {}
    return json.loads(CACHE_FILE.read_text(encoding="utf-8"))


def save_cache(cache: dict[str, str]) -> None:
    CACHE_FILE.parent.mkdir(parents=True, exist_ok=True)
    CACHE_FILE.write_text(
        json.dumps(cache, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )


def translated_brand_aliases(target: str, cache: dict[str, str]) -> dict[str, str]:
    missing = [brand for brand in BRANDS if f"brand:{target}:{brand}" not in cache]
    if missing:
        output = translate_request(f"\n{SPLITTER}\n".join(missing), target)
        parts = output.split(SPLITTER)
        if len(parts) != len(missing):
            raise ValueError(f"Could not build protected brand aliases for {target}")
        for brand, translated in zip(missing, parts):
            cache[f"brand:{target}:{brand}"] = translated.strip()
        save_cache(cache)
    return {brand: cache[f"brand:{target}:{brand}"] for brand in BRANDS}


def normalize_brands(value: str, source: str, aliases: dict[str, str]) -> str:
    for brand in sorted(BRANDS, key=len, reverse=True):
        if not re.search(re.escape(brand), source, flags=re.IGNORECASE):
            continue
        alias = aliases[brand]
        if alias and alias.casefold() != brand.casefold():
            value = re.sub(re.escape(alias), brand, value, flags=re.IGNORECASE)
    return value


def translate_values(values: dict[str, str], target: str, cache: dict[str, str]) -> dict[str, str]:
    translated: dict[str, str] = {}
    pending: list[tuple[str, str, dict[str, str]]] = []
    aliases = translated_brand_aliases(target, cache)
    for key, source in values.items():
        cache_key = f"v10:{target}:{key}:{source}"
        if cache_key in cache:
            translated[key] = cache[cache_key]
            continue
        masked, tokens = mask_tokens(source)
        pending.append((key, masked, tokens))

    batch: list[tuple[str, str, dict[str, str]]] = []
    batch_size = 0

    def flush() -> None:
        nonlocal batch, batch_size
        if not batch:
            return
        combined = f"\n{SPLITTER}\n".join(item[1] for item in batch)
        output = translate_request(combined, target)
        parts = output.split(SPLITTER)
        if len(parts) != len(batch):
            raise ValueError(
                f"Translator returned {len(parts)} segments for {len(batch)} inputs ({target})"
            )
        for (key, _masked, tokens), part in zip(batch, parts):
            try:
                value = restore_tokens(part, tokens)
            except ValueError as error:
                raise ValueError(
                    f"{target}:{key}: {error}; translated segment={part!r}"
                ) from error
            value = normalize_brands(value, values[key], aliases)
            translated[key] = value
            cache[f"v10:{target}:{key}:{values[key]}"] = value
        save_cache(cache)
        batch = []
        batch_size = 0

    for item in pending:
        projected = batch_size + len(item[1]) + len(SPLITTER) + 2
        if batch and projected > 2800:
            flush()
        batch.append(item)
        batch_size += len(item[1]) + len(SPLITTER) + 2
    flush()
    return translated


def android_xml_text(value: str) -> str:
    value = value.replace("\\'", "'").replace("'", "\\'")
    return html.escape(value, quote=False)


def update_locale(
    qualifier: str,
    target: str,
    keys: list[str],
    base: dict[str, str],
    base_elements: dict[str, ET.Element],
    cache: dict[str, str],
    refresh_branded: bool,
) -> None:
    path = RES_DIR / f"values-{qualifier}" / "strings.xml"
    if not path.is_file():
        raise FileNotFoundError(path)
    current = parse_strings(path)
    if qualifier == "es":
        needed = {key: base[key] for key in keys if key not in current}
        if needed:
            raise RuntimeError(
                "Spanish is maintained manually and still needs: " + ", ".join(sorted(needed))
            )
        print(f"{qualifier}: complete (manual)")
        return
    needed = {
        key: base[key]
        for key in keys
        if key not in current
        or current[key] == base[key]
        or (refresh_branded and BRAND_PATTERN.search(base[key]))
    }
    if not needed:
        print(f"{qualifier}: complete")
        return

    translations = translate_values(needed, target, cache)
    source = path.read_text(encoding="utf-8-sig")
    additions: list[str] = []
    for key, value in translations.items():
        encoded = android_xml_text(value)
        pattern = re.compile(
            STRING_PATTERN_TEMPLATE.format(name=re.escape(key)),
            flags=re.DOTALL,
        )
        if pattern.search(source):
            source = pattern.sub(
                lambda match: match.group(1) + encoded + match.group(3),
                source,
                count=1,
            )
        else:
            attrs = []
            source_element = base_elements[key]
            if source_element.attrib.get("formatted") == "false":
                attrs.append(' formatted="false"')
            additions.append(
                f'    <string name="{key}"{"".join(attrs)}>{encoded}</string>'
            )
    if additions:
        source = source.replace(
            "</resources>",
            "\n" + "\n".join(additions) + "\n</resources>",
        )
    path.write_text(source, encoding="utf-8", newline="\n")
    print(f"{qualifier}: translated {len(translations)} strings")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--locales",
        nargs="*",
        default=list(LOCALES),
        choices=list(LOCALES),
    )
    parser.add_argument(
        "--refresh-branded",
        action="store_true",
        help="retranslate strings containing protected product or technology names",
    )
    args = parser.parse_args()

    keys = [
        line.strip()
        for line in KEY_FILE.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    ]
    base = parse_strings(BASE_FILE)
    missing = [key for key in keys if key not in base]
    if missing:
        raise RuntimeError("Unknown base keys: " + ", ".join(missing))
    base_elements = parse_base_elements()
    cache = load_cache()
    for qualifier in args.locales:
        update_locale(
            qualifier,
            LOCALES[qualifier],
            keys,
            base,
            base_elements,
            cache,
            args.refresh_branded,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
