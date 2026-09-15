#!/usr/bin/env python3
"""Download official IEEE + Bluetooth SIG assigned-number lists and pack a compact
binary for Fieldwatch offline lookup (app/src/main/assets/lookups/radiodb.bin).
"""
from __future__ import annotations

import csv
import io
import re
import struct
import sys
import urllib.request
from datetime import date, datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "lookups"
CACHE = ROOT / "scripts" / ".lookup-cache"

IEEE = {
    "oui.csv": "https://standards-oui.ieee.org/oui/oui.csv",
    "mam.csv": "https://standards-oui.ieee.org/oui28/mam.csv",
    "oui36.csv": "https://standards-oui.ieee.org/oui36/oui36.csv",
    "cid.csv": "https://standards-oui.ieee.org/cid/cid.csv",
}
SIG = {
    "company_identifiers.yaml": (
        "https://bitbucket.org/bluetooth-SIG/public/raw/main/"
        "assigned_numbers/company_identifiers/company_identifiers.yaml"
    ),
    "appearance_values.yaml": (
        "https://bitbucket.org/bluetooth-SIG/public/raw/main/"
        "assigned_numbers/core/appearance_values.yaml"
    ),
    "service_uuids.yaml": (
        "https://bitbucket.org/bluetooth-SIG/public/raw/main/"
        "assigned_numbers/uuids/service_uuids.yaml"
    ),
}

MAGIC = b"SPLK"
VERSION = 1


def fetch(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "FieldwatchLookupBuilder/1.0"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        dest.write_bytes(resp.read())


def unquote_yaml(raw: str) -> str:
    s = raw.strip()
    if len(s) >= 2 and s[0] == s[-1] and s[0] in "'\"":
        return s[1:-1]
    return s


def parse_company_yaml(text: str) -> list[tuple[int, str]]:
    out: list[tuple[int, str]] = []
    value: int | None = None
    for line in text.splitlines():
        s = line.strip()
        if s.startswith("- value:"):
            value = int(s.split(":", 1)[1].strip(), 16)
        elif s.startswith("name:") and value is not None:
            out.append((value, unquote_yaml(s.split(":", 1)[1])))
            value = None
    return out


def parse_service_yaml(text: str) -> list[tuple[int, str]]:
    out: list[tuple[int, str]] = []
    uuid: int | None = None
    for line in text.splitlines():
        s = line.strip()
        if s.startswith("- uuid:"):
            uuid = int(s.split(":", 1)[1].strip(), 16)
        elif s.startswith("name:") and uuid is not None:
            out.append((uuid, unquote_yaml(s.split(":", 1)[1])))
            uuid = None
    return out


def parse_appearance_yaml(text: str) -> list[tuple[int, str]]:
    """GAP Appearance: 10-bit category << 6 | 6-bit subcategory."""
    out: list[tuple[int, str]] = []
    cat: int | None = None
    cat_name: str | None = None
    pending_sub: int | None = None
    in_sub = False
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip(" "))
        s = line.strip()
        if s.startswith("- category:"):
            cat = int(s.split(":", 1)[1].strip(), 16)
            cat_name = None
            in_sub = False
            pending_sub = None
        elif s.startswith("name:") and cat is not None and not in_sub and cat_name is None:
            cat_name = unquote_yaml(s.split(":", 1)[1])
            out.append((cat << 6, cat_name))
        elif s.startswith("subcategory:"):
            in_sub = True
        elif in_sub and s.startswith("- value:"):
            pending_sub = int(s.split(":", 1)[1].strip(), 16)
        elif in_sub and s.startswith("name:") and cat is not None and cat_name is not None and pending_sub is not None:
            sub_name = unquote_yaml(s.split(":", 1)[1])
            out.append(((cat << 6) | pending_sub, f"{cat_name} / {sub_name}"))
            pending_sub = None
        elif indent == 0 and s.startswith("appearance_values"):
            continue
    return out


def read_ieee_csv(path: Path) -> list[tuple[str, str, str]]:
    rows: list[tuple[str, str, str]] = []
    with path.open(newline="", encoding="utf-8", errors="replace") as fh:
        for row in csv.DictReader(fh):
            assignment = (row.get("Assignment") or "").strip().upper()
            org = (row.get("Organization Name") or "").strip()
            registry = (row.get("Registry") or "").strip()
            if assignment and org:
                rows.append((registry, assignment, org))
    return rows


class Names:
    def __init__(self) -> None:
        self._idx: dict[str, int] = {}
        self._list: list[str] = []

    def add(self, name: str) -> int:
        name = re.sub(r"\s+", " ", name).strip()
        if name not in self._idx:
            self._idx[name] = len(self._list)
            self._list.append(name)
        return self._idx[name]

    def blob(self) -> tuple[bytes, bytes]:
        encoded = [n.encode("utf-8") for n in self._list]
        offsets = [0]
        total = 0
        for e in encoded:
            total += len(e)
            offsets.append(total)
        off = b"".join(struct.pack("<I", o) for o in offsets)
        return off, b"".join(encoded)


