#!/usr/bin/env python3
"""Builds the paint catalogue for both apps from Arcturus5404/miniature-paints (MIT).

Usage: python3 tools/gen-catalogue.py <dir with Vallejo.md, Citadel_Colour.md, Army_Painter.md, LICENSE> <commit sha> <pwa repo>
Writes domain/src/main/resources/catalogue.json (this repo) and src/paints/catalogue.json (PWA), byte-identical,
plus the dataset's license next to each. Rows: [brand, code, English name, range, type, hex].
"""
import json
import re
import sys
from pathlib import Path

SOURCES = [("Vallejo", "Vallejo.md"), ("Citadel", "Citadel_Colour.md"), ("Army Painter", "Army_Painter.md")]
HEX = re.compile(r"`(#[0-9A-Fa-f]{6})`")


def paint_type(range_name: str, name: str) -> str:
    r = f"{range_name} {name}".lower()
    if "primer" in r or "spray" in r:
        return "primer"
    if "medium" in r or "thinner" in r or "varnish" in r:
        return "medium"
    if "metal" in r or "liquid gold" in r or "metallic" in r:
        return "metallic"
    if "wash" in r or "shade" in r or "tone" in r:
        return "wash"
    if "contrast" in r or "speedpaint" in r or "technical" in r or "effect" in r or " fx" in r:
        return "other"
    return "opaque"


def rows(brand: str, text: str):
    lines = [l for l in text.splitlines() if l.startswith("|")]
    header = [c.strip() for c in lines[0].strip("|").split("|")]
    for line in lines[2:]:
        cells = [c.strip() for c in line.strip("|").split("|")]
        if len(cells) != len(header):
            continue
        row = dict(zip(header, cells))
        hex_match = HEX.search(row.get("Hex", ""))
        name = row.get("Name", "").strip()
        if not hex_match or not name:
            continue
        code = row.get("Code", "").strip()
        if not code or code.lower() == "null":
            code = name  # Citadel (and some Army Painter sets) sell by name, not by code
        rng = row.get("Set", "").strip()
        yield [brand, code, name, rng, paint_type(rng, name), hex_match.group(1).lower()]


def main():
    src, sha, pwa = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3])
    paints, seen = [], set()
    for brand, file in SOURCES:
        # Airbrush versions last, so a shared name ("Abaddon Black") is the brush-on paint and the
        # airbrush one becomes "Abaddon Black (Air)".
        for row in sorted(rows(brand, (src / file).read_text(encoding="utf-8")), key=lambda r: "air" in r[3].lower()):
            key = (row[0].lower(), row[1].lower())
            if key in seen:
                row[1] = f"{row[1]} ({row[3]})"  # the same name in another set, e.g. Citadel Air
                key = (row[0].lower(), row[1].lower())
                if key in seen:
                    continue
            seen.add(key)
            paints.append(row)
    data = {
        "source": f"https://github.com/Arcturus5404/miniature-paints/tree/{sha}",
        "license": "MIT, Copyright (c) 2022 Rick Fleuren (see catalogue-LICENSE.txt)",
        "paints": paints,
    }
    text = json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n"
    licence = (src / "LICENSE").read_text(encoding="utf-8")
    for out in (Path("domain/src/main/resources"), pwa / "src/paints"):
        out.mkdir(parents=True, exist_ok=True)
        (out / "catalogue.json").write_text(text, encoding="utf-8")
        (out / "catalogue-LICENSE.txt").write_text(licence, encoding="utf-8")
    print(len(paints), "paints")


if __name__ == "__main__":
    main()
