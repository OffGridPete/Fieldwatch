#!/usr/bin/env python3
"""Generate GeneratedCatalog.kt from dist/fieldwatch-signatures.json.

The JSON is the single source of truth for the stock signature catalog —
the same file CatalogRemote downloads for in-app updates. Run:

    python tools/gen_default_catalog.py

then commit the regenerated Kotlin. DefaultCatalogTest asserts the
compiled catalog still parses back to exactly this JSON, so drift is
caught by unit tests even if this script is skipped.
"""

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
JSON_PATH = ROOT / "dist" / "fieldwatch-signatures.json"
OUT_PATH = ROOT / "app/src/main/java/app/fieldwatch/domain/GeneratedCatalog.kt"
WIDTH = 120


def kstr(s: str) -> str:
    """Kotlin string literal: escape \\, \", $ and control chars."""
    out = []
    for ch in s:
        if ch in ('\\', '"'):
            out.append('\\' + ch)
        elif ch == '$':
            out.append('\\$')
        elif ch == '\n':
            out.append('\\n')
        elif ch == '\r':
            out.append('\\r')
        elif ch == '\t':
            out.append('\\t')
        else:
            out.append(ch)
    return '"' + ''.join(out) + '"'


def kdouble(v) -> str:
    """Double literal — keep a decimal point so Kotlin types it Double."""
    s = repr(float(v))
    if '.' not in s and 'e' not in s and 'E' not in s:
        s += '.0'
    return s


def render(name, pairs, indent):
    """Name(arg = v, …) — one line when short, else one arg per line."""
    items = [(k, v) for k, v in pairs if v is not None]
    one = f"{name}(" + ", ".join(f"{k} = {v}" for k, v in items) + ")"
    if "\n" not in one and indent + len(one) <= WIDTH:
        return one
    pad = " " * (indent + 4)
    body = ",\n".join(f"{pad}{k} = {v}" for k, v in items)
    return f"{name}(\n{body}\n{' ' * indent})"


def render_list(elems, indent):
    if not elems:
        return "emptyList()"
    one = "listOf(" + ", ".join(elems) + ")"
    if "\n" not in one and indent + len(one) <= WIDTH:
        return one
    pad = " " * (indent + 4)
    return "listOf(\n" + ",\n".join(f"{pad}{e}" for e in elems) + ",\n" + " " * indent + ")"


def rule(r, indent):
    return render("MatchRule", [
        ("kind", f"RuleKind.{r['kind']}"),
        ("text", kstr(r["text"]) if r.get("text") else None),
        ("companyId", str(r["companyId"]) if r.get("companyId") else None),
        ("dataPrefixHex", kstr(r["dataPrefixHex"]) if r.get("dataPrefixHex") else None),
        ("radio", f"RadioKind.{r['radio']}" if r.get("radio") else None),
        ("enabled", "false" if r.get("enabled") is False else None),
    ], indent)


def when(w, indent):
    return render("DecodeWhen", [
        ("offset", str(w["offset"])),
        ("length", str(w["length"]) if w.get("length", 1) != 1 else None),
        ("op", f"DecodeWhenOp.{w['op'].upper()}"),
        ("valueHex", kstr(w["valueHex"])),
        ("and", when(w["and"], indent) if w.get("and") else None),
    ], indent)


def field(f, indent):
    enum_lit = None
    if f.get("enum"):
        enum_lit = "mapOf(" + ", ".join(
            f"{kstr(k)} to {kstr(v)}" for k, v in f["enum"].items()
        ) + ")"
    return render("DecodeField", [
        ("id", kstr(f["id"])),
        ("label", kstr(f["label"])),
        ("offset", str(f["offset"])),
        ("length", str(f["length"]) if f.get("length") else None),
        ("type", f"DecodeType.{f['type'].upper()}"),
        ("endian", f"DecodeEndian.{f['endian'].upper()}" if f.get("endian", "le") != "le" else None),
        ("bitOffset", str(f["bitOffset"]) if f.get("bitOffset") is not None else None),
        ("bitWidth", str(f["bitWidth"]) if f.get("bitWidth") is not None else None),
        ("scale", kdouble(f["scale"]) if f.get("scale") is not None else None),
        ("offsetAdd", kdouble(f["offsetAdd"]) if f.get("offsetAdd") is not None else None),
        ("modulo", kdouble(f["modulo"]) if f.get("modulo") is not None else None),
        ("unit", kstr(f["unit"]) if f.get("unit") else None),
        ("enumLabels", enum_lit),
        ("gate", when(f["when"], indent + 4) if f.get("when") else None),
    ], indent)


def decode(d, indent):
    return render("FleetDecode", [
        # @SerialName is camelCase for DecodeSource.
        ("source", "DecodeSource." + {
            "manufacturerData": "MANUFACTURER_DATA",
            "serviceData": "SERVICE_DATA",
        }[d["source"]]),
        ("serviceUuid", kstr(d["serviceUuid"]) if d.get("serviceUuid") else None),
        ("companyId", str(d["companyId"]) if d.get("companyId") is not None else None),
        ("fields", render_list(
            [field(f, indent + 8) for f in d.get("fields") or []], indent + 4,
        )),
    ], indent)


def fleet(f, indent=8):
    return render("Fleet", [
        ("id", kstr(f["id"])),
        ("name", kstr(f["name"])),
        ("enabled", "false" if f.get("enabled") is False else None),
        ("matchAny", "false" if f.get("matchAny") is False else None),
        ("colorIndex", str(f["colorIndex"]) if f.get("colorIndex") else None),
        ("rules", render_list(
            [rule(r, indent + 8) for r in f.get("rules") or []], indent + 4,
        )),
        ("minPeers", str(f["minPeers"]) if f.get("minPeers") else None),
        ("peerWindowSec", str(f["peerWindowSec"]) if f.get("peerWindowSec", 60) != 60 else None),
        ("clusterByOui", "true" if f.get("clusterByOui") else None),
        ("sequentialMac", "true" if f.get("sequentialMac") else None),
        ("notes", kstr(f["notes"]) if f.get("notes") else None),
        ("attentionNote", kstr(f["attentionNote"]) if f.get("attentionNote") else None),
        ("builtIn", "true" if f.get("builtIn") else None),
        ("kind", f"SignatureClass.{f['kind']}" if f.get("kind", "OTHER") != "OTHER" else None),
        ("decode", decode(f["decode"], indent + 4) if f.get("decode") else None),
    ], indent)


def main() -> int:
    pack = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    fleets = pack["fleets"]
    lines = [
        "// GENERATED from dist/fieldwatch-signatures.json",
        "// by tools/gen_default_catalog.py — do not edit by hand.",
        "",
        "package app.fieldwatch.domain",
        "",
        "/** Stock signature catalog, compiled in. JSON order is kept. */",
        "internal object GeneratedCatalog {",
        "    // One function per fleet: a single 223-element listOf initializer",
        "    // would exceed the JVM 64KB method limit in <clinit>.",
        "    val fleets: List<Fleet> = listOf(",
    ]
    for i in range(len(fleets)):
        lines.append(f"        f{i}(),")
    lines.append("    )")
    lines.append("")
    for i, f in enumerate(fleets):
        lines.append(f"    private fun f{i}() =\n        " + fleet(f, indent=8))
    lines += [
        "}",
        "",
    ]
    OUT_PATH.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    print(f"Wrote {len(fleets)} fleets -> {OUT_PATH.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