def pack_u16_pairs(pairs: list[tuple[int, int]]) -> bytes:
    pairs = sorted({k: v for k, v in pairs}.items())
    keys = b"".join(struct.pack("<H", k) for k, _ in pairs)
    idxs = b"".join(struct.pack("<H", v) for _, v in pairs)
    return struct.pack("<I", len(pairs)) + keys + idxs


def pack_u32_pairs(pairs: list[tuple[int, int]]) -> bytes:
    pairs = sorted({k: v for k, v in pairs}.items())
    keys = b"".join(struct.pack("<I", k) for k, _ in pairs)
    idxs = b"".join(struct.pack("<H", v) for _, v in pairs)
    return struct.pack("<I", len(pairs)) + keys + idxs


def pack_long(pairs: list[tuple[int, int, int]]) -> bytes:
    """(bits, prefix, name_index) packed as u64 key = bits<<56 | prefix."""
    items = sorted({(bits << 56) | prefix: idx for bits, prefix, idx in pairs}.items())
    keys = b"".join(struct.pack("<Q", k) for k, _ in items)
    idxs = b"".join(struct.pack("<H", v) for _, v in items)
    return struct.pack("<I", len(items)) + keys + idxs


def main() -> int:
    cache = CACHE
    cache.mkdir(parents=True, exist_ok=True)
    use_cache = "--offline" in sys.argv
    for name, url in {**IEEE, **SIG}.items():
        dest = cache / name
        if use_cache and dest.exists():
            print(f"cache {name}")
            continue
        print(f"fetch {name}")
        fetch(url, dest)

    names = Names()
    mal: list[tuple[int, int]] = []
    cid: list[tuple[int, int]] = []
    longp: list[tuple[int, int, int]] = []

    for registry, assignment, org in read_ieee_csv(cache / "oui.csv"):
        mal.append((int(assignment, 16), names.add(org)))
    for registry, assignment, org in read_ieee_csv(cache / "cid.csv"):
        cid.append((int(assignment, 16), names.add(org)))
    for registry, assignment, org in read_ieee_csv(cache / "mam.csv"):
        longp.append((28, int(assignment, 16), names.add(org)))
    for registry, assignment, org in read_ieee_csv(cache / "oui36.csv"):
        longp.append((36, int(assignment, 16), names.add(org)))

    companies = parse_company_yaml((cache / "company_identifiers.yaml").read_text(encoding="utf-8"))
    appearances = parse_appearance_yaml((cache / "appearance_values.yaml").read_text(encoding="utf-8"))
    services = parse_service_yaml((cache / "service_uuids.yaml").read_text(encoding="utf-8"))

    bt = [(v, names.add(n)) for v, n in companies]
    app = [(v, names.add(n)) for v, n in appearances]
    uuid = [(v, names.add(n)) for v, n in services]

    name_off, name_blob = names.blob()
    built = int(date.today().strftime("%Y%m%d"))

    sections: dict[str, bytes] = {
        "mal": pack_u32_pairs(mal),
        "cid": pack_u32_pairs(cid),
        "long": pack_long(longp),
        "btc": pack_u16_pairs(bt),
        "app": pack_u16_pairs(app),
        "uuid": pack_u16_pairs(uuid),
        "noff": struct.pack("<I", len(names._list)) + name_off,
        "nstr": name_blob,
    }
    order = ["mal", "cid", "long", "btc", "app", "uuid", "noff", "nstr"]
    header_len = 16 + 8 * len(order)
    cursor = header_len
    table = io.BytesIO()
    table.write(MAGIC)
    table.write(struct.pack("<HH", VERSION, 0))
    table.write(struct.pack("<I", built))
    table.write(struct.pack("<I", len(order)))
    blobs = []
    for key in order:
        blob = sections[key]
        pad = (4 - (len(blob) % 4)) % 4
        blob = blob + b"\x00" * pad
        tag = key.encode("ascii").ljust(4, b"\x00")
        table.write(tag)
        table.write(struct.pack("<I", cursor))
        cursor += len(blob)
        blobs.append(blob)
    packed = table.getvalue() + b"".join(blobs)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    bin_path = OUT_DIR / "radiodb.bin"
    bin_path.write_bytes(packed)
    meta = OUT_DIR / "radiodb.txt"
    meta.write_text(
        "\n".join(
            [
                f"built {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}",
                f"bytes {len(packed)}",
                f"names {len(names._list)}",
                f"ieee-mal {len(mal)} https://standards-oui.ieee.org/oui/oui.csv",
                f"ieee-mam {sum(1 for b,_,_ in longp if b==28)} https://standards-oui.ieee.org/oui28/mam.csv",
                f"ieee-mas {sum(1 for b,_,_ in longp if b==36)} https://standards-oui.ieee.org/oui36/oui36.csv",
                f"ieee-cid {len(cid)} https://standards-oui.ieee.org/cid/cid.csv",
                "bt-company "
                f"{len(companies)} https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/company_identifiers/",
                f"bt-appearance {len(appearances)}",
                f"bt-service-uuid {len(services)}",
                "",
            ]
        ),
        encoding="utf-8",
    )
    print(f"wrote {bin_path} ({len(packed)} bytes)")
    print(meta.read_text())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
