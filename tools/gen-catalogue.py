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


# Words that make a paint a medium, metallic, wash or ink. Whole words only: "Medium Grey" is a colour,
# "Ancient Stone" is not a "tone", and a metal word followed by a colour ("Gold Yellow", "Bronze Brown")
# names a colour, not a metal.
MEDIUM = re.compile(r"\b(varnish|thinner|flow improver|retarder)\b|\bmedium$|^medium xpress$")
METAL = re.compile(r"\b(metal|metallic|gunmetal|gold|silver|steel|iron|copper|brass|bronze|chainmail|mithril|alloy|chrome|alumin(i)?um|pewter|titanium)\b(?!\s+(yellow|brown|green|ochre|flesh|fleshtone|grey|gray|red|blue))")
INK = re.compile(r"\binks?\b")
WASH = re.compile(r"\b(wash|washes|shade|shades)\b")
CONTRAST_RANGES = ("contrast", "speedpaint", "xpress", "special fx", "weathering", "technical")
METAL_RANGES = ("metal color", "liquid gold", "metallic")
NOT_METAL = {"steel legion drab", "red iron oxide"}  # a khaki and a pigment despite the metal word
METAL_BRAND_NAMES = {  # Citadel metallics whose names carry no metal word
    "leadbelcher", "retributor armour", "ironbreaker", "runelord brass", "sycorax bronze", "warplock bronze",
    "balthasar gold", "screaming bell", "gehenna's gold", "hashut copper", "brass scorpion", "fulgurite copper",
    "stormhost silver", "auric armour gold", "liberator gold", "runefang steel", "canoptek alloy", "skullcrusher brass",
}


# Checked one by one against Vallejo's official charts (Game Color CC266 Rev03 2025 and rev14 2014;
# Model Color CC070 Rev18 and CC329 R00):
# paints whose line (metallic, ink, fluo, special effects) the name alone does not reveal.
TYPE_OVERRIDES = {
    ("Vallejo", "72.060"): "metallic",  # Tinny Tin, Game Color Metallic
    **{("Vallejo", c): "ink" for c in ("72.082", "72.083", "72.084")},  # Game Color Ink: White, Magenta, Dark Turquoise
    **{("Vallejo", c): "other" for c in ("72.103", "72.104", "72.156", "72.157", "72.158", "72.159", "72.160", "72.161")},  # Fluo
    **{("Vallejo", f"72.13{i}"): "other" for i in range(0, 7)},  # 2014 Special Effect colours (Rust, Fresh Blood…)
    ("Vallejo", "70.800"): "metallic",  # Gunmetal Blue, Model Color Metallic
    **{("Vallejo", c): "other" for c in ("70.730", "70.733", "70.735", "70.736", "70.737")},  # Model Color Fluo
    **{("Vallejo", c): "other" for c in ("70.934", "70.935", "70.936", "70.937", "70.938", "70.834")},  # Transparent
    **{("Vallejo", c): "wash" for c in ("70.831", "70.832", "70.853", "70.854", "70.855")},  # Glazes, like Citadel Glaze
}

# Vallejo's Game Color ink line (Yellow, Red, … Sepia, Black): the dataset names them without "Ink".
GAME_INKS = {f"72.0{i}" for i in range(85, 95)}


def paint_type(range_name: str, name: str, code: str = "") -> str:
    r, n = range_name.lower(), name.lower().strip()
    if code in GAME_INKS:
        return "ink"
    if "primer" in r or "spray" in r or re.search(r"\bprimer\b", n):
        return "primer"
    if MEDIUM.search(n):
        return "medium"
    if INK.search(n) or INK.search(r):
        return "ink"
    if any(m in r for m in METAL_RANGES) or (METAL.search(n) and n not in NOT_METAL) or n in METAL_BRAND_NAMES:
        return "metallic"
    if WASH.search(r) or r in ("shade", "warpaints tone", "glaze") or WASH.search(n):
        return "wash"
    if any(c in r for c in CONTRAST_RANGES):
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
        kind = TYPE_OVERRIDES.get((brand, code)) or paint_type(rng, name, code if brand == "Vallejo" else "")
        yield [brand, code, name, rng, kind, hex_match.group(1).lower()]


def main():
    src, sha, pwa = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3])
    paints, seen = [], set()
    for brand, file in SOURCES:
        # Airbrush and spray versions last, so a shared name ("Abaddon Black", "Leadbelcher") is the
        # brush-on paint and the others become "Abaddon Black (Air)", "Leadbelcher (Spray)".
        for row in sorted(rows(brand, (src / file).read_text(encoding="utf-8")), key=lambda r: any(w in r[3].lower() for w in ("air", "spray"))):
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
