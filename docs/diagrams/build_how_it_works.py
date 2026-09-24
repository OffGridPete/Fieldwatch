#!/usr/bin/env python3
"""Render the Fieldwatch 'How it works' architecture diagram (print PNG)."""

from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "how-it-works.png")

NIGHT = (11, 15, 20, 255)
INK = (232, 238, 242, 255)
MUTED = (154, 166, 178, 255)
RULE = (46, 58, 72, 255)
CARD = (18, 26, 34, 255)
CARD2 = (24, 34, 44, 255)
PHOS = (61, 255, 154, 255)
PHOS_DIM = (18, 92, 56, 255)
AMBER = (232, 168, 64, 255)
WIFI = (110, 178, 255, 255)
BLE = (186, 154, 255, 255)

W, H = 2400, 1180
PAD = 52

FONT = "/System/Library/Fonts/SFNS.ttf"
MONO = "/System/Library/Fonts/SFNSMono.ttf"


def F(size, bold=False):
    return ImageFont.truetype(FONT, size)


def M(size):
    return ImageFont.truetype(MONO, size)


def rr(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def wrap(d, s, f, max_w):
    words = s.split()
    lines, cur = [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if d.textlength(trial, font=f) <= max_w:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def body(d, x, y, s, f, fill, max_w, leading):
    for line in wrap(d, s, f, max_w):
        d.text((x, y), line, font=f, fill=fill)
        y += leading
    return y


def chevron(d, x, y, color=PHOS):
    d.polygon([(x, y - 10), (x + 16, y), (x, y + 10)], fill=color)


def arrow_h(d, x1, x2, y, color=PHOS):
    d.line((x1, y, x2 - 18, y), fill=color, width=4)
    chevron(d, x2 - 16, y, color)


def arrow_v(d, x, y1, y2, color=PHOS):
    d.line((x, y1, x, y2 - 16), fill=color, width=4)
    d.polygon([(x, y2), (x - 10, y2 - 16), (x + 10, y2 - 16)], fill=color)


def card(d, box, kicker, title, lines, accent=PHOS, files=None):
    x1, y1, x2, y2 = box
    rr(d, box, 18, fill=CARD2, outline=RULE, width=2)
    d.rectangle((x1, y1, x1 + 8, y2), fill=accent)
    d.text((x1 + 28, y1 + 20), kicker.upper(), font=F(16, True), fill=accent)
    d.text((x1 + 28, y1 + 48), title, font=F(28, True), fill=INK)
    yy = y1 + 92
    for para in lines:
        yy = body(d, x1 + 28, yy, para, F(20), MUTED, x2 - x1 - 56, 28) + 8
    if files:
        d.line((x1 + 24, y2 - 52, x2 - 24, y2 - 52), fill=RULE, width=1)
        d.text((x1 + 28, y2 - 28), files, font=M(15), fill=PHOS, anchor="lm")
    return box


def build():
    img = Image.new("RGBA", (W, H), NIGHT)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 10, H), fill=PHOS)

    d.text((PAD + 10, 40), "TECHNICAL SPECIFICATIONS  ·  HOW IT WORKS", font=F(20, True), fill=PHOS)
    d.text((PAD + 10, 78), "FIELDWATCH", font=F(52, True), fill=INK)
    body(
        d,
        PAD + 10,
        142,
        "A hear on this phone is a Wi-Fi access-point beacon or a Bluetooth LE advertisement. "
        "Fieldwatch matches it locally, draws Live, and can sit, debrief, or publish TAK — all on the handset.",
        F(24),
        MUTED,
        W - PAD * 2 - 20,
        32,
    )

    # grid: 3 x 2
    grid_top = 218
    grid_bot = H - 88
    gap_x, gap_y = 40, 40
    cw = (W - PAD * 2 - gap_x * 2) / 3
    ch = (grid_bot - grid_top - gap_y) / 2

    cells = []
    for r in range(2):
        for c in range(3):
            x1 = PAD + c * (cw + gap_x)
            y1 = grid_top + r * (ch + gap_y)
            cells.append((x1, y1, x1 + cw, y1 + ch))

    # 0 air  1 scan  2 match
    # 3 live 4 watch 5 share
    # Air: two stacked inner tiles instead of one sparse block
    x1, y1, x2, y2 = cells[0]
    rr(d, cells[0], 18, fill=CARD2, outline=RULE, width=2)
    d.rectangle((x1, y1, x1 + 8, y2), fill=WIFI)
    d.text((x1 + 28, y1 + 20), "01  ON THE AIR", font=F(16, True), fill=WIFI)
    d.text((x1 + 28, y1 + 48), "Beacons and ads", font=F(28, True), fill=INK)
    inner_h = (y2 - 60 - (y1 + 96) - 16) / 2
    iy = y1 + 96
    for accent, kick, title, blurb in (
        (WIFI, "Wi-Fi", "Access-point beacons", "SSID, BSSID, channel, vendor IE. Not Wi-Fi clients."),
        (BLE, "Bluetooth LE", "Advertisements", "Name, company ID, UUID, manufacturer data. No pairing."),
    ):
        ib = (x1 + 24, iy, x2 - 24, iy + inner_h)
        rr(d, ib, 12, fill=CARD, outline=RULE, width=1)
        d.text((ib[0] + 16, ib[1] + 12), kick.upper(), font=F(14, True), fill=accent)
        d.text((ib[0] + 16, ib[1] + 36), title, font=F(22, True), fill=INK)
        body(d, ib[0] + 16, ib[1] + 68, blurb, F(18), MUTED, ib[2] - ib[0] - 32, 24)
        iy += inner_h + 12
    d.line((x1 + 24, y2 - 52, x2 - 24, y2 - 52), fill=RULE, width=1)
    d.text((x1 + 28, y2 - 28), "radio/WifiRadio.kt  ·  BleRadio.kt", font=M(15), fill=PHOS, anchor="lm")
    card(
        d,
        cells[1],
        "02  This phone",
        "ScanService",
        [
            "Foreground service with the “Fieldwatch scanning” notification. Home leaves it running; swipe-away or Stop ends it.",
            "Wi-Fi is a batch radio (~30 s at High performance — the OS cap). BLE streams in between.",
        ],
        PHOS,
        files="radio/ScanService.kt  ·  Permissions.kt",
    )
    card(
        d,
        cells[2],
        "03  Identify",
        "Catalog match",
        [
            "SignatureEngine scores OUI, name glob, UUID, and manufacturer data against the stock pack plus rows you add.",
            "Decode fields map cleartext BLE bytes after a match. Encrypted ads stay hex.",
        ],
        BLE,
        files="domain/SignatureEngine.kt  ·  DefaultCatalog.kt",
    )
    card(
        d,
        cells[3],
        "04  Picture",
        "Live + Tune",
        [
            "FilterEngine decides who appears. Tune (top right) is Display: Radar, Strength list, Timeline, Hybrid, By class — plus sort and fields.",
            "Live cap is about 400 radios. Filters hide radios; Display hides fields.",
        ],
        PHOS,
        files="ui/LiveScreens.kt  ·  domain/FilterEngine.kt",
    )
    card(
        d,
        cells[4],
        "05  Attention",
        "Watchlist and Hunt",
        [
            "Bookmark a signature to beep and/or speak. Extra attention families and drones ship watched. Hunt walks one BLE radio by RSSI.",
            "Named radios are one-MAC aliases with an optional alert.",
        ],
        AMBER,
        files="alert/Alerter.kt  ·  domain/Hunt.kt",
    )
    card(
        d,
        cells[5],
        "06  Keep / share",
        "Log, sits, TAK",
        [
            "Rotating log on disk. A sit is a named window of everything heard. Debrief and AI Export use the open sit or last 15 minutes.",
            "TAK / CoT is off by default. Privacy mode masks the screen and pauses the feed; the log still holds full MACs and GPS.",
        ],
        PHOS,
        files="data/LogStore.kt  ·  data/SitStore.kt  ·  domain/TakPublish.kt",
    )

    # arrows between cells
    def mid_right(box):
        return box[2], (box[1] + box[3]) / 2

    def mid_left(box):
        return box[0], (box[1] + box[3]) / 2

    def mid_bot(box):
        return (box[0] + box[2]) / 2, box[3]

    def mid_top(box):
        return (box[0] + box[2]) / 2, box[1]

    for a, b in ((0, 1), (1, 2), (3, 4), (4, 5)):
        x1, y = mid_right(cells[a])
        x2, _ = mid_left(cells[b])
        arrow_h(d, x1 + 6, x2 - 6, y)

    # footer
    d.line((PAD, H - 58, W - PAD, H - 58), fill=RULE, width=1)
    d.text(
        (PAD + 8, H - 30),
        "Not in earshot: Wi-Fi clients  ·  Classic Bluetooth  ·  cellular / LTE / C-V2X     Offline-first. No Fieldwatch server. No account. No telemetry.",
        font=F(18),
        fill=MUTED,
        anchor="lm",
    )
    d.text((W - PAD, H - 30), "app.fieldwatch", font=M(16), fill=PHOS, anchor="rm")

    img.save(OUT, "PNG")
    print("wrote", OUT, img.size)


if __name__ == "__main__":
    build()
