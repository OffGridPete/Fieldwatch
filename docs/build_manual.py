#!/usr/bin/env python3
"""Build the Fieldwatch user manual and technical documentation PDF."""

from __future__ import annotations

import os
import shutil
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    CondPageBreak,
    Frame,
    Image,
    KeepTogether,
    ListFlowable,
    ListItem,
    NextPageTemplate,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)
from reportlab.platypus.flowables import Flowable, ImageAndFlowables
from reportlab.platypus.tableofcontents import TableOfContents

DOC_DIR = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(DOC_DIR, "Fieldwatch_User_Manual.pdf")
DIST_OUT = os.path.join(os.path.dirname(DOC_DIR), "dist", "Fieldwatch_User_Manual.pdf")
ICON = os.path.join(os.path.dirname(DOC_DIR), "app", "src", "main", "res", "mipmap-xxxhdpi", "ic_launcher.png")
SHOTS = os.path.join(DOC_DIR, "screenshots")
DIAGRAMS = os.path.join(DOC_DIR, "diagrams")
PRIVACY_NOTE = ""

INK = colors.HexColor("#12171C")
MUTED = colors.HexColor("#4A5560")
RULE = colors.HexColor("#C5CDD4")
PANEL = colors.HexColor("#F4F6F8")
ACCENT = colors.HexColor("#0B7A48")
ACCENT_DK = colors.HexColor("#063D26")
NIGHT = colors.HexColor("#0B0F14")
PHOS = colors.HexColor("#3DFF9A")
AMBER = colors.HexColor("#C47A00")
WARN_BG = colors.HexColor("#FFF6E5")
NOTE_BG = colors.HexColor("#E8F5EE")
HEADER_BG = colors.HexColor("#E6EEE9")
PAGE_W, PAGE_H = letter


def styles():
    base = getSampleStyleSheet()
    s = {
        "cover_kicker": ParagraphStyle(
            "cover_kicker", fontName="Helvetica", fontSize=9, leading=12,
            textColor=PHOS, alignment=TA_CENTER, tracking=2, letterSpacing=1.4,
        ),
        "h1": ParagraphStyle(
            "H1", fontName="Helvetica-Bold", fontSize=16, leading=20,
            textColor=ACCENT_DK, spaceBefore=16, spaceAfter=8, keepWithNext=0,
        ),
        "h2": ParagraphStyle(
            "H2", fontName="Helvetica-Bold", fontSize=12.5, leading=16,
            textColor=INK, spaceBefore=12, spaceAfter=5, keepWithNext=0,
        ),
        "h3": ParagraphStyle(
            "H3", fontName="Helvetica-Bold", fontSize=11, leading=14,
            textColor=ACCENT_DK, spaceBefore=9, spaceAfter=4, keepWithNext=0,
        ),
        "body": ParagraphStyle(
            "Body", fontName="Helvetica", fontSize=9.5, leading=13,
            textColor=INK, alignment=TA_JUSTIFY, spaceAfter=7,
        ),
        "body_left": ParagraphStyle(
            "BodyLeft", fontName="Helvetica", fontSize=9.5, leading=13,
            textColor=INK, alignment=TA_LEFT, spaceAfter=6,
        ),
        "bullet": ParagraphStyle(
            "Bullet", fontName="Helvetica", fontSize=9.5, leading=13,
            textColor=INK, leftIndent=14, bulletIndent=0, spaceAfter=3,
        ),
        "caption": ParagraphStyle(
            "Caption", fontName="Helvetica-Oblique", fontSize=8, leading=10,
            textColor=MUTED, spaceBefore=2, spaceAfter=10,
        ),
        "cell": ParagraphStyle(
            "Cell", fontName="Helvetica", fontSize=8, leading=10.5, textColor=INK,
        ),
        "cell_b": ParagraphStyle(
            "CellB", fontName="Helvetica-Bold", fontSize=8, leading=10.5, textColor=INK,
        ),
        "toc1": ParagraphStyle(
            "TOC1", fontName="Helvetica-Bold", fontSize=10.5, leading=16,
            textColor=INK, spaceBefore=3,
        ),
        "toc2": ParagraphStyle(
            "TOC2", fontName="Helvetica", fontSize=9.5, leading=13,
            textColor=MUTED, leftIndent=14,
        ),
        "callout": ParagraphStyle(
            "Callout", fontName="Helvetica", fontSize=9, leading=12.5,
            textColor=INK, leftIndent=4, rightIndent=4,
        ),
        "mono": ParagraphStyle(
            "Mono", fontName="Courier", fontSize=8, leading=11, textColor=INK,
            backColor=PANEL, leftIndent=6, rightIndent=6, spaceBefore=4, spaceAfter=8,
        ),
        "footer": ParagraphStyle(
            "Footer", fontName="Helvetica", fontSize=8, textColor=MUTED,
        ),
    }
    return s


S = styles()


def P(text, style="body"):
    return Paragraph(text, S[style])


def bullets(items):
    return ListFlowable(
        [ListItem(Paragraph(i, S["body_left"]), leftIndent=12, bulletColor=ACCENT) for i in items],
        bulletType="bullet",
        start="•",
        leftIndent=16,
        bulletFontName="Helvetica",
        bulletFontSize=9,
        spaceBefore=2,
        spaceAfter=8,
    )


def numbered(items):
    return ListFlowable(
        [ListItem(Paragraph(i, S["body_left"]), leftIndent=16) for i in items],
        bulletType="1",
        leftIndent=18,
        bulletFontName="Helvetica",
        bulletFontSize=9,
        spaceBefore=2,
        spaceAfter=8,
    )


def callout(title, body, kind="note"):
    bg = NOTE_BG if kind == "note" else WARN_BG
    bar = ACCENT if kind == "note" else AMBER
    data = [[P(f"<b>{title}.</b> {body}", "callout")]]
    t = Table(data, colWidths=[6.5 * inch])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), bg),
        ("BOX", (0, 0), (-1, -1), 0.4, bar),
        ("LINEBEFORE", (0, 0), (0, -1), 3, bar),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ]))
    return KeepTogether([Spacer(1, 4), t, Spacer(1, 8)])


def _phone(name, width):
    path = os.path.join(SHOTS, name)
    ir = ImageReader(path)
    iw, ih = ir.getSize()
    img = Image(path, width=width, height=width * ih / float(iw))
    img.hAlign = "CENTER"
    return img


def figure(name, caption, width=2.45 * inch):
    return KeepTogether([
        Spacer(1, 8),
        _phone(name, width),
        P(caption + PRIVACY_NOTE, "caption"),
    ])


def _diagram(name, width):
    path = os.path.join(DIAGRAMS, name)
    ir = ImageReader(path)
    iw, ih = ir.getSize()
    img = Image(path, width=width, height=width * ih / float(iw))
    img.hAlign = "CENTER"
    return img


def diagram(name, caption, width=6.9 * inch):
    return KeepTogether([
        Spacer(1, 8),
        _diagram(name, width),
        P(caption, "caption"),
        Spacer(1, 8),
    ])


class CaptionedPhone(Flowable):
    """Image + caption stacked; ImageAndFlowables needs _restrictSize."""

    def __init__(self, name, caption, width, privacy=True):
        super().__init__()
        self.img = _phone(name, width)
        note = PRIVACY_NOTE if privacy else ""
        self.cap = P(caption + note, "caption")
        self._box_w = width

    def _restrictSize(self, availWidth, availHeight):
        return self.wrap(availWidth, availHeight)

    def _unRestrictSize(self):
        return None

    def wrap(self, availWidth, availHeight):
        iw, ih = self.img.wrap(self._box_w, availHeight)
        cw, ch = self.cap.wrap(iw, availHeight)
        self._iw, self._ih, self._ch = iw, ih, ch
        return iw, ih + 6 + ch

    def draw(self):
        self.img.drawOn(self.canv, 0, self._ch + 6)
        self.cap.drawOn(self.canv, 0, 0)


def figure_wrap(name, caption, *items, width=2.2 * inch, privacy=True):
    """Phone screenshot on the right; body text wraps on the left, then full width below.

    Later same-section paragraphs are folded in by wrap_body_around_figures() so they
    keep using the leftover column beside the phone instead of starting under it.
    """
    flows = []
    for it in items:
        if isinstance(it, str):
            flows.append(P(it))
        else:
            flows.append(it)
    if not flows:
        flows.append(P(""))
    return ImageAndFlowables(
        CaptionedPhone(name, caption, width, privacy=privacy),
        flows,
        imageSide="right",
        imageLeftPadding=12,
        imageRightPadding=0,
        imageTopPadding=2,
        imageBottomPadding=10,
    )


_WRAP_STYLES = {"Body", "BodyLeft"}
_HEADING_STYLES = {"H1", "H2", "H3"}
# Enough room for the heading plus several lines of body. keepWithNext is off on
# headings so a following multi-page table is not glued to the title.
_HEADING_KEEP = {"H1": 2.35 * inch, "H2": 2.15 * inch, "H3": 1.95 * inch}


def _is_heading(item):
    return isinstance(item, Paragraph) and item.style.name in _HEADING_STYLES


def _contains_image(item):
    if isinstance(item, (Image, ImageAndFlowables, CaptionedPhone)):
        return True
    content = getattr(item, "_content", None) or getattr(item, "content", None)
    if content:
        return any(_contains_image(sub) for sub in content)
    if isinstance(item, Table):
        for row in getattr(item, "_cellvalues", []) or []:
            for cell in row:
                if _contains_image(cell):
                    return True
    return False


def prevent_orphan_headings(flow):
    """Do not leave a heading as the last line on a page.

    keepWithNext is not enough when the next flowable is a tall screenshot: the
    heading fits, the figure does not, and the heading sits alone at the bottom.
    Pull the heading into a following figure so they travel together. Other
    headings get a conditional page break so at least a few lines of body fit
    under them.
    """
    out = []
    i = 0
    n = len(flow)
    while i < n:
        item = flow[i]
        if _is_heading(item):
            item.keepWithNext = 0
            keep = _HEADING_KEEP.get(item.style.name, 2.1 * inch)
            j = i + 1
            skipped = []
            while j < n and isinstance(flow[j], Spacer):
                skipped.append(flow[j])
                j += 1
            nxt = flow[j] if j < n else None
            if isinstance(nxt, ImageAndFlowables):
                nxt._content = [item] + list(nxt._content or [])
                out.append(nxt)
                i = j + 1
                continue
            if nxt is not None and _contains_image(nxt) and isinstance(nxt, KeepTogether):
                nxt._content = [item] + skipped + list(nxt._content or [])
                out.append(nxt)
                i = j + 1
                continue
            out.append(CondPageBreak(keep))
            out.append(item)
            i += 1
            continue
        out.append(item)
        i += 1
    return out


def wrap_body_around_figures(flow):
    """Keep body copy wrapping beside a screenshot until the next heading or chapter.

    ImageAndFlowables only wraps the flowables given to it. Call sites pass the first
    paragraph, so the rest of the section started at full width under the phone. Fold
    following Body / BodyLeft paragraphs and lists into the wrap; stop at H1–H3,
    tables, callouts, page breaks, and the next figure.
    """
    out = []
    i = 0
    n = len(flow)
    while i < n:
        item = flow[i]
        if isinstance(item, ImageAndFlowables):
            extra = []
            j = i + 1
            while j < n:
                nxt = flow[j]
                if isinstance(nxt, Paragraph) and nxt.style.name in _WRAP_STYLES:
                    extra.append(nxt)
                    j += 1
                    continue
                if isinstance(nxt, ListFlowable):
                    extra.append(nxt)
                    j += 1
                    continue
                break
            if extra:
                item._content = list(item._content) + extra
            out.append(item)
            i = j
            continue
        out.append(item)
        i += 1
    return out


def figure_pair(left_name, left_cap, right_name, right_cap, width=2.48 * inch):
    left = _phone(left_name, width)
    right = _phone(right_name, width)
    t = Table(
        [
            [left, right],
            [P(left_cap + PRIVACY_NOTE, "caption"), P(right_cap + PRIVACY_NOTE, "caption")],
        ],
        colWidths=[3.25 * inch, 3.25 * inch],
    )
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("ALIGN", (0, 0), (-1, 0), "CENTER"),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("TOPPADDING", (0, 0), (-1, -1), 2),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
    ]))
    return KeepTogether([Spacer(1, 8), t, Spacer(1, 6)])


NOTICE_SHORT = (
    "Experimental software. Use at your own risk. Fieldwatch does not guarantee that "
    "trackers, cameras, tags, or any other device will be found. Each phone has radio "
    "and OEM limits that this app cannot overcome. Do not use Fieldwatch in any situation "
    "where safety is in question, including life-threatening situations."
)


def table(headers, rows, widths):
    head = [Paragraph(f"<b>{h}</b>", S["cell_b"]) for h in headers]
    body = [[Paragraph(str(c), S["cell"]) for c in row] for row in rows]
    t = Table([head] + body, colWidths=widths, repeatRows=1)
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), HEADER_BG),
        ("TEXTCOLOR", (0, 0), (-1, 0), ACCENT_DK),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("GRID", (0, 0), (-1, -1), 0.35, RULE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, PANEL]),
    ]))
    return t


def draw_ig_mark(c, x, y, s, color=PHOS):
    """Simple camera-outline mark. Origin is the left of the glyph, vertical center y."""
    c.saveState()
    c.setStrokeColor(color)
    c.setFillColor(color)
    c.setLineWidth(0.9)
    c.roundRect(x, y - s * 0.55, s * 1.15, s * 1.1, s * 0.22, fill=0, stroke=1)
    c.circle(x + s * 0.58, y, s * 0.28, fill=0, stroke=1)
    c.circle(x + s * 0.92, y + s * 0.32, s * 0.08, fill=1, stroke=0)
    c.restoreState()


def draw_x_mark(c, x, y, s, color=PHOS):
    c.saveState()
    c.setStrokeColor(color)
    c.setLineWidth(1.15)
    c.setLineCap(1)
    c.line(x, y + s * 0.5, x + s, y - s * 0.5)
    c.line(x, y - s * 0.5, x + s, y + s * 0.5)
    c.restoreState()


def draw_cover(c, doc):
    c.saveState()
    c.setFillColor(NIGHT)
    c.rect(0, 0, PAGE_W, PAGE_H, fill=1, stroke=0)
    c.setFillColor(PHOS)
    c.rect(0, 0, 10, PAGE_H, fill=1, stroke=0)
    c.setStrokeColor(colors.HexColor("#1A222C"))
    c.setLineWidth(0.6)
    cx, cy, r = PAGE_W / 2, PAGE_H * 0.58, 92
    for i in range(1, 5):
        c.circle(cx, cy, r * i / 4, fill=0, stroke=1)
    c.line(cx - r, cy, cx + r, cy)
    c.line(cx, cy - r, cx, cy + r)
    c.setStrokeColor(PHOS)
    c.setLineWidth(1.4)
    c.line(cx, cy, cx + 62, cy + 62)
    c.setFillColor(AMBER)
    c.circle(cx + 54, cy + 54, 3.2, fill=1, stroke=0)
    if os.path.exists(ICON):
        try:
            c.drawImage(ICON, PAGE_W - 92, PAGE_H - 92, width=56, height=56,
                        mask="auto", preserveAspectRatio=True)
        except Exception:
            pass
    c.setFillColor(PHOS)
    c.setFont("Helvetica", 9)
    c.drawString(48, PAGE_H - 52, "USER MANUAL  ·  TECHNICAL DOCUMENTATION")
    c.setFillColor(colors.HexColor("#E8EEF2"))
    c.setFont("Helvetica-Bold", 42)
    c.drawString(48, PAGE_H - 118, "FIELDWATCH")
    c.setFillColor(PHOS)
    c.setFont("Helvetica", 13)
    c.drawString(48, PAGE_H - 142, "Passive Signal Intelligence for Android")
    c.setStrokeColor(colors.HexColor("#2A3340"))
    c.setLineWidth(0.8)
    c.line(48, PAGE_H - 160, PAGE_W - 48, PAGE_H - 160)
    c.setFillColor(colors.HexColor("#C8D0D8"))
    c.setFont("Helvetica", 10)
    y = 210
    for line in (
        "Receive-only observation of publicly broadcast Wi-Fi and Bluetooth LE.",
        "Offline-first. No cloud. No account. No telemetry.",
        "Pattern matching, visualization, alerts, and local logging.",
        "Experimental. Use at your own risk. Not for safety-critical use.",
    ):
        c.drawString(48, y, line)
        y -= 16
    c.setFillColor(MUTED)
    c.setFont("Helvetica", 9)
    c.drawString(48, 108, "Version 1.1.10")
    c.drawString(48, 94, "26 September 2026")
    c.drawString(48, 80, "Package  app.fieldwatch   ·   Android 10+ (API 29)   ·   Target API 35")
    c.setStrokeColor(colors.HexColor("#2A3340"))
    c.setLineWidth(0.6)
    c.line(48, 68, PAGE_W - 48, 68)
    c.setFillColor(colors.HexColor("#E8EEF2"))
    c.setFont("Helvetica", 10)
    c.setFillColor(MUTED)
    c.setFont("Helvetica", 8)
    c.drawString(48, 50, "Copyright (c) 2026 Off Grid Pete LLC. All rights reserved.")
    c.setFillColor(PHOS)
    c.setFont("Helvetica", 8)
    draw_ig_mark(c, 48, 20, 7)
    c.setFillColor(colors.HexColor("#C8D0D8"))
    c.setFont("Helvetica", 8)
    c.drawString(62, 18, "@OffGridPete")
    draw_x_mark(c, 148, 20, 6.5)
    c.setFillColor(colors.HexColor("#C8D0D8"))
    c.drawString(162, 18, "@OGridPete")
    c.setFillColor(PHOS)
    c.setFont("Helvetica", 8)
    c.drawRightString(PAGE_W - 48, 50, "CONTROLLED FIELD DOCUMENT")
    c.restoreState()


def draw_body(c, doc):
    c.saveState()
    c.setFillColor(NIGHT)
    c.rect(0, PAGE_H - 28, PAGE_W, 28, fill=1, stroke=0)
    c.setFillColor(PHOS)
    c.setFont("Helvetica-Bold", 8)
    c.drawString(48, PAGE_H - 18, "FIELDWATCH")
    c.setFillColor(colors.HexColor("#9AA6B2"))
    c.setFont("Helvetica", 8)
    c.drawRightString(PAGE_W - 48, PAGE_H - 18, "User Manual & Technical Documentation")
    c.setStrokeColor(RULE)
    c.setLineWidth(0.4)
    c.line(48, 40, PAGE_W - 48, 40)
    c.setFillColor(MUTED)
    c.setFont("Helvetica", 8)
    c.drawString(48, 28, "v1.1.10  ·  Off Grid Pete LLC")
    draw_ig_mark(c, 148, 30, 5.2, MUTED)
    c.setFillColor(MUTED)
    c.setFont("Helvetica", 8)
    c.drawString(160, 28, "@OffGridPete")
    draw_x_mark(c, 238, 30, 5.0, MUTED)
    c.setFillColor(MUTED)
    c.drawString(250, 28, "@OGridPete")
    c.drawRightString(PAGE_W - 48, 28, f"{doc.page}")
    c.restoreState()


class FieldwatchDoc(BaseDocTemplate):
    def afterFlowable(self, flowable):
        def consider(item):
            if isinstance(item, Paragraph):
                name = item.style.name
                text = item.getPlainText()
                if name == "H1" and text != "Contents":
                    self.notify("TOCEntry", (0, text, self.page))
                elif name == "H2":
                    self.notify("TOCEntry", (1, text, self.page))
            elif isinstance(item, (ImageAndFlowables, KeepTogether)):
                for inner in list(getattr(item, "_content", []) or []):
                    consider(inner)

        consider(flowable)


def toc():
    t = TableOfContents()
    t.levelStyles = [S["toc1"], S["toc2"]]
    t.dotsMinLevel = 0
    return t


def story():
    flow = []
    flow += [NextPageTemplate("body"), PageBreak()]
    flow += [
        P("Notice — safety &amp; disclaimer", "h1"),
        callout(
            "Use at your own risk",
            "Fieldwatch is experimental software provided as-is under the MIT License, with no warranty of any kind, "
            "express or implied. It is for experimental and educational observation of "
            "publicly broadcast Wi-Fi and Bluetooth LE only. "
            "<b>Do not use Fieldwatch in any situation where safety is in question</b>, "
            "including life-threatening situations, personal-security decisions, "
            "or emergency response. An empty list, a missing signature name on a row, or a quiet Debrief "
            "does not mean you are safe. A match does not mean you have identified a "
            "person, a vehicle, or a threat.",
            "warn",
        ),
        P(
            "This is a hobby project, provided as-is under the MIT License. Use at your own risk. "
            "Using Fieldwatch is your responsibility. To the maximum extent permitted by law, Off Grid Pete LLC "
            "is not liable for indirect, incidental, special, consequential, or punitive damages arising from "
            "its use or inability to use this project."
        ),
        P(
            "There is no guarantee that trackers, cameras, tags, access points, or any other "
            "device will be found, named, or reported. Radios that are off, cellular-only, "
            "asleep, randomized, quiet, or outside what this handset’s OS exposes will not "
            "appear. Each phone has its own radios, firmware, scan quotas, and OEM battery "
            "policies, and software cannot address those limits."
        ),
        P(
            "Pattern matches, GPS co-travel (“Moving with you” / “possible tail”), Debrief "
            "language, and AI Export output are hypotheses — not identity, not a legal finding, "
            "and not a complete RF capture. You are solely responsible for how you use this "
            "app and this document, and for complying with local law. By using the software "
            "or this manual you accept these terms and the MIT License."
        ),
        P(
            "Location data, if tagging is on, is this phone at the moment the packet was heard "
            "(hear-time) — not the other radio. "
            "There is no Fieldwatch server; stamps stay on the handset until you share them. Logs "
            "keep full coordinates even when Privacy mode masks the screen and sit reports. "
            "Debrief, Log export, AI Export (sit or one radio), and radio-detail Share as text "
            "can take that path off the phone. A TAK / CoT feed, if you turn it on, sends "
            "markers (full MAC and coordinates) onto the LAN you configured; Privacy mode "
            "pauses that feed. Online place names use the system geocoder "
            "(often the OEM / Google network), not a Fieldwatch cloud. How you store, share, or "
            "publish those files is your responsibility."
        ),
        P("MIT License", "h2"),
        P("Copyright (c) 2026 Off Grid Pete LLC"),
        P(
            "Permission is hereby granted, free of charge, to any person obtaining a copy "
            "of this software and associated documentation files (the “Software”), to deal "
            "in the Software without restriction, including without limitation the rights "
            "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell "
            "copies of the Software, and to permit persons to whom the Software is "
            "furnished to do so, subject to the following conditions:"
        ),
        P(
            "The above copyright notice and this permission notice shall be included in all "
            "copies or substantial portions of the Software."
        ),
        P(
            "THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR "
            "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, "
            "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE "
            "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER "
            "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, "
            "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE "
            "SOFTWARE."
        ),
        P(
            "AndroidX, Kotlin, and related libraries remain Apache-2.0. IEEE and Bluetooth SIG "
            "assigned-number tables packed in this app are subject to those organizations’ terms. "
            "See NOTICE in the source tree."
        ),
        PageBreak(),
        P("Contents", "h1"),
        toc(),
        P("How this book is organized", "h3"),
        P(
            "Read chapters 4 and 5 first. Chapter 4 covers install, permissions, Quick start "
            "(the five tabs and Tune), and the max-collection checklist (§4.5); chapter 5 maps "
            "the screens. Chapter 8 is Filters — which radios the Live display shows, including "
            "Moving with you (§8.5). Chapter 9 is Signatures — which patterns are labeled. "
            "Chapter 12 is playbooks for a question you can actually ask. Chapter 14 is the "
            "pipeline on one page (how a hear becomes a row). The rest is reference: how the "
            "radios work, logging, field hygiene, and the stock catalog."
        ),
        table(
            ["If you want…", "Go to"],
            [
                ["Install, first launch, and Quick start", "Chapter 4, then §4.4"],
                ["How a hear becomes a row (pipeline)", "Chapter 14"],
                ["Hear as often as the phone allows (battery is the cost)", "§4.5"],
                ["What each tab and the tune icon is", "§4.4, then Chapter 5"],
                ["Fewer radios on the Live display, not quieter rows", "Chapter 8, then Live display → Display in §5.3"],
                ["Who is walking or driving with me", "§8.5 (how the test works) and §12.2 (the playbook)"],
                ["Turn a pattern family on or off", "Chapter 9"],
                ["Read cleartext BLE bytes (temp, Remote ID, …)", "§9.6 Decode fields"],
                ["Build my own decode map from a payload spec", "§9.6.1–§9.6.5"],
                ["Overlay radios on ATAK / TAK (CoT feed)", "§5.8 (configure) and §12.15 (sit)"],
                ["Path / sit compare / log export", "§5.6, then §11"],
                ["Custom name / Observer notes", "§5.5, Settings → Named radios"],
                ["Followed / new arrival / hunt / cameras", "Chapter 12"],
                ["A word you do not recognize", "Appendix glossary"],
            ],
            [3.2 * inch, 3.3 * inch],
        ),
        Spacer(1, 6),
        callout(
            "Screenshots",
            "Phone figures in this book were captured on a Galaxy A54 with "
            "<b>Settings → Privacy mode</b> on. MAC tails are **:**:** so this PDF does not "
            "publish full addresses, and GPS last-fix on detail is masked. Logs on the phone "
            "are unchanged. Turn Privacy mode off when you need the full MAC or coordinates "
            "on screen.",
            "note",
        ),
        PageBreak(),
    ]

    # 1 Introduction
    flow += [
        P("1. Introduction &amp; Background", "h1"),
        P("1.1 Purpose of Fieldwatch", "h2"),
        P(
            "I built Fieldwatch as a personal tool to look at what Wi-Fi access points and "
            "Bluetooth LE ads my phone was able to pick up, so I could better understand "
            "what devices were being used around me. It is passive: it only listens. There is "
            "no dongle, no account, and no backend server. I wanted something that would work "
            "offline in the field."
        ),
        P(
            "I wanted a modern interface that was easy to use; a display I could tune for the job; "
            "filters so I do not have to look at everything; a signature library I can extend on the fly; "
            "and reports of what was seen."
        ),
        P(
            "I have been using it and iterating on it for a while, and it has been useful "
            "enough that I thought I would share it."
        ),
        P(
            "This is a hobby — something I do for fun in my spare time. There is no Fieldwatch backend. There are no ads. "
            "Everything lives on the phone. You install an APK (sideload)."
        ),
        P(
            "If you spot an error, something stupid, or a feature idea — in the app or the documentation — "
            "please open a GitHub issue. This is how we make it better. I hope you find it as useful as I have. "
            "I look forward to hearing how it goes."
        ),
        P(
            "You do not need radio-school jargon to run it. This manual uses the app’s own "
            "words and spells them out. A section number such as §5.5 jumps to more detail. "
            "Read chapters 4 and 5 first; use chapter 12 when you have a question in the field. "
            "Chapter 14 is the one-page pipeline if you want the architecture before the screens."
        ),
        P(
            "The Notice after the cover is not optional. Fieldwatch is experimental, it misses "
            "radios, and it is not a safety system."
        ),
        P("What you will see — and what you will not", "h2"),
        P(
            "On the Live display, the glanceable mark on each row is the <b>circle with a class glyph</b> — "
            "Finder tags, Phones / PCs, Audio, Home IoT, and so on. Unmatched radios show a "
            "question mark. The same glyphs sit on Filters class chips and on By class headers. "
            "There are still two radio kinds. On the Live display they are a small <b>Wi-Fi</b> or "
            "<b>Bluetooth</b> icon at the start of the subtitle (the small second line), not "
            "two-letter AP/LE tags and not the row’s main mark:"
        ),
        bullets([
            "<b>AP (Wi-Fi access point).</b> A radio that is beaconing a network name (SSID): a home router, a café hotspot, a mesh node, a phone in hotspot mode, some cameras and IoT gadgets that advertise Wi-Fi. A laptop or phone that is only <i>joined</i> to someone else’s Wi-Fi does <b>not</b> appear. Stock Android cannot show those clients.",
            "<b>LE (Bluetooth Low Energy advertiser).</b> A gadget that is broadcasting BLE packets: headphones, a phone, an AirTag-class tracker, a speaker, a watch, a lot of unnamed BLE rows. Fieldwatch listens; it does not pair or connect for detection.",
        ]),
        P(
            "Display → Subtitle None hides that second line (and the radio-kind icon with it) so more rows "
            "fit; the class glyph and the title stay. Detail spells the kind in words "
            "(Wi-Fi access point / BLE advertiser). The scan notification still says "
            "“N Wi-Fi · N BLE · N signatures.” §5.3, §5.4."
        ),
        P(
            "The number on the right of a list row is <b>RSSI</b> in dBm — how loud that radio is "
            "<i>at this phone</i>, not a tape measure. Closer to 0 is louder (for example −40 is "
            "much louder here than −90). Walls, your body, a car, and the gadget’s own transmit "
            "power all move that number. Hunt (§5.5, §12.8, §12.13) uses changes in loudness, "
            "not meters."
        ),
        P("A few words used throughout", "h2"),
        bullets([
            "<b>Class glyph.</b> The circle on a Live display row. First matching signature class, or <font face='Courier'>?</font> if unmatched. That is how you scan the list. §5.4, §9.5.",
            "<b>AP / LE.</b> The two radio kinds. AP = Wi-Fi access point (beaconing a network, not a client). LE = BLE advertiser. On the Live display they are the small Wi-Fi / Bluetooth icons on the subtitle, not two-letter tags and not the circle. Subtitle None drops them with that line. The two bullets above are the full meaning.",
            "<b>MAC.</b> The radio address. Default first line of a Live display row (Display → Title). You can move it to the subtitle, or hide the second line. BLE phones often randomize it, so a “new” MAC can be the same physical gadget with a new name tag.",
            "<b>Signature.</b> A named pattern Fieldwatch looks for (AirTag, Flock, Tile, …). A chip on a row means the pattern hit — not that you have identified a person or a serial number. Signatures always label; Filters decide what the Live display shows.",
            "<b>Filter vs log.</b> Filters only change what the Live display shows. The rotating log, if logging is on, still writes what was heard. Hiding AirTags on the Live display does not delete them from the file.",
            "<b>Debrief vs log.</b> Debrief is the sit report of radios still in memory (last 15 minutes, about 400) unless a named sit is selected. Log export is the rotating session file. Sit export is the roster of that window. On a drive without a sit, tap Debrief more than once. §5.6, §11.4.1.",
            "<b>Stale / gone.</b> No packet for longer than Settings → Stale after (and Display → Brief hold). The row can stay on screen dimmed. It does not mean the gadget is powered off forever.",
            "<b>Watch / bookmark.</b> A pip and/or spoken phrase when that signature family or that one MAC appears. Voice can say the class, the signature name, or both (Settings → What to say). Detail bookmark is one radio; it does not auto-delete when the radio leaves (§5.5, §10.1–10.2.1).",
            "<b>Sit.</b> One session of watching radios — a room, a walk, or a drive. Reports → Start sit names the window. Path, Debrief, Sit export, Compare this-sit, and AI Export use it. If you never start one, those reports use last 15 minutes in memory. Log export is still the session file. §5.6.",
            "<b>Chip.</b> The colored signature name on a Live display row. A pattern hit, not identity.",
            "<b>OUI.</b> The first three bytes of a MAC, assigned to a vendor. The same module vendor shows up in many products, so an OUI-only match is a weak guess.",
        ]),
        P("1.2 Design philosophy", "h2"),
        bullets([
            "<b>Passive only.</b> Fieldwatch listens. It does not deauthenticate, probe inject, "
            "pair, connect, or join a network as part of detection.",
            "<b>Offline-first.</b> Configuration, signature definitions, watchlists, and logs live "
            "in the app private directory. There is no account and no backend.",
            "<b>Privacy-focused.</b> Backup is disabled in the manifest. No analytics SDK is included.",
            "<b>Named patterns, not identity.</b> A match is a pattern hit (OUI, name, UUID, "
            "manufacturer data, or co-occurrence). It is not proof of a person, a vehicle, or "
            "a specific unit serial.",
            "<b>You can change the defaults.</b> First launch writes a full signature catalog. Every "
            "signature and preset can be changed or deleted afterward.",
            "<b>You can change how the list looks.</b> The Live display is not a fixed dump of every field. "
            "Display (the tune icon on the Live display) lets you show as much or as little of each radio as "
            "you need. Filters hide radios; Display hides fields on the row. Two people on the "
            "same sit can run two different lists. The detail page still has the full decode.",
        ]),
        P("1.3 Intended use cases", "h2"),
        table(
            ["Use case", "What Fieldwatch is for"],
            [
                ["Privacy awareness", "See which advertised Wi-Fi and BLE sources are around you and whether any match known tracker or camera patterns."],
                ["Counter-surveillance hygiene", "Notice when a named signature appears, stays, or comes back after you move. Confirm with RSSI trend and presence over time, not a single packet. After a walk with GPS tagging, Debrief’s tracking section still covers the last 15 minutes, even if you change Live display view or Filters."],
                ["Field observation", "Walk or sit a location, watch live strength, and export a timestamped log for later review."],
                ["Signature pattern recognition", "Define a signature from an observed radio and reuse it: OUI family, SSID glob, BLE UUID, or manufacturer payload."],
                ["After-action review", "Use timeline, device history, and Reports → Log (CSV / JSON lines / GPX / KML / WiGLE) to reconstruct when an emitter appeared and faded."],
                ["Field playbooks", "Chapter 12: followed on a walk, new arrival in a room, Hunt a BLE radio / vehicle sweep, one signature family, hide clutter, own kit vs a candidate tag, camera/ALPR sit, Extra attention (card-reader / pentest kit), then Debrief / AI Export / Log export."],
            ],
            [1.7 * inch, 4.8 * inch],
        ),
        Spacer(1, 8),
        callout(
            "Not a weapon system",
            "Fieldwatch is an observer. It cannot disable cameras, unlock trackers, or identify people. "
            "Treat matches as hypotheses that need corroboration (sightline, second radio, or later revisit).",
            "note",
        ),
        P("1.4 Status of this software", "h2"),
        callout("Experimental — not for safety-critical use", NOTICE_SHORT, "warn"),
        P(
            "The full terms are on the Notice page immediately after the cover. The intended uses "
            "in §1.3 are experimental observation and after-action review, not personal "
            "protection or emergency decisions."
        ),
    ]

    # 2 Research
    flow += [
        PageBreak(),
        P("2. Research &amp; Design Decisions", "h1"),
        P("2.1 Research inputs", "h2"),
        P(
            "The default signature catalog and matching rules are compiled from public sources, "
            "not from proprietary vendor documentation. Treat every component-vendor OUI as a "
            "guess — the same chip shows up in many products."
        ),
        table(
            ["Source class", "What it contributed"],
            [
                ["IEEE MA-L registry", "B4:1E:52 is registered to Flock Safety (9 May 2024). This is the only high-confidence Flock-assigned OUI in the default set."],
                ["Independent ALPR / DeFlock research", "Field OUI lists for LiteOn camera radios and Silicon Labs battery packs commonly seen on Flock hardware; SSID patterns Flock-XXXXXX, FS Ext Battery, Penguin, Pigvision."],
                ["GainSec / firmware write-ups", "Raven BLE service UUID range 0x3100–0x3500 and manufacturer ID 0x09C8 (XUNTONG) as stronger digital fingerprints than OUI alone."],
                ["BLE tracker conventions", "Apple 0x004C Offline Finding 0x12; Samsung SmartTag FD5A / 0x0075; Tile 0x00C7 and FEED/FEDD. Chipolo and Pebblebee/moto tag are name-only Find Hub locators (stock rows, on)."],
                ["Android platform docs", "WifiManager scan throttling, BLE ScanSettings, permission model (location, NEARBY_WIFI_DEVICES, BLUETOOTH_SCAN), foreground-service types."],
            ],
            [1.9 * inch, 4.6 * inch],
        ),
        Spacer(1, 6),
        P("2.2 Why these detection methods", "h2"),
        P(
            "Stock Android exposes two public, legal, receive-only surfaces that a normal "
            "app can use without root:"
        ),
        bullets([
            "<b>Wi-Fi scan results</b> — BSSID, SSID (or hidden), RSSI, frequency/channel, capabilities, and on recent APIs a Wi-Fi standard / channel-width hint. Every result is an access-point-class beacon (infrastructure AP, hotspot, mesh node, IoT soft-AP). Associated clients are not in this list.",
            "<b>BLE advertisements</b> — address, local name, RSSI, service UUIDs, manufacturer-specific data, raw advertisement bytes, PHY/TX metadata when the stack provides them.",
        ]),
        P(
            "Those are the only two collection surfaces. Fieldwatch does not run Bluetooth Classic "
            "inquiry (<font face='Courier'>startDiscovery()</font>). That API transmits inquiry "
            "packets and is a different radio from BLE advertisements. Modules that only answer "
            "Classic (HC-05 / HC-06 and similar) will not appear. §3.5, §7.2."
        ),
        P(
            "Those fields are enough to implement a named-pattern engine, and not enough "
            "for direction finding, station-side Wi-Fi sniffing, or decryption. The engine "
            "therefore matches on OUI / MAC prefix, name or glob, 16-bit or 128-bit service UUID, "
            "manufacturer company ID, manufacturer data prefix, radio kind, hidden SSID, and "
            "optional multi-device co-occurrence (shared OUI and/or sequential MAC tails)."
        ),
        P("2.3 Why these visualizations", "h2"),
        P(
            "Five views share the same filtered set of radios, so you can change how things look "
            "without changing what was collected. You can also change each list row: Display "
            "(the tune icon on the Live display) is where you pick identity, order, and extra facts. Filters "
            "decide <i>which radios</i> appear on the Live display; Display decides <i>how much of each radio</i> "
            "you see. Logging is not affected."
        ),
        bullets([
            "<b>Classic radar</b> is a polar plot of RSSI, not a PPI with real azimuth. It is the fastest way to see “how many, how close, which signature.” Angle is a stable hash of the MAC so the same radio stays in the same slice. The bright sweep line leads; the shaded fan trails behind it. Radar labels are not Title/Subtitle; they stay short signature / name guesses.",
            "<b>Strength list</b> is the working view for triage. Default row is MAC over Name + type, RSSI on the right, optional bars and signature chips. Title line, Subtitle line (including None), Sort, Brief hold, Frequency under RSSI, first/last, and the extra switches all live on Display. Vendor is on detail, not the list. Show as little as a one-line name, or as much as name + MAC + bar + sparkline + ages.",
            "<b>Timeline</b> answers “when did it appear and leave” over a 15-minute presence strip. A continuous on-air radio grows one solid bar; a real dropout (~45 s) then a return starts a new segment. Same Title/Subtitle as the list.",
            "<b>Hybrid</b> keeps the ranked list and adds a full-width RSSI sparkline on a fixed −30 to −100 dBm grid (packet order, newest on the right) plus the same trend mark.",
            "<b>By class</b> is an outline of the same filtered set: every class A–Z by name, then signatures A–Z, then radios. Show all (default) keeps empty classes so the list does not jump; Collapse empty hides zeros. Those chips sit at the top of the list and scroll away with it. Tap a class, then a signature, then a radio for detail. Unmatched is last. Radio rows use the same Display switches as the strength list. A radio that matched two classes is counted in both; the header still says how many unique radios are on the Live display.",
        ]),
        P("2.4 Trade-offs: Android phone vs dedicated sniffer hardware", "h2"),
        table(
            ["Dimension", "Android handset (Fieldwatch)", "Typical ESP32 sniffer"],
            [
                ["Radios", "Independent Wi-Fi (2.4/5/6 GHz) and BLE 5.x; both can run at once.", "Usually one 2.4 GHz radio; Wi-Fi and BLE must time-slice."],
                ["Wi-Fi visibility", "Access points only. No promiscuous STA / probe capture on stock Android.", "Promiscuous mode can see stations, probes, and hidden-SSID clients."],
                ["Scan rate", "OS-throttled. Foreground + location helps; background is limited.", "Channel hop under operator control."],
                ["BLE", "Low-latency observer, extended ads, manufacturer + service data.", "Observer possible; less RAM for history."],
                ["Persistence", "Large local store, export via share sheet, notifications.", "microSD / LittleFS; limited UI."],
                ["Direction", "None (no AoA API).", "None unless extra hardware is added."],
                ["Legal / TOS surface", "Uses public scan APIs; still subject to local law.", "Same receive-only constraint if the firmware does not transmit."],
            ],
            [1.35 * inch, 2.55 * inch, 2.6 * inch],
        ),
        Spacer(1, 6),
        P(
            "Fieldwatch takes the phone’s advantages — dual radios, 5 GHz APs, notifications, "
            "unlimited rule counts, history — and does not pretend to have the ESP32’s "
            "promiscuous Wi-Fi view. If an investigation needs station-side frames, use a "
            "dedicated sniffer in parallel and treat Fieldwatch as the BLE + AP overlay."
        ),
    ]

    # 3 Limitations
    flow += [
        PageBreak(),
        P("3. Limitations", "h1"),
        P("3.1 Android platform restrictions", "h2"),
        bullets([
            "<b>Wi-Fi scan throttle.</b> Android rate-limits WifiManager.startScan() to about four scans per two minutes. Fieldwatch therefore spaces requests at about 30 / 40 / 55 seconds (high / balanced / saver) and tracks the quota so it does not burst then black out. Wi-Fi is always a batch-then-wait radio — there is no continuous AP stream on stock Android. Settings → Faster Wi-Fi AP scans can drop that to about 8 s, but only after Developer options → Wi-Fi scan throttling is off and Fieldwatch has read that OS switch (Android 11+). Until then the stock cadence is the ceiling. A miss between batches is a miss for signatures: an interesting AP (Cradlepoint IBR*, AirLink OUI, UniFi, Cisco, a hidden fleet SSID) is only labeled if a scan ran while you were in range. On a drive that window is short. §7.1.1, §10.3.1.",
            "<b>Location services must be on.</b> Without system Location enabled, BSSIDs and often BLE addresses are hidden or randomized further. Fine location is required even though Fieldwatch is not a mapping app.",
            "<b>NEARBY_WIFI_DEVICES and BLUETOOTH_SCAN</b> are required on Android 12/13+. Fieldwatch does not set the neverForLocation flag, because that flag would strip the identifiers the signature engine needs.",
            "<b>OEM battery managers</b> (notably Samsung One UI) will freeze background radios unless the app is exempted. Settings → Allow background usage, then Unrestricted battery. Some phones (Samsung among them) do not open onto Unrestricted — tap Allow background usage to click through and select it.",
            "<b>Samsung BLE suspend.</b> Long low-latency unfiltered scans are parked after a few minutes. Fieldwatch recycles the scanner on a duty cycle (~70 s on, a few seconds rest) and restarts if ads go silent for ~18 s. The subtitle may read BLE cycling or BLE parked · restarting.",
            "<b>No Wi-Fi stations.</b> Every Wi-Fi row is an access point (or a device beaconing like one: phone hotspot, mesh node, camera/IoT soft-AP). A phone, laptop, or camera that is only a client on someone else’s network does not appear.",
            "<b>No promiscuous or monitor mode.</b> Stock Android does not expose an 802.11 monitor interface to ordinary apps. Fieldwatch cannot put the Wi-Fi chip in promiscuous mode, cannot capture raw frames, cannot follow a BSSID on one channel, and cannot see management frames the OS scan does not already return. A USB sniffer or rooted/custom stack is a different tool.",
            "<b>No cellular RF.</b> Fieldwatch does not scan LTE/5G. A camera that only talks on the carrier network is invisible even if you are standing under it.",
            "<b>Dense crowds.</b> Phones rotate BLE addresses. Fieldwatch caps the live set at 400 radios (hard ceiling 900) and drops unnamed BLE after about three minutes so the heap does not collapse. Debrief reads that same map, so a few kilometers of city driving can already have replaced the start of the trip in RAM (§11.4.1).",
        ]),
        callout(
            "Wi-Fi detections are access points",
            "The small Wi-Fi icon at the start of a Wi-Fi row’s subtitle is literal (the circle "
            "is still the class glyph). Stock Android only reports scan results from radios that "
            "are beaconing an SSID (including hidden-SSID APs, which show with an empty name and "
            "the HIDDEN flag). Fieldwatch cannot see associated stations, wildcard probe requests, "
            "or a device that is on Wi-Fi but not advertising a network. A phone in hotspot or "
            "Wi-Fi Direct group-owner mode is an access point and will show; the same phone joined "
            "to a café network will not. Station-side work needs a dedicated sniffer.",
            "note",
        ),
        P("3.2 No direction finding", "h2"),
        P(
            "The phone does not expose angle of arrival. Radar angle is a hash of the MAC "
            "address so a given radio keeps a stable seat on the plot; it is not north, not "
            "left, and not a bearing. Distance from center is RSSI, which correlates only "
            "loosely with range — walls, body attenuation, and TX power dominate. "
            "Hunt (§5.5, §12.13) uses that same RSSI as a closer/further needle and, if you "
            "hold the phone against your body and turn, a crude heading from body-block. "
            "That is not DF and not a compass."
        ),
        P("3.3 Dependence on active broadcast", "h2"),
        P(
            "A radio that is powered down, in a sleep interval longer than the stale window, "
            "or advertising on a PHY/channel the stack is not reporting will not appear. "
            "Many Flock and similar ALPR units are cellular-first: they uplink over LTE/5G and "
            "only expose Wi-Fi during provisioning, a tech visit, or as a backup. Newer quiet "
            "installs may beacon never. Absence of a match is not absence of a camera. BLE "
            "trackers often advertise at a low duty cycle — they flicker in and out of the live set. "
            "See §7.6.1."
        ),
        P("3.4 Battery", "h2"),
        P(
            "High performance uses BLE SCAN_MODE_LOW_LATENCY (recycled about every 70 s) and "
            "asks for a Wi-Fi scan about every 30 s — the fastest cadence that stays under the "
            "OS four-scans-per-two-minutes cap. Balanced is ~40 s Wi-Fi / SCAN_MODE_BALANCED; "
            "Battery saver is ~55 s / SCAN_MODE_LOW_POWER. Faster Wi-Fi AP scans (Settings), only "
            "while Developer options has Wi-Fi scan throttling off, asks about every 8 s and uses "
            "more battery and heat; BLE cadence is unchanged. Keep screen on (Settings) holds the "
            "display while Fieldwatch is in front so Samsung does not park BLE for screen-off. "
            "The scan notification is required so Android will keep the scan service running. It is not optional decoration."
        ),
        P("3.5 What Fieldwatch cannot do", "h2"),
        bullets([
            "Intercept, decrypt, or read payload traffic.",
            "Deauth, jam, pair, or otherwise act on a target.",
            "Identify a person, a vehicle plate, or a specific camera serial from RF alone.",
            "Guarantee that an OUI match is Flock hardware. Only B4:1E:52 is IEEE-assigned to Flock Safety; other prefixes are component vendors used in many products.",
            "See associated Wi-Fi clients, hidden-SSID stations, wildcard probe requests, or frames addressed to a Flock MAC. Wi-Fi on Fieldwatch is AP beacons only.",
            "Enter promiscuous or 802.11 monitor mode, capture raw frames, or lock the phone onto one channel. The stock API is startScan() batches of APs.",
            "Hear a camera’s LTE/5G backhaul. Cellular is out of band for this app.",
            "Hear Bluetooth Classic (BR/EDR). Fieldwatch does not run Classic inquiry. A Classic-only serial module (HC-05 / HC-06) will not appear. Hobby BLE serial matches BLE advertisement names only. §7.2, §12.14.",
            "Build a true RF map with headings.",
            "Run without the operator granting location, nearby-devices, and notification permissions.",
            "Guarantee that a tracker, camera, or any other device is present or absent. A miss is normal. Do not treat Fieldwatch as a safety system.",
        ]),
        callout(
            "Confidence discipline",
            "Treat B4:1E:52 plus a Flock-* SSID or Raven UUID 0x3100–0x3500 as high confidence. "
            "Treat LiteOn / Silicon Labs / Raspberry Pi OUIs as low confidence unless a name, "
            "UUID, or manufacturer ID corroborates. Component OUIs ship in millions of unrelated devices.",
            "warn",
        ),
    ]

    # 4 Getting started
    flow += [
        PageBreak(),
        P("4. Getting Started", "h1"),
        P(
            "This chapter is the install and first-run path. Download "
            "<font face='Courier'>Fieldwatch.apk</font> from the Fieldwatch APK GitHub repository, then sideload it."
        ),
        P("4.1 Installation", "h2"),
        P(
            "Fieldwatch is not on the Play Store; you install an APK yourself (sideload). Package "
            "name <font face='Courier'>app.fieldwatch</font>. Minimum Android 10 (API 29); this "
            "build targets API 35. Tested on a Galaxy A54; meant for most Android 10+ phones. The app does not "
            "self-update, so keep a copy of the APK you trust."
        ),
        P(
            "<b>From the Fieldwatch APK repository (usual path).</b> Download "
            "<font face='Courier'>Fieldwatch.apk</font> from that repo. If you downloaded it on a "
            "computer, copy it to the phone (USB, Drive, Files). On the phone, open Files or "
            "Downloads and tap the APK. If Android blocks it: Settings → Apps → Special app "
            "access → Install unknown apps (Samsung: Settings → Security and privacy → Install "
            "unknown apps) and allow the app that opened the APK (Files, Chrome, Drive, …), "
            "then tap the APK again. Play Protect may warn that the app is not from Play — "
            "expected for a sideload. Install anyway only if you trust this file. If the "
            "repository lists a SHA-256, check it against the file you downloaded."
        ),
        P(
            "<b>From a computer (adb).</b> With USB debugging on:"
        ),
        P("adb install -r Fieldwatch.apk", "mono"),
        P(
            "When install finishes, open Fieldwatch from the launcher. Leave the persistent "
            "“Fieldwatch scanning” notification — Stop on that notification ends collection."
        ),
        P("4.2 Required permissions", "h2"),
        table(
            ["Permission", "Why Fieldwatch asks"],
            [
                ["Location (fine + coarse)", "Android will not return BSSIDs or useful BLE addresses without it. Tag detections with GPS (on by default) requests live GPS/network updates while scanning (last-known older than 30 s is ignored) and stamps log rows, Moving with you, and Debrief."],
                ["Nearby Wi-Fi devices (13+)", "Wi-Fi scan on current Android versions."],
                ["Bluetooth scan + connect (12+)", "BLE observer. Connect is required by the stack to read names on some devices; Fieldwatch does not create ACL connections for detection."],
                ["Notifications (13+)", "Persistent scan status and watchlist alerts."],
                ["Vibrate", "Watchlist haptic."],
                ["Foreground service (location + connected device)", "Keep both radios scanning when the UI is not in front."],
                ["Internet (install-time)", "Online place names and maps (Debrief/AI Export geocoder; Path OSM tiles) and, if you turn it on, the TAK / CoT UDP feed (§5.8). Fieldwatch has no account and does not call a Fieldwatch server."],
                ["Ignore battery optimizations", "Optional. Requested from Settings so OEM killers do not freeze the service."],
            ],
            [2.1 * inch, 4.4 * inch],
        ),
        Spacer(1, 6),
        P("4.3 First launch", "h2"),
        figure_wrap(
            "fig-tour.png",
            "Live tour — Tune, Pause, and the five tabs.",
            numbered([
            "A <b>Disclaimer and license</b> page appears once. Check <b>I have read this and I agree</b>, then Continue. Scanning starts after that. After permissions, Live shows a one-time overlay (Tune, Pause, the five tabs). Got it dismisses it. Settings → Show Live tour brings it back. The five tabs and Tune are §4.4.",
            "Grant the permission screen. Fieldwatch will not start radios until the required set is complete.",
            "A foreground notification <b>Fieldwatch scanning</b> appears. Leave it; dismissing via Stop ends collection.",
            "On first run the app writes <font face='Courier'>files/config.json</font> and loads the stock catalog, presets, bookmarks, and Settings. Later launches reload that file. Settings → Export signatures / Export settings share the catalog and switches. Neither pack includes logs or GPS (§5.7, §9.3).",
            "The Live display opens on the last view mode (default: By class). Display ships with RSSI bars, Signature names, Frequency, and First / last seen on. The header shows live counts as three small icons: Wi-Fi access points, BLE advertisers, and on-air signature matches (the same hub icon as the Signatures tab). A radio hint may follow those numbers.",
            "Turn on system Location and Bluetooth if either is off. The Live display header and Settings show radio hints (Wi-Fi next Ns, waiting on OS, BLE cycling). Keep screen on is enabled by default. If you will leave the app: Settings → Allow background usage, then Unrestricted battery. Some phones (Samsung among them) do not open onto Unrestricted — tap Allow background usage to click through and select it.",
            ]),
            privacy=False,
        ),
        callout(
            "Stay scanning",
            "After first launch, open Settings in Fieldwatch and turn on Allow background usage, then Unrestricted battery. Some phones do not open onto Unrestricted — tap Allow background usage to click through and select it. "
            "Keep screen on is enabled by default so BLE is not parked on screen-off while you watch; turn it off when you pocket the phone. "
            "Also disable any “Put unused apps to sleep” entry for Fieldwatch. "
            "The full max-collection checklist (permissions, OEM battery, High performance, Faster Wi-Fi) is §4.5.",
            "warn",
        ),
        P(
            "<b>Out of the box.</b> First launch and Restore load the stock catalog. Every row "
            "labels when its rules hit. These Settings ship on: Keep screen on, Tag detections with GPS, "
            "Online place names, Watchlist alerts, Beep, Voice on watched signature (What to say: Class + signature), and Jump to new watched detection. Stock bookmarks (alert on a new match) "
            "are Extra attention rows: Axon, WatchGuard Video, Ray-Ban / Meta glasses, "
            "Snap Spectacles, Fieldy, Plaud Note, Hobby BLE serial, Hak5 Pineapple, Flipper Zero, Pwnagotchi, "
            "Marauder / Deauther, GhostESP, Bruce, Porkchop, Cradlepoint, AirLink, Compex, Novatel Wireless, Utility Inc, and roadside / public camera + ALPR (Flock, Penguin, Pigvision, FS Ext Battery, Genetec AutoVu, Rekor, Motorola Vigilant, Verkada, Avigilon, Axis, Hikvision, Dahua, Hanwha Wisenet, Uniview, Rhombus); "
            "plus every built-in Drone-class row (DJI, Remote ID, Skydio, Autel, Parrot, HOVERAir). "
            "Privacy mode, the shade notification, and the TAK / CoT feed stay off. "
            "Chip colors follow class (§9.5)."
        ),
        P("4.4 Quick start", "h2"),
        P(
            "Five tabs along the bottom, plus one icon at the top right of Live. That is the "
            "whole chrome. First launch points at each of them with a tour overlay after you "
            "accept the license; <b>Got it</b> dismisses it. Settings → <b>Show Live tour</b> "
            "opens the same overlay again whenever you want it."
        ),
        table(
            ["Control", "What it is for"],
            [
                ["Live / Pause", "The picture of radios you are hearing now (radar, list, timeline, hybrid, or By class). Tap this tab again while you are already on it to <b>Pause</b> — the picture freezes; Wi-Fi and BLE keep scanning and the log still appends. Tap Live to run the list again. From another tab, this item only navigates here."],
                ["Filters", "Which radios appear on Live. Presets (BLE only, Watched only, …), class Show only / Hide these, Show only selected / Hide selected signatures, RSSI / name / OUI. Signatures still label and can beep. Logging is unchanged."],
                ["Signatures", "The pattern catalog — which radios get a name. Tap a row to edit. Bookmark a row to beep (and/or speak) when that family appears. Hide a family on Filters, not here."],
                ["Reports", "Named sits, Path, Debrief, Sit export, Compare, AI Export, Signature candidates, Log export. Start and End sit live here."],
                ["Settings", "Appearance (Night mode, Keep screen on, Privacy mode), scan intensity, GPS tagging, watchlist voice, TAK / CoT, logging, catalog export / import, Update stock catalog from GitHub, Restore defaults, Show Live tour. Row layout is Live → Display, not here."],
                ["Tune (top right on Live)", "The sliders icon. Opens <b>Display</b> over Live: View (Radar, Strength list, Timeline, Hybrid, By class), Sort, Brief hold, Title line, Subtitle line, then switches for RSSI bars, signature names, Frequency, first/last. This is how you change the picture — appearance, sorting, and which fields each row shows. Tap Tune again, or tap the dimmed list, to close it. Not on Settings. §5.3."],
            ],
            [1.7 * inch, 4.8 * inch],
        ),
        Spacer(1, 6),
        callout(
            "Filters hide radios. Display hides fields",
            "Users looking for Radar vs list vs By class will not find it on Settings. "
            "It is Display, under the tune icon at the top right of Live. Filters change "
            "<i>who</i> is on the list; Display changes <i>how the list looks</i>.",
            "note",
        ),
        figure_wrap(
            "fig-display.png",
            "Display. Tap the tune icon (top right on Live) to open it.",
            "View, Sort, Brief hold, Title line, Subtitle line, and the extra-fact switches all live here. "
            "Tap Tune again or tap the dimmed radios to close it. Walk through the chrome once:",
        ),
        numbered([
            "Confirm the header is counting (Wi-Fi, Bluetooth, and signatures icons with numbers). If both radio counts stay at 0, Location / Wi-Fi / Bluetooth are probably off at the system level — turn them on, wait ~30 s for the first Wi-Fi batch.",
            "You should be on the Live display, By class. Tap a class, then a signature, then a radio. Each radio row is one radio. The circle is a class glyph (unmatched = ?) — that is the glanceable mark. AP = Wi-Fi access point and LE = BLE advertiser; they still mean those two radio kinds (§1.1). On the Live display they are a small Wi-Fi or Bluetooth icon at the start of the subtitle, not two-letter tags and not the title. Default first line is the MAC (SemiBold). Default second line is that icon, then Name + type (SSID / advertised BLE name / a type guess such as Apple · AirTag), then rand/gone. RSSI bars, signature names, Frequency, and first/last seen ship on. Vendor is not on the list — open detail. The number on the right is RSSI in dBm (loudness here, not meters; −50 is louder than −90).",
            "Tap the tune icon (top right). That is <b>Display</b>: how the list looks for this job. Pick a View first (By class, Strength list, Hybrid, Timeline, or Classic radar). Then Sort, Brief hold, Title line, Subtitle line, and the extra-fact switches. Close it when the list looks the way you want — tap Tune again or tap the dimmed radios behind the panel. In a crowded plaza, set Subtitle to None and turn the extras off — you still have every radio; you just see less of each. §5.3.",
            "Tap a row. That is device detail: a saved custom name is the large title; advertised name smaller; Observer notes (cyan) under the name; “What this looks like,” Extra attention if any, Signature family, signal, decode, optional Decoded fields on BLE when that signature has a map (§9.6), bookmark (watch this MAC), Hunt on BLE, Create signature from device. Custom name / notes edit is hidden on a random / privacy BLE MAC. Back returns to the Live display.",
            "Work the other four tabs once: Filters (try BLE only, then All traffic), Signatures (Class A–Z, tap a class), Reports (Sits, Path, Debrief, Compare, Log), Settings (Appearance — Night mode, Privacy mode, Keep screen on — and Show Live tour if you want the overlay again).",
            "Do not expect every gadget in the room to appear. Phones on café Wi-Fi without hotspot, cellular-only cameras, and sleeping tags will not. That is a phone limit, not a broken install. Chapter 12 is what to do once the list makes sense.",
        ]),
        P("4.5 Max collection (battery is the cost)", "h2"),
        P(
            "§4.3 is enough to see radios. This page is the walkthrough if you want the phone "
            "to scan as often as stock Android will allow — a sit, a walk, or a drive — and you "
            "accept heat and drain. Pocket carry and overnight bag are the other way: Battery saver "
            "and §13.2. A USB pack is more useful than arguing with Adaptive battery if you need "
            "this for hours."
        ),
        P(
            "High performance already ships on, as do Keep screen on, GPS tagging, and logging. "
            "What this checklist adds is grant every permission, stop the OEM from parking the "
            "scan, leave the screen on while you watch, and (optional) Faster Wi-Fi AP scans for "
            "signed access points at speed. It does not lift stock Android limits — no monitor mode, "
            "no Wi-Fi clients, no Classic Bluetooth, no direction finding. It only stops the phone "
            "from going to sleep on the radios."
        ),
        P("4.5.1 System radios", "h3"),
        P(
            "Android will not give Fieldwatch Wi-Fi BSSIDs or useful BLE without Location, even if "
            "Fieldwatch’s own switches are on. Do this in the phone’s Settings, not only inside Fieldwatch."
        ),
        numbered([
            "<b>Location ON</b>, and <b>Precise / fine</b> (not Approximate). High-accuracy / GPS + Wi-Fi + mobile (Samsung: Location → Location method, or Location services). Tag detections with GPS needs a live fix or the path stays 0.",
            "<b>Wi-Fi ON</b>. Fieldwatch only hears access-point beacons. The radio must be up.",
            "<b>Bluetooth ON</b>. BLE advertisements only. Classic (HC-05 / HC-06) still will not appear.",
            "If the phone has <b>Wi-Fi scanning</b> and <b>Bluetooth scanning</b> under Location / Improve accuracy / Location services, turn those on too. They let the OS scan for location; they do not replace Wi-Fi and Bluetooth being on.",
            "Turn <b>Battery saver / Power saving</b> off while you collect, or exclude Fieldwatch. System-wide saver can throttle scans even when Fieldwatch is Unrestricted.",
        ]),
        P("4.5.2 Permissions Fieldwatch asks", "h3"),
        P(
            "First screen: <b>Fieldwatch needs the radios</b> → Grant permissions. "
            "Fieldwatch will not start scanning until the required set is complete. "
            "Wording varies by Android version. If you tapped Deny, grant later at "
            "Android Settings → Apps → Fieldwatch → Permissions. Precise Location is required; "
            "Approximate is not enough."
        ),
        table(
            ["Grant this", "What it is for"],
            [
                ["Location — Precise (While using the app)", "Wi-Fi scan results and BLE addresses. The “Fieldwatch scanning” notification is a foreground service, so While using is enough; Allow all the time is fine if the phone offers it. Fieldwatch does not request background-only location."],
                ["Nearby Wi-Fi devices (Android 13+)", "Lets Fieldwatch run Wi-Fi AP scans."],
                ["Nearby devices / Bluetooth scan + connect (Android 12+)", "BLE observer. Connect is required by the stack to read some names. Fieldwatch does not create a Bluetooth connection for detection."],
                ["Notifications (Android 13+)", "Persistent “Fieldwatch scanning” status, and optional watchlist alerts. Allow the scan notification; Stop on that card ends collection."],
            ],
            [2.4 * inch, 4.1 * inch],
        ),
        Spacer(1, 6),
        P(
            "Battery exemptions are not on that first screen. They are Settings → "
            "<b>Allow background usage</b> and <b>Unrestricted battery</b> (§4.5.3). Vibrate is install-time. Internet "
            "is for Online place names and maps (on by default); Fieldwatch has no account."
        ),
        P("4.5.3 Stop the OEM from parking the scan", "h3"),
        P(
            "OEM battery managers will freeze BLE when the screen locks or when they decide "
            "Fieldwatch is unused. Names differ by phone. Do these after first launch, then leave "
            "the scan notification alone."
        ),
        numbered([
            "In Fieldwatch: Settings → <b>Allow background usage</b>. Opens Fieldwatch’s Battery page; turn on that Android switch. Then Settings → <b>Unrestricted battery</b>. Pixel / stock usually shows Unrestricted / Optimized / Restricted on that page — select Unrestricted. Some phones (Samsung among them) do not open onto that choice. If you only see Allow background usage, tap that row (the words, not the switch) to click through, then select <b>Unrestricted</b>. Each Fieldwatch switch follows that Android grant when you return.",
            "Android Settings → Apps → Fieldwatch → Battery (or App battery usage). Select <b>Unrestricted</b>. If that choice is not on the first screen (Samsung among others), tap Allow background usage to click through and select it.",
            "Samsung: Android Settings → Battery → <b>Background usage limits</b> (or Put unused apps to sleep). Do not sleep Fieldwatch. If it is on a Sleeping list, remove it. Never sleeping apps can include Fieldwatch if you want it locked there.",
            "Leave Fieldwatch in Recents. Swiping it out (or Stop on the scan notification) ends the service. Home leaves scanning running.",
            "Keep the <b>Fieldwatch scanning</b> notification. It is how the radios stay up when the UI is not in front.",
        ]),
        callout(
            "Keep screen on while you watch",
            "Fieldwatch Settings → Keep screen on ships on. Leave it on while the Live display or Hunt is in "
            "front so Samsung does not park BLE when the display blanks. Turn it off when you "
            "pocket the phone if you still want the notification scanning and you do not need "
            "low-latency BLE. Unrestricted background is not the same switch — it does not hold "
            "the screen, and it does not lift Wi-Fi or BLE scan quotas.",
            "note",
        ),
        P("4.5.4 Fieldwatch Settings for this sit", "h3"),
        numbered([
            "<b>Scan intensity: High performance</b> (ships on). BLE low-latency, recycled about every 70 s. Wi-Fi about every 30 s until you add Faster Wi-Fi. Balanced and Battery saver are the compromise; not this page. §10.3.",
            "<b>Faster Wi-Fi AP scans</b> (optional, off until you do this). For signed APs on a drive. (1) Settings → About phone → tap Build number until Developer options exist. (2) Settings → Developer options → <b>Wi-Fi scan throttling → Off</b>. (3) Fieldwatch → Settings → Faster Wi-Fi AP scans → On. About every 8 s AP batches instead of ~30 s. More battery and heat than High performance alone. Fieldwatch will not flip the switch while the OS is still throttling. Turn the Fieldwatch switch off when the drive is over. §7.1.1, §10.3.1.",
            "<b>Keep screen on</b> — on while you are looking at the Live display / Hunt.",
            "<b>Tag detections with GPS</b> — leave on if you want Moving with you, Debrief distance, or log lat/lon. High-accuracy Location. Path stays 0 until a live fix.",
            "<b>Write to disk / logging</b> — leave on if you want Log export, Signature candidates, or a file after the 15-minute Debrief window. Turn off only if the plaza is so dense the UI feels late.",
            "<b>Stale after</b> — default 45 s is fine for a busy list. For slow tags, 90–120 s so they do not flicker to gone between advertisements.",
        ]),
        P("4.5.5 Check it is actually scanning", "h3"),
        numbered([
            "The shade has <b>Fieldwatch scanning</b> with Wi-Fi / BLE / signatures counts. If that card is gone, the service is not running — open Fieldwatch again.",
            "Live display header counts are not both stuck at 0. If they are: Location, Wi-Fi, and Bluetooth at the system level (§4.5.1), then wait ~30 s for the first Wi-Fi batch (~8 s if Faster Wi-Fi is in effect).",
            "Header hint is not <b>BLE parked · restarting</b> while you watch. If it is: Keep screen on, Unrestricted background, do not sleep the app. §13.2.",
            "If Faster Wi-Fi is on, Wi-Fi next Ns is a short count. If the header reads <b>Wi-Fi fast scan needs Developer options</b>, the OS throttle came back on — fix Developer options, then return to Settings.",
            "The phone will get warm. That is the radios and the screen, not a crash. Plug in or use a pack for a long sit.",
        ]),
        callout(
            "What this will not fix",
            "Quiet or cellular-only cameras, phones on café Wi-Fi that are not hotspots, "
            "sleeping tags, and randomized BLE that walks out of range still miss. "
            "A full permission set and Unrestricted battery do not turn a phone into a "
            "monitor-mode radio. An empty list does not mean you are safe. §3, Notice.",
            "warn",
        ),
    ]

    # 5 Navigation
    flow += [
        PageBreak(),
        P("5. App Navigation &amp; Interface Overview", "h1"),
        P(
            "This chapter maps the screens. If you have not launched the app yet, start with §4.4. "
            "The first bottom tab is labeled <b>Live</b> on the phone. This book calls that screen the "
            "<b>Live display</b> so it is not confused with live GPS or a recording. "
            "The circle on each row is a class glyph. The two radio kinds are Wi-Fi access points and BLE advertisers "
            "(§1.1); they show as a small Wi-Fi or Bluetooth icon on the subtitle. "
            "RSSI is loudness at this phone in dBm (closer to 0 is louder). "
            "Filters change who appears. Display (§5.3) changes how each row looks. "
            "Pause freezes the picture; the radios keep scanning and the log still writes. "
            "A new filter applies when you run the Live display again."
        ),
        P("5.1 Main layout", "h2"),
        figure_wrap(
            "fig-live.png",
            "Fig. 1 — Live display (hybrid).",
            "One screen, five tabs, and a device-detail page on top. "
            "The bottom bar is Live, Filters, Signatures, Reports, Settings. "
            "Signatures is the pattern catalog. Reports is Path, Debrief, Compare, Sit export, AI Export, and Log export. "
            "The top bar is FIELDWATCH plus three live counts: Wi-Fi access points, BLE advertisers, and on-air signature matches "
            "(the same hub icon as the Signatures tab). A radio hint may follow — Wi-Fi next 27s, waiting on OS, BLE cycling, or BLE parked. "
            "The scan notification spells the same three counts. Exports live on Reports. "
            "Screenshots in this book use Privacy mode (MAC tails **:**:**) unless noted.",
        ),
        figure_wrap(
            "fig-signatures.png",
            "Fig. 2 — Signatures.",
            "Signatures is the pattern catalog. The title shows how many signatures are loaded (stock plus any you added). "
            "Each row is a class glyph, a hexagon when that row has a Decode fields map (§9.6), and a bookmark for watch. "
            "Name A–Z or Class A–Z (classes start collapsed). Tap a row to edit. + adds a blank signature. "
            "No matching on/off — hide a family on Filters. Full write-up: §9.",
        ),
        P("5.2 Bottom navigation", "h2"),
        table(
            ["Tab", "Function"],
            [
                ["Live", "The on-screen picture: radar, list, timeline, hybrid, or By class. Tune (top right) opens Display. Tap this tab again to Pause (radios still scan and log); tap Live to run the list. Double-tap FIELDWATCH to jump to the top. A running sit shows FIELDWATCH · SIT; start and end are on Reports."],
                ["Filters", "Which radios appear. Order: presets, radios, Moving with you, New detections only, Signatures only, Watched only, Named radios only, Hide Fast Pair account-key, class Show only / Hide these, Show only selected signatures, Hide selected signatures, RSSI / name / OUI. Signatures still label and can beep. See §8 and Figs. 15–17."],
                ["Signatures", "The pattern catalog. Title shows how many signatures are loaded (stock plus any you added). Each row shows the class glyph, and a hexagon when that row has a Decode fields map (§9.6). Name A–Z or Class A–Z (classes start collapsed; tap to open). Tap a row to edit (rules, color, Decode fields on BLE). Bookmark = watch (beep and/or spoken class). No matching on/off — hide on Filters. + adds a blank signature. Fig. 2, §9.6."],
                ["Reports", "Sits (optional named window), Path, Debrief (text / PDF), Sit export, Compare sits, AI Export, Signature candidates, Log export (Format + radios), Reset / clear log. The selected sit drives Path, Debrief, Sit export, and Compare’s this-sit side. Signature candidates mines the rotating log. GPS / place names / logging on-off stay on Settings."],
                ["Settings", "Appearance (Night mode, Keep screen on, Privacy mode), scan intensity, GPS tagging, TAK / CoT feed (off), place names, logging, beep and/or voice / optional shade card, Test alert, Named radios, battery exemption, export / import signatures, Update stock catalog from GitHub, Settings backup, restore defaults, Show Live tour. Row layout is Live display → Display (tune), not here. TAK: §5.8."],
            ],
            [1.2 * inch, 5.3 * inch],
        ),
        Spacer(1, 6),
        table(
            ["What you want", "Where"],
            [
                ["Fewer radios on the Live display", "Filters (chapter 8)"],
                ["Change the picture (Radar / list / By class)", "Live display → Tune, top right (§4.4, §5.3)"],
                ["Less text on each row", "Live display → Tune → Display (§5.3)"],
                ["Stop seeing a family on the Live display", "Filters → Hide these (class) or Hide selected (one family)"],
                ["Hide MAC tails on the screen", "Settings → Privacy mode"],
                ["Put radios on an ATAK map", "Settings → TAK / CoT feed (§5.8, §12.15)"],
            ],
            [2.4 * inch, 4.1 * inch],
        ),
        Spacer(1, 6),
        P("5.3 Display inspector (Live display)", "h2"),
        P(
            "The Live display is not a fixed dump of every field. You can fit it to the job — a "
            "crowded plaza, a sit, a hunt, or copying a MAC. View (Radar, Strength list, Timeline, "
            "Hybrid, By class), Sort, and the row fields all live in one place: tap the "
            "<b>tune</b> icon at the top right of Live. That opens <b>Display</b> as a panel over "
            "the list (the radios dim; tap the dim area or Tune again to close). Your choices "
            "stay until you change them. This is not on Settings."
        ),
        figure_wrap(
            "fig-display.png",
            "Fig. 3 — Display. Tune (top right on Live) opens this panel over the list.",
            "View is the first dropdown. That is Radar vs Strength list vs Timeline vs Hybrid vs By class. "
            "Sort, Brief hold, Title line, and Subtitle line follow; on/off items are switches. "
            "Radar labels are not Title/Subtitle — they stay a short signature name or type guess.",
        ),
        callout(
            "Filters hide radios. Display hides fields",
            "Filters (§8) decide <i>who</i> is on the Live display. Display decides <i>how each row looks</i>. "
            "Subtitle None does not drop a radio — it only drops the second line so more rows "
            "fit. Vendor, payload, and the full decode stay on detail. Logging is unchanged. "
            "Two operators on the same sit can run two different rows.",
            "note",
        ),
        P("Identity — what each row says", "h3"),
        bullets([
            "<b>Title line</b> — First line of each list, hybrid, or timeline row. It stays bold even when it is a MAC. <b>MAC address</b> (default) puts the radio address on the title so you can copy it or hunt by BSSID without opening the second line. <b>Advertised name</b> is the SSID or BLE local name, or &lt;hidden&gt; / unnamed LE if there is no name (no type guess). <b>Name + type</b> uses the advertised name if there is one, otherwise the same guess as the detail page (for example Apple, Inc. · AirTag or Find My accessory). That “Apple, Inc.” is a type guess. IEEE vendor is on the detail page, not the list. Title cannot be None. Radio kind never sits on this line — the Wi-Fi / Bluetooth icon leads the subtitle.",
            "<b>Subtitle line</b> — Second line, small monospace. Always starts with a small <b>Wi-Fi</b> icon (access point) or <b>Bluetooth</b> icon (BLE advertiser). Then Advertised name, <b>Name + type</b> (default), or MAC, plus <font face='Courier'>rand</font> / <font face='Courier'>gone</font>. <b>None</b> hides the second line so more rows fit; rand/gone move onto the title. The kind icon does not move onto the title, so that line stays a readable MAC or name and the row can collapse. On Name + type or Advertised name, unnamed BLE is written <font face='Courier'>unnamed</font> (the Bluetooth icon already marks LE — not “unnamed LE” twice). Vendor is never on the list row — IEEE board/chip vendor is on detail.",
        ]),
        P("View and linger", "h3"),
        bullets([
            "<b>View</b> — Classic radar, Strength list, Timeline, Hybrid + sparklines, By class (default). Same filtered set; only the picture changes. Sort orders the list / hybrid / timeline and the radios under a signature in By class. Radar still plots by RSSI radius (a seat on the ring, not a stack). §6.",
            "<b>Brief hold</b> — Off (Stale after only), or hold 10 / 30 / 60 s after the last packet. The row stays at last-heard RSSI, rank, and radar ring; values do not decay. A new packet updates them. On-screen linger is the longer of Stale after and Brief hold. Filters → New detections only uses this same time as the minimum stay after the last packet.",
        ]),
        P("Sort — order of the list", "h3"),
        P(
            "Display → Sort is the order of radios on Strength list, Hybrid, Timeline, and under a signature in By class. "
            "It does not hide radios (Filters do that) and it does not change the log or Debrief. "
            "The default is <b>Strongest (avg 30s)</b>. The eight choices:"
        ),
        table(
            ["Sort", "What it does", "Use when"],
            [
                ["Strongest signal", "Loudest last packet at the top. The number on the right is that instant RSSI.", "Instant triage, pick a Hunt target, a plaza where average would hide a sudden loud tag."],
                ["Strongest (avg 30s)", "Rank by the average of RSSI samples in the last 30 s (last packet if there are none). Extra “avg N” under the RSSI. Radar rings use that average too. This is the default.", "Sitting Wi-Fi that jumps every scan; a tag you are walking toward without the list jumping."],
                ["Newest heard", "Most recent packet at the top, then strength.", "Who just spoke. A fixture that keeps advertising stays high."],
                ["Newest alert", "Most recent watchlist alert (beep / voice / flash) at the top, then last-heard. Radios that have not alerted this session sink. A second alert on the same radio jumps it back to the top. Those radios keep a phosphor bell on the list.", "Watchlist sit: the last thing that piped, then the one before."],
                ["Newest arrival", "First-seen this session, newest at the top, then last-heard. A radio that has been here an hour sinks even if it is loud.", "Who showed up latest, not who is loudest now."],
                ["New at bottom", "First-seen, oldest at the top; new rows append; gone rows drop. The list sticks to the bottom unless you scroll up.", "A sit or drive that you read like a log. New at bottom is the only sort that pins the end."],
                ["Name A–Z", "Alphabetical on the Title line, then MAC. Title defaults to MAC, so this is MAC order until you change Title.", "Find a known SSID, copy an address, compare two lists."],
                ["Signatures first", "Any signature match above unmatched, then by strength (same strength rule as last Strongest choice).", "Pattern hits without turning on Filters → Signatures only — unmatched radios stay, they just sink."],
            ],
            [1.45 * inch, 2.4 * inch, 2.65 * inch],
        ),
        Spacer(1, 4),
        P(
            "Picking Strongest signal or Strongest (avg 30s) sets the list back to a strength "
            "order. The other six keep the last strength rule only as a tie-break where they use it "
            "(newest heard, signatures first). Newest alert ranks by watchlist flash time, not RSSI. "
            "Sort is not a filter: a weak named tag is still on the list, just "
            "not at the top unless you chose Signatures first, Newest arrival, or Newest alert.",
            "body_left",
        ),
        P("Extra facts — turn on only what the job needs", "h3"),
        bullets([
            "<b>RSSI bars</b> — Switch, on by default. Track plus fill from last-heard strength (the same last packet as the number, not the 30 s average).",
            "<b>Signature names</b> — Switch, on by default. Independent of bars. List, hybrid, and timeline show up to three chips when a device matches more than one signature. Radar uses only the first match as the blip label. Detail lists every match. Off still matches; it only hides the chips.",
            "<b>Frequency</b> — Switch, on by default. Channel and MHz sit under the RSSI on the right so a long name does not cut them off (for example <font face='Courier'>ch6 · 2437MHz</font>). BLE advertisements usually have no frequency.",
            "<b>First / last seen</b> — Switch, on by default. Age since first packet this session and age since the last packet, ticking once a second.",
        ]),
        P(
            "<b>Default row</b> (By class, Sort Strongest avg 30s, Title MAC, Subtitle Name + type, "
            "bars on, signatures on, Frequency on, first/last on, Brief hold 10 s): class glyph in "
            "a circle (unmatched = ?); first line MAC; second line the Wi-Fi or Bluetooth icon, then name or type guess, "
            "then rand/gone; live RSSI, trend, and avg N on the right; optional bar and chips. "
            "A bookmarked new hit flashes the row for one second when the watchlist fires (pip and/or spoken class). After that, a phosphor (night-green) bell stays on the chip row for the rest of the session.",
            "body_left",
        ),
        P("Fit the row to the job", "h3"),
        table(
            ["Mission", "Typical Display"],
            [
                ["Learn the neighborhood", "Leave the defaults: MAC over Name + type, bars on, signature names on. Strength list, Sort → Strongest (avg 30s)."],
                ["Plaza / list too dense", "Subtitle None. Bars, Frequency, and first/last off. Strength list, not Hybrid. You still see every filtered radio — just the MAC (the radio-kind icon is gone with the subtitle, so the title stays readable)."],
                ["Copy a MAC / BSSID", "Already the default (Title MAC). Set Subtitle to None if you want more rows on screen. Pause, then read or long-press."],
                ["Wi-Fi channel sit", "Frequency on. Title can stay MAC. Sort strongest. Bars on if you want a glanceable loudness strip."],
                ["Hunt / follow one BLE", "Hybrid + sparklines. Keep Title MAC. First/last on. Frequency off (BLE rarely has it). Then open Hunt from detail."],
                ["Sit — who came and went", "Timeline. Sort newest arrival or new at bottom. First/last on. Title is already MAC if you will write addresses down."],
                ["Signatures only, quieter list", "Signature names on. Subtitle None (MAC only). Pair with Filters → Signatures only (§8), not instead of it."],
            ],
            [1.9 * inch, 4.6 * inch],
        ),
        Spacer(1, 6),
        P(
            "These Display controls are not repeated on Settings. New detections only is not on "
            "this card — it is a Filter; its Mark seen / Reset seen buttons sit above the tabs "
            "(§5.3.2). If you were scrolled to the top, new stronger rows push the list down. "
            "If you have scrolled away, the list does not snap. Double-tap the FIELDWATCH title / "
            "count line to scroll quickly to the top (about 140 ms, linear)."
        ),
        P("5.3.1 Pause the Live display", "h3"),
        P(
            "On the Live display the bottom item is a toggle, not a second “go to top.” While the list "
            "is updating it reads <b>Pause</b>. Tap it and the Live display freezes: same rows, same RSSI, same "
            "chips, and the header shows FIELDWATCH · PAUSED. Wi-Fi and BLE keep scanning and the log still "
            "appends — pause freezes the picture, not the radios. The bottom item then reads <b>Live</b> "
            "(play); tap it to run the list again. From Filters / Signatures / Settings the same item "
            "only navigates back; it does not un-pause until you tap the Live display while already on that tab."
        ),
        P(
            "Filters and Settings still update while paused. Chip taps, Show only / Hide these, and "
            "Restore still take effect; the frozen Live display does not change until you run it again. "
            "The pause banner says so. Use pause when the list is moving too fast to tap a row. Open "
            "detail from a paused row and you get that frozen observation even if the radio has since "
            "gone stale on the live set. Use it so you can read what was on the screen, then resume. "
            "While New detections only is on, Mark seen / Reset seen stay above the tabs and still "
            "work while paused — Mark seen absorbs the frozen list, not radios that arrived after "
            "the freeze."
        ),
        P("5.3.2 New detections on the Live display", "h3"),
        P(
            "The switch is on Filters → New detections only. While it is on, the Live display shows two extra "
            "controls. They are not on Display and they are not on the Filters tab."
        ),
        bullets([
            "<b>New only</b> — a one-line hint under Display (and under the Pause banner if frozen). "
            "Shows New only, New only · N hidden, or New only · learning sitting Wi-Fi.",
            "<b>Mark seen / Reset seen</b> — a bar just above the tab bar, always reachable without "
            "scrolling the list. Hidden when the filter is off.",
        ]),
        P(
            "On first turn-on, Fieldwatch snapshots what is already here (plus sitting Wi-Fi through "
            "the next scan). Already-seen only grows on that first snapshot or on Mark seen. "
            "Mark seen adds what is on the Live display now; while Pause is on, that is the frozen "
            "list, not radios that arrived after the freeze. Reset seen clears already-seen to zero "
            "so those radios can show as new again. Clearing the log does not reset this. "
            "Randomized BLE addresses still look new. A new radio stays while it is heard, then at "
            "least as long as Display → Brief hold after the last packet."
        ),
        P("5.3.3 Moving with you on the Live display", "h3"),
        P(
            "The switch is on Filters, under Radios to show. New detections only sits under it. "
            "Signatures only, Watched only, Named radios only, and the class chips are further down. "
            "Turning it on starts a follow test on all radios. It clears Signatures only, "
            "Watched only, Named radios only, and class Show only so those filters do not empty the list. Hide these "
            "stays if you were hiding a bag tag. The Moving with you preset at the top replaces "
            "the whole filter the same way. While it is on, the Live display shows Follow · path N m "
            "and a <b>Start over</b> button above the tabs (same bar as Mark seen / Reset seen if "
            "New detections only is also on). Start over clears the operator GPS path and every "
            "radio’s GPS trail. The live list and the log are not wiped. Path goes back to 0 m; "
            "walk or drive ~50 m again to see who comes back. A tag in the bag should reappear; "
            "house APs should not. Turning the filter off does not clear the path. "
            "After the switch is on you can add Show only again if you really want only finder tags that co-travel."
        ),
        P(
            "The test itself is not a fixed radius. GPS is stamped on your phone at the moment "
            "a radio is heard, and “still here” is allowed to grow with how fast you have been "
            "moving — a sidewalk tag is held to about a house-length; a tag in the car is not "
            "dropped between advertisements just because you covered hundreds of meters in a few "
            "seconds. Wi-Fi in the car is given a longer hold than BLE, because Android scans "
            "access points less often than it delivers advertisements. The full checklist, the "
            "speed table, and why a house AP at the door still fails are §8.5."
        ),
        P(
            "What you see on the Live display is only the current picture, not a recording. Reports → Debrief (text or PDF) still "
            "includes the last 15 minutes of tracking (possible trackers with you / possible tail) "
            "from radios in memory (cap ~400; §11.4.1). Radios you only passed are omitted. That is true whether you are on radar, "
            "list, timeline, hybrid, or By class, and whether Moving with you or any other filter is on. "
            "GPS tagging must have been on, and you must have moved, or the report says the "
            "following test was not run. §11.4, §12.2.",
            "body_left",
        ),
        P("5.4 Status indicators", "h2"),
        bullets([
            "<b>Class glyph</b> in the circle: signature class of the first match (Finder tags, Phones / PCs, Audio, …). Unmatched radios show a question mark. The same glyphs sit on Filters class chips and on By class headers.",
            "<b>Radio kind</b> (Wi-Fi / Bluetooth icon) at the start of the subtitle: Wi-Fi access point versus BLE advertiser. AP means the radio is beaconing a network (or acting as a hotspot / soft-AP), not that it is a Wi-Fi client. Detail spells those words. Subtitle None drops the icon with the second line so the title stays a MAC or name.",
            "<b>Colored chips</b>: matched signature names when Signature names is on. List / Hybrid / Timeline show up to three. Radar labels the blip with the first match only. Four or more matches: extra names are on the detail page.",
            "<b>Decode hexagon</b>: inside a signature chip, in that chip’s color, when <i>that</i> signature has a Decode fields map (§9.6). Dual-chip radios mark only the mapped name(s). That mark is a catalog check: the Live display does not parse the bytes. Values are on detail. Extra attention “!” is its own chip. A cyan notes chip means this radio has Observer notes. A phosphor bell means this radio already alerted this session. Display → Signature names off hides the names and the hexagon; “!”, the notes chip, and the bell still show.",
            "<b>&gt;&gt; &gt; = &lt; &lt;&lt;</b>: RSSI trend from recent packets (much stronger / stronger / steady / weaker / much weaker). Green approaching, red fading. Blank until a few samples exist.",
            "<b>New only · N hidden</b>: Live display hint while New detections only is on. Mark seen / Reset seen sit above the tabs.",
            "<b>Follow · path N m</b>: Live display hint while Moving with you is on. Start over (above the tabs) clears the GPS path and trails.",
            "<b>new Ns</b>: age since first seen, when New detections only is on.",
            "<b>rand</b>: locally administered / randomized address bit is set.",
            "<b>gone</b> (list) or a dim blip (radar): last packet older than the longer of Stale after (default 45 s) and Brief hold. Held Wi-Fi APs and BLE recycle do not count as gone.",
            "<b>first / last</b>: optional line when First / last seen is on. Age since first packet this session and age since the last packet (last now if it just spoke).",
            "<b>chN · MHz</b>: under RSSI when Frequency is on. BLE ads usually have none.",
            "<b>Scan notification</b>: “N Wi-Fi · N BLE · N signatures”, updated about every 2.5 s. Home leaves scanning running. Swiping Fieldwatch out of Recents (or Stop on the notification) ends the service so it does not keep alerting in the background.",
            "<b>Watchlist</b>: pip and/or spoken phrase on media volume, row flash, optional jump. Beep and Voice are independent (Settings). Voice can say the class, the signature name, or both; not Hunt. Fires once when a watched radio first appears (or returns after gone). After the one-second flash, a phosphor bell stays on that list row (list, hybrid, timeline, By class) for the rest of the session — not Extra attention “!”. Newest alert sorts by the same event. On By class the matching class and signature open so the row can flash, and the jump keeps those headers on screen when the radio is close enough. On radar the blip pings (expanding rings and a bright core) for any of those cues, including voice-only; after the ping a phosphor ring stays on that blip (drawn on top). Test alert plays whatever is on. System shade card is off unless Settings → System notification is on.",
        ]),
        P("5.5 Device detail", "h2"),
        figure_wrap(
            "fig-detail.png",
            "Fig. 4 — Device detail. Custom name is the title; advertised name is smaller. "
            "Cyan Observer notes sit under the name (not Extra attention gold). Privacy mode masks MAC tails.",
            "Tap a row or a radar blip. Detail is the full decode of that observation, resolved "
            "offline from packed IEEE and Bluetooth SIG tables (no network). Jargon is spelled "
            "out in plain language (for example BR/EDR not supported = BLE-only, no classic "
            "Bluetooth). RSSI is a short band (very strong / strong / medium / weak / very weak) "
            "so the page does not reflow as the number ticks.",
            "<b>What this looks like</b> (top card). A cautious guess. A matched catalog family "
            "outranks a generic SSID heuristic — a <font face='Courier'>DIRECT-rR-Raven-*</font> AP "
            "is a Raven / ShotSpotter sensor, not “a phone or TV on Wi-Fi Direct.” Then advertised "
            "identity: GAP Appearance, Class of Device, service UUIDs, Apple/Google maker bytes, "
            "Fast Pair model ID. Hedge words are Most likely / Probably / Could be. This is what "
            "the radio is broadcasting, not a visual ID. Unmatched radios still get a guess from "
            "those fields, or a low-confidence “Wi-Fi access point” / “Bluetooth LE advertiser.”",
        ),
        P(
            "<b>Notes</b> (quiet card under Extra attention, or under the guess if there is none). "
            "Editor Notes for each matched signature — what that family is and how it is typically used. "
            "Stock copy is product context, not the match recipe (company IDs, UUIDs, name globs stay on Identity / Maker data). "
            "Not the amber Extra attention card, and not a Live “!”. Dual-chip radios list each family. "
            "Empty Notes skip the card. Share as text and AI Export include them.",
        ),
        P(
            "<b>Extra attention</b> (optional amber card under that guess). Only if a <i>matched</i> "
            "signature has text in Extra attention — a separate field from Notes. Stock fills it "
            "on Hobby BLE serial, Axon, WatchGuard Video, Digital Ally, Reveal Media, Wolfcom, "
            "Ray-Ban / Meta glasses, Snap Spectacles, Brilliant Frame, Even G1, "
            "Fieldy, Plaud Note, Limitless, Bee, Omi, Friend, Hak5 Pineapple, Flipper Zero, Pwnagotchi, Marauder / Deauther, "
            "GhostESP, Bruce, Porkchop, Cradlepoint, AirLink, Compex, Novatel Wireless, Utility Inc, Panasonic i-PRO / Arbitrator, "
            "and roadside / public camera + ALPR "
            "(Flock, Penguin, Pigvision, FS Ext Battery, Genetec AutoVu, Rekor, Motorola Vigilant, Verkada, Avigilon, Axis, Hikvision, Dahua, Hanwha Wisenet, Uniview, Rhombus, Hayden AI, Miovision, Tattile, LVT LiveView — those rows also ship with the bookmark on). "
            "Many camera/ALPR rows are name-only; cellular-only units stay quiet. A “!” mark on the "
            "Live display row means open detail and read that card. Pattern match, not identity, not "
            "a skimmer detector, not a safety finding. You can put Extra attention on any "
            "signature you edit."
        ),
        P(
            "<b>Signature family</b> (card above Create signature from device). Same analysis "
            "as Reports → Signature candidates (§5.6.4), aimed at <b>this</b> radio. It names "
            "the best unique on-air ID (name glob, vendor IE, service UUID, manufacturer-data "
            "prefix, or stable OUI) and counts distinct MACs that share it in the rotating log "
            "and on the air now. Packet count does not matter. Randomized addresses with no "
            "other ID, house-like names, chip-module OUIs, protocol IEs, generic UUIDs, and "
            "whole-OS company IDs (Apple 0x004C, Google, Samsung, Microsoft) are skipped — a "
            "crowd of those is not a catalog family. Pattern match, not identity."
        ),
        bullets([
            "<b>Strong family</b> — about eight or more distinct radios share that ID. Phosphor card, big count. A catalog pattern, not this MAC.",
            "<b>Possible family</b> — two through seven. Amber. Thin sample — still a family, not a crowd.",
            "<b>This radio only</b> — no other MAC shares a unique ID, or there is nothing unique to cluster on. A signature from here will mostly tag this address.",
            "<b>Already tagged</b> — this radio already matches a catalog signature. The card names the match. That is <b>not</b> a stop on a second signature. A device may carry several chips (§7.4).",
        ]),
        P(
            "The usual second-tag case is a <b>protocol plus a place</b>. Stock iBeacon is "
            "Apple company 0x004C type 0x02/0x15 — a layout any vendor can send, not a store. "
            "Walk into a shop with hundreds of iBeacons and they often share one proximity UUID "
            "(wayfinding, baskets, asset tags). Create a custom signature on that UUID and the "
            "same radios dual-label: iBeacon <b>and</b> your store row. The Live display shows up to three "
            "chips; detail lists every match. Stock <b>Target Atrius basket</b> is this pattern "
            "(UUID 5993A94C-… plus service 0xB1BB) — mute generic iBeacon in a mall and the "
            "Target UUID still labels those baskets. Minew / Estimote / Kontakt.io also "
            "dual-label with iBeacon. Fieldwatch drops the iBeacon chip only when a "
            "<i>non-beacon</i> product already labeled the radio (Sony TV, Tesla phone-key)."
        ),
        P(
            "The card is the verdict only. <b>Create signature from device</b> still drafts "
            "this radio, including a MAC pin (§9.2), even when the card says Already tagged. "
            "For a Strong or Possible family on an <i>unmatched</i> radio, "
            "Reports → Signature candidates drafts the <b>shared</b> rule without pinning "
            "this MAC (§9.2.1). Candidates skip radios the catalog already tagged, so a floor "
            "of iBeacons will not appear there — use detail decode and Create from device, then "
            "tighten the manufacturer-data prefix to the UUID. Logging off: live count still "
            "works; the log number fills in once the rotating log has been read."
        ),
        bullets([
            "<b>Identity</b> — advertised name, MAC, address type (public factory vs random/privacy). Vendor from IEEE OUI, MA-M, or MA-S when the address is universal; randomized BLE addresses skip the OUI table. Bluetooth Company ID from manufacturer data is named from the SIG list (~4012).",
            "<b>Signal</b> — RSSI plus min/max this session, claimed TX power, channel / MHz, Wi-Fi standard and channel width when the OS reports them.",
            "<b>BLE</b> — PHY (1M / 2M / Coded), connectable, advertising interval, decoded Flags, GAP Appearance, Class of Device (major / minor / service classes), named 16-bit service UUIDs, service data, manufacturer payload. Connectable stays Yes once any advertisement from this radio was connectable — scan responses from the same MAC are not connectable and no longer flip the line.",
            "<b>Known payloads</b> — iBeacon (UUID / major / minor / calibrated TX); Google Fast Pair (pairing-mode 24-bit model ID with a local name list, or account-key broadcast); Apple Continuity (AirPods/Beats model, battery, in-ear/in-case; Find My / Offline Finding; Nearby Info activity; Nearby Action; AirDrop; Handoff; Hey Siri; AirPlay; Instant Hotspot). Eddystone UID / URL / TLM / EID (service 0xFEAA) <b>accumulate</b> on this page: each frame type stays once heard, labeled on the raw line (UID, URL, TLM, EID). Frames do not replace each other. Microsoft Swift Pair / Nearby Sharing when present. Unknown 0xFF blobs stay company + hex — there is no official database of proprietary payloads.",
            "<b>Decoded fields</b> — BLE only. If a matched signature has a field map (§9.6), labels and values from the <i>current</i> advertisement (temperature, model, Remote ID location, …). The heading carries the same hexagon as the Live display chip. Encrypted or short payloads stay hex; a note appears if a map exists but did not apply to this packet (Govee lights often only send a name). Matched signatures lists a hexagon next to names that have a map. Apple / Fast Pair / Eddystone / Microsoft stay in Known payloads and Maker data. Share as text, AI Export, and Debrief notable BLE print the same lines.",
            "<b>Wi-Fi AP</b> — SSID (or Hidden), RSN/WPA/AKM/cipher from information elements in plain language, supported rates, capability string, vendor-specific IEs (OUI + type + payload) looked up in IEEE OUI/CID.",
            "<b>Session</b> — first/last seen, hit count, optional GPS (operator phone at hear-time), <b>every</b> matched signature (not capped at three), raw advertisement bytes, RSSI sparkline, 15-minute presence. Bookmark (top bar) watches this radio — see below. BLE: Hunt. Signature family card. Then Create signature from device.",
        ]),
        P(
            "Decode blocks on this page (Identity, Signal, Bluetooth advertisement, services "
            "including Eddystone, maker data, Session, Signature family) keep the tallest "
            "height they have reached on this radio, so a shorter frame or a shorter guess "
            "does not yank Hunt and the share buttons around."
        ),
        P(
            "Opened from a paused Live display, detail is the frozen snapshot even if that radio has "
            "since gone stale. Create signature from device on that page drafts rules from the observation."
        ),
        P(
            "<b>Custom name</b> is behind the edit icon on the name row. A saved name is the large title on this page; "
            "the advertised SSID or LE name sits smaller underneath. It labels <b>this MAC</b> without turning Alert on. "
            "Save name writes it to Settings → Named radios (Alert off until you bookmark). "
            "The name shows on the Live display and on reports. Filters → Named radios only keeps only those rows. "
            "Watched only is narrower: it needs Alert on this radio, or a bookmarked signature match. "
            "On BLE, if the address looks random / privacy (IEEE local bit or Android Random type), "
            "the edit control is hidden — a name would not follow a rotation. Wi-Fi always offers the field: "
            "a locally administered BSSID on a vehicle, mesh, or guest AP usually stays put. A name you already saved still shows as the title. "
            "Bookmark (top-right) still lets you watch this MAC, with the same rotation warning.",
            "body_left",
        ),
        P(
            "<b>Observer notes</b> is a cyan block under the name (not Extra attention gold, not catalog Notes). "
            "Up to 280 characters, same MAC as the custom name. Edit on this page or Settings → Named radios. "
            "Debrief, Compare, Path, and AI Export include an Observer notes section (heard radio + the note). "
            "Live list shows a cyan notes chip on that row (next to Extra attention “!”). "
            "Saving notes without a name still creates the Named-radio row (suggested label, Alert off). "
            "The same BLE hide-pencil rule as custom name applies. Settings backup includes the note.",
            "body_left",
        ),
        P(
            "<b>Bookmark (top-right)</b> watches <b>this radio only</b> — the device key "
            "(KIND + MAC), not the signature family. Outline = not watched; filled = on the "
            "watchlist. One tap; Fieldwatch prefills a name from the advertised name or type guess. "
            "A saved custom name is the large title; the advertised name is smaller underneath. Rename, "
            "Observer notes, turn Alert on or off, or drop it on Settings → Named radios. It does <b>not</b> go "
            "away when the radio leaves the Live display or goes stale. While it is gone, nothing alerts. "
            "If the <i>same</i> MAC returns, Fieldwatch alerts once "
            "(Settings → Watchlist alerts, plus Beep and/or Voice). Sitting detections do not fire "
            "again. Randomized BLE gets a new address: the watch stays on the old key and will not "
            "follow the rotation — that orphan stays on Named radios until you remove it or "
            "Clear all. Bookmark still works on a random / privacy address (IEEE local bit or Android Random type) — "
            "Fieldwatch cannot tell a static random MAC from one that will rotate, and the watch will not follow a new address. "
            "Signature-row bookmarks are the other kind of watch (any match of that "
            "family); Named radios never lists those. Full alert behavior: §10.1.",
            "body_left",
        ),
        figure_wrap(
            "fig-hunt.png",
            "Fig. 5 — Hunt.",
            "<b>Hunt</b> (BLE only). Device detail → Hunt. A full-screen page for this advertiser. "
            "You get a large RSSI and a cue: Very Close, Closer, Further, About the same, Quiet, or Gone. "
            "Very Close is about −45 dBm or louder (the same “very strong” band as detail). "
            "Closer / Further / About the same come from a few seconds of smoothed packets (about a 3 dB step). "
            "A ring around YOU contracts on Closer / Very Close and expands on Further. Loudest this hunt and a hunt-only sparkline sit with it. Cue and hint are in fixed slots so the dBm number does not jump. "
            "Beep and Vibrate (off by default, remembered) sit under Reset / Back and tick faster as RSSI gets louder — a short click, not the watchlist chirp. Silent when Quiet or Gone. "
            "The screen stays on while Hunt is open. "
            "It uses the live BLE store, even if the Live display was paused. This is not meters, not a compass, "
            "and not a map pin on the other radio. Wi-Fi access points have no Hunt button: stock "
            "Android batches them about every 30 s (about 8 s with Faster Wi-Fi AP scans), still "
            "too slow to walk toward. Randomized BLE can vanish mid-hunt (Gone). Experimental. Field tactics: §12.13.",
        ),
        P(
            "At the bottom of the page, under Hunt and the Signature family card: "
            "<b>Create signature from device</b>, then two shares that dump "
            "<b>this one radio</b> — not the 15-minute sit. Both use the Android share sheet. "
            "Neither is a legal identity. Treat the paste as operationally sensitive (MAC, SSID, payload, GPS).",
            "body_left",
        ),
        bullets([
            "<b>Share as text</b> — Plain dump of the fields on this page: identity, What this looks like, Extra attention if present, Notes if present, signal (including recent RSSI samples), advertisement decode, session times, last fix if tagging is on, matched signatures, raw bytes, 15-minute on-air windows. Sparklines become a number list. Header: experimental, not a legal identity, stock Android radios.",
            "<b>AI Export</b> — Paste-ready prompt for a chat about <b>this radio only</b>. Same experimental-use disclaimer as Reports → AI Export (the model is told to repeat it and not give safety advice). Includes the Share-as-text dump plus instructions to use public registries (IEEE OUI, Bluetooth SIG company, GAP Appearance, known formats) and to cite which field supports each claim. Asks for likely product class, competing hypotheses, what Fieldwatch could not see, and what not to conclude (owner, following, distance). If Online place names and GPS tagging are on, a geocode of the last fix may be included — that is still the <i>phone</i>, not this radio. One device; not the sit inventory.",
        ]),
        P("5.6 Reports tab", "h2"),
        figure_wrap(
            "fig-reports.png",
            "Fig. 6 — Reports → Sits. The selection drives Path, Debrief, Sit export, and Compare’s this-sit side. Privacy mode banner on.",
            "Bottom bar, next to Settings. Named sits (optional), Path, Debrief, Sit export, Compare sits, AI Export, "
            "signature candidates, and Log export. GPS tagging, place names, logging on/off, and rotate size stay on Settings. "
            "The sit you select here drives Path, Debrief, Sit export, and Compare’s this-sit side. "
            "If you never start a sit, those reports use last 15 minutes in RAM.",
        ),
        P("Named sits", "h3"),
        figure_wrap(
            "fig-sit.png",
            "Fig. 6 — Live, sit running.",
            "A sit is a named window of <b>everything heard</b>, kept even after the Live list "
            "drops the MAC. Start sit and End sit are on Reports. Name is optional "
            "(blank becomes a timestamp). Radios currently heard are copied in; new hears are "
            "journaled while it runs. Cap 3000 unique radios; unnamed BLE drops first. If the phone is low on memory, new unmatched radios are not added. One sit at a time. End sit on Reports "
            "freezes it — keep 10. Starting another sit at that cap warns that ending it will delete the oldest. Logging can be off. Settings pack does not include sits. Restore "
            "defaults does not wipe them. Not DF.",
        ),
        bullets([
            "<b>While it runs.</b> Live title FIELDWATCH · SIT and a status banner. Path, Debrief, Sit export, Compare’s this-sit side, and AI Export use this window, not 15 minutes. Filters, Hunt, TAK, and the 400-radio Live list stay as they are. Start and End sit stay on Reports.",
            "<b>After End sit.</b> The sit appears in the list on Reports. Pick it for Path / Debrief / Sit export / Compare this-sit, or leave Last 15 minutes selected. Rename / Delete sit under the list.",
            "<b>Path</b> — North-up plot of the selected sit (open, saved, or last 15 minutes). Full write-up: §5.6.1.",
            "<b>Debrief (text) / Debrief (PDF)</b> — Same sit report, two formats. Uses the selected sit, or last 15 minutes in RAM (~400 radios, hard ceiling 900). Hobby / as-is disclaimer at the top. Custom names replace advertised names. Observer notes is its own section after Where you were. Filters and Live view do not change what Debrief sees. On a drive without a sit, tap Debrief more than once (§11.4.1). PDF adds bold stay/transit lines, Label: kickers, and a letter-size path figure when GPS recorded a walk.",
            "<b>Compare (text) / Compare (PDF)</b> — This sit vs a second saved sit. Presence only: only here, only there, in both. Kind + MAC. Same window as Debrief. Observer notes after Windows. Last 15 minutes vs a named sit is a different net (RAM ~400 vs sit 3000). PDF overlays both walks when both have GPS.",
            "<b>Compare AI Export</b> — Paste-ready addendum for a chat. Embeds the onboard Compare, then overlap (both/union), Wi-Fi vs BLE in each bucket, RAND BLE among exclusives, exclusive Extra attention / Named radios, and Observer notes if any. Instructs the model not to reprint the lists. Sit report AI Export stays this window.",
            "<b>AI Export</b> — Sit-level paste-ready addendum (the open or selected sit, otherwise last 15 minutes with a 5-minute slice). Onboard Debrief verbatim, then rates, RSSI bands, Extra attention, finder-tag IDs, and Observer notes — not a second inventory. Instructs the model not to reprint Debrief. For a <i>single</i> radio, use AI Export on the device-detail page instead.",
            "<b>Sit export</b> — Own card under Sit report. Same Format chips as Log export, for a different file. Full write-up: §5.6.2.",
            "<b>Signature candidates</b> — Mines the rotating log for unmatched families that share a unique on-air ID. Create signature opens a draft (shared rule, no MAC pin). Save returns you to the list and re-runs it. Offline. §5.6.4, §11.5.",
            "<b>Log export</b> — Own card, titled Log export. Format dropdown and radios chips for the rotating session file. Full write-up: §5.6.3 and §11.6.",
            "<b>Reset / clear log</b> — Bottom of Reports. Deletes rotated files on the phone. Does not reset New detections already-seen. Does not delete sits.",
        ]),
        P("5.6.1 Path", "h3"),
        figure_wrap(
            "fig-path.png",
            "Fig. 6 — Reports → Path. North-up operator track, Extra attention (red) and Named (blue) hear-points, scale bar.",
            "Reports → Path is a north-up plot of <b>this phone</b> for the sit you selected "
            "(open sit, a saved sit, or last 15 minutes). "
            "Tag detections with GPS must have been on, and the path must be about 10 m or more, "
            "or the card says so. Start sit for a longer track than Live’s last 15 minutes. "
            "Privacy mode still draws the line; coordinate text is masked.",
        ),
        P(
            "The line is this phone. Header counts stops. "
            "Red dots are Extra attention; blue dots are bookmarked radios. "
            "Each KIND+MAC plots once, at the strongest RSSI hear-point. "
            "Observer notes on a bookmarked radio list after the numbered roster. A custom name without a bookmark does not plot. "
            "Thick green on the line is a stay (~40 m, same as Debrief Where you were). Time ticks (HH:mm) sit along the path. "
            "When several radios stack at one place, the plot shows one number and a count badge. "
            "Tap that number for a single inset; tap again to close. Isolated dots and the roster open detail. "
            "The distance scale sits under the plot. The route is inset so Start/End do not sit on the frame. "
            "GPS jumps that shoot out and back, or that imply more than about 150 km/h, are dropped from the trace."
        ),
        P(
            "Settings → <b>Online place names and maps</b> (on by default) also loads OpenStreetMap tiles under this trace when the phone has internet. "
            "Tiles fill the plot box, then clip; extra map shows around the route. "
            "Offline, no tiles, or Privacy mode: the north-up plot only — no error dialog. Airplane mode is fine. "
            "Turn that switch off to keep streets out of Debrief/AI Export and maps off Path together."
        ),
        P(
            "Observer notes on a <b>bookmarked</b> radio list after the numbered roster "
            "(plot number, custom name, MAC, the note). Live list still shows only the cyan notes chip, not the text."
        ),
        P(
            "Debrief PDF and Compare PDF include a letter-size operator-path figure of the same walk. "
            "On the PDF, stacked radios at one place share one number; the Path key lists every radio "
            "at that stop. Compare overlays this sit (solid) and the second sit (dashed) when both have "
            "enough GPS samples. Caption stays inside the panel outline."
        ),
        P("Compare sits", "h3"),
        figure_wrap(
            "fig-compare.png",
            "Fig. 6 — Reports → Compare sits. This sit is the same window as Debrief; pick a second saved sit.",
            "This sit versus a second saved sit. Same window as Debrief. Text and PDF, same letter layout. "
            "Second sit defaults to the next-newest saved sit. Presence only: only here, only there, in both. Kind + MAC. "
            "Custom names replace advertised names. Observer notes after Windows. "
            "Last 15 minutes vs a named sit is a different net (RAM ~400 vs sit 3000). "
            "PDF overlays both walks when both have GPS (this sit solid, second sit dashed).",
        ),
        P("5.6.2 Sit export", "h3"),
        P(
            "Own card on Reports, directly under Sit report. Same Format dropdown and Both / Wi-Fi / BLE chips as Log export, plus Share and Save. "
            "It is <b>not</b> the rotating log. The selected sit (open sit, a saved sit, or last 15 minutes) is the same window Path and Debrief already use."
        ),
        P(
            "<b>Sit export vs Log export.</b> Log export is the session tape: every hear written to disk while logging was on, across the whole day, rotating files. "
            "Sit export is the roster of that window: one row per unique KIND+MAC the sit kept (named sit cap 3000; last 15 minutes is RAM about 400). "
            "A radio heard a hundred times is one sit-export row and many log-export lines. "
            "Sit export does not need logging on — the sit stores its own radios and GPS trail. "
            "Log export needs Write to disk on, or the rotating file is empty. "
            "Clearing the log does not delete sits; deleting a sit does not touch the log. "
            "Signature candidates still mines the log, not Sit export."
        ),
        bullets([
            "<b>Log file — CSV / JSON lines</b> — One line per unique radio: kind, MAC, advertised name, custom name, Observer notes, RSSI min/max, channel, first/last, hits, lat/lon when tagged, Extra attention.",
            "<b>GPX / KML</b> — This phone’s path as a track, plus a hear-point per unique radio (loudest GPS-trail sample). Log export’s GPX/KML are hear-point waypoints only — no operator track.",
            "<b>WiGLE CSV</b> — One row per unique radio at that hear-point. Weaker than a log WiGLE file, which has many hears. Advertised SSID, not the custom name.",
        ]),
        P(
            "Privacy mode does <b>not</b> mask Sit export files (full MACs and lat/lon), same as Log export. Debrief / Compare / AI Export still mask. "
            "Fieldwatch does not upload. Empty Wi-Fi-only or BLE-only after a map format errors with a hint to tag GPS."
        ),
        P("5.6.3 Log export", "h3"),
        figure_wrap(
            "fig-log.png",
            "Fig. 6 — Reports → Log export. Format dropdown and radios chips. Rotating file is JSON lines; CSV / GPX / KML / WiGLE are Share/Save projections. Fieldwatch does not upload.",
            "The rotating file on disk is JSON lines: "
            "<font face='Courier'>files/logs/fieldwatch-NNN.jsonl</font>, one hear per line — the session tape, not the sit roster. "
            "Settings only turns logging on or off and sets rotate size. Format is chosen here, at Share / Save time. "
            "Existing CSV rotate parts still import. Fieldwatch does not upload. Sit export is the other card (§5.6.2).",
        ),
        bullets([
            "<b>Log file — CSV</b> — Same facts as the rotating file, spreadsheet columns. Concatenates rotate parts and strips extra headers.",
            "<b>Log file — JSON lines</b> — Same rows as the on-disk file.",
            "<b>GPX — GPS Exchange</b> — Waypoints where this phone heard each radio. Custom names on pin titles. Needs Tag detections with GPS; untagged rows are omitted.",
            "<b>KML — Google Earth</b> — Same hear-points as Placemarks.",
            "<b>WiGLE CSV — wigle.net</b> — WigleWifi-1.4 upload schema. Advertised SSID (not the custom name) so a WiGLE import still matches the air. You would upload; Fieldwatch does not.",
        ]),
        P(
            "Radios chips: Both radios, Wi-Fi only, BLE only. Empty GPS-tagged set after a map format "
            "errors with a radio-specific hint (turn tagging on, or pick Both). Privacy mode does "
            "<b>not</b> mask the log or map files — full MACs and lat/lon, same as the rotating file. "
            "Debrief / Compare / AI Export still mask. Share uses the Android share sheet. "
            "Save uses the system picker. Progress is a moving bar, not a stuck 0%. "
            "§11.1–11.3 are the columns; §11.6 is Share / Save / clear. Sit export is §5.6.2."
        ),
        P("5.6.4 Signature candidates", "h3"),
        P(
            "Use this when the Live display is full of unmatched radios and you want <b>families</b>, not a "
            "dump of every <font face='Courier'>?</font>. It reads the rotating on-disk log "
            "(§11), not the 15-minute Debrief map. Logging must have been on. "
            "Fieldwatch collapses rows to unique kind+MAC, <b>re-matches against the catalog on "
            "the phone now</b> (the log’s <font face='Courier'>fleets</font> column is whatever "
            "matched at write time — ignore it), then clusters leftovers that share a unique "
            "on-air ID: a name glob, a Wi-Fi vendor IE, a BLE UUID, manufacturer data, or a "
            "stable OUI. A family needs two or more distinct radios. Randomized addresses "
            "with no other ID, house-like names, chip-module OUIs, and protocol IEs "
            "(WPA / RSN / P2P) are skipped — one muted line at the top says how many. "
            "Device detail runs the same test on one radio (Signature family, §5.5). "
            "Already-tagged radios are omitted here; a second store-UUID chip is Create from device (§5.5, §9.2)."
        ),
        figure_wrap(
            "fig-candidates.png",
            "Fig. 7 — Signature candidates.",
            "Each card is one family: proposed name, class guess, radio count (number plus a Wi-Fi or Bluetooth icon), the rule in "
            "monospace, one sentence why it is not a house SSID, and a couple of examples. "
            "Privacy mode masks MAC tails on those examples. <b>Create signature</b> opens "
            "the existing editor as a draft with the <b>shared</b> rule — no MAC pin "
            "(that is Create from device, §9.2). Change the name or class, then Save. "
            "Nothing is added until you Save. The new row is <b>your</b> signature, not a "
            "stock catalog edit. Save returns you to Signature candidates and re-runs the "
            "list so that family should drop off. Cancel returns to the same list without "
            "re-running. Offline; no web lookup. Pattern match, not identity.",
        ),
        P(
            "Vendor-IE families (a Roku-class ID on a randomized BSSID) need the "
            "<font face='Courier'>vendor_ie</font> column, which is only on rows written after "
            "this feature shipped. Name globs and stable OUIs still work on older log parts. "
            "Empty list: logging was off, the log is short, or everything unmatched is noise. §11.5.",
            "body_left",
        ),
        P("5.7 Settings screen", "h2"),
        figure_wrap(
            "fig-settings.png",
            "Fig. 8 — Settings → Appearance.",
            "Settings is radios, logging, and appearance. It is not where you hide radios on the Live display, "
            "and not where you tap Debrief. Mute a noisy OUI or name with the per-rule switches "
            "on a signature. Hide a whole family on Filters. See §7.6, §8.",
        ),
        bullets([
            "<b>Appearance</b> — Night mode (off by default): red-on-black field display so chips, text, RSSI, Hunt, and Extra attention do not dump green or blue into a dark sit. Phone brightness is unchanged. Restore defaults turns it off. Fig. 9. Keep screen on (on by default): holds the display while Fieldwatch is in front so Samsung does not park BLE; turn it off when you pocket the phone. Privacy mode (off by default) hides the last three octets of every MAC on the Live display, radar, timeline, detail, Hunt, Named radios, and watchlist cards as **:**:**. GPS last-fix on detail and coordinates in Debrief / AI Export / detail Share become “masked”; street names are omitted from those sit reports. The OUI stays. Logs, matching, Moving with you, and saved signatures still use the real MAC and GPS. A TAK / CoT feed is paused while Privacy mode is on so full MACs and coordinates are not sent onto the LAN (§5.8).",
        ]),
        figure_wrap(
            "fig-settings-night.png",
            "Fig. 9 — Settings → Appearance, Night mode on.",
            "Night mode is the first switch under Appearance. Text, chips, RSSI, Hunt, and Extra attention become shades of red so green and blue do not dump into a dark sit. Phone brightness is unchanged. Restore defaults turns it off.",
        ),
        P("Radios, watchlist, logging, and backup", "h3"),
        bullets([
            "<b>Radios</b> — Scan intensity: High performance / Balanced / Battery saver (Wi-Fi ~30 / 40 / 55 s). Faster Wi-Fi AP scans: a second switch. Fieldwatch reads the OS Wi-Fi scan-throttle flag (Android 11+) and will not turn this on while that flag is still on. Developer options → Wi-Fi scan throttling → Off, then flip Fieldwatch. About every 8 s instead of ~30 s. Purpose: more chances to hear an AP while it is in range so a catalog signature (OUI or factory SSID) can fire — important on a drive, when a roadside or vehicle AP may only be loud for a few seconds. More battery and heat. Header may read Wi-Fi fast scan needs Developer options if the OS switch came back on. Fieldwatch cannot flip Developer options. §7.1.1, §10.3.1.",
            "<b>Watchlist</b> — Watchlist alerts is the master switch (off: no beep, voice, flash, jump, or shade card; bookmarking still works). Beep and Voice are independent: pip only, spoken phrase only, or pip then phrase. Voice (on by default) can say the class (finder tags, audio, …), the signature name (Apple AirTags, Axon, …), or both — Settings → What to say; default is Class + signature. Not Hunt; a second hit is skipped while a phrase is being spoken. Jump to new watched detection is on. Optional system notification (off by default). Test alert plays whatever is on. <b>Named radios (N)</b> opens the list of one-MAC names, Observer notes, and optional alerts: rename, notes, Alert on/off, remove one, or Clear all (signature watches stay on Signatures). Stock bookmarks watch Extra attention families (body-cam, camera glasses, recording wearables, pentest, public-safety vehicle APs, roadside / public camera + ALPR) and every built-in Drone-class row (DJI, Remote ID, Skydio, Autel, Parrot, HOVERAir). Unbookmark a row on Signatures if you do not want that alert. Flock LiteOn / Espressif OUIs can be noisy. Field write-up: §10.1–10.2.1.",
            "<b>Tag detections with GPS</b> — On by default. Requests live GPS and network location updates while scanning, then stamps each hear (detail, Moving with you, Debrief, log lat/lon). Last-known older than 30 s is ignored. Path stays 0 until a live fix. High-accuracy Location. Needed for Debrief distance/following, Filters → Moving with you, and heard-here TAK pins. Advertised payload pins (Remote ID) do not need this. A Log export with tagging on contains operator coordinates.",
            "<b>TAK / CoT feed</b> — Off by default. UDP Cursor-on-Target to ATAK / WinTAK / iTAK. Destination chips: This phone (127.0.0.1:10011), LAN multicast (239.2.3.1:6969), Custom. UDP only — a TAK server’s TCP 8087 is not this feed. Heard-here pins sit at operator GPS at the loudest hear (closest approach) and are labeled (here). Advertised lat/lon (stock Remote ID) sit on the aircraft; sticky UAS ID keeps one moving marker; decoded pilot lat/lon is a second pin. Gone radios are dropped. Settings shows last send. What to send chips: Extra attention (on), Payload location (on), Watchlist (off), All signatures (off). Privacy mode pauses the feed. Full configuration: §5.8. Sit: §12.15.",
            "<b>Online place names and maps</b> — On by default. One switch. Debrief and AI Export reverse-geocode GPS stamps (system geocoder). Reports → Path loads OpenStreetMap tiles under the trace (no Fieldwatch cloud, no API key). Offline, no geocoder, no tiles, or Privacy mode: Debrief uses coordinates only and Path stays the north-up plot — no error dialog. Turn off to keep streets and map tiles out of reports and Path together. Generate buttons are on Reports (§5.6, §5.6.1).",
            "<b>Logging</b> — Write to disk, rotate size, Stale after slider (when a radio is marked gone). Line/disk counts. The rotating file is JSON lines. Format (CSV, JSON lines, GPX, KML, WiGLE) and radios (Both / Wi-Fi / BLE) are on Reports → Log. Share, Save, and Reset / clear log are on Reports.",
            "<b>Allow background usage</b> — Switch. Opens Fieldwatch’s Battery page; turn on Allow background usage so the OS may run the scan when Fieldwatch is not in front. Follows that Android setting. Not Keep screen on.",
            "<b>Unrestricted battery</b> — Switch. Opens the Battery page. Select Unrestricted (not Optimized). Some phones (Samsung among them) do not open onto that choice — tap Allow background usage (the words, not the switch) to click through and select Unrestricted. Fieldwatch follows that grant when you return.",
            "<b>Signatures — export / import</b> — Export signatures shares a JSON pack of the whole catalog (stock plus any you added or edited, including Decode fields). Save signatures to SD card / storage… writes the same file through the system picker. Import signatures… reads a pack from another Fieldwatch. Same id or the same match rules are skipped, so importing twice does not clone the catalog. Extra rules on a stock row (for example a glob you added to Govee) merge onto the local row; a missing Decode fields map on that stock id is filled from the pack. A new name that already exists is imported as “Name (imported)”. Watchlist, filters, settings, logs, and GPS are not in the pack. A settings pack is a different file — use Import settings. Done and error both show an OK dialog. The file is <font face='Courier'>fieldwatch-signatures-YYYYMMDD.json</font>.",
            "<b>Update stock catalog from GitHub</b> — Needs internet. Pulls <font face='Courier'>dist/fieldwatch-signatures.json</font> from the Fieldwatch GitHub. Replaces stock rows, including Extra attention text. Bookmarks, Settings, muted stock rows, extra rules you added on a stock id, and signatures you added stay. Dialogs: no internet, could not reach GitHub, could not import catalog, already on the latest catalog, catalog updated. Offline: Import signatures from a file. A new APK still applies default watches; this button does not. Settings footer shows Catalog N under the app version.",
            "<b>Restore default signatures &amp; presets</b> — Rewrites the catalog (stock rows, class colors, and stock Decode fields maps), stock bookmarks (Extra attention plus Drone-class), the full stock filter-chip set (including chips you long-press deleted), named radios, and the default Settings switches (Keep screen on, Tag detections with GPS, Online place names, Voice on with Class + signature, Jump on, TAK / CoT off, Night mode off). This wipes custom signatures and any chips you saved. Export signatures and Export settings first if you want a backup. It is not an undo for a single rule. To drop one preset chip, long-press it on Filters. There is no second factory-settings button; this is the stock rewrite.",
            "<b>Settings backup — export / import</b> — Fieldwatch-only backup for a factory reset or a new phone. Export settings shares a JSON pack; Save settings to SD card / storage… writes the same file through the system picker. Import settings… replaces Settings switches, the current filter, filter presets, named radios, and signature watches on this phone. The catalog stays (that is Export / Import signatures). Logs, GPS, and already-seen for New detections only stay out of the pack. The first-run disclaimer is not overwritten, so scanning does not stop. Importing twice is the same as once. Picking a signature pack by mistake tells you to use Import signatures. Done and error both show an OK dialog. The file is <font face='Courier'>fieldwatch-settings-YYYYMMDD.json</font>. Not a Spectre config import.",
            "<b>Show Live tour</b> — Opens Live with the first-launch overlay again: Tune is Display, Pause, Filters, Signatures, Reports, Settings. The same overlay runs once after the license on a new install. Got it dismisses it.",
        ]),
        P(
            "How the Live display row looks — View (Radar, list, timeline, hybrid, By class), Sort, "
            "Title line, Subtitle line, RSSI bars, signature chips, Frequency, first/last, Brief hold — "
            "lives on Live → Tune (Display), not here. New detections only is a Filter."
        ),
        P(
            "The Settings footer (under the version line) shows this phone’s current IPv4, or none. "
            "It refreshes when you return to Settings. Same-phone ATAK CIV: that address is the TAK Host (§5.8)."
        ),
        P("5.8 TAK / CoT feed", "h2"),
        callout(
            "Off by default — this puts coordinates on the LAN",
            "The TAK / CoT feed sends Cursor-on-Target UDP markers to whatever is listening "
            "on the host and port you set (ATAK, WinTAK, or iTAK). Markers include "
            "the full MAC and either this phone’s GPS or an advertised payload lat/lon. "
            "Privacy mode pauses the feed so those are not sent. There is no Fieldwatch TAK server "
            "and no account. You are responsible for who is on that network and for local law. "
            "Do not use this overlay as a safety, intercept, or targeting picture.",
            "warn",
        ),
        P(
            "Settings → <b>TAK / CoT</b>, directly under Tag detections with GPS. The master "
            "switch ships off. Turning it on does not change the Live display, Filters, logging, or "
            "watchlist beeps. It is a parallel UDP feed of radios Fieldwatch already labeled."
        ),
        P("5.8.1 What it is, and what it is not", "h3"),
        P(
            "Cursor-on-Target (CoT) is the XML event ATAK already understands. Fieldwatch writes "
            "a small event per radio — uid, type, a point, a callsign, and a remarks line — and "
            "sends it as a UDP datagram. Destination chips: <b>This phone</b> "
            "(<font face='Courier'>127.0.0.1:10011</font>, ATAK CIV on this handset), "
            "<b>LAN multicast</b> (<font face='Courier'>239.2.3.1:6969</font>, other ATAKs on this Wi-Fi), "
            "or <b>Custom</b> (unicast IPv4 / hostname). Host still defaults to "
            "<font face='Courier'>239.2.3.1</font> port <font face='Courier'>10011</font> until you pick a chip. "
            "UDP only. A TAK server’s TCP 8087 is not this feed — ATAK on a phone that is already "
            "logged into a server may or may not relay injected CoT."
        ),
        bullets([
            "<b>It is</b> an overlay of radios Fieldwatch heard, at a coordinate Fieldwatch already has.",
            "<b>Heard here</b> means this phone’s GPS at the loudest hear so far (closest approach). The other radio is somewhere in earshot, not on that pin. Walking away does not drag it.",
            "<b>Advertised position</b> means the BLE advertisement itself decoded to WGS84 (stock Remote ID Location; any custom map whose field ids are <font face='Courier'>latitude</font> / <font face='Courier'>longitude</font>). That pin is what the gadget claimed, not a Fieldwatch DF fix.",
            "<b>It is not</b> a Remote ID plugin, a drone tracker, direction-finding, pairing, GATT, or Wi-Fi monitor mode. It does not join the multicast group (send-only). When a radio leaves the feed, Fieldwatch sends a CoT with stale=now so ATAK drops it instead of waiting ~120 s. Privacy pause does not send those gone events (the feed just stops; ATAK stale-times out).",
            "<b>It is not</b> the Live display. Filters do not shrink the feed. A radio you hid on the Live display still publishes if it matches What to send and has a pin.",
        ]),
        P("5.8.2 Two kinds of pin", "h3"),
        P(
            "Every published event needs a coordinate. Fieldwatch picks in this order:"
        ),
        numbered([
            "<b>Advertised payload.</b> If this radio has a sticky <font face='Courier'>latitude</font> + <font face='Courier'>longitude</font> from a decode map, that pair is the pin. GPS tagging can be off. Stock Remote ID uses this path.",
            "<b>Operator GPS (heard here).</b> Otherwise a tagged hear on this radio. The TAK pin holds the loudest RSSI so far (closest approach), not the last hear. Settings → Tag detections with GPS must be on, and there must be a live fix (last-known older than 30 s is ignored, same as the log). GPS tagging off and no payload → nothing is sent for that radio.",
        ]),
        P(
            "Remote ID (ASTM F3411 / OpenDroneID on BLE UUID 0xFFFA) rotates message types. "
            "A Location message has lat/lon/alt; the next packet is often Basic ID with none. "
            "Fieldwatch keeps the last <i>valid</i> Location pair on that radio for the session, "
            "so the ATAK pin does not blink off between types. Basic ID "
            "<font face='Courier'>uas_id</font> sticks the same way — that is the TAK uid, "
            "not the rotating BLE MAC — so one aircraft is one marker that <i>moves</i>. "
            "Until the first Basic ID, the uid is still the MAC; then it jumps once and the "
            "old MAC marker is dropped. The System message’s "
            "<font face='Courier'>op_lat</font> / <font face='Courier'>op_lon</font> are the "
            "<b>pilot / operator</b> location — they are a <i>second</i> pin (Orange), linked "
            "to the aircraft, not a substitute for it. A 0,0 pair, a non-finite number, or a value outside "
            "±90 / ±180 is rejected and does not clobber a previous good fix."
        ),
        callout(
            "Heard-here is still this phone",
            "An Axon, Flipper, or Pineapple marker at your GPS is “I heard that radio here,” "
            "not “that gadget is at this lat/lon.” The pin updates only when this hear is louder "
            "than the last send (you got closer). Walk away and it stays. A keep-alive every ~10 s "
            "refreshes the same lat/lon so ATAK does not drop it. This is not direction-finding. "
            "Remote ID Location is the exception: that pin is the advertised aircraft position, "
            "which may be kilometers from you.",
            "note",
        ),
        P("5.8.3 Generic field ids — Remote ID works without a special case", "h3"),
        P(
            "The publisher never asks “is this Remote ID?” It looks up stable decode field "
            "<b>ids</b> on whatever signature matched. Stock Remote ID already maps Location "
            "to <font face='Courier'>latitude</font>, <font face='Courier'>longitude</font>, "
            "and <font face='Courier'>alt_geo</font> (i32 LE × 1e−7 degrees, protocol v2). "
            "A custom signature that uses those same ids pins advertised position the same way. "
            "There is no TAK checkbox on Decode fields. Labels can say “Aircraft lat”; the id "
            "must be <font face='Courier'>latitude</font>."
        ),
        table(
            ["Field id", "Role"],
            [
                ["latitude (aliases lat)", "Advertised WGS84 latitude. Required with longitude for a payload pin."],
                ["longitude (aliases lon, lng)", "Advertised WGS84 longitude. Required with latitude."],
                ["alt_geo (aliases altitude, alt, hae)", "Optional HAE meters for the CoT point. Sticky with the lat/lon pair."],
                ["op_lat / op_lon (aliases operator_lat / operator_lon)", "Remote ID System operator (pilot) location. Second TAK pin, linked to the aircraft. Not the aircraft pin. Do not reuse these ids for the aircraft."],
                ["uas_id (alias serial)", "Remote ID Basic ID. Sticky. TAK aircraft uid when at least four characters after cleaning."],
                ["self_id", "Remote ID Self ID. Sticky. Preferred advertised callsign when present."],
            ],
            [2.4 * inch, 4.1 * inch],
        ),
        Spacer(1, 6),
        P(
            "How to wire a custom map: Signatures → the row → Decode fields → More on the "
            "lat/lon cards → set ID to <font face='Courier'>latitude</font> and "
            "<font face='Courier'>longitude</font> (scale as the spec requires; Remote ID is "
            "i32 LE, scale 1e−7, unit °). Save. Settings → TAK / CoT feed on, Payload location "
            "chip on. No other TAK setup. §9.6.3."
        ),
        P("5.8.4 Turn it on — step by step", "h3"),
        numbered([
            "Put the phone and the TAK client on the <b>same LAN</b> if you will use multicast (239.2.3.1). Cellular and most guest Wi-Fi will not deliver that group. Some access points filter multicast — then use unicast.",
            "On ATAK CIV: Manage Inputs should show a UDP CoT listener (typically 10011 on 0.0.0.0). Stock ATAK also listens to 239.2.3.1:6969 for self-SA. You do not install a Fieldwatch plugin.",
            "Fieldwatch: Settings → Privacy mode <b>off</b> (the feed pauses while it is on).",
            "Fieldwatch: Settings → TAK / CoT feed → On. Destination: This phone (127.0.0.1:10011) for ATAK CIV on this handset; LAN multicast (239.2.3.1:6969) for other ATAKs on this Wi-Fi; Custom for a unicast IPv4. If This phone does not plot, Custom with this phone’s Wi-Fi IPv4 (Settings footer) and port 10011. UDP only — not TCP 8087.",
            "Leave Extra attention and Payload location on (they ship on). That is the out-of-the-box set: “!” families at your GPS, plus any radio that advertised lat/lon (Remote ID) at that advertised point.",
            "Scan. An Extra attention radio with a GPS fix, or a Remote ID Location packet, should appear on the TAK map within a few seconds. Heard-here callsigns end in (here). Remote ID callsign is Self ID, else UAS ID, else the signature name. Settings shows last send count, dest, and time under the host fields.",
        ]),
        P("5.8.5 Host and port", "h3"),
        table(
            ["Setting", "Default", "What to put"],
            [
                ["This phone", "127.0.0.1:10011", "ATAK CIV on this handset. If nothing plots, Custom with the footer IPv4 and port 10011."],
                ["LAN multicast", "239.2.3.1:6969", "Other ATAKs on this Wi-Fi (SA multicast). TTL 1. Guest Wi-Fi that isolates clients will fail."],
                ["Custom host", "239.2.3.1", "Unicast IPv4 / hostname, or the default multicast address with port 10011. Trimmed on save. Empty is ignored so a wipe of the field does not store blank."],
                ["Port", "10011", "UDP port 1–65535. Digits only. ATAK CIV CoT input (This phone). SA multicast uses 6969. Not a TAK server TCP 8087."],
            ],
            [1.2 * inch, 1.3 * inch, 4.0 * inch],
        ),
        Spacer(1, 6),
        bullets([
            "<b>Multicast (default).</b> Fieldwatch sends; it does not join the group. TTL is 1 (this LAN, not routed). Phone and ATAK must share a Wi-Fi (or Ethernet via a tether) that actually forwards 239.2.3.1. A hotspot that isolates clients will fail; try unicast to the ATAK device’s IP.",
            "<b>Unicast.</b> This phone uses 127.0.0.1:10011. Custom host = one EUD’s IPv4. Fieldwatch does not open a TCP stream to a TAK server (8087). If ATAK on this phone is already logged into a server, that ATAK may or may not relay injected CoT — prove it on one sit before counting on it.",
            "Fieldwatch already has install-time INTERNET (Online place names). The feed uses that permission for UDP. There is no Fieldwatch cloud; packets go only to the host you typed.",
            "Restore default signatures &amp; presets resets host/port and turns the feed off.",
        ]),
        P("5.8.6 What to send", "h3"),
        P(
            "Four independent chips, shown only while the master switch is on. A radio publishes "
            "when <i>any</i> selected chip matches <i>and</i> a pin exists. Unmatched radios "
            "(no signature) never go, unless you named that one MAC and Watchlist is on with Alert on."
        ),
        table(
            ["Chip", "Ships", "Who is selected"],
            [
                ["Extra attention", "On", "Any matched signature whose Extra attention text is not empty (stock: Hobby BLE serial, Axon, WatchGuard Video, Ray-Ban / Meta glasses, Snap Spectacles, Fieldy, Plaud Note, Hak5 Pineapple, Flipper Zero, Pwnagotchi, Marauder / Deauther, GhostESP, Bruce, Porkchop, Cradlepoint, AirLink, Compex, Novatel Wireless, Utility Inc, Flock, Penguin, Pigvision, FS Ext Battery, Genetec AutoVu, Rekor, Motorola Vigilant, Verkada, Avigilon, Axis, Hikvision, Dahua, Hanwha Wisenet, Uniview, Rhombus). Pin is usually heard-here (your GPS)."],
                ["Payload location", "On", "Any radio with a sticky advertised lat/lon from a decode map. Stock Remote ID is the reason this defaults on: that row has no Extra attention mark, so without this chip it would never publish. Custom maps with the same field ids are included."],
                ["Watchlist", "Off", "Bookmarked signatures, and Named radios whose Alert is on. Named radios with Alert off (label only) stay off the feed."],
                ["All signatures", "Off", "Every labeled radio. A plaza will flood ATAK. Use it for a short sit, not a walk."],
            ],
            [1.45 * inch, 0.7 * inch, 4.35 * inch],
        ),
        Spacer(1, 6),
        P(
            "A radio can match more than one chip (Remote ID is Payload location; a bookmarked "
            "Axon is Extra attention and Watchlist). It is still one uid, one marker. "
            "When many radios qualify at once, Fieldwatch sends at most about 24 per tick, Extra "
            "attention and payload pins first, then louder RSSI."
        ),
        P("5.8.7 Privacy, GPS tagging, and when nothing is sent", "h3"),
        bullets([
            "<b>Master off</b> (default) — no datagrams.",
            "<b>Privacy mode on</b> — the feed pauses even if the master is on. Settings shows a line under the switch. Fieldwatch will not send masked MACs or masked coordinates. Turn Privacy mode off to publish; the screen can stay on Privacy for screenshots while you are not feeding TAK.",
            "<b>GPS tagging off</b> — heard-here pins stop. Advertised payload pins (Remote ID) still publish.",
            "<b>No live GPS</b> — same as tagging off for heard-here. Path 0 m on the Live display is the hint. High-accuracy Location, scanning running, wait for a fix.",
            "<b>No matching chip</b> — a Ruuvi, a phone, an unmatched AP does not go unless you turn All signatures or Watchlist (and bookmark it).",
            "<b>Invalid advertised coords</b> — 0,0, NaN, or out of range are ignored. A previous good Location pair on that radio stays.",
            "<b>Scanning stopped</b> — Stop on the scan notification ends the loop. Markers already on ATAK stale out in ~120 s.",
        ]),
        P(
            "Advertised aircraft, pilot, and this-phone markers are sent when they first qualify, "
            "when they have moved about 30 m, or when about 10 s have passed. Heard-here markers "
            "move only when this hear is louder than the last send (closest approach); weaker hears "
            "still refresh the same lat/lon after about 10 s so ATAK does not stale-drop. Peak RSSI "
            "is kept across those keep-alives so a later louder hear still moves the pin. Sitting on "
            "one corner does not spam ATAK. When a radio leaves the feed (gone, or no longer selected), "
            "Fieldwatch sends a CoT with <font face='Courier'>stale</font> equal to now so ATAK drops it. "
            "Missing the 24-per-tick cap or a GPS blip does not count as gone. Privacy pause does not "
            "send those events."
        ),
        P("5.8.8 What ATAK shows", "h3"),
        bullets([
            "<b>uid</b> — Remote ID with a sticky UAS ID is <font face='Courier'>FIELDWATCH-RID-</font> plus that id (one aircraft, one moving marker). Otherwise <font face='Courier'>FIELDWATCH-BLE-</font> or <font face='Courier'>FIELDWATCH-WIFI-</font> plus the MAC without colons. Pilot pin is <font face='Courier'>FIELDWATCH-PILOT-</font> plus the same id. A rotated BLE address without a UAS ID is still a new marker.",
            "<b>type</b> — advertised drone-class (Remote ID / DJI class) is <font face='Courier'>a-u-A-M-H-Q</font> (unknown UAV, Yellow). Heard-here Extra attention is <font face='Courier'>a-u-G</font> (Maroon). Pilot is <font face='Courier'>a-u-G</font> (Orange). Other ground is Cyan. Not friendly/hostile affiliation.",
            "<b>callsign</b> — Named radio label if that MAC is named; else advertised Self ID / UAS ID; else an Extra attention signature name; else the first matching signature; else the advertised name; else the MAC tail. Heard-here callsigns end in (here).",
            "<b>point</b> — lat/lon as above. <font face='Courier'>hae</font> is advertised altitude when known, else the CoT unknown (9999999). ce/le are unknown.",
            "<b>remarks</b> — A short card (newlines) when you inspect the marker: callsign, Wi-Fi or BLE, full MAC, RSSI dBm, Wi-Fi channel when known, advertised position / heard here (operator GPS) / operator (pilot) position, UAS ID, advertised name if it is not already the callsign, up to three signature names, Extra attention text. Cap about 800 characters. Map label is still the 32-character callsign. Privacy mode pauses the feed, so remarks are not sent while it is on.",
            "<b>how</b> — <font face='Courier'>m-g</font> (machine / GPS). Time/start/stale are UTC.",
        ]),
        P(
            "This is not a TAK data package, not a KML, and not a GeoJSON share. If the map "
            "stays empty: Privacy mode, master off, wrong LAN, AP multicast filter, or no "
            "qualifying radio with a pin. §14 troubleshooting."
        ),
        P("5.8.9 Limits you will hit", "h3"),
        bullets([
            "Stock Android does not give Wi-Fi Neighbor Awareness Networking / stuffed beacons reliably. Many Remote ID aircraft also broadcast Wi-Fi; Fieldwatch’s Remote ID row is the BLE UUID 0xFFFA advertisement. A miss on BLE is a miss on TAK.",
            "No DF, no range. Heard-here is the loudest operator GPS while that radio was in earshot, not a bearing. Advertised position is whatever the gadget encoded, including a bad GPS on the aircraft.",
            "No pairing, no GATT, no encrypted ads. If lat/lon only exist after a connect, Fieldwatch will never pin them.",
            "A randomized BLE MAC is a new uid when it rotates, unless a sticky UAS ID is already on that radio (Remote ID Basic ID). Phones and bag tags do not get that id.",
            "All signatures in a plaza will load ATAK with café APs and headphones. That is the chip working. Turn it off.",
            "Multicast does not traverse the internet. A teammate on LTE will not see 239.2.3.1 from your phone. This feed is UDP; it does not log into a TAK server. Put ATAK on the LAN, or prove whether that ATAK relays injected CoT to the server.",
        ]),
        P("5.8.10 Field checklist", "h3"),
        P(
            "Feed status under the host fields shows pins on the feed (what Fieldwatch is keeping on ATAK), sends this tick, dest, error, and time. A send count that flashes and returns to 0 is the keep-alive hold (about 10 s, or 30 m for advertised / this-phone) — the on-the-feed number should stay. A drone sit with ATAK open is §12.15. Extra attention overlay (body-cam / glasses / "
            "pentest) is the same switch with Payload location optional. For decode-map ids, §9.6."
        ),
    ]

    # 6 Views
    flow += [
        PageBreak(),
        P("6. Visualization Modes", "h1"),
        P(
            "All five views show the same filtered radios. If radar looks empty and the list "
            "does not, check the filter first. Radar draws stale devices as dim blips so sitting "
            "on the Filters tab does not blank the plot. List, hybrid, and timeline share Display → "
            "Title line and Subtitle line; radar labels stay a short signature name or type guess. "
            "Display is how you pack more or less onto each row without changing who is on the air (§5.3)."
        ),
        P("6.1 Classic radar", "h2"),
        figure_wrap(
            "fig-radar.png",
            "Fig. 10 — Classic radar.",
            "<b>What it shows.</b> A polar plot. You are the center. Each filtered device is a blip.",
        ),
        bullets([
            "Radius is last-heard RSSI. Rings are labeled −40, −60, −80, −100 dBm. Stronger signals sit closer to YOU. Brief hold keeps the blip on that ring; it does not crawl outward.",
            "Angle is a hash of the MAC. It is a stable seat, not a compass heading.",
            "Color is the first matching signature palette (class colors, §9.5), or the RSSI palette if unmatched (≥−55 green, −55 to −70 amber, −70 to −85 orange, else red).",
            "Named (signature-matched) blips are larger, haloed, and labeled with the first matching signature name (list views can show three).",
            "A watchlist hit pings that blip for one second: two expanding rings and a bright core (the same flash as the list — pip, spoken class, or both). After the ping, a phosphor ring stays on that blip for the rest of the session (same radios as the list bell). Alerted blips are drawn on top. Distinct from the named class-color halo.",
            "Dim blips are last-seen positions after the stale window.",
            "The rotating sweep is a scope cue only. The bright line leads; the shaded fan trails. It does not scan azimuth.",
            "Tap a blip for detail.",
        ]),
        P("<b>How to interpret.</b> A cluster of bright, close blips is a dense, loud RF neighborhood — typically the room you are in plus nearby APs. A named halo that walks inward on successive looks is something getting stronger, not necessarily closer in a straight line. If every blip is dim, the radios have not been heard within the stale window; check that scanning is still running and Location is on.", "body_left"),
        P("<b>When it is most useful.</b> Walk-throughs, briefings (“how busy is this plaza”), and watching whether a named signature is in the current bubble without reading MACs.", "body_left"),
        table(
            ["Strengths", "Weaknesses"],
            [
                ["Instant density and relative loudness. Signature color pops.", "No real bearing. Overplotting in crowded 2.4 GHz spaces. Labels omitted when more than 24 unnamed devices are up."],
                ["Same filter as the list, so Signatures only becomes a clean named-pattern view.", "Easy to over-read radius as meters. Indoor multipath will jump a blip in and out."],
            ],
            [3.25 * inch, 3.25 * inch],
        ),
        Spacer(1, 8),
        P("6.2 Strength-ranked live list", "h2"),
        figure_wrap(
            "fig-list.png",
            "Fig. 11 — Strength list. Privacy mode masks MAC tails (**:**:**). Radios with Observer notes also carry a cyan notes chip on this row (next to Extra attention “!”).",
            "<b>What it shows.</b> Devices in Display → Sort order (default Strongest averaged over 30 s; every option is in §5.3). This is "
            "the view Display was built for: one row per radio, as dense or as spare as you set. "
            "Each row: class glyph in a circle (unmatched = ?); first line from Title line (default MAC); "
            "second line from Subtitle line (Wi-Fi or Bluetooth icon, then Name + type plus rand/gone). Subtitle None "
            "hides that line and moves rand/gone onto the title — the kind icon goes with the subtitle so the "
            "title stays a MAC or name. Vendor is not on the list — open detail. When "
            "Frequency is on, channel · MHz sit under the RSSI on the right. Optional &gt;&gt;/&lt;&lt; "
            "trend, RSSI bar, first/last ages, and compact signature chips (up to three; further "
            "matches on detail). A hexagon in a chip means that signature has a Decode fields map; "
            "the values are on detail (§5.4, §9.6). A bookmarked new hit flashes the row for one second when the watchlist fires. A phosphor bell stays on the chip row for the rest of the session. "
            "Fit-the-row recipes: §5.3.",
        ),
        P("<b>How to interpret.</b> Work top-down unless Sort is New at bottom (then new rows append). A sudden new row near the top is something loud that just appeared (strongest / newest sorts). A name of &lt;hidden&gt; is a hidden SSID AP. On the subtitle, unnamed BLE is unnamed (no advertised name and no useful decode — the Bluetooth icon already marks LE). Title Advertised name still says unnamed LE. Apple, Inc. · AirTag… on the subtitle is Name + type, a type guess. IEEE vendor is on the detail page. The number on the right is RSSI.", "body_left"),
        P("<b>When it is most useful.</b> Default working view. Triage, tap a row for detail, confirm a filter, or pick a device to watch. Thin it (Subtitle None, extras off) in a plaza before you start hiding radios with Filters.", "body_left"),
        table(
            ["Strengths", "Weaknesses"],
            [
                ["Readable identifiers. Best place to act (tap for detail / watch). You can change how much each row shows.", "No spatial intuition. Easy to miss a weak named device if you only look at the top."],
                ["Gone rows stay visible so you can still inspect history. Subtitle None packs more rows without dropping radios.", "Very dense environments still produce a long scroll — then use Filters, not only Display."],
            ],
            [3.25 * inch, 3.25 * inch],
        ),
        Spacer(1, 8),
        P("6.3 Timeline view", "h2"),
        figure_wrap(
            "fig-timeline.png",
            "Fig. 12 — Timeline.",
            "<b>What it shows.</b> One card per filtered device heard in the last 15 minutes, in Display → Sort order. Title and subtitle follow the same Display lines as the list, including the class glyph and the Wi-Fi / Bluetooth icon on the subtitle. Under that identity is a time strip: left = 15 minutes ago, right = now. Solid segments are on-air windows; gaps are real dropouts. The number is current RSSI, not an average over the bar; Frequency, if on, sits under it. The clock ticks about once a second so a sitting radio’s bar grows toward the right until it fills the window.",
        ),
        P("<b>How to interpret.</b> A continuous growing bar is a fixture (home AP, or a phone that keeps advertising). Wi-Fi scan waits do not count as dropouts — sitting APs stay one bar. A new segment appears only after the device is marked gone (longer of Stale and Brief hold) and then heard again. A short tick that repeats is a low-duty advertiser. A bar that starts when you turned a corner and ends when you left is geometry, not “the device left the universe.” After 15 minutes of continuous presence the bar is full width; it does not grow past the left edge.", "body_left"),
        P("<b>When it is most useful.</b> Sits, vehicle rides, and any question of the form “did this come and go with me.”", "body_left"),
        table(
            ["Strengths", "Weaknesses"],
            [
                ["Makes appearance / disappearance obvious. Good for log correlation.", "Fixed 15-minute window. No multi-hour strip in-app (use the exported log)."],
                ["Independent of radar’s RSSI-radius metaphor. Same Title/Subtitle as the list.", "UUID still waits for detail. Channel · MHz only if Frequency is on."],
            ],
            [3.25 * inch, 3.25 * inch],
        ),
        Spacer(1, 8),
        P("6.4 Hybrid view", "h2"),
        figure_wrap(
            "fig-hybrid.png",
            "Fig. 13 — Hybrid.",
            "<b>What it shows.</b> The strength list (same Title/Subtitle, Sort, and extra-fact switches) plus a full-width sparkline of recent RSSI samples (up to 40 points kept per device) and the same &gt;&gt; / &lt;&lt; trend mark. Scale is fixed: top = −30 dBm, bottom = −100 dBm. A faint 10 dB grid with a left-hand scale (−30 / −50 / −70 / −100) and four vertical columns. A dot marks the newest packet. There is no banner on this view; the grid is the legend. In a plaza, drop back to Strength list and Subtitle None before you blame the sparkline for the clutter.",
        ),
        P("<b>How to interpret.</b> Left = older packets, right = newest, up = stronger. A line high on the strip is loud. A line near the bottom is weak. A flat line at one height means the radio is sitting still at that strength — that is the usual Wi-Fi picture between 30 s scans. The line only climbs or drops when RSSI actually changes. After 40 samples the waveform shifts left. The chevron is the glanceable answer: &gt;&gt; much stronger (~+8 dB), &gt; stronger (~+3 dB), = steady, &lt; weaker, &lt;&lt; much weaker. A sawtooth is often a duty-cycled BLE advertiser, not motion.", "body_left"),
        P("<b>When it is most useful.</b> Following one or two candidates while still seeing the rest of the field. Better than radar for “is this getting louder.”", "body_left"),
        table(
            ["Strengths", "Weaknesses"],
            [
                ["Combines identity and trend without leaving the list. Fixed scale so a sitting radio is a flat line at a real dBm height.", "Still no bearing. Detail has a larger copy of the same plot."],
                ["Same actions as the list (tap for detail).", "The sparkline is packet order, not a clock — Wi-Fi sits as a flat line between scans."],
            ],
            [3.25 * inch, 3.25 * inch],
        ),
        Spacer(1, 8),
        P("6.5 By class", "h2"),
        figure_wrap(
            "fig-by-class.png",
            "Fig. 14 — By class.",
            "<b>What it shows.</b> An outline of the same filtered Live display. Every signature class "
            "(Audio, Wearables, Cameras, …) is a row with the class glyph, A–Z by class name. "
            "Show all (default) keeps empty classes dimmed at 0 so the list does not jump; Collapse empty hides those zeros. "
            "The chips sit at the top of the list and scroll off as you go down — they are not sticky. "
            "Tap a class to list the signatures in it (A–Z by name; a hexagon on the signature row means that row has a Decode fields map); tap a signature to list those radios "
            "(same class glyph, Title/Subtitle, RSSI bars, signature chips, Frequency, and first/last as the strength list); tap a radio for the existing detail page. "
            "Back returns to the outline. Unmatched is last. "
            "This is a Display view, not a Report — Debrief is still the 15-minute snapshot on Reports.",
        ),
        P(
            "<b>How to interpret.</b> Counts are radios, not packets. A dual-chip radio (Tapo on a TP-Link OUI, "
            "for example) appears under each class it matched; the header still reports unique radios on the Live display, "
            "and “N in more than one class” when that happens. Filters still apply: Show only Cameras → Cameras has the hits and the other classes sit at 0 (unmatched is 0 too) unless Collapse empty is on. "
            "Tapping a class does not turn on Show only. Pause, New detections, and Moving with you "
            "work the same as the list. A watchlist hit opens that class and signature so the row "
            "can flash (and jumps so those headers stay on screen when the radio is close enough, "
            "if Jump to new watched detection is on). A phosphor bell stays on that radio row for the rest of the session.",
            "body_left",
        ),
        P("<b>When it is most useful.</b> A sit where you want “how many of each family” before you pick a radio. Plaza noise is the Unmatched count.", "body_left"),
        table(
            ["Strengths", "Weaknesses"],
            [
                ["Class totals without walking the list. Same radios as Live display.", "No RSSI plot. Dual-class radios appear twice if you expand both."],
                ["Tap through to the same detail page. Display extra facts apply to the radio rows.", "A Cameras filter leaves other classes at 0 — tap All traffic first for the whole plaza."],
            ],
            [3.25 * inch, 3.25 * inch],
        ),
    ]

    # 7 Detection
    flow += [
        PageBreak(),
        P("7. Detection Methods", "h1"),
        P("7.0 How collection fits together", "h2"),
        P(
            "This chapter is how Fieldwatch hears radios on stock Android. You can skip the API "
            "names and still run the app; they are here so a technical reader can see what the "
            "phone will and will not expose."
        ),
        P(
            "One pipeline: <b>hear</b> (this chapter) → <b>match</b> named signatures (§7.3–7.4, §9) → "
            "<b>filter</b> which radios appear on the Live display (§8) → <b>show and log</b> (the Live display, watchlist, "
            "JSON lines) → <b>report</b> (Path / Debrief / Compare / AI Export / Signature candidates, §11). Filters never change what was "
            "heard, and Display never changes the filter. Debrief ignores the Live display filter and uses the "
            "last 15 minutes of memory. Signature candidates reads the rotating log, not that RAM map."
        ),
        P(
            "Two surfaces only: Wi-Fi <b>access-point</b> scan results, and BLE advertisements. "
            "No cellular, no Wi-Fi clients, no Bluetooth Classic inquiry, no direction finding."
        ),
        P("7.1 Wi-Fi passive scanning", "h2"),
        P(
            "Fieldwatch registers for <font face='Courier'>SCAN_RESULTS_AVAILABLE_ACTION</font> and "
            "a ScanResultsCallback, and calls <font face='Courier'>WifiManager.startScan()</font> "
            "on a quota-aware interval. Each ScanResult becomes an observation of kind WIFI with "
            "BSSID, cleaned SSID, RSSI, channel derived from MHz (2.4, 5, and 6 GHz bands), "
            "hidden-SSID flag, and extras (capabilities, and on API 30+ wifiStandard and channelWidth)."
        ),
        P(
            "A successful scan returns every AP in one batch; the OS then requires a wait. "
            "High performance asks about every 30 s, balanced 40 s, saver 55 s. If startScan "
            "is refused, Fieldwatch backs off (up to 45 s) and shows Wi-Fi waiting on OS. Cached "
            "results are not treated as a fresh batch. The last AP set is held on screen during "
            "the wait, and empty result lists are ignored. Signature matching only runs on radios "
            "from those batches. An AP you drove past between scans is not labeled, not logged as "
            "a new hear, and does not beep — even if its OUI is in the catalog. Faster Wi-Fi AP "
            "scans (§7.1.1, §10.3.1) is the optional way to shrink that gap after the OS throttle "
            "is off."
        ),
        P("7.1.1 Faster Wi-Fi AP scans (optional)", "h2"),
        P(
            "Stock Android is the bottleneck, not Fieldwatch’s matcher. A Cradlepoint IBR, an "
            "AirLink OUI, a UniFi vendor IE, a Cisco BSSID, or a hidden fleet SSID is only "
            "tagged when a <font face='Courier'>startScan()</font> batch actually contains that "
            "BSSID; between batches the catalog is idle. On a sit that is usually fine: the "
            "same house and campus APs come back every 30 s. On a drive it is the difference "
            "between catching a vehicle or roadside AP and missing it entirely."
        ),
        P(
            "How far you roll between batches, approximate:"
        ),
        table(
            ["Speed", "Stock High performance (~30 s)", "Faster scans (~8 s)"],
            [
                ["25 mph (city)", "about 335 m", "about 90 m"],
                ["45 mph", "about 600 m", "about 160 m"],
                ["65 mph (highway)", "about 870 m", "about 230 m"],
            ],
            [1.7 * inch, 2.5 * inch, 2.3 * inch],
        ),
        Spacer(1, 6),
        P(
            "A patrol or fleet gateway (Cradlepoint IBR600/IBR1100/IBR1700, Sierra Wireless "
            "AirLink, some Compex / Novatel / Utility Inc radios) is often only in RF range for "
            "one short pass — a few hundred meters at speed, sometimes less with body, glass, "
            "and other cars in the way. Hidden SSIDs still expose the MAC, so the OUI rule is "
            "the usual hit, but the phone still has to scan while that MAC is audible. One "
            "stock batch every 600–870 m can skip the whole pass. Batches every ~8 s give "
            "several chances to hear the BSSID, run the signature, show the chip, and (if that "
            "row is bookmarked) beep. That is the point of the switch: interesting APs by "
            "signature, sooner, while you are moving."
        ),
        P(
            "It does not make Wi-Fi continuous. Each scan is still a full AP list, then a wait. "
            "It does not see Wi-Fi clients. It does not change BLE. It is not Hunt (Hunt stays "
            "BLE-only because Wi-Fi will never be packet-by-packet). RSSI on a drive is still "
            "not meters. Keep Tag detections with GPS on if you want that hear on the path and "
            "in Debrief."
        ),
        P(
            "How to turn it on: Android 11 or newer. Settings → About phone → tap Build number "
            "until Developer options exist. Developer options → <b>Wi-Fi scan throttling</b> → "
            "Off. Return to Fieldwatch → Settings → Faster Wi-Fi AP scans → On. Fieldwatch reads "
            "<font face='Courier'>WifiManager.isScanThrottleEnabled()</font> before it will "
            "accept the switch; if the OS is still throttling, the switch stays off and a "
            "dialog explains, with Open developer options. Fieldwatch cannot change the OS switch. "
            "If you turn OS throttling back on, the Fieldwatch switch may stay saved on but scans "
            "return to ~30/40/55 s and the header can read Wi-Fi fast scan needs Developer "
            "options. Come back to Settings and the caption updates on resume."
        ),
        P(
            "While it is actually in effect, Fieldwatch skips the four-scans-per-two-minutes quota "
            "and asks about every 8 s. If <font face='Courier'>startScan()</font> starts "
            "returning false, the existing backoff still applies (up to 45 s) so a hostile OEM "
            "limit does not spin. Battery and heat go up; use it for the drive or the first "
            "minutes on a corridor, not an all-day pocket sit. Battery saver intensity still "
            "applies to BLE. Turn Faster Wi-Fi AP scans off when you no longer need the extra "
            "AP batches."
        ),
        P(
            "Every WIFI observation is an access-point-class emitter: a router, extender, mesh "
            "node, phone hotspot, or IoT/camera radio that is advertising an SSID. The Live display "
            "labels these AP. A device that is only associated as a station — a phone on café "
            "Wi-Fi, a laptop, a camera talking to its own AP — is not in the scan results and "
            "will not appear, even if it is a foot away. Hidden-SSID APs still appear (empty "
            "name, HIDDEN flag) because they are beaconing; hidden-SSID <i>clients</i> do not."
        ),
        P(
            "This is not promiscuous capture. Fieldwatch never opens a monitor interface and never "
            "sends probe requests beyond what the OS scan already does. There is no continuous "
            "Wi-Fi search on a stock Galaxy. If the job needs stations, probes, or frames to a "
            "specific MAC, use a dedicated sniffer in parallel."
        ),
        P("7.2 Bluetooth Low Energy scanning", "h2"),
        P(
            "A single <font face='Courier'>BluetoothLeScanner</font> runs in the foreground service "
            "with a match-all ScanFilter (so Samsung does not treat it as an unfiltered screen-off "
            "scan). Ads are queued with drop-oldest backpressure; the service batches them instead "
            "of starting a coroutine per packet."
        ),
        table(
            ["Intensity", "BLE ScanSettings", "Wi-Fi interval"],
            [
                ["High performance", "SCAN_MODE_LOW_LATENCY, CALLBACK_TYPE_ALL_MATCHES, MATCH_MODE_STICKY. Recycled ~70 s on / ~2.5 s rest. If the OS parks the scan (no ads ~18 s), rest 8–12 s and continue in BALANCED until it recovers.", "30 s *"],
                ["Balanced", "SCAN_MODE_BALANCED, same match policy, longer on-period (~180 s)", "40 s *"],
                ["Battery saver", "SCAN_MODE_LOW_POWER, recycle about every 20 minutes", "55 s *"],
            ],
            [1.45 * inch, 3.55 * inch, 1.5 * inch],
        ),
        Spacer(1, 4),
        P(
            "* Wi-Fi column is the stock OS-safe wait. About 8 s instead while Faster Wi-Fi AP "
            "scans is in effect (Developer options throttle off + Settings switch). §7.1.1, §10.3.1.",
            "caption",
        ),
        Spacer(1, 6),
        P(
            "Each advertisement yields address, local name, RSSI, service UUIDs (including "
            "keys from service data), the first manufacturer ID and a short manufacturer hex, "
            "and extras (primary PHY, txPower, advertise flags when present). Fieldwatch "
            "does not connect, pair, or read GATT characteristics for detection. "
            "Restarting the BLE scanner every few seconds is what produced 100+ "
            "start/stop cycles and a dead BLE list on Samsung — Fieldwatch does not do that."
        ),
        P(
            "This is BLE only. Fieldwatch never calls "
            "<font face='Courier'>BluetoothAdapter.startDiscovery()</font> (Classic BR/EDR inquiry). "
            "That scan transmits, typically pauses BLE on the same adapter, and is how HC-05 / HC-06 "
            "serial modules show up in Android Bluetooth settings. They will not appear on the Live display. "
            "A BLE advertiser may still <i>claim</i> dual-mode in its flags or Class of Device; that "
            "is self-description, not a Classic scan. §3.5."
        ),
        P("7.3 Identifiers the matcher uses", "h2"),
        table(
            ["Field", "Typical origin", "Notes"],
            [
                ["OUI / MAC prefix", "First 1–6 octets of the address", "OUI is 24-bit. Full MAC is more specific but fragile on randomized BLE."],
                ["Name / SSID", "Wi-Fi SSID or BLE AD local name", "Case-insensitive substring or glob (* and ?)."],
                ["Service UUID", "BLE AD type 0x02/0x03/0x06/0x07 or service data keys", "16-bit values are compared as both short and Bluetooth-base 128-bit forms."],
                ["Manufacturer ID", "BLE AD type 0xFF company identifier", "Apple 0x004C, Samsung 0x0075, Tile 0x00C7, XUNTONG 0x09C8, etc."],
                ["Manufacturer data prefix", "Bytes after the company ID", "AirTag Offline Finding uses 0x12 as the first payload byte."],
                ["Radio kind", "WIFI or BLE", "Do not OR this alone or the signature matches every radio of that type."],
                ["Hidden SSID", "Empty SSID on a Wi-Fi result", "Matches the class of hidden APs. Combine with a full BSSID (AND) to follow one radio."],
                ["Vendor IE OUI", "802.11 element 221 when the OS returns IEs", "LiteOn 00:80:19 / 00:0A:EB on Flock. Many phones strip IEs from scan results."],
                ["Co-occurrence", "min peers + window + cluster-by-OUI / sequential MAC", "Optional on a custom signature. Stock rows do not use clustering."],
            ],
            [1.7 * inch, 2.0 * inch, 2.8 * inch],
        ),
        Spacer(1, 6),
        P("7.4 How a named signature is applied", "h2"),
        numbered([
            "Each new or updated sighting is stored by key <font face='Courier'>KIND:MAC</font>.",
            "Every signature is evaluated. If match-any is on, one hitting rule is enough. If it is off, every enabled rule must hit.",
            "Signatures with min-peers, cluster-by-OUI, or sequential-MAC run a second pass over currently live devices inside the peer window (default 60 s).",
            "A device may match several signatures. Chips show up to three names; detail lists every match; the log joins all names with +. A store iBeacon UUID on top of stock iBeacon is the usual dual-label (§5.5, §9.2).",
        ]),
        P(
            "Re-matching also runs when you save a signature or toggle a rule, so a newly created signature "
            "labels devices already in memory without waiting for the next packet."
        ),
        P("7.5 In-memory set size and crowded rooms", "h2"),
        P(
            "Every phone in a plaza can rotate its BLE address, so “unique devices” grow without "
            "bound if every MAC is kept. Fieldwatch caps the live map at 400 radios, dropping the oldest "
            "unnamed devices first. Unnamed BLE older than about three minutes is "
            "evicted; named / signature-matched devices stay up to 15 minutes after last seen. "
            "In a dense environment you still see a full list, but it is the strongest and most "
            "recent 400, not every random MAC that walked by. Debrief and AI Export read this same "
            "map, not the log — on a drive the start of the trip can already be gone from RAM "
            "(§11.4.1). Logging also samples in a flood "
            "(new radios, or every 25th hit, at most 16 lines per batch) so the disk mutex "
            "does not freeze the UI. Radios still inside Brief hold are not dropped by the crowd cap."
        ),
        P("7.6 Per-rule enable", "h2"),
        P(
            "Each rule on a signature has its own on/off switch in the editor. Off keeps the rule "
            "in the list but it does not match. Custom and built-in signatures work the same way."
        ),
        P(
            "Typical field use: open <b>Flock Safety Cameras</b>, turn off the LiteOn / Espressif OUI "
            "rules, leave B4:1E:52 and the Flock-* name globs on. Match-any still means one enabled "
            "rule is enough. Penguin and Pigvision are name-only — if you switch every name rule off, "
            "they will not match until you turn one back on."
        ),
        P(
            "Stock Android cannot see wildcard probe requests from a hidden Flock STA, or "
            "frames addressed to a Flock MAC. See §7.6.1 for what a Flock/camera match is worth in the field."
        ),
        P("7.6.1 Real-world limits of Flock / ALPR / camera matching", "h3"),
        P(
            "A colored chip that says Flock Safety Cameras is a <b>pattern match on a public "
            "broadcast</b>, not a visual identification, not a serial number, and not proof that "
            "a Flock pole is at your GPS pin. Use it as a cue to look with your eyes, then "
            "write what you actually saw."
        ),
        P("<b>The phone’s radios are the limit, not just the signatures.</b> Fieldwatch is a "
            "stock-API listener on a phone. It cannot put Wi-Fi in promiscuous "
            "or 802.11 monitor mode, cannot capture raw frames or probe-only stations, cannot "
            "lock onto one channel, and cannot hear LTE/5G at all. If the camera is quiet on "
            "Wi-Fi and BLE, this phone will not invent a detection. A dedicated sniffer (external "
            "adapter / monitor-mode device) is a different collection system — Fieldwatch does not "
            "replace it.", "body_left"),
        P("<b>Cellular-first and quiet radios.</b> Current Flock-style and "
            "similar ALPR/camera poles often treat the carrier modem as the primary path. Wi-Fi "
            "is not a standing beacon for the neighborhood; it is a commissioning or fallback "
            "radio. After install it may be powered down, hidden, or up only when a tech is on "
            "site. BLE, if present at all, is usually a short-range maintenance advert, not a "
            "continuous billboard. Drive-by during the week of installation can light up "
            "Flock-* SSIDs; the same block six months later can be RF-silent on the bands this "
            "phone can see while the cameras are still up and recording. "
            "<b>No chip does not mean no camera. A quiet pole is the expected mature state.</b>", "body_left"),
        P("<b>The camera may not be on Wi-Fi at all.</b> LTE/5G backhaul with the Wi-Fi radio "
            "off is normal. Fieldwatch only sees access-point beacons. A camera that is only a "
            "modem, or only a client on a city SSID, does not appear.", "body_left"),
        P("<b>You never see the camera as a station.</b> Stock Android does not report associated "
            "clients, hidden-SSID stations, or probe-only devices. You cannot watch a Flock "
            "radio join a network. You only see it if that radio is <i>beaconing</i> an AP "
            "(including a hidden SSID AP, which shows as &lt;hidden&gt; plus BSSID — the name "
            "rules will not fire until the SSID is visible).", "body_left"),
        P("<b>Only one OUI is actually Flock’s.</b> IEEE MA-L <font face='Courier'>B4:1E:52</font> "
            "is registered to Flock Safety. Treat that, especially with a Flock-* SSID, as high "
            "confidence. The other ~28 prefixes in the catalog are LiteOn, Espressif, and similar "
            "module vendors. Those chips ship in printers, plugs, toys, and cameras that are not "
            "Flock. An OUI-only hit on 3C:71:BF (Espressif) is a weak hypothesis. Silicon Labs "
            "OUIs on FS Ext Battery are the same story: lots of unrelated IoT. "
            "A weak OUI hit still gets Extra attention “!”, a watchlist beep (stock bookmark), "
            "and a TAK Extra attention pin at your GPS if that feed is on — Extra attention and "
            "the bookmark apply to the whole signature, not only B4:1E:52.", "body_left"),
        P("<b>Names help, and they lie.</b> Flock-ABCDEF during provisioning is a strong name. "
            "A substring “Flock” on an unrelated SSID is weaker. Penguin / Pigvision are "
            "name-only and low uniqueness. Optional catalog rows (Verkada, Axis, Hikvision, …) "
            "are also mostly names, ship <b>off</b>, and will label any AP that chose that word. "
            "Do not write “Hikvision camera” in a log because an SSID contained Hikvision.", "body_left"),
        P("<b>Raven is a different radio.</b> Acoustic ShotSpotter / Raven sensors are not the "
            "ALPR camera. Stronger digital fingerprints are BLE UUID 0x3100–0x3500 and "
            "manufacturer 0x09C8 (XUNTONG), plus OUI D4:11:D6. The name “RAVEN” alone is easier "
            "to collide with.", "body_left"),
        P("<b>Vendor IEs are often missing.</b> LiteOn 00:80:19 / 00:0A:EB in element 221 is a "
            "nice corroboration when the OS includes IEs. Samsung scan results frequently omit "
            "them. A silent vendor-IE switch is normal, not proof the AP is clean.", "body_left"),
        P("<b>Geometry is not identity.</b> RSSI is not meters. Radar angle is a hash of the MAC, "
            "not a bearing. The GPS on a detection is the <i>phone’s</i> fix at hear-time. A loud "
            "Flock-* SSID means the AP is nearby enough to decode a beacon, not that the pole "
            "is at that coordinate. Walk, watch RSSI, and look up.", "body_left"),
        P("<b>Absence and presence are both easy to over-read.</b> Duty cycle, OS Wi-Fi throttle "
            "(~30 s batches), 5/6 GHz APs that drop while 2.4 GHz remains, hidden SSID, "
            "cellular-first / quiet radios, and a disabled signature all produce an empty list. "
            "Conversely, a LiteOn OUI plus a generic camera SSID in a retail park is usually "
            "someone else’s kit. Bookmark the signature if you want a beep; still confirm "
            "visually before you call it infrastructure. A sit that finds nothing on a known "
            "camera corridor is still a valid sit — it means this phone heard no matching "
            "<i>broadcast</i>, not that the corridor is empty.", "body_left"),
        callout(
            "How to talk about a match",
            "High: B4:1E:52 and/or SSID Flock-* / FLCK, or Raven UUID 0x3100–0x3500 / mfg 0x09C8. "
            "Medium: FS Ext Battery by name, or Flock name without the IEEE OUI. "
            "Low: LiteOn / Espressif / Silicon Labs OUI alone, Penguin/Pigvision name, optional "
            "catalog name-only cameras. Never: “this is a Flock camera” from RF without a "
            "visual or the IEEE OUI (or a clear Flock-* SSID). Pattern match ≠ plate, person, or serial.",
            "warn",
        ),
        P("7.7 Offline assigned-number databases", "h2"),
        P(
            "Vendor and UUID names are not fetched at runtime. Fieldwatch ships a packed binary "
            "(<font face='Courier'>assets/lookups/radiodb.bin</font>, binary-searched) built from "
            "the current official listings: IEEE MA-L / MA-M / MA-S / CID CSVs and the Bluetooth "
            "SIG Assigned Numbers YAML (company identifiers, GAP appearance, 16-bit service UUIDs). "
            "Class of Device is decoded from the Core Assigned Numbers bitfields. Rebuild with "
            "<font face='Courier'>python3 scripts/build_lookups.py</font>. Randomized MACs skip "
            "OUI lookup. Beacon interval is not an Android ScanResult field and is omitted when "
            "the stack does not provide it."
        ),
        P("7.8 Advertisement payload decode", "h2"),
        P(
            "After the standard AD header, manufacturer-specific bytes are the vendor’s private "
            "format. Fieldwatch decodes layouts that are public specs or well-documented field "
            "research, and shows those fields above the raw hex on detail:"
        ),
        bullets([
            "<b>iBeacon</b> — Apple type 0x02 / 0x15: UUID, major, minor, calibrated TX.",
            "<b>Google Fast Pair</b> — service 0xFE2C. Three-byte model ID = pairing mode (named from a local well-known list). Longer payloads = already-paired account-key bloom filter (show/hide UI).",
            "<b>Apple Continuity</b> — company 0x004C TLV stream: Proximity Pairing / AirPods (0x07), Find My (0x12), Nearby Info (0x10), Nearby Action (0x0F), AirDrop, Handoff, Hey Siri, AirPlay, Instant Hotspot.",
            "<b>Eddystone</b> — service 0xFEAA. UID, URL, TLM, and EID frames are kept separately on detail once heard (labeled on the raw line). They do not overwrite each other as the beacon rotates.",
            "<b>Microsoft</b> — company 0x0006 Nearby Sharing / Swift Pair device class when the beacon type is recognized.",
        ]),
        P(
            "There is no official catalog of arbitrary 0xFF payloads. Rebuild the IEEE/SIG name "
            "tables with <font face='Courier'>python3 scripts/build_lookups.py</font>. Fast Pair "
            "product names are a curated local list, not Google’s full partner catalog."
        ),
    ]

    # 8 Filters
    flow += [
        PageBreak(),
        P("8. Filtering System", "h1"),
        figure_wrap(
            "fig-filters-top.png",
            "Fig. 15 — Filters, presets and radios.",
            "Filters are how you stop staring at every radio in a plaza. They sit on the second "
            "tab, next to the Live display, because the usual loop is look at the list, then trim it. Nothing "
            "on this tab turns the radios off. Debrief, AI Export, and the on-disk log ignore "
            "the Live display filter <i>and</i> which view is open (radar / list / timeline / hybrid) — "
            "they still see the 15-minute memory / the file. If the list goes empty, undo the last "
            "switch before you assume the area is clean.",
        ),
        P(
            "Do not reach for Filters first when the list is merely <i>busy</i>. Live display → Display "
            "can hide the second line, bars, Frequency, and first/last without dropping a radio "
            "(§5.3, §12.11). Use this tab when you actually want fewer radios on the Live display."
        ),
        P("8.1 Architecture", "h2"),
        figure_wrap(
            "fig-filters-mid.png",
            "Fig. 16 — Filters, signature classes.",
            "Hearing radios and showing them are separate jobs. Fieldwatch always records what it "
            "hears; the filter only changes what the Live display (radar, list, timeline, hybrid, By class) shows. The log "
            "still writes every observation, including radios the current filter would hide, so "
            "you can thin the list without punching holes in the file.",
        ),
        P(
            "On the Filters tab, top to bottom: presets, radios to show, Moving with you, "
            "New detections only, Signatures only, Watched only, Named radios only, Hide Fast Pair account-key, signature classes (Show only / Hide these plus class chips), "
            "Show only selected signatures, Hide selected signatures (class A–Z lists open under those switches), "
            "RSSI / name / OUI, Extra filter logic (AND/OR). "
            "Reset filter clears every clause, including class picks, remembered show/hide picks, and "
            "New detections only (the already-seen set is cleared)."
        ),
        P("On the tab, in the same order as Figs. 15–16:", "body_left"),
        bullets([
            "<b>Radios to show</b> — Both, Wi-Fi only, or BLE only. A device is never both.",
            "<b>Moving with you</b> — GPS co-travel on <b>BLE only</b> (most GPS-stamped samples at about −75 dBm or stronger). Wi-Fi access points stay off: a loud AP you drive past paints hundreds of meters of your hear-time path and looks like it moved with you. Needs Tag detections with GPS (live updates, not a stale last-known) and ~45 m of path. A bag or car tag counts. “Still here” grows with your recent speed (§8.5). A second phone usually will not: BLE MAC rotation starts a new radio with an empty trail. The switch starts a BLE follow test (clears Signatures only / Show only / Named radios only / Watched only; Hide these stays). Always AND. Live display → Start over clears the path and trails, not the log. Debrief still writes the last-15-minute tracking section even if this is off.",
            "<b>New detections only</b> — hide radios already here. Always AND. Mark seen / Reset seen sit on the Live display (§5.3.2). First turn-on snapshots what is on the air. Randomized BLE addresses still look new. All traffic or Reset filter turns this off and clears already-seen.",
            "<b>Signatures only</b> — hide radios that match no signature. While class Show only or Show only selected signatures is narrowing the Live display, this is already true: the switch stays on and disabled until you turn those off.",
            "<b>Watched only</b> — hide radios that are not a bookmarked signature match and not a Named radio with Alert on. Always AND. Hide these still applies (Watched only + Hide Surveillance drops bookmarked cameras). Label-only names stay on Named radios only. The Live display shows a Watched only strip while this is on.",
            "<b>Named radios only</b> — hide radios that do not have a custom name (Settings → Named radios). Alert can still be off. Not the same as Signatures only or Watched only. A random / privacy MAC will not follow a rotation. The Live display shows a Named radios only strip while this is on.",
            "<b>Hide Fast Pair account-key</b> — drop plaza Fast Pair chips that are already paired, when Fast Pair is the only signature on the row. Pairing-mode stays (chip Fast Pair pairing, subtitle pair). A Pixel that also matched Google still shows. This is not Hide selected Fast Pair, which removes the whole family including pairing-mode. Always AND. What the two payloads mean: §9.5.",
            "<b>Signature classes</b> — Live display only; signatures still label. <b>Show only</b> keeps radios of the class chips you pick. <b>Hide these</b> drops those classes and leaves the rest, including unmatched radios. Show only with no class picked leaves the Live display unchanged. Class chips sit two across with the same glyphs as the Live display. Cameras / Drones / Finder tags / Phones / PCs and the rest are these chips — Show only or Hide these, then Save current as… if you want a named preset. Class vs color: §9.5.",
            "<b>Show only selected signatures</b> — only radios matching the families you pick stay on the Live display. The list opens under the switch, grouped Class A–Z (tap a class to open its signatures, same outline as the Signatures tab). Empty list = no extra include. Separate picks from Hide selected.",
            "<b>Hide selected signatures</b> — hide one family (your bag AirTag) without hiding the whole Finder tags class. The list opens under the switch, grouped Class A–Z (tap a class to open). Empty list = hide nothing. Picks are remembered if you turn hide off and on again.",
            "<b>RSSI / name / OUI</b> — last RSSI ≥ slider (default −100 dBm = off); substring of name or MAC; substring of MAC or vendor string.",
        ]),
        P(
            "Class Show only is how you get “just trackers” or “just cameras” on the Live display. "
            "Hide these (or Hide selected) only takes a family off the Live display — it still labels, "
            "still logs, still appears in Debrief, and can still beep if bookmarked."
        ),
        P("8.2 AND vs OR", "h2"),
        P(
            "<b>AND</b> (default): every active clause must pass. Use this for a focused sit "
            "(“BLE + signatures only + RSSI ≥ −70”)."
        ),
        P(
            "<b>OR</b>: radios, Signatures only, Named radios only, Watched only, class Hide these, Hide selected, Moving with you, and New detections only still apply. Among the optional "
            "clauses (name query, OUI query, RSSI floor if raised above −100, class Show only), any one pass is "
            "enough. Use this when you want a broad search (“name contains Flock OR OUI contains B41E52”). "
            "AND/OR never overrides a hidden class or signature — if Hide these Finder tags is on, "
            "AirTags stay off the list even in OR mode."
        ),
        P("8.3 Signatures only, classes, and selected families", "h2"),
        figure_wrap(
            "fig-filters-selected.png",
            "Fig. 17 — Selected signatures.",
            "<b>Signatures only</b> hides radios that match no signature, so the Live display is pattern hits "
            "of every class. If the list goes empty, nothing matching is in earshot — signatures "
            "always label; Filters only hide radios from the Live display. Class <b>Show only</b> or Show only "
            "selected signatures already hides unmatched radios, so this switch stays on and "
            "disabled until you turn those off. <b>Hide these</b> does not hide unmatched — "
            "Signatures only stays a real switch then (named hits minus the hidden classes, or "
            "the whole field minus those classes).",
        ),
        P(
            "<b>Signature classes</b> group stock (and custom) rows into Finder tags, Retail beacons, "
            "Signage, Wearables, Surveillance, Drones, Pentest, Public safety, Vehicle, Glasses, Audio, Cameras, Thermostats, "
            "Access control, Health, Home IoT, ISP / routers, Mesh, Phones / PCs, and Other. Each signature has one "
            "class, set in the editor. Public safety is Axon / WatchGuard Video (body-worn and in-car) "
            "plus public-safety vehicle APs (Cradlepoint, AirLink, Compex, Novatel Wireless, Utility Inc). "
            "Many of those radios are used in law enforcement; they are not exclusive to it. "
            "Government, municipal, and other corporate fleets likely run some of the same kit. "
            "Filters → <b>Show only</b> plus those chips keeps matching "
            "radios of the picked classes. <b>Hide these</b> drops the picked classes. Neither "
            "button turns labeling off — a hidden class still labels, still logs, still appears "
            "in Debrief, and can still beep if bookmarked. Class sits are not stock chips: "
            "Show only or Hide these, then Save current as… if you want that sit as a named preset. "
            "See §9.5 for which stock rows sit in which class."
        ),
        P(
            "<b>Hide selected signatures</b> removes the individual families you pick and leaves "
            "everything else, including unmatched radios. Use it when you want one row gone "
            "(your bag AirTag) without hiding Tile and Chipolo. Turn the switch on to see the "
            "indented list, then turn a signature on to hide it. Turning Hide selected off "
            "collapses that list and stops hiding, but the same signatures stay picked. Reset "
            "filter is what forgets the picks. Class Hide these and Hide selected can be on together."
        ),
        P(
            "Signatures only plus Hide these Finder tags is named hits minus bag-tag clutter. "
            "Show only Surveillance is the camera/ALPR sit. They are not locked against each other. "
            "<b>Watched only</b> is a narrower Live include: bookmarked signature matches plus Named radios with Alert on. "
            "Signatures only still shows every labeled family, including ones you did not bookmark. Hide these still applies."
        ),
        P("8.4 Presets", "h2"),
        P(
            "The top of Filters is two-across chips. A tap <b>replaces the whole filter</b> "
            "(radios, Moving with you, New detections only, Signatures only, Watched only, Named radios only, class Show only / Hide these and the class chips, Show only selected and Hide selected "
            "and their picks, RSSI floor, name/MAC and OUI queries, AND/OR). "
            "It does not change Display, GPS tagging, or the log. "
            "Stock chips are ordinary saved filters built from signature class, "
            "not a second matching engine."
        ),
        P(
            "<b>Save current as…</b> (name field + Save) appends a new chip with a snapshot of "
            "whatever is on this tab right now, including class picks, hide picks, and New detections only. "
            "It does not overwrite a built-in chip. Tap a chip to apply it (the active chip is highlighted). "
            "Long-press any chip and confirm Delete. That removes the chip from this list, "
            "not the filter currently on the Live display. Stock chips you remove stay gone across catalog "
            "updates; Settings → Restore default signatures &amp; presets puts the short stock "
            "set back (and still rewrites the catalog). Reset filter still clears this tab "
            "even if you deleted All traffic. "
            "<b>Reset filter</b> at the bottom clears this tab (All traffic, New detections off, "
            "already-seen cleared). It is not “undo the last preset.” Applying All traffic or "
            "Reset filter also turns New detections off and clears already-seen."
        ),
        P(
            "A class Show only only <i>shows</i> radios that already matched. If Finder tags are "
            "hidden on Filters, a Finder-tags sit looks empty — turn Hide these off, or tap the class chip again. "
            "Cameras / Drones / Surveillance / Finder tags and the other class sits are not stock chips: pick "
            "Show only plus the class chip, then Save current as… to keep that sit."
        ),
        table(
            ["Preset", "What it sets", "Notes"],
            [
                ["All traffic", "Both radios, no RSSI floor, no queries, Signatures only / Watched only / Named radios only off, class filter off, Hide selected off, Hide Fast Pair account-key off, Moving with you off, New detections off.", "Show everything. First look at a block. Applying it also clears already-seen."],
                ["Wi-Fi only", "BLE off.", "APs, hotspots, mesh, soft-AP. No LE rows."],
                ["BLE only", "Wi-Fi off.", "Advertisers only."],
                ["Strong signal", "RSSI floor −70 dBm. Both radios.", "Drops the quiet crowd. Not a Hunt. Pair with Display → Subtitle None if the list is still busy."],
                ["Moving with you", "Moving with you on. BLE follow test (Wi-Fi APs excluded). No Signatures only, no Watched only, no Named radios only, no class Show only.", "Still needs Settings → Tag detections with GPS and ~50 m of path. Bag/car tag yes. Access points stay off — range looks like co-travel. “Still here” grows with speed (§8.5). The switch starts a BLE follow test (it clears Show only, Signatures only, Named radios only, and Watched only). Debrief tracking is separate (§12.2)."],
                ["Watched only", "Watched only on. Both radios. No class filter, no RSSI floor.", "Bookmarked signature matches and Named radios with Alert on. Hide these still applies. Label-only names stay on Named radios only."],
            ],
            [1.35 * inch, 2.55 * inch, 2.6 * inch],
        ),
        Spacer(1, 4),
        P(
            "Stock presets leave New detections only off. After you save your own chip, tap it "
            "to get that sit back in one hit (for example “plaza −80 + Hide these Finder tags”). Display "
            "Sort / Title / Subtitle are not stored in a preset — those live on the Live display → Display.",
            "body_left",
        ),
        P("8.5 How Moving with you works", "h2"),
        P(
            "Moving with you is Fieldwatch’s answer to a simple field question: which loud radios "
            "are staying with <i>me</i> as I walk or drive, rather than radios I only meet when "
            "I arrive? It is a Live display filter, not a Hunt and not a direction finder. GPS is always "
            "your phone at hear-time. RSSI is loudness here, not meters. Pattern chips on those "
            "rows are still not identity."
        ),
        P(
            "Wi-Fi access points are excluded. GPS is the phone at hear-time, not the other radio. "
            "A loud AP you drive past is still in earshot for hundreds of meters, so its trail "
            "is your path and looks like co-travel. Moving with you is therefore BLE only: a tag "
            "in the bag or a speaker in the car is heard again and again while the phone’s GPS "
            "path lengthens. Passing phones on an interstate are loud for a few seconds and then "
            "gone. Those should flash through and leave. A BLE radio that is actually with you should stay."
        ),
        P("8.5.1 What has to be true before anyone qualifies", "h3"),
        P(
            "Tag detections with GPS must be on, Location must be high accuracy, and scanning "
            "must be running so the phone is giving live fixes. A last-known location older than "
            "30 seconds is ignored. The operator path has to grow to about 45 m before the test "
            "is even willing to run — sitting still does not count. Filters shows the path meter; "
            "the Live display shows Follow · path N m while the switch is on."
        ),
        P(
            "Each radio then has to pass every gate below. Fail any one and it stays off this "
            "filtered list (it is still in the log, and Debrief can still discuss it)."
        ),
        bullets([
            "<b>Heard recently.</b> Last packet in the last 90 seconds.",
            "<b>Loud enough right now.</b> Last RSSI about −75 dBm or stronger. A quiet dip below that drops the row until it is loud again.",
            "<b>A trail, not a single ping.</b> At least two GPS stamps on that radio. Stamps are only added when the phone has moved about 8 m (or 18 m within 30 s), so a stationary crowd does not pad the trail.",
            "<b>The trail itself moved.</b> The radio’s GPS path, and the box around those stamps, must each cover at least about 27 m (60 percent of the 45 m operator move). A radio heard only at the door has a tiny trail and fails here even if it is screaming loud.",
            "<b>Loud along the trail, not just at the end.</b> At least two-thirds of those GPS stamps must also be about −75 dBm or stronger. A radio that was quiet for most of the drive and only woke up at the house does not qualify.",
            "<b>Still here, given how fast you are going.</b> The last time Fieldwatch heard that radio, the phone’s GPS was somewhere. That stamp is compared with where the phone is now. How far you are allowed to have gone in between is the speed-aware part (§8.5.2).",
        ]),
        P(
            "A second phone in the car usually fails the trail gates, not the speed gate. iOS "
            "and many Android stacks rotate the BLE address, so Fieldwatch sees a brand-new radio "
            "with an empty trail. Use a tag with a stable MAC (AirTag, Tile, SmartTag in the bag) "
            "as the confidence check that the filter is working."
        ),
        P("8.5.2 Why the “still here” window grows with speed", "h3"),
        P(
            "The last GPS stamp is not the other radio’s location. It is your phone, at the "
            "moment Fieldwatch heard that radio. If the radio is quiet for a few seconds, the phone "
            "keeps moving. On a sidewalk those few seconds are a house-length. On an interstate "
            "they are hundreds of meters. If “still here” were a fixed 50 m circle around the "
            "phone, those two cases would look identical, and a tag in the cup holder would blink "
            "off between advertisements even though it never left the car."
        ),
        P(
            "Moving with you therefore asks a different question as you speed up: not “is this "
            "radio 50 meters from me right now?” but “have I traveled farther, since we last "
            "heard this radio, than this radio could reasonably have gone silent?” Recent speed "
            "is path length divided by the time on that path — an average over the follow, not "
            "a single GPS velocity. It is clamped between 0 and 40 m/s (about 0–90 mph) so a "
            "jumpy fix cannot grant kilometers of slack."
        ),
        P(
            "A BLE tag in the bag usually speaks every few seconds. The hold time is 15 seconds, "
            "then multiplied by your recent speed. A 50 m floor still wins when you are slow, so a "
            "sidewalk tag is not given a city-block of slack. That floor hands off around a fast "
            "walk or slow bicycle (about 7.5 mph). On top of that product, 25 m of GPS slack is "
            "added at every speed — enough to cover ordinary fix jitter without pretending RSSI "
            "is a tape measure."
        ),
        P(
            "In short: <b>allowed distance = the larger of 50 m or (speed × 15 s), plus 25 m</b>. "
            "Wi-Fi access points never get this window — they are excluded before the gates."
        ),
        table(
            ["Your recent speed", "BLE still-here", "What that feels like"],
            [
                ["Walking (~3 mph)", "about 75 m", "A house-length. The 50 m floor is still doing the work."],
                ["Fast walk / bicycle (~7.5 mph)", "about 75 m", "Just leaving the floor."],
                ["Neighborhood (~15 mph)", "about 125 m", "A tag that skipped a couple of advertisements still counts as with you."],
                ["City street (~30 mph)", "about 225 m", "A few seconds of silence is a couple of blocks, not a miss."],
                ["Interstate (~55 mph)", "about 400 m", "Two seconds of driving is already ~50 m. The hold is now a handful of advertisements."],
                ["Fast highway (~70 mph)", "about 500 m", "A car tag should stay. Roadside BLE still fails the trail-moved gates."],
            ],
            [1.8 * inch, 1.5 * inch, 3.2 * inch],
        ),
        Spacer(1, 6),
        P(
            "Those distances are how far the <i>phone</i> is allowed to have traveled since the "
            "radio’s last GPS stamp. They are not a detection radius around the other device, "
            "and they do not mean Fieldwatch thinks a tag is 400 m away. A bag tag that is still "
            "advertising will keep getting fresh stamps, so it stays near “here” in GPS terms "
            "even while the allowed window is large."
        ),
        P("8.5.3 What still drops, even on the highway", "h3"),
        P(
            "A large still-here window does not keep passing radios on the list. The trail-moved "
            "and two-thirds-loud gates are unchanged at every speed. A phone in the next lane is "
            "loud for a few seconds. It rarely collects two GPS stamps that already span ~27 m "
            "of <i>your</i> path, and its trail goes quiet as soon as you pull away. Those rows "
            "still flash up and leave. That is the filter working. A tag in the car has been "
            "stamped all the way down the interstate, so it survives the same gates."
        ),
        P(
            "Wi-Fi access points never qualify. If an AP still shows under this filter, that is a bug. "
            "House BLE you meet at the door fails the trail-moved gates. If a bag tag does not show, "
            "the path was too short, GPS only started stamping at the destination, or you have not "
            "walked/driven the ~45 m the test needs. Live display → Start over, then move again."
        ),
        P(
            "Start over (on the Live display, above the tabs) clears the operator path and every radio’s GPS "
            "trail so you can run the test again. It does not wipe the live list or the log. "
            "Turning Moving with you off does not clear the path. Reports → Debrief still writes "
            "the last-15-minute co-travel callouts whether this filter is on or off — that sit "
            "report is §11.4 and §12.2, not this switch."
        ),
        callout(
            "This is still a heuristic",
            "A row that stays is a loud radio whose GPS trail followed your phone. It is not "
            "proof that someone is following you, and a miss is not a clean bill. Quiet tags, "
            "rotating Find My addresses, and radios the OS did not deliver will not appear. "
            "If safety is in question, leave and get help — do not wait on this list.",
            "warn",
        ),
        P("8.6 Field recipes", "h2"),
        P(
            "One-line filter setups. Chapter 12 walks each playbook: setup, what you should see, "
            "and what you must not claim from the picture."
        ),
        table(
            ["Situation", "Suggested filter"],
            [
                ["Quick sweep of a block", "All traffic, High performance, Strength list or Hybrid. Display defaults. Do not use Signatures only yet — you need the noise to know the neighborhood."],
                ["Long sit / vehicle", "Balanced or Saver. Timeline. If the list is busy, Display → Subtitle None before you raise the RSSI floor. Leave logging on."],
                ["Focused family", "Filters → Show only plus the class chips (Public safety, Cameras, Drones, Surveillance, Finder tags, …). Matching stays on. Save current as… if you want that sit as a chip."],
                ["Named hits minus clutter", "Signatures only + Hide these Finder tags (or Hide selected on one family, e.g. your bag AirTag)."],
                ["Drop trackers, keep everything else", "Filters → Hide these, then Finder tags."],
                ["Tracker hunt", "Filters → Show only, Finder tags, BLE only. Watch the signatures. Battery saver is usually enough; tags advertise slowly."],
                ["SSID / OUI lead", "Name query or OUI query, AND, both radios on. Do not enable Signatures only or you will hide an unmatched but relevant AP."],
                ["Who is walking with me", "Settings → Tag detections with GPS (high-accuracy Location). Walk ~50 m until the path is not 0. Filters → Moving with you (preset or the switch — apply the preset, or the switch by itself; do not stack a class Show only on top). A tag in your bag/car will match. A second iPhone usually will not (MAC rotation). Walking uses a tight still-here window; a drive uses a larger one (§8.5). After the walk: Reports → Debrief — last 15 minutes of tracking, even if you leave this filter or change view."],
                ["What just arrived", "Filters → New detections only. On the Live display, Mark seen once the room is known (buttons sit above the tabs). Reset seen starts over. Hint: New only · N hidden."],
            ],
            [1.8 * inch, 4.7 * inch],
        ),
    ]

    # 9 Signatures
    flow += [
        PageBreak(),
        P("9. Named Signatures &amp; Creating Custom Signatures", "h1"),
        P("9.1 What a signature is", "h2"),
        figure_wrap(
            "fig-signatures.png",
            "Fig. 2 (repeated) — Signatures.",
            "A signature is a named bundle of match rules, a color, and optional cluster flags. "
            "It is not proof of vendor identity. Built-in only marks where a row came from; you "
            "can still edit or delete it. Each rule has its own on/off switch, so you can mute a "
            "noisy OUI or name without deleting the rule. Edit rules here, not on Settings. "
            "When the rules hit, the radio is always labeled. Display can hide the chip on the Live display; "
            "Filters hide the radio. The catalog list is Name A–Z by default. Class A–Z is an "
            "outline of classes (collapsed until you tap one) so you can open Public safety "
            "or Vehicle without scanning the alphabet.",
            P("9.2 Create from an observed device", "h2"),
            numbered([
                "Find the device in the Live display (any view) and tap it for detail.",
                "Tap Create signature from device on the detail screen.",
                "Fieldwatch suggests a name from the advertised name, vendor+OUI, or “BLE/Wi-Fi + last octets.”",
                "Pre-filled rules: full MAC prefix (this radio); name or glob if a name exists; up to three service UUIDs; manufacturer ID and first data byte when present. OUI is added only if nothing else except MAC was available. A “radio = BLE/Wi-Fi” OR rule is deliberately not added — that would label every radio of that type.",
                "If the address looks randomized, the notes field warns you. Prefer name / UUID / manufacturer rules and consider deleting the MAC rule.",
                "Edit, set match-any, color, min-peers if needed, then Save.",
                "If you already know the BLE payload layout, open the new row → Decode fields and map the bytes (§9.6). Identity (the rules) and decode (the field map) are separate saves.",
            ]),
        ),
        P(
            "Read <b>Signature family</b> on that radio’s detail page first (§5.5). "
            "Strong family or Possible family means other MACs already share the ID — "
            "Create from device will still pin <b>this</b> MAC. Use Signature candidates "
            "(§9.2.1) when you want the shared rule instead. This radio only is when a MAC pin "
            "is the honest unmatched draft. <b>Already tagged is not a veto.</b> If the decode "
            "shows a store-wide iBeacon UUID (or another ID tighter than the stock row), keep "
            "Create from device, delete the MAC pin, and lengthen the manufacturer-data prefix "
            "to type 0x02 / length 0x15 plus that UUID (hex <font face='Courier'>0215</font> + "
            "32 UUID digits) — the same shape as Target Atrius basket. The stock iBeacon row "
            "stays; your row is the second chip. The draft’s first manufacturer byte alone "
            "(<font face='Courier'>02</font>) would match every iBeacon, not this store."
        ),
        P("9.2.1 Create from Signature candidates", "h3"),
        P(
            "When several unmatched radios share one ID (the same <font face='Courier'>H2O-</font> glob, "
            "the same vendor IE, the same stable OUI), do not pin one MAC. "
            "Detail’s Signature family card is the one-radio version of that test. "
            "Reports → Signature candidates (§5.6.4, §11.5) drafts the <b>shared</b> rule and "
            "omits the MAC pin. Save still lands in your catalog as a custom row — same editor, "
            "same Save. That is how you add a family without cataloging a house AP’s BSSID. "
            "It only mines <b>unmatched</b> radios. A floor of stock iBeacons will not list "
            "here; for a store UUID on radios that already chip iBeacon, use §9.2."
        ),
        P("9.3 Manual create and edit", "h2"),
        P(
            "The Signatures title is the catalog total (stock plus any you added). "
            "Tap + to create a blank signature. In the editor you set name, notes, Extra "
            "attention, AND vs match-any (OR), cluster flags, color, and the rule list. "
            "Each rule has an on/off switch: off keeps it, but it does not match. "
            "BLE signatures also have <b>Decode fields</b> (None or N fields) under the rules — §9.6. "
            "Wi-Fi-only rows hide that control."
        ),
        P(
            "Rule kinds: OUI, MAC prefix, name contains, name glob, service UUID, "
            "manufacturer ID, manufacturer data, radio kind, hidden SSID, vendor IE OUI. "
            "Enter manufacturer IDs as hex (for example 0x004C)."
        ),
        P(
            "On a Wi-Fi row the MAC <b>is</b> the BSSID. A MAC-prefix rule with the full "
            "twelve hex digits is that one access point. Fewer digits (B4:1E:52) is the "
            "vendor block — same matcher as an OUI rule. Hidden SSID matches any AP whose "
            "scan result has an empty name; pair it with a MAC prefix (AND) to pin one "
            "hidden box. Stock Android never reveals the secret SSID string."
        ),
        P(
            "There is no matching on/off switch. Signatures always label when their rules hit. "
            "Hide a family on Filters (class Hide these, or Hide selected signatures). "
            "Display → signature names hides the chip on the Live display without dropping the radio "
            "(the decode hexagon goes with it). "
            "The bookmark requests an alert when that signature hits (and a silent shade card if "
            "that Settings switch is on)."
        ),
        P(
            "Open a signature and tap Delete signature at the bottom to remove it (confirm first). "
            "Built-in rows can be deleted; Restore default signatures brings the stock set back. "
            "Settings → Export signatures writes the whole catalog to a JSON pack you can share "
            "or keep as a backup. Import signatures adds new rows and extra rules; it does not "
            "delete anything. Same id or the same match rules are skipped. A colliding name is "
            "imported as “Name (imported)”. Update stock catalog from GitHub replaces stock rows "
            "(including Extra attention) from the repo; bookmarks and Settings stay. Needs internet. "
            "Restore defaults still wipes customs — export "
            "signatures (and Export settings for named radios / switches) first if you want them back."
        ),
        P("9.4 Practices that hold up in the field", "h2"),
        bullets([
            "Start tight (full MAC or manufacturer data + company ID), then loosen if the target rotates addresses.",
            "Do not OR a lone RADIO_KIND rule.",
            "Do not treat a Silicon Labs or LiteOn OUI as unique. Pair it with a name, UUID, or require min-peers.",
            "Prefer 16-bit UUIDs in the 0xFDxx / vendor range over common GAP UUIDs.",
            "For a family, use match-any and several OUIs plus a name glob. For one radio, keep the MAC rule and turn match-any off if you also add other clauses.",
            "A generic DIRECT- / ANDROID- / ESP_ name is unmatched unless a product family also hits (Raven, Roku, Epson).",
            "Mute a noisy method on one signature with the rule switch (e.g. LiteOn OUIs on Flock) instead of deleting the rule.",
            "After editing, glance at the Live display. If half the café just inherited your new name, the rule is too broad — undo immediately.",
        ]),
        P("9.5 Using the pre-loaded catalog", "h2"),
        P(
            "On first launch, Fieldwatch loads the stock signature catalog. "
            "Restore default signatures &amp; presets loads it again. "
            "Every stock row is on: when its rules hit, the radio is labeled."
        ),
        P(
            "To take a noisy family off the Live display, use Filters — Hide these for a whole class, "
            "or Hide selected for one signature. Matching still runs. The log, Debrief, "
            "and bookmarks are unchanged."
        ),
        P(
            "Each signature has a <b>class</b> (Finder tags, Surveillance, Drones, Vehicle, "
            "and so on). Filters → Show only / Hide these use class. Chip color on the Live display is separate; you can "
            "change it in the editor. Custom signatures default to class Other."
        ),
        P(
            "The Signatures tab and the Hide selected / Show only selected lists are sorted "
            "A–Z, including custom rows."
        ),
        P(
            "Stock chip colors follow class, so Live display and radar mean something before you open "
            "a row. A named match uses that signature’s color. Unmatched radios use the RSSI "
            "palette: ≥−55 green, −55 to −70 amber, −70 to −85 orange, else red. An upgrade "
            "re-applies stock colors to built-in rows once. Custom signatures are left alone."
        ),
        table(
            ["Filter class", "Stock signatures (abbreviated)"],
            [
                ["Finder tags", "Apple AirTags, Samsung SmartTags, Tile, Chipolo, Pebblebee / moto tag"],
                ["Retail beacons", "iBeacon, Target Atrius basket, Minew, Estimote, Kontakt.io"],
                ["Signage", "Retail LED sign, Electronic shelf label"],
                ["Wearables", "Garmin, Fitbit, Oura, Pokemon GO Plus, Fieldy, Plaud Note"],
                ["Surveillance", "Flock, Raven, Penguin, Pigvision, FS Ext Battery, Genetec, Rekor, Vigilant, Verkada, Avigilon, Axis, Hikvision, Dahua, Hanwha Wisenet, Uniview, Rhombus, UniFi Protect, BlueTOAD Spectra, BlipTrack"],
                ["Drones", "Remote ID, DJI, Skydio, Autel, Parrot, HOVERAir"],
                ["Pentest", "Hak5 Pineapple, Flipper Zero, Pwnagotchi, Marauder / Deauther, GhostESP, Bruce, Porkchop, Hobby BLE serial"],
                ["Public safety", "Axon, WatchGuard Video, Cradlepoint, AirLink, Compex, Novatel Wireless, Utility Inc. Used in law enforcement, not exclusive to it — government, municipal, and other corporate fleets likely run some of the same kit."],
                ["Vehicle", "Tesla, Tesla tsTPMS, Rivian, Ford, Honda, Hyundai, Toyota, Nissan, Subaru, BMW, Volkswagen, Porsche, Jaguar Land Rover, BYD, Chevrolet hotspot, GM hotspot, Audi MMI, Mercedes MBUX, Uconnect, CarPlay, CARLINK, Motive, PeopleNet, Samsara, AUMOVIO, Winegard, Goodyear TPMS, Schrader TPMS, Pacific TPMS, Huf, FOBO TPMS, Aftermarket TPMS, SYTPMS, TireCheck, TPMS service"],
                ["Glasses", "Ray-Ban / Meta glasses, Snap Spectacles"],
                ["Audio", "Apple audio, Sony, Bose, JBL / Harman, Sonos, Shokz"],
                ["Cameras", "Wyze, Ring, Arlo, eufy Security, Nest, Tapo, Reolink, GoPro, Osmo, Insta360"],
                ["Thermostats", "Nest Thermostat, ecobee, Sensi, Honeywell Home"],
                ["Access control", "August, Schlage, Nuki, Lockly, Kevo, Master Lock, igloohome, Tedee, Kwikset, ASSA ABLOY, SALTO, dormakaba, Paxton"],
                ["Health", "Honeywell Xenon HC, Omron, Withings, Dexcom"],
                ["Home IoT", "Nest Weave, Tuya, Govee, Haiku Fan, myQ, Hatch, Orbit B-hyve, Samsung appliance, EcoWater, Amazon, Logitech, HP, Epson, LG webOS TV, Roku, Nespresso, RadiaCode, Ruuvi, Blue Maestro, SensorPush, SnapAV"],
                ["ISP / routers", "UniFi, UniFi AP, Meraki, Cisco, Aruba, Ruckus, Ruijie, Fortinet, Mist, Sophos, Extreme, Edgecore, WatchGuard AP, Mojo, NETGEAR, TP-Link, ASUS, Linksys, Eero, Google Wifi, Huawei, Plume, D-Link, DWnet, Belkin, Xfinity, Spectrum, AT&amp;T, Verizon, Starlink, GL.iNet, MikroTik, EnGenius, Zyxel, Peplink, OpenWrt, Arris, T-Mobile, HUMAX, Sagemcom, Arcadyan, Askey, Calix, Nokia, AirTies, Tenda, WAVLINK, Sercomm, Luxul, CenturyLink, Adtran, Cambium, TRENDnet, Cudy, Vantiva, Hitron, Actiontec, Buffalo, Grandstream, Inseego, Franklin, Synology"],
                ["Mesh", "Meshtastic, MeshCore, Helium, goTenna, SenseCAP, RAK WisGate"],
                ["Phones / PCs", "Apple Device, Fast Pair, Google, Microsoft Device, Phone hotspot"],
                ["Other", "—"],
            ],
            [1.7 * inch, 4.8 * inch],
        ),
        Spacer(1, 6),
        table(
            ["Color", "Class", "Stock signatures"],
            [
                ["Red", "Pentest / cheap serial", "Hak5 Pineapple, Flipper Zero, Pwnagotchi, Marauder / Deauther, GhostESP, Bruce, Porkchop, Hobby BLE serial"],
                ["Amber", "Surveillance and drones (same chip color; class splits them)", "Flock, Raven, Penguin, Pigvision, FS Ext Battery, Genetec, Rekor, Vigilant, Verkada, Avigilon, Axis, Hikvision, Dahua, Hanwha Wisenet, Uniview, Rhombus, UniFi Protect, BlueTOAD Spectra, BlipTrack, Remote ID, DJI, Skydio, Autel, Parrot, HOVERAir"],
                ["Purple", "Phones / Find My tags", "Apple Device, Apple AirTags, Chipolo, Fast Pair, Google (Pixel / 0x00E0), Phone hotspot"],
                ["Cyan", "Wearable trackers", "Samsung SmartTags, Tile, Pebblebee / moto tag, Garmin, Fitbit, Oura, Pokemon GO Plus, Fieldy, Plaud Note, iBeacon, Target Atrius basket, Minew, Estimote, Kontakt.io"],
                ["Green", "Mesh / LoRa", "Meshtastic, MeshCore, Helium, goTenna, SenseCAP, RAK WisGate"],
                ["Orange", "Glasses and audio (same chip color; class splits them)", "Ray-Ban / Meta glasses, Snap Spectacles, Apple audio, Sony, Bose, JBL / Harman, Sonos, Shokz"],
                ["Teal", "Public safety and vehicle (same chip color; class splits them)", "Axon, WatchGuard Video, Cradlepoint, AirLink, Compex, Novatel Wireless, Utility Inc, Tesla, Tesla tsTPMS, Rivian, Ford, Honda, Hyundai, Toyota, Nissan, Subaru, BMW, Volkswagen, Porsche, Jaguar Land Rover, BYD, Chevrolet hotspot, Mercedes MBUX, Uconnect, CarPlay, CARLINK, Motive, Samsara, Winegard, Goodyear / Schrader / Pacific / Huf / FOBO / Aftermarket / SYTPMS / TireCheck / TPMS service"],
                ["Blue", "Health", "Honeywell Xenon HC, Omron, Withings, Dexcom"],
                ["Silver", "Cameras / PCs / home IoT / home Wi-Fi / retail signage / access control", "GoPro, Osmo, Insta360, eufy, Wyze, Ring, Arlo, Nest, Tapo, Reolink, Microsoft Device, Amazon, Starlink, Logitech, HP, Epson, LG webOS TV, Nespresso, RadiaCode, Nest Thermostat, Nest Weave, ecobee, Sensi, Honeywell Home, Tuya, Govee, Haiku Fan, myQ, Hatch, Orbit B-hyve, August, Schlage, Nuki, Lockly, Kevo, Master Lock, igloohome, Tedee, Kwikset, ASSA ABLOY, SALTO, dormakaba, Paxton, Ruuvi, Blue Maestro, SensorPush, SnapAV, Retail LED sign, Electronic shelf label, UniFi, UniFi AP, Meraki, Cisco, Aruba, Ruckus, Fortinet, Mist, Sophos, Extreme, Edgecore, WatchGuard AP, Mojo, NETGEAR/TP-Link/ASUS/Linksys/Eero/Google Wifi/D-Link/Belkin/Xfinity/Spectrum/AT&amp;T/Verizon/GL.iNet/MikroTik/EnGenius/Zyxel/Peplink/OpenWrt/Arris"],
            ],
            [0.95 * inch, 1.7 * inch, 3.85 * inch],
        ),
        Spacer(1, 6),
        P(
            "<b>Notes</b> on a signature show on radio detail (quiet card) and in Share / AI Export "
            "for matching radios — what that family is and how it is typically used. "
            "Stock copy is product context, not company IDs or UUIDs (those stay on Identity / Maker data). "
            "They do not put a “!” on Live "
            "and they are not the amber Extra attention card. "
            "<b>Extra attention</b> is a separate field. It is empty on almost every row. "
            "If Extra attention is filled, a matching radio gets a “!” on the list, the amber card "
            "on detail, and a line in Debrief / AI Export."
        ),
        P(
            "The stock catalog fills Extra attention on Hobby BLE serial, Axon, WatchGuard Video, Digital Ally, "
            "Reveal Media, Wolfcom, Ray-Ban / Meta glasses, Snap Spectacles, Brilliant Frame, Even G1, "
            "Fieldy, Plaud Note, Limitless Pendant, Bee Pendant, Omi, Friend Pendant, Hak5 Pineapple, Flipper Zero, "
            "Pwnagotchi, Marauder / Deauther, GhostESP, Bruce, Porkchop, Cradlepoint, AirLink, Compex, Novatel Wireless, "
            "Utility Inc, and the roadside / public camera + ALPR rows: Flock Safety Cameras, Penguin, "
            "Pigvision, FS Ext Battery, Genetec AutoVu, Rekor, Motorola Vigilant, Verkada, Avigilon, "
            "Axis, Hikvision, Dahua, Hanwha Wisenet, Uniview, Rhombus, Panasonic i-PRO, Hayden AI, Miovision, Tattile, LVT LiveView. Those rows ship with the bookmark on, so a new match beeps. "
            "That is body-cam, Meta / Snap glasses, recording wearables, pentest kit, "
            "public-safety vehicle APs, and public cameras / plate readers — not Tesla, not headphones, "
            "not UniFi Protect, not BlueTOAD Spectra, not BlipTrack, not access-control locks. Flock LiteOn / Espressif OUIs can be noisy; unbookmark that row if it is. The Public safety rows "
            "are used in law enforcement; they are not exclusive to it (government, municipal, and other "
            "corporate fleets likely run some of the same kit). Unbookmark any row you do not want to hear."
        ),
        P(
            "A “!” is a pattern, not identity and not proof of recording or an attack. "
            "Meta company IDs also match Quest headsets. Fieldy / Plaud Note are conversation "
            "recorders, not proof someone is recording you. PineAP clones look like ordinary SSIDs. "
            "You can type Extra attention on any other signature, built-in or custom."
        ),
        P(
            "<b>Home and ISP Wi-Fi</b> rows (NETGEAR, TP-Link, Xfinity, Starlink, and the rest of "
            "that class) are Wi-Fi only. Most also match the vendor’s IEEE OUI on the BSSID, so a "
            "renamed SSID still hits. Virtual BSSIDs (guest / mesh / extra SSID) that set the "
            "locally-administered bit still hit if the universal OUI is in the catalog. Apple / Google / "
            "Samsung prefixes are not recovered, so a random phone hotspot does not become a router. "
            "A matching vendor IE still hits. Hide a family on Filters if factory-named "
            "gateways paint Live display."
        ),
        P(
            "<b>Spectrum</b> also matches Spectrum Mobile / Spectrum Free Trial hotspots and has "
            "no Charter OUI (OEM boxes). <b>Chevrolet hotspot</b> (myChevrolet*), <b>Mercedes MBUX</b> "
            "(MBUX*), and <b>Motive</b> (Motive * / Motive_*) are factory in-car / fleet SSIDs — not "
            "dealer or café names."
        ),
        P(
            "<b>UniFi AP</b> is factory SSIDs plus Ubiquiti IEEE OUIs (ISP / routers). Other Ubiquiti AP radios "
            "(airMAX, AmpliFi, UISP) that beacon can hit. A second <b>UniFi</b> row matches advertised "
            "names UniFi / Ubiquiti / UAP-* on either radio (same class). <b>UniFi Protect</b> is BLE names "
            "UVC G3/G4/G6 Instant (Protect cameras in setup), not AP BSSIDs — that row stays Surveillance."
        ),
        P(
            "<b>Meraki</b> is Meraki* plus Cisco Meraki IEEE OUIs (not Cisco Systems). "
            "<b>Cisco</b> is Wi-Fi Cisco* (Aironet / Catalyst / Business / RV) plus old Aironet tsunami "
            "and Cisco Systems IEEE OUIs. It is not a Cisco substring — that would hit San Francisco. "
            "Fieldwatch sees AP beacons only, so a Cisco BSSID is an AP. Meraki and Cisco-Linksys keep "
            "their own OUI lists. Both sit in ISP / routers, not Surveillance. Meraki APs often carry "
            "a Cisco vendor IE (00:00:0C CCX / AP-name); Fieldwatch keeps Meraki only on those beacons."
        ),
        P(
            "<b>Aruba</b> is Aruba* / SetMeUp* / InstantOn* (not a lone instant word; not Hewlett Packard "
            "Enterprise OUIs). <b>Ruckus</b> is Ruckus* / Configure.Me* plus Ruckus Wireless OUIs. "
            "<b>Fortinet</b> is Fortinet* / FortiAP* / FAP-config* plus Fortinet OUIs. Those sit in "
            "ISP / routers with UniFi AP, Meraki, Cisco, Mist, Sophos, Extreme, Edgecore, WatchGuard AP, "
            "and Mojo — campus access points, not cameras."
        ),
        P(
            "<b>MikroTik</b> (Routerboard.com OUIs), <b>EnGenius</b>, <b>Zyxel</b>, and "
            "<b>Peplink</b> / Pepwave add vendor OUIs. <b>OpenWrt</b> and <b>GL.iNet</b> stay name-only "
            "(no unique IEEE OUI; GL.iNet boards use chip-module prefixes). <b>Arris</b> / SURFboard "
            "has the Arris OUI; CommScope’s mixed block is not used. <b>Google Wifi</b> is not Pixel BLE, "
            "not Nest-* cameras, and not Google Inc OUIs (phones / hotspots)."
        ),
        P(
            "Brand BLE rows in the stock catalog include Tesla, Rivian, Google, Sony, Bose, Garmin, "
            "Amazon, Fitbit, Oura, Logitech, HP, Epson, JBL / Harman, Sonos, Shokz, GoPro, Osmo, Insta360, DJI, "
            "Microsoft Device, Apple Device, Apple audio, Fast Pair, Tuya, Govee, Haiku Fan, myQ, LG webOS TV, "
            "Nespresso, and RadiaCode. Exact match IDs are in Appendix B. A few cautions: Google is "
            "company 0x00E0 / Pixel / Chromecast. Fast Pair is a separate Phones / PCs row (next paragraph). Apple Device is Continuity types, "
            "not AirPods (0x07). iPhones also send Find My 0x12; Fieldwatch drops the AirTags chip when "
            "Continuity is on the same radio. Bose includes UUID FEBE as well as FE21 / 0x009E. "
            "Tuya is 0x07D0 / FD50 / TUYA*, not the two-letter TY. Govee identity is name-only; "
            "once it matches, Decode fields read H5074/H5075 (0xEC88) and H510x/H517x (0x0001) "
            "temp / humidity / battery (§9.6). "
            "Shokz is OpenRun / OpenFit names, not Battery 0x180F. DJI is 0x08AA / DJI* (BLE and Wi-Fi). "
            "Osmo Action / Pocket / 360 / Nano are the Osmo row (0x08AA model IDs 0x0006–0x0022 plus OsmoAction* names), not DJI. "
            "Insta360 is Arashi Vision 0x10D7 plus X3 / Ace Pro / GO 3 names. "
            "In-flight drones also hit <b>Remote ID</b> (ASTM UUID 0xFFFA). Skydio / Autel / Parrot "
            "ANAFI-Bebop / HOVERAir are name rows; Autel default-ssid and Parrot company 0x0043 are not used."
        ),
        P(
            "<b>Fast Pair is not “Android.”</b> It is Google’s tap-to-pair UUID (FE2C), a Phones / PCs "
            "row of its own. iPhones fill that class because they constantly advertise Continuity "
            "(Apple Device). Most pocket Androids advertise little Fieldwatch can name, so they stay "
            "unmatched unnamed BLE. Google is Pixel / Chromecast only. Phone hotspot only while the "
            "phone is an access point with a factory SSID. When Fast Pair does appear, the advertisement "
            "is one of two shapes. <b>Pairing mode</b> is three bytes — a model ID. Android nearby will "
            "offer a tap-to-pair card (Pixel Buds, Galaxy Buds, a phone in pairing). That is uncommon "
            "and worth a look. Live display labels the chip Fast Pair pairing and puts <font face='Courier'>pair</font> "
            "on the subtitle; if the same radio later sends the long payload, that mark stays for the rest "
            "of the session. The <b>long payload</b> is an account-key filter from a device already on "
            "someone’s account. In a mall or station that is the usual Fast Pair chip: plaza noise, not "
            "a new pairing. Filters → Hide Fast Pair account-key hides those chips when Fast Pair is the "
            "only match. A Pixel that also matched Google still shows. Hide selected Fast Pair, or Hide "
            "phones, drops pairing-mode as well."
        ),
        P(
            "<b>Public safety vs vehicle.</b> Public safety is Axon / WatchGuard Video (body-worn and in-car) plus public-safety vehicle APs: Cradlepoint, AirLink, Compex, Novatel Wireless, Utility Inc. Many of those radios are used in law enforcement; they are not exclusive to it. Government, municipal, and other corporate fleets likely run some of the same body-worn cameras and vehicle gateways. Pattern match, not that agency or that officer. Vehicle is Tesla "
            "(including Cybertruck phone-key names S…C), Rivian, Ford 0x0723, Honda 0x0915, Hyundai 0x0826, "
            "Toyota 0x0977, Nissan 0x0BA6, Subaru 0x0A10, BMW 0x05EB, Volkswagen 0x011F / FE30/FE31, "
            "Porsche 0x0120, Jaguar Land Rover 0x020B, BYD 0x0C34, Chevrolet / GM hotspots, Mercedes 0x017C / MBUX*, "
            "Audi 0x010E / MMI, Motive, Samsara, and BLE TPMS (Goodyear 0x0B99, Schrader 0x0601, "
            "Pacific Industrial 0x0E32, Huf 0x070A, FOBO / Salutica 0x0127 / UUID 00EE, TireCheck 0x0BA2, "
            "SYTPMS name BR / UUID 0x27A5, Aftermarket TPMS* / UUID FBB0 / 0x0001 data 80–83, SIG service 0x1860). "
            "Tesla tsTPMS is the name tsTPMS* only — company 0x022B stays on Tesla; UUID 0x1122 is not unique. "
            "Factory 315/433 MHz valve-stem TPMS does not appear. A bare Nokia 0x0001 match is not cataloged. "
            "Kia / Volvo / Lucid / Polestar have no unique SIG company ID in this catalog. A miss is common: "
            "many cars never put that company ID in an advertisement, and Classic Bluetooth is invisible."
        ),
        P(
            "<b>Drones vs surveillance.</b> Drones is Remote ID (UUID FFFA), DJI, Skydio, Autel, Parrot, "
            "HOVERAir. Osmo / Insta360 action cameras are Cameras, not Drones. Surveillance is poles, ALPR, and commercial readers — not aircraft and not campus access points. "
            "Same amber chip; class splits them."
        ),
        P(
            "<b>Glasses vs audio vs cameras.</b> Glasses is Ray-Ban / Meta, Snap Spectacles. Audio is "
            "Apple audio, Sony, Bose, JBL / Harman, Sonos, Shokz. Cameras is Wyze, Ring, Arlo, eufy, Nest, "
            "Tapo, Reolink, GoPro, Osmo, Insta360 — consumer and action cameras. Not Flock / UniFi Protect (Surveillance) "
            "and not Axon (Public safety)."
        ),
        P(
            "<b>Signage vs retail beacons.</b> Signage is LED message displays and Bluetooth ESL 0x1857. "
            "Retail beacons is iBeacon / Target Atrius basket / Minew / Estimote / Kontakt.io. The advertised name on a LED sign "
            "is the sign text, not a product name. iBeacon is Apple 0x004C type 0x02/0x15 (any vendor can "
            "send it — mute in a mall). Target Atrius basket is iBeacon UUID 5993A94C-… plus service 0xB1BB (shopping-cart tags; not Acuity 0x0346 on those radios). Minew is IEEE OUI AC:23:3F; Estimote is 0x015D; Kontakt.io is 0x01FD. "
            "Not Eddystone FEAA (JBL, printers, and others use it)."
        ),
        P(
            "<b>Environmental sensors (Home IoT).</b> Ruuvi 0x0499, Blue Maestro 0x0133, SensorPush custom UUIDs. "
            "Ruuvi and Blue Maestro ship Decode fields (temp / humidity / pressure on the advertisement). SensorPush stays identity-only."
        ),
        P(
            "<b>Smart locks / access (BLE).</b> ASSA ABLOY (0x012E / HID 0x0124 / Yale 0x0BDE / UUID FCBF / "
            "Seos / Yale*) — not Apple FCB2. August (0x01D1 / FE24). Schlage / Allegion (0x013B / FCF4). "
            "Nuki (a92ee* services). SALTO (0x0199). dormakaba (0x0C64). Paxton (0x0196). Lockly names. "
            "Kevo / Unikey (0x015E). Master Lock (0x014B). igloohome (0x05BA). Tedee (0x0725). Kwikset names. "
            "Access control class (silver), not Surveillance."
        ),
        P(
            "<b>Smart thermostats (BLE).</b> ecobee (company 0x07D6). Nest Thermostat (Nest Labs 0x01B5; "
            "not Nest cameras). Sensi (name only — not Emerson 0x04DF). Honeywell Home / Lyric / Amazon "
            "Smart Thermostat names (not Honeywell 0x0526 or Resideo 0x0B01). A miss is common: most wall "
            "units are Wi-Fi after setup and do not keep advertising BLE."
        ),
        P(
            "<b>Health vs wearables vs Home IoT.</b> Health is clinic and home-medical BLE: Honeywell Xenon "
            "healthcare scanners (Xenon_*HC* / CCB-U00-H*, not warehouse CCB-U00-G), Omron Healthcare 0x020E "
            "(cuffs and scales; not industrial OMRON 0x02D5), Withings scales / BPM Connect, Dexcom G6/G7. "
            "Garmin / Fitbit / Oura stay Wearables. Honeywell Home thermostats stay Thermostats. Pattern match, "
            "not a patient or that hospital."
        ),
        P(
            "If you get local false positives, hide the family on Filters or mute the noisy rules "
            "in the editor (for example a neighborhood of Espressif gadgets tripping Flock OUI 3C:71:BF). "
            "Restore default signatures &amp; presets rebuilds the catalog (including stock Decode fields), stock bookmarks, and the "
            "default Settings switches (Keep screen on, Tag detections with GPS, Online place names, Voice on with Class + signature, TAK / CoT off). "
            "It also wipes custom signatures, presets, and named radios. Export signatures and Export settings first if you want a backup. "
            "Do not use Restore as an undo for a single rule."
        ),
        P(
            "To mute noisy methods on Flock (for example LiteOn OUIs) without deleting the row, "
            "open the signature and switch off those rules. See §7.6 and §7.6.1."
        ),
        P("See Appendix B for the exact default rules."),
        P("9.6 Decode fields", "h2"),
        figure_wrap(
            "fig-decode-editor-row.png",
            "Fig. 18 — Signature editor (Ruuvi).",
            "After a signature matches a BLE advertiser, Fieldwatch can map <b>cleartext</b> bytes "
            "in that advertisement to labels — temperature, model, Remote ID location, and so on. "
            "Identity match stays on the rule list: Decode fields do not help the matcher. "
            "The Live display does not parse the bytes (that path stays cheap). A small hexagon "
            "in the signature chip — same color as the name — means that signature has a map; "
            "dual-chip radios mark only the mapped name(s). Display → Signature names off hides "
            "the chips, so the hexagon goes with them. Values fill in on device detail "
            "(Decoded fields), Share as text, AI Export for that radio, and Debrief’s "
            "notable BLE lines.",
        ),
        figure_wrap(
            "fig-decode-fields.png",
            "Fig. 19 — Decode fields.",
            "This is BLE advertisements only: no pairing, no GATT connect, no crypto. "
            "Encrypted or unknown payloads stay hex, the same as Maker data already does. "
            "Apple Continuity, Fast Pair, Eddystone, and Microsoft stay on the built-in "
            "Known payloads / Maker data blocks; Decode fields is for catalog and custom "
            "rows those paths do not cover. Wi-Fi-only signatures hide the control."
        ),
        P(
            "You need two things, in this order: a signature whose rules hit the radio, and "
            "a field map for the payload you already understand from a vendor spec, a "
            "reverse-engineering note, or the hex on detail. The map does not print values on "
            "the Live display; the hexagon on that chip only means a map exists. "
            "If the radio is unmatched, create the signature first (§9.2 / §9.3), Save, "
            "then open Decode fields on that row."
        ),
        P("9.6.1 From a payload you already understand", "h3"),
        P(
            "Worked path. You have a BLE radio on the Live display, the maker bytes (or service data) on "
            "detail, and a layout — byte 0 is format, bytes 1–2 are temperature, and so on. "
            "Fieldwatch will not invent that layout; you type it."
        ),
        numbered([
            "Confirm identity. Open detail. Matched signatures lists the row that will carry the map. If none, Create signature from device (§9.2): keep a manufacturer ID / UUID / name rule that will still hit when the MAC rotates, then Save. A MAC-only pin is a poor home for a family map.",
            "Read the hex you will slice. On detail, Maker data → Raw payload is the bytes <b>after</b> the Bluetooth company ID. Service data FEAF (or whatever UUID) is the bytes after that UUID. Count in hex pairs: the first pair is offset 0, the next is 1. Do not count the company ID or the UUID toward offset.",
            "Signatures → that row → <b>Decode fields</b> (under Add rule). Pick Manufacturer or Service data to match the hex you counted. For manufacturer, set Company ID (0x0499, 0xEC88, …) unless every matched radio has only one maker record. For service data, set the UUID (FFFA, FE6A, FEAF, FEED, or the full 128-bit form).",
            "Add field for each value you care about. Label is what detail shows. Type, offset, length, endian, unit. Add field starts the next card at the previous offset + length. Save on the Decode fields bar writes the map; Back without Save discards.",
            "Check Preview at the bottom if a matching radio is on the air. Then open that radio on detail: Decoded fields should match the spec. A miss with raw hex still on the page usually means offset 0 included the company ID, the wrong source, or an Only if that this packet failed.",
        ]),
        P(
            "Example (Ruuvi Data Format 5, company 0x0499). Manufacturer. Company ID 0x0499. "
            "Field Format: u8, offset 0, length 1. Field Temperature: i16, offset 1, length 2, "
            "endian BE, scale 0.005, unit °C, Only if offset 0 length 1 equals <font face='Courier'>05</font>. "
            "Humidity is u16 BE at offset 3, scale 0.0025, unit %, same Only if. "
            "Format 3 packets skip those fields because byte 0 is 03, not 05 — that is the point of the gate. "
            "Stock Ruuvi already ships this map. Copy the idea for a sensor that is not in the catalog."
        ),
        P("9.6.2 Source: manufacturer vs service data", "h3"),
        P(
            "<b>Manufacturer</b> reads AD type 0xFF. Fieldwatch strips the two-byte company ID "
            "before your map runs, so byte 0 is the first payload byte after 0x004C / 0x0499 / "
            "0xEC88 / … — the same slice as detail’s Raw payload. Company ID on this screen "
            "is a filter among maker records on that radio (empty = the first record). "
            "A Govee light and a Govee hygrometer can both match the Govee name rule; the "
            "hygrometer map reads 0xEC88 (H5074/H5075) and 0x0001 (H510x). A light with no "
            "matching payload simply does not decode."
        ),
        P(
            "<b>Service data</b> reads one service-data UUID. Byte 0 is the first byte of that "
            "service’s data, not of the whole advertisement. UUID is required (16-bit FEAA / FFFA "
            "or the dashed 128-bit form). Several service-data blocks can sit on one radio; "
            "only the UUID you typed is parsed."
        ),
        P(
            "One signature, one source. If a product puts sensors in manufacturer data and a "
            "serial in service data, pick the slice you care about, or make a second signature "
            "for the other UUID (dual-label). You cannot point one map at both sources."
        ),
        P("9.6.3 Field cards", "h3"),
        P(
            "Each card is one value. A field that does not fit — offset past the end, gate failed, "
            "empty UTF-8 — is skipped silently, and the other fields still show. That is how one map "
            "covers two packet formats."
        ),
        table(
            ["Control", "What it does"],
            [
                ["Label", "Text on detail / Debrief / Preview. Changing it slugs the ID until you set a custom ID under More."],
                ["Type", "How to read the slice. See the type table below. Defaults the length."],
                ["Offset", "Start byte in the source payload. 0 is the first byte after the company ID (manufacturer) or the first service-data byte."],
                ["Length", "Byte count. Type fills a default (u8=1, u16/i16=2, u24=3, u32/i32/f32=4, mac=6). utf8 / hex / bits start at 1 until you set them."],
                ["Unit", "Appended after the number (°C, %, hPa, mV, dBm). Empty = number only."],
                ["Endian", "LE (default) or BE. Hidden for u8 / i8 / utf8 / hex / bool. Applies to the integer/float/bits word, including bits."],
                ["Bit offset / width", "Type bits only. After the slice is read as an unsigned integer in that endian, Fieldwatch shifts from the least-significant bit. Width 1–32."],
                ["Scale / Add", "Numeric types only. After the integer/float read: modulo (if set), then × scale, then + add. Temperature often needs scale 0.01 or 0.005. Pressure that is stored as (hPa − 500)×100 uses scale 0.01 and add 500."],
                ["Only if…", "Skip this field unless a gate holds (§9.6.4)."],
                ["Named values…", "Map a raw number or hex to a word (§9.6.4)."],
                ["More", "ID (stable key; leave the slug unless you export maps or want TAK advertised-position pins). TAK / CoT looks up ids, not labels: latitude + longitude (optional alt_geo) pin the gadget; op_lat / op_lon are the Remote ID pilot and are not the aircraft pin. §5.8.3. Modulo is remainder before scale — Govee packed humidity is modulo 1000."],
            ],
            [1.45 * inch, 5.05 * inch],
        ),
        Spacer(1, 6),
        table(
            ["Type", "Bytes (default)", "What you get"],
            [
                ["u8 / i8", "1", "Unsigned 0–255 or signed −128…127. No endian."],
                ["u16 / i16", "2", "16-bit int. Set BE when the spec says big-endian (Ruuvi, Blue Maestro)."],
                ["u24", "3", "Unsigned 24-bit. Govee H5075 packs temp in a 24-bit BE int."],
                ["u32 / i32", "4", "32-bit int. Remote ID lat/lon are i32 LE × 1e−7 degrees."],
                ["f32", "4", "IEEE-754 float. Rare in ads."],
                ["bits", "from bit offset+width", "Unsigned bitfield inside the slice. Use named values for 0/1 flags (asleep/awake)."],
                ["bool", "1", "Any non-zero byte → yes; all zeros → no."],
                ["utf8", "you set", "Text, trailing NULs and spaces stripped. Remote ID UAS ID / Self ID."],
                ["hex", "you set", "Spaced hex dump of the slice. Tile’s rotating private id."],
                ["mac", "6", "AA:BB:CC:DD:EE:FF from the first six bytes (Ruuvi payload MAC)."],
            ],
            [1.45 * inch, 1.2 * inch, 3.85 * inch],
        ),
        Spacer(1, 6),
        P(
            "Math order is fixed: read the integer or float, then remainder (modulo), then "
            "multiply (scale), then add. Empty scale, add, or modulo leaves that step off. "
            "Named values run on the <b>raw</b> integer (or the UTF-8 / hex string), not on the scaled number — "
            "map 65 to HERO13 Black, not 0.005×something."
        ),
        P("9.6.4 Only if and named values", "h3"),
        P(
            "<b>Only if…</b> is how one signature handles two layouts. Each field can have its "
            "own gate. If the gate fails, that field is omitted; the rest still run."
        ),
        bullets([
            "<b>equals</b> — bytes at Offset / Length must match Hex. Ruuvi Format 5: offset 0, length 1, hex 05. Remote ID location: the message-type nibble is packed with protocol 2, so the header byte is 12 for location (type 1, proto 2).",
            "<b>not equals</b> — skip when those bytes are Hex (a reserved/invalid marker).",
            "<b>mask</b> — every 1-bit in Hex must be set in the payload slice: (payload AND Hex) equals Hex. Use it for flag bytes when you only care that a bit is on.",
            "<b>payload length</b> (When → length) — the whole source payload must be exactly Bytes long. Offset/Hex are ignored. Govee H5074 is 7 bytes; H5075 is 5 or 6. Same company ID, different length, different fields.",
        ]),
        P(
            "Hex is raw bytes, no 0x, no spaces required (<font face='Courier'>05</font>, "
            "<font face='Courier'>0215</font>). Length of Hex must match Length of the gate "
            "or the field never shows."
        ),
        P(
            "<b>Named values…</b> is a table of Raw → Show as. Raw is the integer as decimal "
            "(65) or hex (0x41), or the UTF-8 / hex string for those types. Unlisted values "
            "still show as the number (or scaled number). GoPro / Osmo / Nest Weave / Remote ID "
            "message type ship these tables. Add value for each known id; Remove clears the table."
        ),
        P("9.6.5 Preview, detail, and reports", "h3"),
        bullets([
            "<b>Preview</b> (bottom of Decode fields) uses a radio this signature already matches that is on the air now. You see the source hex and the parsed rows. No matching radio: Save anyway. A matching radio with empty hex: wrong source or that advertisement has no manufacturer/service bytes. Parsed empty: offset/length/gate — the usual miss is counting the company ID as offset 0.",
            "<b>Device detail → Decoded fields</b> is the same parse on the current advertisement. Several matched signatures with maps: each row is prefixed with the signature name. A map that exists but produced nothing prints a short note (Govee lights usually only send a name); raw hex stays below.",
            "<b>Live display chip</b> shows a hexagon when the signature has a map — not the parsed values. Radar labels do not carry the hexagon. TAK / CoT (§5.8) also reads the numeric values for ids latitude / longitude (and alt_geo) to pin advertised position; that is not shown on the Live display.",
            "<b>Share as text / AI Export</b> on detail include Decoded fields. Reports → Debrief notable BLE prints label: value under those radios.",
            "Save on Decode fields writes the map onto this signature (stock or custom). Back without Save discards the editor. <b>Remove decode map</b> (confirm) clears the whole map; empty fields + Save also stores None. Export / Import signatures includes the map. Restore default signatures &amp; presets reloads stock maps and wipes custom rows — export first.",
        ]),
        P("9.6.6 Stock maps", "h3"),
        P(
            "These rows ship a map (catalog upgrade fills them onto built-in rows that did not "
            "have one). You can edit or clear any of them. Fitbit, Find My tags, and other "
            "connect-or-crypto ads are identity-only — older Fitbit advertisements still do not "
            "carry steps."
        ),
        table(
            ["Signature", "Source", "What detail can show"],
            [
                ["Ruuvi", "Mfr 0x0499", "Format 5: temp / humidity / pressure / accel / battery / TX / movement / sequence / MAC in payload. Format 3: humidity / pressure / accel / battery (Format 3 temperature is sign-magnitude, not a plain int)."],
                ["Remote ID", "Service FFFA", "Open Drone ID app code, counter, message type. Protocol 2: Basic ID, location (field ids latitude / longitude / alt_geo), Self ID, System (op_lat / op_lon = pilot, not the aircraft pin), Operator ID. TAK Payload location uses those ids. Not a tail number."],
                ["Blue Maestro", "Mfr 0x0133", "Tempo Disc battery, log interval, stored logs, temperature, humidity."],
                ["GoPro", "Mfr 0xF202", "Schema, processor awake/asleep, Wi-Fi AP, pairing, model name, media offload."],
                ["Osmo / DJI", "Mfr 0x08AA", "Model id (Osmo Action / Pocket / 360 and some aircraft). Osmo and DJI rows share the map; identity rules still split cameras from drones."],
                ["Govee", "Mfr 0xEC88 / 0x0001", "H5074 / H5075 / H510x temperature, humidity, battery — after the name match. Lights with a Govee name may not fit those layouts; then the map does not apply."],
                ["Kontakt.io", "Service FE6A", "Location packet: battery, TX, channel, moving."],
                ["Estimote", "Mfr 0x015D", "Frame type (Nearable / Telemetry). Packed sensor bytes are not expanded."],
                ["Nest Weave", "Service FEAF", "Weave device-identification block: vendor (Nest Labs / Yale), product (Protect / thermostat / cam / Guard / Detect when the enum hits), pairing, 64-bit Weave device id. A 2-byte FEAF payload is the product id alone."],
                ["Tuya", "Mfr 0x07D0", "Bound flag and protocol version. UUID bytes stay encrypted."],
                ["Tile", "Service FEED", "Rotating private id (8 bytes of hex). Not a serial and not a stable identity."],
                ["Aftermarket TPMS", "Mfr 0x0001", "Valve-cap kits after the TPMS* / FBB0 / data 80–83 match: wheel, pressure kPa, temperature, battery, alarm. A bare Nokia 0x0001 radio does not hit this row."],
                ["SYTPMS", "Mfr (7-byte BR blob)", "Bicycle / scooter BR sensors: gauge psi, temperature, battery volts, alarm / rotating / standing still."],
                ["Tesla tsTPMS", "Mfr 0x022B", "Awake ads: pressure psi, temperature °F, battery mV. Sleep packets skip those fields. Identity is still the tsTPMS* name."],
            ],
            [1.35 * inch, 1.35 * inch, 3.8 * inch],
        ),
        Spacer(1, 6),
        callout(
            "A decoded value is still a pattern",
            "Temperature, model, or a Remote ID location is what that advertisement contained, "
            "not proof of a serial, an owner, or that a drone is overhead. "
            "A miss (map did not apply) is common: different firmware, a short scan response, "
            "or a family that encrypts the rest of the payload. Raw bytes stay on the page.",
            "note",
        ),
        P("9.6.7 Practices that hold up", "h3"),
        bullets([
            "Get identity tight before you decode. A map on a rule that also hits lights, TVs, or every 0x004C radio will either stay empty or show nonsense.",
            "Count offsets on detail’s Raw payload, not on a USB sniffer dump that still includes the company ID / UUID / AD length.",
            "Prefer Company ID filled in. Empty = first maker record; dual-company ads (Apple + vendor) will decode the wrong slice.",
            "Gate on a format byte or payload length when the family has more than one layout. Do not overlap two temperature fields without gates — you will show both or neither.",
            "BE when the spec says big-endian. LE is the editor default and is wrong for Ruuvi / Blue Maestro / Govee H5075 packing.",
            "Named values are for enums and flags, not for scaling. Scale 0.01 is not a named value of “°C”.",
            "Do not expect GATT. If the interesting number only appears after a connect (modern Fitbit steps, many locks), Fieldwatch will never see it.",
            "Open a stock row (Ruuvi, Remote ID, Govee) and read its cards before you type a new map. The editor is the spec.",
            "If you want TAK to pin advertised position, set the field IDs to latitude and longitude (scale as the spec). Labels can be anything. op_lat / op_lon are not the pin. §5.8.3.",
            "After Save, open a live radio. If half the values are missing, the advertisement is a different format or a scan response — add Only if, or accept that this packet is short.",
        ]),
    ]

    # 10 Alerts
    flow += [
        PageBreak(),
        P("10. Alerts &amp; Sensitivity", "h1"),
        P("10.1 Watchlist", "h2"),
        P(
            "A watch target is either a device key (KIND:MAC) or a signature ID. Fieldwatch alerts "
            "once when that radio first appears this session, and again only if it leaves "
            "(gone) and later returns. Sitting matches do not repeat. If New detections only "
            "is on, an alert fires only when that row would actually appear on the Live display. Already-here "
            "and learning-window Wi-Fi never alert while they are hidden."
        ),
        bullets([
            "Device: open detail → bookmark icon to be notified when that radio appears. Prefills a name (advertised name or type guess). Settings → Named radios lists those MACs: rename, Observer notes, Alert on/off, remove one, Clear all. Signature watches are not on that list. A rotated BLE address stays until you delete it.",
            "Signature: the bookmark requests an alert when a new match appears. Stock bookmarks on first launch / Restore: Extra attention (Axon, WatchGuard Video, Ray-Ban / Meta glasses, Snap Spectacles, Fieldy, Plaud Note, Hobby BLE serial, Hak5 Pineapple, Flipper Zero, Pwnagotchi, Marauder / Deauther, GhostESP, Bruce, Porkchop, Cradlepoint, AirLink, Compex, Novatel Wireless, Utility Inc, plus roadside / public camera + ALPR: Flock, Penguin, Pigvision, FS Ext Battery, Genetec AutoVu, Rekor, Motorola Vigilant, Verkada, Avigilon, Axis, Hikvision, Dahua, Hanwha Wisenet, Uniview, Rhombus) and every built-in Drone-class row (DJI, Remote ID, Skydio, Autel, Parrot, HOVERAir).",
            "Master switch: Settings → Watchlist alerts. Off suppresses beep, voice, vibration, flash, and jump. Beep and Voice are independent (pip, spoken phrase, or both). Jump works with any of those. Settings → System notification (off by default) posts a silent shade card; skip it in the field.",
        ]),
        P("10.2 Beep, voice, flash, and optional shade card", "h2"),
        P(
            "The default alert is in-app only. Beep (on by default) is a synthesized ~1 kHz double pip "
            "on media volume. Voice (on by default) speaks a short phrase (Class + signature). They are independent: "
            "pip then phrase (the default), pip only, or spoken phrase only. Either cue also flashes the row for one second "
            "and can jump Live display to that row. Test alert plays whatever combination is on. "
            "On By class the matching class and signature open first so the flash is visible, "
            "and the jump keeps those headers on screen when the radio is not far down the outline. "
            "On radar the blip pings with two expanding rings and a bright core — that ping is the "
            "watchlist hit, not the pip itself, so voice-only still pings. "
            "Vibration is a short double pulse. Settings → System notification (off by default) "
            "posts a silent shade card on channel <font face='Courier'>fieldwatch_watch_v3</font> "
            "so the scan loop does not build a notification on every new hit. The separate "
            "<font face='Courier'>fieldwatch_scan</font> channel is the low-importance “Fieldwatch scanning” "
            "status and is not an alert."
        ),
        P("10.2.1 Voice on watched signature", "h3"),
        P(
            "Voice is the second watchlist cue. It ships on. Turn it off under Settings → "
            "Voice on watched signature if you only want the pip. Watchlist alerts still gates it — master off means no pip, "
            "no speech, no flash, no jump."
        ),
        P(
            "Settings → <b>What to say</b> picks the phrase (chips, enabled while Voice is on). "
            "Default is <b>Class + signature</b>. <b>Class</b> is the same bucket as the Live display glyph. A bookmarked AirTag, "
            "Chipolo, or Tile all say “finder tags.” Axon says “public safety.” ISP / routers "
            "says “I S P routers.” Use that in a pocket or on a drive when you need the kind. "
            "<b>Signature</b> says the catalog row instead (“Apple AirTags,” “Axon”). "
            "<b>Class + signature</b> says both (“finder tags, Apple AirTags”). Slashes in a "
            "name are spoken as a pause (Ray-Ban / Meta glasses → “Ray-Ban Meta glasses”)."
        ),
        P(
            "A <b>named radio</b> (detail bookmark, Settings → Named radios) always speaks "
            "the watch name — the prefilled advertised name or type guess, or the custom name you "
            "typed on Named radios (“Peter’s Mesh Node”). Class / Signature / Both does not "
            "apply. An unmatched radio still speaks that name, not “unmatched.” A "
            "<b>signature bookmark</b> still follows What to say. If two signatures match the "
            "same radio, a signature bookmark speaks that family, not whichever chip landed first."
        ),
        P(
            "Pip and voice still mix three ways: pip then phrase about 400 ms after the double pip (the default), pip only, or "
            "spoken phrase only. Use voice-only when you cannot look. "
            "Use pip only on a sit when speech would be noise. Use both when you want a heads-up "
            "then the words. Test alert plays the same mix — with Voice on and Class selected you "
            "hear “finder tags”; Signature says “Apple AirTags”; Class + signature says both. "
            "Jump to new watched detection works with beep, voice, or both; it is not tied to the pip. "
            "Hunt’s geiger tick never speaks."
        ),
        P(
            "Speech is on-device text-to-speech (no internet, no Fieldwatch server). If a phrase is "
            "already being spoken, later hits in that burst are skipped so names do not overlap. "
            "A phone with no TTS language pack still pips if Beep is on. Raise media volume; "
            "voice uses the same stream as the pip."
        ),
        P("10.3 Scan intensity", "h2"),
        table(
            ["Mode", "Use when", "Cost"],
            [
                ["High performance", "Walking, drive-by, first five minutes on site. Ships on. Full phone checklist: §4.5.", "Highest drain and heat. BLE low-latency with a ~70 s recycle. Wi-Fi about every 30 s (stock). For signed APs at speed, add Faster Wi-Fi AP scans (§10.3.1)."],
                ["Balanced", "Default for a one-hour sit.", "Acceptable catch rate; Wi-Fi about every 40 s (stock)."],
                ["Battery saver", "All-day bag carry or overnight in a room.", "You will miss short BLE bursts. Wi-Fi about every 55 s (stock). Raise stale if the list flickers to gone."],
            ],
            [1.5 * inch, 2.7 * inch, 2.3 * inch],
        ),
        Spacer(1, 6),
        P(
            "Stale window (Settings, 15–180 s, default 45) is independent of intensity. "
            "On-screen linger is the longer of Stale and Brief hold, so Brief hold 60 s "
            "keeps a radio visible a full minute after the last packet. A saver scan plus "
            "a short stale window will still mark slow advertisers gone unless Brief hold "
            "is raised. For tags, High performance or stale 90–120 s is safer."
        ),
        P("10.3.1 Faster Wi-Fi AP scans", "h2"),
        P(
            "A second Settings switch, under scan intensity. Default off. It does not replace "
            "High / Balanced / Saver for BLE. It only changes how often Fieldwatch asks Android "
            "for a new Wi-Fi AP list."
        ),
        P(
            "<b>Why it exists.</b> Catalog signatures (OUI on the BSSID or vendor IE, factory "
            "SSID globs such as IBR* / AirLink* / UniFi*) run on what the last scan batch "
            "contained. They do not search the air between batches. On a sit, the same APs "
            "return. On a drive, an interesting AP — a Cradlepoint in a fleet vehicle, an "
            "AirLink gateway, a UniFi or Cisco roadside box, a hidden SSID that still exposes "
            "its MAC — may only be in range for one short pass. At 45 mph you roll about 600 m "
            "between stock High-performance batches and about 160 m between faster batches; at "
            "65 mph those figures are about 870 m and 230 m (§7.1.1). Faster scanning is more "
            "chances to hear that BSSID while it is loud enough to appear in scan results, so "
            "the signature can chip, the Extra attention “!” can show, and a stock bookmark can "
            "alert. Missing the scan window means missing the signature, even if the radio was "
            "real and in the catalog."
        ),
        P(
            "<b>OS gate — Fieldwatch checks before the switch will turn on.</b> Android 11+ exposes "
            "whether Wi-Fi scan throttling is enabled. Fieldwatch reads that flag when you try to "
            "turn Faster Wi-Fi AP scans on, and again when Settings resumes. If the OS is still "
            "throttling (the factory default), the Fieldwatch switch stays off and a dialog tells "
            "you to use Developer options; Open developer options is offered. Fieldwatch cannot "
            "write that OS flag. Android 10 cannot be queried, so the Fieldwatch switch stays off "
            "there."
        ),
        P(
            "<b>How to enable, in order.</b> (1) Settings → About phone → tap Build number until "
            "the phone says you are a developer. (2) Settings → Developer options → Wi-Fi scan "
            "throttling → Off. (3) Return to Fieldwatch → Settings → Faster Wi-Fi AP scans → On. "
            "Keep screen on and the scan notification still apply. Tag detections with GPS "
            "should stay on if you care where that hear was."
        ),
        P(
            "<b>While it is in effect.</b> About every 8 s Fieldwatch calls "
            "<font face='Courier'>startScan()</font> and skips the four-per-two-minutes quota. "
            "The header’s Wi-Fi next Ns count is shorter. If the OEM still refuses scans, "
            "fail/backoff is unchanged (Wi-Fi waiting on OS, up to 45 s). If you turn OS "
            "throttling back on, faster mode is not used even if the Fieldwatch switch is still "
            "saved on; the header can read Wi-Fi fast scan needs Developer options. Turn the "
            "Fieldwatch switch off when the drive is over — battery and heat are higher than High "
            "performance alone."
        ),
        P(
            "<b>What it will not do.</b> Not a continuous Wi-Fi stream. Not Hunt (still BLE). "
            "Not associated clients. Not a guarantee you will hear every fleet AP — body block, "
            "other vehicles, 5 GHz range, and a renamed randomized BSSID without a vendor IE "
            "still miss. Pattern match is still not that agency or that car. Use it as a "
            "drive-time AP net for signatures you already care about, then look with your eyes."
        ),
        P("10.4 Alert configuration practices", "h2"),
        bullets([
            "Stock bookmarks already watch body-cam, camera glasses, recording wearables (Fieldy / Plaud Note), pentest, public-safety vehicle APs, and roadside / public camera + ALPR. Unbookmark a family you do not want to hear (Meta IDs also hit Quest; Flock LiteOn / Espressif OUIs are noisy).",
            "Watch the signature, not twenty MACs, when the question is “is any Flock/Raven/AirTag here.”",
            "Watch the device when you have already isolated one radio and need to know if it returns after you leave and come back.",
            "A generic DIRECT- / ESP_ name is unmatched unless a product family also hits. Do not invent a catch-all for those prefixes — it dual-labels real rows.",
            "Confirm the first alert visually. A single BLE packet can be a passerby.",
            "The shade card is silent by design. Raise media volume and tap Test alert (pip, spoken class, or both — whatever is on). Check POST_NOTIFICATIONS only if you also want the card.",
        ]),
    ]

    # 11 Logging
    flow += [
        PageBreak(),
        P("11. Logging", "h1"),
        P("11.1 What is written", "h2"),
        P(
            "Logging can be turned off in Settings. When it is on, observations append under "
            "<font face='Courier'>files/logs/</font> as "
            "<font face='Courier'>fieldwatch-NNN.jsonl</font> (JSON lines, one hear per line). "
            "CSV, GPX, KML, and WiGLE are Share/Save projections on Reports → Log — they are not "
            "a second live write format. Older CSV rotate parts still import. "
            "Default rotation is 1024 KB; the index cycles through 000–011 (12 files). "
            "In a flood, Fieldwatch writes new radios and every 25th repeat (capped per batch) "
            "so the log mutex does not stall the Live display. Export concatenates the current parts and "
            "strips extra CSV headers. If Tag detections with GPS is on and the phone has a fix, "
            "each written row includes <font face='Courier'>lat</font> and "
            "<font face='Courier'>lon</font> — the operator phone at hear-time, not the other "
            "radio. Those fields are empty when tagging is off or there is no fix yet. "
            "New rows also append <font face='Courier'>vendor_ie</font> (Wi-Fi vendor-IE OUIs, "
            "up to eight, pipe-separated). That column is last so older 17-column parts in a "
            "concatenated export still line up: lat/lon stay put; vendor_ie is empty on those "
            "rows. BLE rows leave it empty. Signature candidates uses it; WPA / RSN / P2P / "
            "Qualcomm chip IEs are logged but not clustered as families. The "
            "<font face='Courier'>fleets</font> column is the signature names at <b>write time</b> — "
            "re-match from the other columns if the catalog has changed."
        ),
        P("11.2 CSV columns", "h2"),
        table(
            ["Column", "Meaning"],
            [
                ["timestamp", "Unix epoch milliseconds (device clock)."],
                ["iso", "UTC timestamp yyyy-MM-dd'T'HH:mm:ss.SSS'Z'."],
                ["kind", "WIFI or BLE."],
                ["mac", "Normalized address, colon-separated hex."],
                ["name", "SSID or BLE local name. Commas/newlines stripped. Empty if unknown or hidden."],
                ["rssi", "Last received signal strength, dBm."],
                ["channel", "Wi-Fi channel derived from MHz; 0 for BLE."],
                ["freq", "MHz. BLE is recorded as 2402 as a band marker, not a precise advertising channel."],
                ["oui", "First three octets."],
                ["vendor", "IEEE MA-L/M/S name for a universal MAC, else Bluetooth company name when that is all we have. Randomized BLE addresses usually empty."],
                ["fleets", "Matched signature names joined with +, at write time. Column name is still fleets for log compatibility. Signature candidates re-matches; do not trust this column after a catalog change."],
                ["mfg", "Manufacturer company ID as hex, or empty."],
                ["uuids", "Service UUIDs joined with |."],
                ["flags", "RAND and/or HIDDEN."],
                ["raw", "Manufacturer data hex, else raw advertisement hex, truncated to 80 characters."],
                ["lat", "Operator latitude (WGS84 decimal degrees, 6 dp) when Tag detections with GPS is on and a fix exists. Empty otherwise. Phone GPS at hear-time, not the other radio."],
                ["lon", "Operator longitude, same rules as lat."],
                ["vendor_ie", "Last column. Wi-Fi vendor-IE OUIs, pipe-separated, up to eight (for example C8:3A:6B|00:50:F2). Empty on BLE, on older 17-column rows, and when the scan reported none. Signature candidates clusters product IEs; 00:50:F2 (WPA), 00:0F:AC (RSN), 50:6F:9A (P2P), and 8C:FD:F0 (Qualcomm chip) are logged but not used as a family ID."],
            ],
            [1.3 * inch, 5.2 * inch],
        ),
        Spacer(1, 6),
        P("Header row:", "body_left"),
        P("timestamp,iso,kind,mac,name,rssi,channel,freq,oui,vendor,fleets,mfg,uuids,flags,raw,lat,lon,vendor_ie", "mono"),
        P(
            "Older files omit the last column. A Log export that concatenates mixed parts writes "
            "the 18-column header once; rows from before this feature have 17 fields, so "
            "<font face='Courier'>vendor_ie</font> reads empty and lat/lon stay in place."
        ),
        P("11.3 JSON Lines", "h2"),
        P(
            "Each line is one object. Same facts as the CSV row. JSONL is easier to feed to jq "
            "or a notebook; CSV is easier in a spreadsheet. JSON uses "
            "<font face='Courier'>rand</font> / <font face='Courier'>hidden</font> booleans "
            "instead of a flags field, and <font face='Courier'>mfg</font> as an integer "
            "(CSV stores that company ID as hex). Older JSONL lines omit "
            "<font face='Courier'>vendor_ie</font>, <font face='Courier'>rand</font>, and "
            "<font face='Courier'>hidden</font>; the parser treats them as empty / inferred "
            "from the MAC."
        ),
        table(
            ["Field", "Meaning"],
            [
                ["ts", "Unix epoch milliseconds (same as CSV timestamp)."],
                ["iso", "UTC timestamp."],
                ["kind", "WIFI or BLE."],
                ["mac", "Normalized address."],
                ["name", "SSID or BLE local name."],
                ["rssi", "Last RSSI, dBm."],
                ["channel", "Wi-Fi channel; 0 for BLE."],
                ["freq", "MHz. BLE recorded as 2402 as a band marker."],
                ["oui", "First three octets."],
                ["vendor", "IEEE or Bluetooth company name, or null."],
                ["fleets", "Matched signature names joined with +, at write time. Re-match if the catalog changed."],
                ["mfg", "Manufacturer company ID as integer, or null. (CSV stores the same ID as hex.)"],
                ["uuids", "Service UUIDs, comma-separated."],
                ["raw", "Manufacturer or advertisement hex, up to 160 characters."],
                ["vendor_ie", "Wi-Fi vendor-IE OUIs, pipe-separated, up to eight. Empty on older files and BLE. Same meaning as the CSV column."],
                ["rand", "JSON only. Randomized address. CSV uses flags RAND instead."],
                ["hidden", "JSON only. Hidden SSID. CSV uses flags HIDDEN instead."],
                ["lat", "Operator latitude, or JSON null if tagging is off or there is no fix. Phone GPS at hear-time."],
                ["lon", "Operator longitude, or JSON null. Same rules as lat."],
            ],
            [1.3 * inch, 5.2 * inch],
        ),
        P("11.4 Debrief and AI Export", "h2"),
        callout(
            "Experimental sit report — not a finding",
            NOTICE_SHORT +
            " Debrief “possible tail” language and AI Export analysis are hypotheses from "
            "15 minutes of in-memory radios. They are not identity, not complete, and not "
            "advice about whether you are safe. Do not hand a Debrief or an AI chat to anyone as proof. "
            "The AI Export prompt opens with this same disclaimer and tells the model to repeat it.",
            "warn",
        ),
        P(
            "Reports has two Debrief buttons. Same sit report, two formats. "
            "Both are built from radios still in memory (last 15 minutes, about 400; §11.4.1), "
            "or from the selected sit. Live view and Filters do not change that snapshot. "
            "Radios keep scanning while the share sheet opens. "
            "Treat either share as operationally sensitive: neighbor SSIDs, MACs, and operator GPS when tagging is on."
        ),
        bullets([
            "<b>Debrief (text)</b> — <font face='Courier'>text/plain</font> on the system share sheet. Paste into notes, chat, or a logbook.",
            "<b>Debrief (PDF)</b> — letter-size (612×792 pt) typeset copy via Android PdfDocument. Night-green FIELDWATCH / FIELD DEBRIEF header, numbered sections, amber Possible trackers with you / Possible tail (finder tags) plus Retail beacons with you / Wearables with you when those lists are non-empty, amber Extra attention callout for each matched special note, green takeaway box, page N / M, footer “Off Grid Pete LLC · operationally sensitive.” Cached under <font face='Courier'>cache/debrief/fieldwatch-debrief-YYYYMMDD-HHMMSS.pdf</font> and shared as <font face='Courier'>application/pdf</font> through FileProvider <font face='Courier'>app.fieldwatch.files</font>. Open in Drive, Files, email, or a PDF viewer.",
        ]),
        P("11.4.1 What Debrief actually sees", "h3"),
        P(
            "Debrief is a sit report of the <b>live map in RAM</b>, not a replay of the log. "
            "Tap the button and Fieldwatch takes the radios still in memory plus the operator GPS path, "
            "then keeps those whose first or last hear is in the last 15 minutes. "
            "Filters and the Live display view (radar / list / timeline / hybrid) do not change that snapshot. "
            "The rotating JSON lines file is a different file — that is Log export, and "
            "that is what Signature candidates reads (§11.5)."
        ),
        P(
            "Two clocks, both short, and a size cap:",
            "body_left",
        ),
        bullets([
            "<b>15-minute report window.</b> A radio last heard 16 minutes ago is not in this Debrief, even if the log still has the packets.",
            "<b>RAM eviction.</b> Unnamed radios (no signature match) drop about 3 minutes after the last packet. Named / signature-matched radios stay up to 15 minutes after last heard.",
            "<b>Crowd cap.</b> The live map holds about 400 radios (hard ceiling 900) so a street of rotating BLE MACs cannot grow forever. Oldest unnamed drop first. Radios still inside Brief hold are not dropped by that cap. If the set still overflows the hard ceiling, even named rows can go.",
        ]),
        P(
            "On a drive those caps bite. City streets are dense: phones rotating BLE, shop APs, other cars. "
            "A few kilometers of traffic can fill the 400. The café or intersection at the start of the trip "
            "is then gone from the next Debrief — unnamed BLE may already have been gone after three minutes. "
            "That is expected. It is not a failed scan, and it is not the log deleting itself."
        ),
        P(
            "What <b>does</b> stay is anything still advertising along the path. A bag tag, a car TPMS, "
            "a planted tracker, your own AirTag or watch — they keep getting packets, so last-heard stays fresh. "
            "If they match a signature they get the 15-minute linger instead of three, and they are not first "
            "in line for the crowd cap. Radios moving with you therefore show up in Debrief after Debrief. "
            "Roadside clutter you only passed does not. Filters → Moving with you is not required for that; "
            "Debrief’s co-travel callouts use the same GPS trails whether that switch is on or off."
        ),
        P("<b>How to work a long trip</b>", "body_left"),
        bullets([
            "Tap Debrief (text or PDF) about every 10–15 minutes, or at each stop, and <b>keep those shares</b>. Each file is that window, not the whole drive.",
            "Do not wait until you get home to tap Debrief once. That last 15 minutes is the last few kilometers, not the departure neighborhood.",
            "Leave logging on. Log export is the hour-long file. Debrief will never reconstruct it from RAM.",
            "You do not need to sit still. A rolling Debrief still writes distance, Where you were, and co-travel for radios that stayed with the car.",
            "A thin inventory of unnamed BLE after a highway run is normal. Check Possible trackers / Possible tail / Vehicle / Extra attention first — those are the rows that survived on purpose.",
        ]),
        P(
            "AI Export uses the same memory snapshot (plus a 5-minute slice). Same caps, same advice: "
            "paste more than once on a long trip if you want the chat to see more than the last stretch."
        ),
        P(
            "The report body is the same in both formats: generated time, 15-minute window, scan "
            "settings, GPS-tag on/off, overall distance traveled (along-track path length and "
            "straight-line span when tagging is on), then (1) executive summary, (2) <b>Where you were</b> "
            "(stays vs transit along the operator path — coordinates once per stay, not on every radio line; "
            "street names on those stays if Online place names ran), (3) <b>Observer notes</b> when any heard radio in this window has a Named-radio note "
            "(custom name, MAC, RSSI, the note — quieter radios still appear here even if they are not in Loudest APs), "
            "then tracking assessment, "
            "then optional amber callouts <b>Possible trackers with you</b> and <b>Possible tail</b> (finder tags) when those lists are non-empty, " +
            "plus <b>Retail beacons with you</b> and <b>Wearables with you</b> when those classes stayed with the path, "
            "then environment, Wi-Fi, BLE (notable BLE also prints Decode fields when a map applied; custom names replace advertised names), signature hits, persistence, Extra attention when a matched "
            "signature has that field filled (full caution text on each hit; amber highlighted boxes on the PDF; "
            "pattern match, not a skimmer detector), anomalies, privacy, "
            "recommended actions, and a one-line takeaway. "
            "When GPS tagging recorded a walk, a letter-size operator-path figure sits with the report (stacked radios at one place share a Path-key number). "
            "<b>Anomalies</b> is not a second inventory: Extra attention, Signature hits, and tracking callouts already list named matches. "
            "Anomalies only keeps cues that are not those sections (Fast Pair in pairing mode, a very loud unnamed radio, a high randomized-BLE count). "
            "AirTag / SmartTag / Tile rosters and Extra attention reprints are omitted."
        ),
        P(
            "If Settings → Tag detections with GPS is on and you have moved about 45 m or more, Debrief tests "
            "whether radios co-traveled with your GPS path, split by class. "
            "The tracking assessment itself is short (did the test run, how far you went). "
            "Only radios that <b>stayed with the path</b> get their own amber callouts. House tags and other radios you only passed are omitted — they are not tracking you. "
            "<b>Possible trackers with you</b> (amber): finder tags — AirTag / Find My, SmartTag, Tile, Chipolo, Pebblebee, and loud pocket Apple BLE. Stayed with the path for the sit. That can be your kit or one planted in the car/bag/on you before you started. "
            "Do not dismiss it as yours; account for each MAC. A pocket iPhone that shows as Apple BLE / Continuity is included when it stays loud "
            "(peak ≥ −55 dBm, never below −70 dBm, at least two GPS stamps, heard in the last three minutes). "
            "<b>Possible tail</b> (separate amber callout): finder tags first heard after the sit started, then stayed with the path. "
            "That can mean someone started following you (their phone or tag), or a device was added during the trip. "
            "A late-join radio is only a possible tail if three extra gates also pass: "
            "the radio’s GPS trail covers about half your path, at least two-thirds of those stamps are −75 dBm or louder "
            "(same idea as Filters → Moving with you), and the last stamp is not 12 dB below the loudest. "
            "A house Find My heard only on a sidewalk arc, or that faded as you walked a loop, is omitted — not a tail. "
            "<b>Retail beacons with you</b> (amber, separate): iBeacon, Target Atrius basket, Minew, Estimote, Kontakt.io that stayed with the path. "
            "Store beacons are usually fixtures — they do not typically move with you. If one did, account for a test tag, a badge, or a short overlap with a fixture. Not a Find My tail. "
            "<b>Wearables with you</b> (amber, separate): Garmin, Fitbit, Oura that stayed with the path. Usually your own watch/ring or someone walking with you. Not typically a planted tracker. "
            "Find My and iPhone addresses rotate; each MAC is this session, not a unique ID. "
            "GPS tagging off, or a sit in one place, cannot run this test — the report says so. "
            "Car drive-bys still need three GPS stamps on that radio (unchanged). "
            "This is a heuristic, not a legal finding and not identity. "
            "You do not need Moving with you on, and you do not need to be looking at the list, "
            "for these callouts to appear — Debrief reads the last 15 minutes whether the Live display is on "
            "radar, list, timeline, or hybrid."
        ),
        P(
            "When GPS tagging is on, <b>Where you were</b> splits the path into stays (~40 m clusters) and "
            "transits. Each stay lists UTC time, dwell, lat/lon (operator phone), optional street name, "
            "and the loud radios GPS-stamped at that stay. Sitting still is one stay. A walk to a new "
            "block becomes stay → transit → stay. Coordinates are not dumped on every AP/BLE line. "
            "Settings → <b>Online place names and maps</b> (on by default) reverse-geocodes stay centroids via the "
            "system geocoder when the phone has internet, and loads map tiles on Reports → Path. Offline: a note, coords still print, Path stays the north-up trace, no error dialog. "
            "Phone GPS ≠ pole or tag location."
        ),
        P(
            "Reports → <b>AI Export</b> is a paste-ready addendum for a chat. "
            "Same window as Debrief, not the rotating log. "
            "The prompt <b>includes the onboard Debrief verbatim</b>, then a small working table: "
            "5- vs 15-minute rates, RSSI bands, RAND BLE percent, Extra attention rows, Observer notes, and finder-tag-like radios for a tracking stress-test. "
            "It does <b>not</b> paste a second Wi-Fi/BLE roster. "
            "The model is told <b>not to rewrite Debrief</b>. Do not dump MAC lists into the answer."
        ),
        P("The prompt contains:", "body_left"),
        bullets([
            "Opens with the same hobby / as-is disclaimer as first-run and Debrief and instructs the model to repeat it.",
            "Job: analyst addendum, not a second Debrief. Five output headings, then a Takeaway that adds one number the onboard takeaway does not already say.",
            "Onboard Debrief, verbatim (same sit report as Debrief text/PDF).",
            "Hear-only constraints (APs only, BLE rotation, GPS is this phone, no invented tail, no safety advice).",
            "Working table: 15- and 5-minute counts, RSSI bands, RAND percent, arrivals/min, persistent vs gone, signature-family counts, GPS path length/span, Extra attention IDs, Observer notes, finder-tag-like radios (not a tail list).",
            "Required output: disclaimer; what Debrief already established; what the numbers add; Extra attention and tracking callouts from the working table; what Hunt / a second sit would shrink; Takeaway. No safety advice.",
        ]),
        P(
            "The snapshot is the same live map as Debrief (§11.4.1): about 400 radios, unnamed BLE "
            "older than about three minutes already gone, 15-minute window (or the named sit). Share-sheet size is capped "
            "(~90k characters). Treat the paste as operationally sensitive."
        ),
        P(
            "Device detail has its own <b>AI Export</b> and <b>Share as text</b> (§5.5). Those are "
            "for the open radio only: a dump of that page, or a prompt that asks a chat to decode "
            "OUI / company / UUIDs and say what the product most likely is. They do not replace "
            "Reports → AI Export (the sit). Do not paste either into a public chat without redaction. "
            "The model is still bound by the experimental disclaimer and must not give safety advice "
            "or name a person.",
            "body_left",
        ),
        P("11.5 Signature candidates", "h2"),
        P(
            "Reports → Signature candidates is a log miner, not a sit report. It does not use "
            "the 15-minute RAM map. It concatenates the rotating parts under "
            "<font face='Courier'>files/logs/</font>, collapses rows to unique kind+MAC, "
            "runs the current catalog through SignatureEngine, and clusters unmatched radios "
            "that share a unique on-air ID. Operator-facing cards and Create signature: §5.6.4. "
            "How a custom row is saved: §9.2.1. Device detail runs the same ID quality on "
            "<b>one</b> radio (Signature family card, §5.5) using the live set plus this log."
        ),
        P(
            "What it reads from each unique radio (CSV names; JSON equivalents in §11.3):"
        ),
        bullets([
            "<b>kind, mac, name, flags (RAND / HIDDEN)</b> — identity. Randomized MACs with no other ID are skipped. House-like names (guest / xfinity / a single dictionary word) are skipped unless another unique ID remains.",
            "<b>vendor</b> — IEEE / company name. Chip-module vendors (Espressif, MediaTek, AMPAK, Qualcomm, UGSI, Realtek) and phone-house OUIs (Google, Apple, Samsung Electronics, Microsoft) are not used as an OUI-only family.",
            "<b>oui</b> — stable BSSID prefix. An OUI family needs three or more distinct MACs, or two with at least two non-house names, so one house AP’s virtual BSSIDs do not become a catalog row.",
            "<b>vendor_ie</b> — Wi-Fi vendor-IE OUIs. This is the Roku-class ID when the BSSID is randomized. Only on rows written after this column shipped. Protocol IEs are logged but not clustered.",
            "<b>mfg, raw, uuids</b> — BLE manufacturer company + first data byte, and 16-bit service UUIDs. Broad company IDs (Apple 0x004C, Google, Samsung 0x0075, Microsoft) and generic UUIDs (Fast Pair FEF3/FCF1, Apple FCB2, battery 180F, …) are not clustered.",
            "<b>fleets</b> — ignored. Re-match uses the catalog on the phone now.",
            "<b>lat, lon, rssi, timestamp</b> — not used for clustering. Frequency is distinct MACs, not packet count.",
        ]),
        P(
            "A family is two or more distinct radios sharing one of those IDs. Overlapping "
            "clusters (the same APs matching both a name glob and an OUI) merge into one card "
            "with extra rules. Cap is 20 families. Create signature drafts those rules with "
            "the radio kind set (Wi-Fi glob does not label BLE). Logging off, a cleared log, "
            "or only noise → empty list. Vendor-IE families need new packets after the column "
            "shipped; name globs and stable OUIs still work on older parts."
        ),
        P("11.6 Share, save, and clear", "h2"),
        P(
            "Reports has two export cards with the same Format chips. They are different files. "
            "<b>Sit export</b> (§5.6.2) is the roster of the selected sit (or last 15 minutes): one row per unique KIND+MAC. Logging can be off. "
            "<b>Log export</b> (§5.6.3) is the rotating session tape: every hear written while logging was on. "
            "A radio heard many times is one sit-export row and many log-export lines. "
            "Clearing the log does not delete sits; deleting a sit does not touch the log. "
            "Signature candidates mines the log, not Sit export."
        ),
        P(
            "Logging on/off and rotate size stay on Settings. "
            "The rotating file is JSON lines (one hear per line). Format Log file — CSV / JSON lines / GPX / KML / WiGLE are Share/Save projections. Both radios / Wi-Fi only / BLE only filters the export. "
            "Log GPX/KML/WiGLE are hear-point waypoints (this phone at hear-time, not a radio fix). Sit GPX/KML add the operator path as a track. "
            "Fieldwatch does not upload; you pick the target. "
            "Share uses the system share sheet. Save to storage uses the Storage Access "
            "Framework — pick internal storage or an SD card. Reset / clear log deletes rotated "
            "files on the phone. Export and save "
            "show a progress dialog; live logging is paused while a log copy runs so the UI "
            "does not lock. Clear skips queued appends and opens a fresh file. FileProvider "
            "authority is <font face='Courier'>app.fieldwatch.files</font>. There is no automatic "
            "off-device sync. Debrief (text or PDF) is the sit summary; Sit export is the sit roster file; "
            "the log file is the after-action tape. "
            "If tagging was on, log rows include operator lat/lon. New Wi-Fi log rows also include vendor_ie. "
            "Privacy mode does not mask Sit export or Log export files."
        ),
        P("11.7 Reading history", "h2"),
        bullets([
            "Sort by iso, then group by mac+kind. A new group is a new radio (or a rotated BLE address).",
            "Plot rssi against iso for approach/depart. Ignore single-sample spikes.",
            "Filter signatures != empty for a signatures-only after-action review — this survives whatever filter was on screen at the time.",
            "A WIFI row with empty name and flags HIDDEN is a hidden SSID AP; use mac/oui, not the name column.",
            "If two MACs share an OUI and appear within the same minute, check whether a cluster rule would have joined them live.",
            "lat/lon are the phone. Plot them as your track, not as the AP or tag’s coordinates. Empty cells mean tagging was off or no fix at that write.",
            "vendor_ie is pipe-separated OUIs. Empty on older rows and BLE. Group by that column (skipping 00:50:F2 / 00:0F:AC / 50:6F:9A / 8C:FD:F0) when you want product IEs the BSSID OUI missed.",
            "fleets is write-time. After a catalog change, re-match from name / oui / vendor_ie / mfg / uuids, or use Reports → Signature candidates.",
        ]),
        P("11.8 Storage", "h2"),
        P(
            "Twelve rotated files at 1 MB is about 12 MB worst case, plus config.json. "
            "Uninstalling the app deletes logs. Copy out before a Restore defaults or a "
            "reinstall. Export signatures and Export settings if you want the catalog and named radios back after a factory reset. The clock is the phone clock; set it correctly before a timed sit. "
            "Tag detections with GPS requests live GPS and network updates while scanning "
            "(stale last-known older than 30 s is ignored) and writes that fix onto the in-memory "
            "sighting (detail, Moving with you, Debrief) and onto each new log row (lat/lon). "
            "New Wi-Fi rows also store vendor_ie. "
            "If Location is not high-accuracy, the path can stay 0 m while you drive. "
            "That stamp is hear-time correlation, not a survey-grade track. "
            "A Log export with tagging on contains operator coordinates — treat the file as sensitive."
        ),
    ]

    # 12 Field playbooks
    flow += [
        PageBreak(),
        P("12. Field Playbooks", "h1"),
        P(
            "These are playbooks for a question you can ask the radios. Each one names "
            "the controls, what the Live display should look like, and what a match can support. "
            "A signature chip means a pattern matched a public broadcast. Confirm with RSSI trend, presence over time, and your eyes. "
            "Filters and Display change only the picture; the log still records. "
            "Debrief’s tracking section is the last 15 minutes in memory, whatever view or filter is on."
        ),
        P(
            "Run <b>one</b> playbook at a time until you can read the result. Stacking "
            "Moving with you + New detections only + Signatures only is allowed, but an empty "
            "list then has three explanations. Chapter 13 is radio hygiene (battery, screen, crowds). "
            "Full terms are on the Notice page. Playbooks such as “Am I being followed?” are radio "
            "exercises, not a way to decide whether you are in danger — §12.2."
        ),
        P("12.1 How to read a playbook", "h2"),
        bullets([
            "<b>Arrive first.</b> High performance, All traffic, Strength list, scan notification present. Note the loudest five APs so “home” noise has a face. If the list is already unreadable, open Display and thin the row (Subtitle None, extras off) before you start hiding radios.",
            "<b>Then narrow.</b> Apply the filter or signature set for the question. If the list goes empty, undo the last clause before assuming the area is clean.",
            "<b>Pause to inspect.</b> Open detail from the frozen row. What this looks like, flags, and payloads beat a glance at a chip.",
            "<b>Leave logging on</b> unless the plaza is melting the disk. You can always hide rows on the Live display and still have the file.",
            "<b>Write the sit.</b> Filters and the Live display view change only what you see. Debrief and AI Export see the 15-minute memory (cap ~400; §11.4.1) — including whether a tracker-like radio stayed with you. On a drive, tap Debrief more than once; Log export is the full file. Which share to tap is §12.12.",
        ]),
        P("12.2 Am I being followed?", "h2"),
        callout(
            "Not a safety test",
            "This playbook cannot tell you that you are or are not being followed in real life. "
            "It only reports loud radios that stay with your phone’s GPS path. Quiet tags, "
            "rotating Find My addresses, a phone that missed ads, and your own bag tag all "
            "break the test. If you believe you are in danger, leave and get help — do not "
            "wait on Fieldwatch. See the Notice page.",
            "warn",
        ),
        P(
            "<b>Question.</b> Is a loud radio staying with me as I walk or drive — not a house AP I meet at the door?",
            "body_left",
        ),
        P("<b>Setup.</b>", "body_left"),
        numbered([
            "Tag detections with GPS and Keep screen on are on by default. Location → high accuracy. Scanning must be running so live GPS updates can start.",
            "Walk or drive until Filters shows a path of about 50 m. If it stays 0 m, the phone is not giving a live fix. Sitting still does not grow the path. After a sit, Live display → Start over if you want a fresh follow test.",
            "Filters → Moving with you (the preset, or the switch under Radios to show). The switch clears Signatures only, Watched only, Named radios only, and class Show only so unmatched radios can co-travel.",
            "Optional: Display → Sort → New at bottom so new co-travelers append instead of jumping the list.",
            "When a row appears that is not your kit: Pause, open detail, bookmark if you want a beep on return.",
        ]),
        P("<b>What you should see.</b> Only loud radios (most GPS-stamped samples about −75 dBm or stronger) that cover a large share of <i>your</i> path in both distance and time. A tag in your bag or car will match — that is the confidence check that the filter is working. House APs that only appear when you arrive should stay hidden. Passing cars on a highway will still come and go; a radio that is actually with you should stay. The “still here” window is not a fixed 50 m circle: it grows with how fast you have been moving, and it is longer for Wi-Fi (slow scans) than for BLE, so a cup-holder tag does not blink off between advertisements. Walking still uses a tight house-length. The checklist and the speed table are §8.5.", "body_left"),
        P("<b>What it is not.</b> Not direction finding. Not the other device’s GPS — it is your phone’s fix at hear-time. Find My / Offline Finding MAC rotation will not stitch a tail that changes address every minute. A radio that is quiet, weak, or only heard at one end of the path will not qualify.", "body_left"),
        P(
            "<b>Write it down.</b> After ~45 m, Reports → Debrief (text) or Debrief (PDF). Tracking assessment is a short “did the test run.” Amber callouts list only radios that stayed with you, split by class: <b>Possible trackers with you</b> / <b>Possible tail</b> (finder tags), <b>Retail beacons with you</b> (iBeacon / Target Atrius basket / Minew / Estimote / Kontakt.io — fixtures; a Target basket you pushed will co-travel), <b>Wearables with you</b> (Garmin / Fitbit / Oura — usually own kit). House tags you passed are omitted. Plus overall distance — for the last 15 minutes in memory, whether you leave Moving with you on, hide the family, or switch to radar / timeline / hybrid / By class. Filters and view do not shrink Debrief. Carry an AirTag or iPhone: it should land in Possible trackers with you if it stayed loud on you (about −55 to −70 dBm). Find My MACs rotate, so you will see this session’s address, not one ID for the hour. A neighborhood loop is a special case: Debrief will not call a house Find My a tail unless that radio covered about half your path, stayed loud (−75 dBm on most GPS stamps), and did not fade 12 dB from its loudest. Car drive-bys still need three GPS stamps. Optional Online place names if you want streets. AI Export if you want a chat to read those callouts plus the inventory — tell it “apply §12.2; do not dismiss whole-sit radios as yours; do not list radios I only passed; do not treat a retail beacon as a Find My tail.” A longer drive: tap Debrief again at the next stop (§11.4.1) — radios that stayed with you will still be there; the first few kilometers of unnamed roadside radios will not. Not a legal finding and not identity.",
            "body_left",
        ),
        callout(
            "Own kit first",
            "If you carry an AirTag, SmartTag, Tile, or a tracker in the car, Moving with you will show it. Hide that family or treat it as a known row before you call anything else a tail.",
            "note",
        ),
        P("12.3 Has someone new entered the space?", "h2"),
        P(
            "<b>Question.</b> What arrived after I sat down — a phone, a tag, a new AP — that was not already in the room?",
            "body_left",
        ),
        P("<b>Setup.</b>", "body_left"),
        numbered([
            "Sit. High performance until the first Wi-Fi batch lands (header: Wi-Fi next Ns, then a jump in the AP count).",
            "Filters → New detections only. The Live display shows New only · learning sitting Wi-Fi, then New only · N hidden.",
            "When the room is the baseline, Mark seen (bar above the tabs). Pause then Mark seen absorbs the frozen picture, not radios that arrived after the freeze.",
            "Brief hold 30 or 60 s so a slow advertiser does not pop in and out.",
            "Reset seen if you change rooms and want that space to count as new. Clearing the log does not reset already-seen.",
        ]),
        P("<b>What you should see.</b> Only radios that were not in the already-seen set. BLE can show as new during the Wi-Fi learning window. After Mark seen, the next new row is the next arrival. A watchlist alert fires only if that row would actually appear on the Live display.", "body_left"),
        P("<b>What it is not.</b> Randomized BLE addresses look new on every rotation — an unnamed LE that “just arrived” may be a phone that changed MAC. Sitting Wi-Fi is hidden through the next scan on purpose. This filter does not identify who walked in; it tells you a radio the session had not already absorbed is now on the air.", "body_left"),
        P(
            "<b>Write it down.</b> New detections only does not shrink Debrief or AI Export — those ignore the Live display filter. Use Debrief persistence / first-seen, or the AI Export 5- vs 15-minute first-seen counts. Log export if you need every written row later.",
            "body_left",
        ),
        P("12.4 Find only this family", "h2"),
        P(
            "<b>Question.</b> I only care about trackers, or only cameras, or only one custom signature.",
            "body_left",
        ),
        P("<b>Setup.</b>", "body_left"),
        numbered([
            "Filters → Show only plus the class chips (Finder tags, Cameras, Drones, Surveillance, Access control, Pentest, …). Matching stays on; the Live display just thins. Save current as… if you want that sit as a chip.",
            "If a family is local false positives, hide it on Filters (Hide these for a class, or Hide selected for one family), or mute noisy rules inside the editor (LiteOn OUIs on Flock — §7.6).",
            "Bookmark the signature if you want a beep when a new match appears.",
            "Radar or Hybrid for “how close”; list for triage; detail for the decode.",
        ]),
        P("<b>What you should see.</b> Unmatched consumer noise drops away. Chips on the remaining rows are the families still on the Live display. If the list is empty, nothing matching is in earshot — or you hid that family on Filters. Matching still labels; it does not go quiet.", "body_left"),
        P("<b>What it is not.</b> Signatures only does not search harder. It hides unmatched radios. Stock catalog rows (Ring, Nest, Hikvision, …) are mostly names — apartment cameras and coincidental SSIDs will label. Hide a family on Filters if that sit is noise. See §7.6.1 before treating a camera chip as a pole.", "body_left"),
        P(
            "<b>Write it down.</b> Debrief “Signature hits” and camera/tracker flags. Sit AI Export gives family counts and Extra attention IDs, not a second signed roster. For one radio, use detail AI Export. Do not paste into a public chat without redaction.",
            "body_left",
        ),
        P("12.5 Get rid of the noise, keep the field", "h2"),
        P(
            "<b>Question.</b> I want the neighborhood, minus the families I already know are clutter.",
            "body_left",
        ),
        P("<b>Setup.</b>", "body_left"),
        numbered([
            "Start All traffic so unmatched radios stay.",
            "Filters → Hide these, then the Finder tags chip. That is the whole class. To hide only your bag AirTag, use Hide selected signatures and turn that one row on.",
            "Useful combo: Signatures only + Hide these Finder tags — named hits, minus bag-tag clutter, cameras still show.",
        ]),
        P("<b>What you should see.</b> The rest of the field stays. Hidden classes drop off the Live display until you turn Hide these off. Class picks stay if you switch modes; Reset filter is what forgets them.", "body_left"),
        P("<b>What it is not.</b> Hiding Finder tags does not mean no AirTags are present — they are still matched and logged. Watchlist will not beep for a row the current Live display filter would keep off the list.", "body_left"),
        P(
            "<b>Write it down.</b> Debrief and AI Export still list hidden families if they were in the live map. That is the point of the log: the Live display can be quiet; the file is not. Use Log export when you later need the clutter you hid.",
            "body_left",
        ),
        P("12.6 Is this my own tag?", "h2"),
        P(
            "Bag-check, not accusation. Turn on Moving with you after ~50 m. If the only tracker-like row is the one you put in the bag or the car, the filter is doing its job. Bookmark that MAC or hide the family so it stops competing with a real candidate.",
            "body_left",
        ),
        P(
            "If you do <b>not</b> carry a tag and a loud tracker-like radio still covers the whole path: Pause, detail, What this looks like, RSSI trend. Share as text or detail AI Export if you want a second read on <i>that</i> radio. Then Reports → Debrief (text or PDF) — “Possible trackers with you” (yours or planted before you started — account for it) vs “Possible tail” (first heard after this sit started). Those callouts are the last 15 minutes in memory even if Moving with you is off and even if the Live display is on another view. Sit-level AI Export if you want a chat to stress-test those callouts with rates and mixes (it embeds Debrief; tell it not to rewrite). All of these are heuristics. Find My rotation still will not stitch.",
            "body_left",
        ),
        P("12.7 Camera / ALPR sit", "h2"),
        callout(
            "No chip does not mean no camera",
            "Quiet and cellular-first poles are often invisible to this phone. Do not use a "
            "blank Live display to decide that a location is safe to photograph, protest, or "
            "drive. Experimental observation only. See §7.6.1 and the Notice page.",
            "warn",
        ),
        P("<b>Setup.</b> Preset Surveillance (or enable Flock / Raven / the camera names you care about, then Signatures only). High performance. Walk the block. Roadside / public camera + ALPR rows (Flock, Penguin, Pigvision, FS Ext Battery, Genetec AutoVu, Rekor, Motorola Vigilant, Verkada, Avigilon, Axis, Hikvision, Dahua, Hanwha Wisenet, Uniview, Rhombus) beep and get Extra attention “!” — the card says what that family is used for. When a chip appears, look with your eyes, then write what you saw — not what the chip said.", "body_left"),
        P(
            "<b>Honest limits.</b> Newer poles are often cellular-first and RF-quiet on Wi-Fi/BLE; no chip does not mean no camera. Fieldwatch cannot hear LTE, cannot see associated clients, and cannot promiscuously capture. IEEE B4:1E:52 plus a Flock-* SSID is high confidence; LiteOn / Espressif / Raspberry Pi OUIs are low unless a name or UUID corroborates. Phone GPS is where <i>you</i> were, not where the pole is. Full limits: §7.6.1.",
            "body_left",
        ),
        P(
            "<b>Write it down.</b> Look first, then Debrief (PDF is handy to file). Signature hits and camera flags are cues, not a pole ID. AI Export can list every camera-like row with payload decode — tell the model “§7.6.1; quiet poles exist; do not place a camera at the GPS pin.” Turn Online place names off if you do not want streets of <i>your</i> path in the paste.",
            "body_left",
        ),
        P("12.8 Hunting a tracker", "h2"),
        callout(
            "No guarantee of a find",
            "Tags advertise slowly, rotate addresses, and may be outside what this handset "
            "hears. Absence of a row is not proof there is no tracker. Hunt is relative "
            "loudness, not meters and not a compass. Do not use this to decide personal "
            "safety. See the Notice page.",
            "warn",
        ),
        P(
            "<b>Find the row first.</b> Filters → BLE only, Show only Finder tags (unmatched stay hidden and Signatures only is dimmed). "
            "Battery saver is usually enough — tags advertise slowly. Brief hold 60 s and Stale "
            "after 90–120 s if they flicker. Timeline. Bookmark the family. Hide selected on your "
            "own tags if they drown the list. AirTag / Find My addresses rotate: you will see a "
            "new MAC, not one identity for the hour. A loud row that stays with you on a walk "
            "belongs in §12.2, not here — Debrief still writes that last-15-minute co-travel "
            "whether Hunt or a Finder-tags filter is on the screen. When you have <b>one</b> BLE candidate, open its detail → "
            "<b>Hunt</b> (Wi-Fi has no Hunt button). Screen stays on. Hold the phone the same way "
            "the whole time. Reset this hunt after you change grip.",
            "body_left",
        ),
        P(
            "<b>If you do not know which radio.</b> Filters → Show only Finder tags. Strength list, High performance. Hide selected on kit you already own "
            "(keys, bag AirTag) or those will always win the top row. Then <i>place the phone</i> "
            "at spots a tag could hide — you are using the handset as a sniffer probe, not walking "
            "the street. On a car: each wheel well, inside the bumper, under the rocker, hitch, "
            "spare, cabin (console, under seats, visors). Dwell 10–20 s at each spot so a slow "
            "advertiser can speak; tags do not ping every second. If a tracker-family row jumps "
            "to the top or RSSI climbs a lot versus the last spot, that is the candidate. Open "
            "that row → Hunt. Repeat the same grid with Hunt’s Closer/Further, then the body-block "
            "turn if you still need a heading. If Hunt goes Gone, the MAC rotated — back to the Live display, "
            "pick the new top tracker row, Hunt again. A quiet sweep is not proof the car is clean: "
            "the tag may be asleep, out of this phone’s earshot, or not in the tracker catalog. "
            "The car’s metal will shadow; a peak at one wheel can be a bounce. Same grid works on "
            "a bag, coat, or stroller: park the phone in each pocket/compartment in turn.",
            "body_left",
        ),
        P(
            "<b>Walk it down.</b> Slow steps, one direction at a time. Watch Closer / Further / "
            "About the same and the loudest-this-hunt number — not the Live display radar angle (that is a "
            "MAC hash, not north). If Closer, keep that heading. If Further, stop, turn, try "
            "another way. About the same means wait or change heading; do not convert dBm to feet. "
            "Very Close (about −45 dBm or louder) means look around — usually in-hand, pocket, or the same bag; still not meters. "
            "Quiet can be a wall, not the wrong way. If Gone from the Live display, the address likely rotated "
            "— back to the Live display, pick the new row if it is still the same family, Hunt again.",
            "body_left",
        ),
        P(
            "<b>Body-block for a possible bearing.</b> The phone has no direction finding. Your "
            "torso damps BLE from one side, the same idea as holding an antenna close to the body "
            "and turning for a peak. Stand still. Hold the phone against the center of your chest "
            "(or the same pocket every time) so your body shadows one hemisphere. Turn slowly "
            "through a full circle. Watch the big RSSI and loudest-this-hunt. If the signal gets "
            "stronger on one heading, that is a <i>candidate</i> bearing — often the direction of "
            "least body in the path, i.e. roughly toward the radio. Repeat the circle. Treat it as "
            "tens of degrees, not a compass rose. Then Reset this hunt and walk that heading using "
            "Closer/Further. Reflections and metal can fake a peak (see structures below).",
            "body_left",
        ),
        P(
            "<b>Structures lie.</b> A wall, floor, or door can drop RSSI 10–20 dB with no change "
            "in true range. Metal (cars, cabinets, elevators, foil-backed insulation) reflects and "
            "shadows — the loudest body-block heading can be a bounce off a van. People and water "
            "absorb. Concrete and rebar scatter. Glass is kinder than brick. Two floors of a house "
            "can fake Further. Covering the antenna with a fist is another wall. Do not treat one "
            "peak as a map pin; walk, turn, and see whether Closer survives the next opening. "
            "More on the Hunt page itself: §12.13.",
            "body_left",
        ),
        P(
            "<b>Write it down.</b> Detail → Share as text or AI Export for that radio. If the "
            "question was a planted tag that stayed with you, Reports → Debrief still has the "
            "last-15-minute tracking assessment (§12.2) — Hunt, Finder-tags filter, and radar vs "
            "list do not change that report. Sit-level AI Export lists Find My / Fast Pair flags. "
            "Log export for a later spreadsheet of MACs (they will rotate).",
            "body_left",
        ),
        P("12.9 Follow an SSID or OUI lead", "h2"),
        P(
            "<b>Setup.</b> Filters → Name / MAC contains, or OUI / vendor contains. AND. Both radios on. Do <b>not</b> enable Signatures only or you will hide an unmatched but relevant AP. Strength list. Open detail → Create signature from device if it keeps appearing.",
            "body_left",
        ),
        P(
            "A BSSID that repeats across visits is a fixture. A randomized BLE MAC that repeats is probably the same session, not a long-lived identity. Debrief Wi-Fi inventory and AI Export’s full AP list are the sit snapshot; Log export is what you compare on the next visit.",
            "body_left",
        ),
        P("12.10 Walk, sit, or drive", "h2"),
        table(
            ["You are…", "Use"],
            [
                ["Sweeping a new block", "All traffic, High performance, Strength list or Hybrid. Learn the noise before you filter."],
                ["Walking — who is with me", "§12.2 Moving with you on the Live display; Debrief still writes the last-15-minute tracking line even if you leave that filter or change view. Need GPS tagging and ~50 m of path. Walking uses a tight ~75 m still-here window (§8.5)."],
                ["Driving — who is with me", "Same filter. “Still here” grows with speed so a car tag does not flash between advertisements; passing roadside radios still fail the trail-moved gates. §8.5, §12.2."],
                ["Sitting a room — who just arrived", "§12.3 New detections only. Mark seen after the room is the baseline. Reset seen when you change rooms."],
                ["ATAK overlay (Remote ID / Extra attention)", "§5.8 configure, §12.15 sit. Settings → TAK / CoT feed. Extra attention + Payload location. Same Wi-Fi LAN. Privacy mode off."],
                ["Long sit / parked vehicle", "Balanced or Saver. Timeline. RSSI floor −80 if unreadable. Logging on. Watchlist, not a stare."],
                ["Drive, then arrive home", "Moving with you is BLE only — house APs stay off. If a bag tag still does not show, the path was too short or GPS only stamped at the destination."],
                ["Long drive (several km)", "Do not wait for one Debrief at the end. Tap Debrief every 10–15 min or at stops and keep the shares (§11.4.1). City RF can fill the ~400 live-set cap in a few kilometers. Radios moving with you stay in each report; roadside unnamed BLE will not. Log export is the hour-long file."],
                ["List too fast to tap", "Pause. Then detail. Resume. Or Display → Subtitle None so more of the list fits without Pause."],
                ["Fit the row to the job", "§5.3. Display is look (Title/Subtitle, extras). Filters are who. Plaza: Subtitle None. Copy MAC: Title → MAC. Channel sit: Frequency on."],
                ["Write the sit", "§12.12. Debrief for a sit report; Sit export for that window’s roster; AI Export for a chat prompt; Log export for the session tape. Long drive: Debrief each stop (§11.4.1)."],
                ["Walk toward one BLE / a tracker chip", "§12.8 (tracker) or §12.13 (Hunt page). Walk for Closer/Further; body-block turn for a candidate bearing. Not meters."],
                ["Don’t know which tracker", "§12.8. Filters → Show only Finder tags, hide own kit, dwell the phone at each hide (wheels, bumpers, cabin). Top row → Hunt. Repeat. Not a clean bill."],
                ["A “!” / card reader / pentest kit", "§12.14. Extra attention on Hobby BLE serial (not a skimmer detector) and Pineapple / Flipper / Pwnagotchi / Marauder / Porkchop. Open detail, look with your eyes. A miss is not a clean bill."],
            ],
            [1.8 * inch, 4.7 * inch],
        ),
        P("12.11 The list is unreadable", "h2"),
        P(
            "A busy list is not the same as the wrong radios. Show less on each row before you "
            "start hiding devices. Display changes how much of each radio you see; Filters change "
            "who is on the list. Logging is not affected either way."
        ),
        bullets([
            "Pause if you cannot tap a row. The radios keep scanning.",
            "Live display → Display (tune). Set Subtitle to None for one identity line and more rows on screen; rand/gone move onto the title. Turn off RSSI bars, Frequency, first/last, and (if chips are noise) Signature names. Prefer Strength list over Hybrid in a plaza.",
            "Title already defaults to MAC. If type guesses (“Apple, Inc. · …”) crowd the subtitle, set Subtitle to Advertised name or None.",
            "Still too many radios: then Filters. Raise the RSSI floor (preset Strong signal, −70 dBm) or the slider −80. Signatures only, Hide these Finder tags, or Hide selected on one family.",
            "Do not stack every filter. Undo the last clause if the list goes empty.",
            "Turn logging off only if you do not need the file. Crowd cap is 400; unnamed BLE older than ~3 minutes may already be gone from the Live display but still in the log if it was written.",
        ]),
        P("12.12 After the sit — reports and AI Export", "h2"),
        P(
            "Debrief, sit-level AI Export, Signature candidates, Log export, Save, and Reset / clear log are on the <b>Reports</b> tab. "
            "GPS tagging, Online place names, and logging on/off stay on Settings. "
            "Two more shares live on the <b>device-detail</b> page (§5.5): Share as text and AI Export for "
            "<i>that radio only</i>. Sit-level Debrief and AI Export are built from the last "
            "15 minutes in the live map (about 400 radios; hard ceiling 900). Live view and Filters do not change it. "
            "How that memory fills and drops on a drive is §11.4.1: tap Debrief more than once; radios moving with you stay. "
            "A planted-tracker co-travel line is in that sit report whenever tagging was on and you moved. "
            "Signature candidates reads the rotating log for unmatched families (§5.6.4, §11.5). "
            "Radios keep scanning while the share sheet opens. Treat every share as operationally sensitive."
        ),
        table(
            ["Share", "What it is", "Use it when"],
            [
                ["Debrief (text)", "Sit report, plain text. Distance, Where you were, Observer notes, tracking assessment (last 15 min, ignores Live display view/filter), environment, inventories, actions, takeaway. Custom names. Reports tab.", "After a walk: was something with me? Notes, Signal/SMS, a logbook. Same words as the PDF."],
                ["Debrief (PDF)", "Same sit report, letter-size typeset (FIELDWATCH header, numbered sections, Observer notes after Where you were, amber Possible trackers with you / Possible tail callouts when those lists are non-empty, amber Extra attention callout per special note, operator-path figure, takeaway box). Tracking does not care which Live display view or filter was on.", "Hand to someone, file the sit, print. Easier to read than the text dump."],
                ["Compare (text / PDF)", "Presence-only this sit vs a second saved sit. Kind + MAC. Observer notes after Windows. Custom names. Path overlay on the PDF when both walks have GPS.", "Two rooms, two days, or last 15 minutes vs a named sit (RAM ~400 vs sit 3000)."],
                ["AI Export (Reports)", "Sit-level analyst <i>prompt</i>: onboard Debrief verbatim, plus a compact working table (5/15-minute rates, RSSI bands, Extra attention, Observer notes, finder-tag IDs). Asks for an addendum — not a rewrite, not a second roster.", "Paste into a chat when you want numbers and a stress-test of tracking callouts. Not a legal memo."],
                ["Signature candidates", "Log miner on Reports. Re-matches the rotating log, lists unmatched families that share a unique on-air ID (2+ radios). Create signature is a draft with the shared rule, no MAC pin. Save returns to the list and re-runs it. Offline.", "After a sit with logging on: recurring unmatched globs / vendor IEs / OUIs worth a custom signature. Not every unknown radio. §5.6.4, §9.2.1, §11.5."],
                ["Log export", "Reports → Log export. Format: CSV, JSON lines, GPX, KML, WiGLE. Radios: Both / Wi-Fi / BLE. Rotating file is JSON lines. Map pins are this phone. Fieldwatch does not upload.", "After-action file, spreadsheet, Google Earth, or a WiGLE upload you start yourself."],
                ["Sit export", "Reports → Sit export. Same Format chips as Log export. One row per unique radio in the selected sit (or last 15 minutes). Logging can be off. GPX/KML include the operator path. Not the rotating log.", "Take this walk’s roster to a spreadsheet or Google Earth without the whole day’s log."],
                ["Share as text (detail)", "Plain dump of the open radio’s detail page.", "Notes, a ticket, or to keep one MAC/payload without the whole sit."],
                ["AI Export (detail)", "Prompt about <b>one</b> radio: dump plus registry/format decode job. Same disclaimer as sit-level AI Export.", "“What is this AP / tag / chip?” Do not use it as a following test — that is Reports → Debrief after you move."],
                ["Hunt (detail, BLE)", "Closer/further needle, rings around YOU, hunt sparkline. Very Close at about −45 dBm or louder. Beep/Vibrate geiger tick at the bottom. Optional body-block heading. Structures shadow and reflect.", "Walk toward one BLE in a room, bag, or car. Not meters. Not a following test. §12.13."],
            ],
            [1.45 * inch, 2.3 * inch, 2.75 * inch],
        ),
        P("<b>How they pair with the playbooks</b>", "body_left"),
        bullets([
            "<b>§12.2 Followed?</b> Tag GPS, walk about 50 m, then Debrief. Read Possible trackers with you and Possible tail (the last 15 minutes in memory — Live display view and Moving with you do not change it). On a longer walk or drive, tap Debrief again at the next stop (§11.4.1) — a radio that stayed with you will still be there. AI Export if you want a chat to stress-test those callouts with rates and mixes — tell it not to rewrite Debrief, not to dismiss whole-sit radios as yours, and not to list radios you only passed.",
            "<b>§12.3 New in the room?</b> Debrief persistence / first-seen, or AI Export 5- vs 15-minute first-seen counts. The New detections filter does not change the report.",
            "<b>§12.4 / §12.7 Families and cameras.</b> Debrief signature hits. Sit AI Export for Extra attention IDs and family counts; detail AI Export for one radio’s payload. Keep §7.6.1 in the prompt for camera sits.",
            "<b>§12.5 Hidden clutter.</b> Still in Debrief/AI Export/log. The Live display was quiet; the file was not.",
            "<b>§12.6 Own tag?</b> Debrief “Possible trackers with you” (yours or planted — account for it) vs “Possible tail.” Same verdicts in AI Export.",
            "<b>§12.8 Tracker hunt.</b> Filters → Show only Finder tags. If you do not know which row: dwell the phone at hide spots (car wheels, bumpers, cabin) until one pops to the top, then Hunt. Walk / body-block to narrow. Structures shadow. Not a clean bill.",
            "<b>§12.9 SSID/OUI lead.</b> Debrief Wi-Fi inventory for the sit. Reports → Compare sits for this window vs a second saved sit (kind + MAC). Log export still compares BSSIDs if you want the file. Signature candidates if the same unmatched glob or vendor IE keeps showing up.",
            "<b>Catalog gap?</b> Open an unmatched radio: Signature family on detail is the one-radio check (Strong / Possible / This radio only). Logging on, then Reports → Signature candidates for the sit-wide list. Recurring unmatched name globs / vendor IEs / stable OUIs. Create signature, Save, then the Live display should label the next hear. Not a house SSID and not a chip-module OUI.",
            "<b>§12.14 Extra attention / pentest kit / card-reader caution.</b> Debrief Extra attention section and PDF amber callouts. Detail Share / AI Export quote EXTRA ATTENTION. Tell a chat “pattern, not a skimmer detector, not proof of an attack.”",
        ]),
        P(
            "<b>Online place names and maps</b> (on by default) applies to Debrief, AI Export, and Path tiles. "
            "Offline: a note, no error, no streets. Distance traveled does not need internet. "
            "Leave it off if you do not want addresses in a chat paste.",
            "body_left",
        ),
        P(
            "Compare OUIs and names across visits in a spreadsheet. A repeating BSSID is a fixture. "
            "Treat Debrief “possible tail” language — and anything an AI writes from AI Export — "
            "as a cue to look again, not as a conclusion.",
            "body_left",
        ),
        P("12.13 Hunt — walk toward a BLE radio", "h2"),
        callout(
            "Relative loudness, not a tracker",
            "Hunt is a fox-hunt needle for one BLE advertiser. It does not measure meters, "
            "does not give a compass bearing, and does not identify a person. Randomized "
            "addresses can vanish mid-hunt. Absence of Closer is not proof the radio is gone. "
            "Do not use this page to decide personal safety. See the Notice page.",
            "warn",
        ),
        P(
            "<b>When to use it.</b> You already have <b>one</b> BLE row you care about "
            "(a tag chip, a loud unnamed LE, a speaker). Open detail → Hunt. Wi-Fi APs have no "
            "Hunt button: the OS only batches them about every 30 s (about 8 s with Faster Wi-Fi AP scans in effect — still not Hunt). Room, bag, car, one floor "
            "are the realistic envelope. A parking garage through walls will lie.",
            "body_left",
        ),
        P("<b>Setup.</b> High performance. Hold the phone the same way the whole hunt (same hand, same tilt). Screen stays on on this page. Reset this hunt after you change grip or after a body-block turn so the walk is not compared to the spin. Logging and GPS tagging can stay on; in a dense plaza they add work — turn them off if the needle feels late.", "body_left"),
        figure_wrap(
            "fig-hunt.png",
            "Fig. 5 (repeated) — Hunt.",
            "<b>Walk.</b> Slow steps. Watch Closer / Further / About the same (~3 dB over a few seconds), "
            "the rings around YOU (in = louder, out = quieter), and the loudest-this-hunt number. "
            "Beep / Vibrate sit under Reset and Back (off until you turn them on): a geiger tick on last-heard RSSI — "
            "faster when louder, silent when Quiet or Gone. Raise media volume if you chose Beep and hear nothing. "
            "Very Close (about −45 dBm or louder) means look here — pocket, bag, in-hand — not a tape measure. "
            "Pause when Quiet; it may be a wall, not the wrong way. If Gone from the Live display, the MAC likely rotated or left — "
            "back to the Live display, pick the new row if it is the same family, start Hunt again. Do not convert dBm to feet.",
        ),
        P(
            "<b>Body-block heading (optional).</b> The handset has no DF. Your torso is a lossy "
            "shield, the same trick as holding a whip close to the body and turning for a peak. "
            "Stand still. Hold the phone against the center of your chest (or the same pocket "
            "every time). Turn slowly in place through a full circle. Watch loudest-this-hunt "
            "and the big RSSI — not the Live display radar angle (that is a MAC hash, not north). The "
            "heading where it peaked is often the direction of least body in the path, i.e. "
            "roughly toward the radio. Reflections, metal, and a second source can fake a peak. "
            "Repeat the circle. Treat the heading as tens of degrees, not a compass rose. Then "
            "Reset this hunt and walk that way using Closer/Further. This is not UWB Precision "
            "Finding and not a bearing you should write as a fact.",
            "body_left",
        ),
        P(
            "<b>Structures change the needle.</b> Hunt reads one path to the radio, not a vacuum. "
            "A wall, floor, or door can drop RSSI 10–20 dB with no change in true range — Quiet "
            "or Further may mean you walked behind masonry, not away from the tag. Metal is worse: "
            "car bodies, filing cabinets, appliances, foil-backed insulation, and elevator cars "
            "reflect and shadow. The loudest heading on a body-block turn can be a bounce off a "
            "van, not the source. Water and people absorb (a crowd between you and a tag looks "
            "like distance). Concrete and rebar (garages, basements) scatter 2.4 GHz; a peak on "
            "the sparkline at a doorway is often a slot through the structure. Glass is kinder "
            "than brick. Two floors of a house are enough to fake Further. Hold the same grip: "
            "covering the antenna with a fist is another “wall.” Do not treat a single peak as "
            "a map pin; walk, turn, and see whether Closer survives the next opening.",
            "body_left",
        ),
        P(
            "<b>Must not conclude.</b> Distance. A person. That you are safe because Hunt went Quiet. "
            "That two Hunts on rotating MACs were the same tag unless the payload still matches. "
            "That the loudest heading is the true bearing when you are next to metal or a wall.",
            "body_left",
        ),
        P(
            "<b>Write it down.</b> Detail → Share as text or AI Export for that radio. If the "
            "question was following you, Reports → Debrief still has the last-15-minute tracking "
            "assessment (§12.2) even from Hunt, and even if the Live display is on radar. Not for locating "
            "a bag tag in a room.",
            "body_left",
        ),
        P("12.14 Extra attention — card readers and pentest kit", "h2"),
        callout(
            "Not a skimmer detector, not proof of an attack",
            "A “!” on the Live display means a matched signature has Extra attention filled. That is a "
            "pattern on a public broadcast, not a person, not a planted overlay, and not a "
            "finding that a crime is in progress. The same hobby BLE modules show up on "
            "printers, cars, and DIY. Pentest firmware can be renamed or quiet. A miss is not "
            "a clean bill. Look with your eyes. Fieldwatch does not connect and does not try PINs. "
            "See the Notice page.",
            "warn",
        ),
        P(
            "<b>Question.</b> Is there a radio here that the catalog tagged for Extra attention — "
            "cheap BLE serial names next to a card reader, or a default pentest AP / Flipper / "
            "handshake collector — and what should I actually do with that mark?",
            "body_left",
        ),
        P("<b>Setup.</b>", "body_left"),
        numbered([
            "Signatures tab: Hobby BLE serial, Hak5 Pineapple, Flipper Zero, Pwnagotchi, Marauder / Deauther, and Porkchop have Extra attention filled. Hide a row on Filters if that name is local noise.",
            "Live display: All traffic, Strength list (or Hybrid). A “!” on the row is the Extra attention mark — not the editor Notes field.",
            "Optional: Filters → Signatures only after you have seen the room, if you only want those families. Do not start filtered or you will miss unmatched radios.",
            "Tap the “!” row. Read Extra attention on detail. Bookmark if you want a beep when that MAC returns. BLE only: Hunt if you need closer/further (§12.13).",
        ]),
        P(
            "<b>What you should see.</b> The “!” only appears when a <i>matched</i> signature has Extra attention text. Detail shows the full caution. Debrief and AI Export quote it. The PDF draws an amber callout per hit. Empty Extra attention = no mark.",
            "body_left",
        ),
        table(
            ["Family", "What Fieldwatch can hear", "Honest limit"],
            [
                ["Hobby BLE serial", "BLE names HMSoft / HM-10 / CC41 / AT-09 / JDY / BT05 / ESP32 BLE. Extra attention: same boards have been used in some pump/ATM overlays.", "Not Classic HC-05/HC-06 (Fieldwatch does not see Classic). Same modules on printers, cars, DIY. Loud next to a card reader: look with your eyes. Not proof of a skimmer. A miss is not clean (name changed, Classic, or cellular)."],
                ["Hak5 Pineapple", "Setup SSID Pineapple_XXXX / Hak5 / WiFi Pineapple.", "Admin/management AP. Not Alfa OUI 00:C0:CA. PineAP clones look like ordinary café SSIDs. Renamed or clone-only: miss."],
                ["Flipper Zero", "OUI 0C:FA:22; BLE name Flipper*.", "Newer units use that IEEE OUI. Custom firmware can change name and MAC. Bluetooth off: miss. Not proof of an attack."],
                ["Pwnagotchi", "Classic BSSID de:ad:be:ef:de:ad; name pwnagotchi.", "Handshake-collector beacon. Custom MAC/name: miss."],
                ["Marauder / Deauther", "Default names MarauderAP / Marauder / Deauther.", "ESP32 Marauder or Spacehuhn-style defaults. Same boards are DIY. Renamed: miss."],
                ["Porkchop", "SSID/name PORKCHOP; BACON fake-AP vendor IE 50:52:4B.", "M5PORKCHOP Cardputer or CYD port. Not Espressif OUI. BLE spam (Apple/Android lookalikes) is not matched. Passive-only or no AP: miss."],
            ],
            [1.35 * inch, 2.45 * inch, 2.7 * inch],
        ),
        P(
            "<b>What it is not.</b> Not a card-skimmer scanner. Not a pentest-kit detector that sees USB-only Hak5, renamed firmware, or quiet radios. Body-worn / glasses / recording-wearable Extra attention (Axon, WatchGuard Video, Ray-Ban / Meta, Snap Spectacles, Fieldy, Plaud Note) is the same “!” mark — those families are §9.5 / the appendix, not this playbook. Fieldwatch never connects. Do not treat a “!” as identity or as a reason to touch someone else’s gear.",
            "body_left",
        ),
        P(
            "<b>Write it down.</b> Detail → Share as text or AI Export for that one radio (the dump starts with EXTRA ATTENTION). Sit: Reports → Debrief (PDF if you want the amber callouts) or AI Export — tell the model “§12.14; Extra attention is a pattern, not a skimmer detector, not proof of an attack.” Filters do not shrink Debrief. Do not paste into a public chat without redaction.",
            "body_left",
        ),
        P("12.15 Overlay on ATAK (TAK / CoT)", "h2"),
        callout(
            "Coordinates on the LAN",
            "This sit puts full MACs and coordinates onto whatever is listening at the host:port "
            "you set. Privacy mode must be off or Fieldwatch will not send. Multicast stays on this "
            "Wi-Fi LAN. Not a safety picture, not DF, not a Remote ID interceptor. §5.8.",
            "warn",
        ),
        P(
            "<b>Question.</b> Can I see Extra attention radios and in-flight Remote ID on the "
            "same map as the rest of the team, without building a Remote ID plugin or teaching "
            "ATAK a Fieldwatch format?",
            "body_left",
        ),
        P("<b>Setup.</b>", "body_left"),
        numbered([
            "Phone and ATAK (or WinTAK / iTAK) on the same Wi-Fi. Confirm the AP does not isolate clients. If multicast never arrives, you will switch Host to the ATAK device’s IPv4.",
            "ATAK already listens to UDP 239.2.3.1:6969 for SA. No Fieldwatch plugin. This feed is UDP; it does not log into a TAK server.",
            "Fieldwatch → Settings → Privacy mode Off. Tag detections with GPS On (high-accuracy Location) if you want heard-here pins; Remote ID advertised position does not need it.",
            "Settings → TAK / CoT feed On. Destination: This phone for ATAK CIV on this handset, LAN multicast for other ATAKs on this Wi-Fi. Extra attention On, Payload location On, Watchlist Off, All signatures Off. Confirm Feed status under the host fields shows a send, not an error.",
            "Live display: All traffic, High performance. Do not filter to Drones only if you also want body-cam / glasses — the feed ignores Live display filters, but you still need those radios to match.",
        ]),
        P(
            "<b>What you should see.</b> Within a few seconds of a qualifying hear, ATAK plots "
            "a marker. Extra attention (Axon, glasses, Flipper, Pineapple, …) sits at <i>your</i> "
            "GPS at the loudest hear — walk toward it and the pin updates; walk away and it stays. "
            "Remote ID Location sits at the <i>advertised</i> aircraft lat/lon (Yellow UAV); the next Basic ID "
            "packet does not clear it and, once UAS ID is heard, that aircraft is one marker that moves. "
            "A decoded pilot location is a second Orange pin. Heard-here callsigns end in (here). "
            "Tap a marker for remarks (name, MAC, RSSI, signatures). "
            "A radio that leaves is dropped on ATAK instead of sitting two minutes.",
            "body_left",
        ),
        table(
            ["If this…", "Then…"],
            [
                ["Nothing on the map", "Privacy mode, master off, wrong LAN, or no Extra attention / Remote ID on the air yet. Open the Live display and confirm a “!” or a Remote ID chip. Try unicast to the ATAK IP if multicast is filtered."],
                ["Pins sit on me, not the drone", "That radio has no advertised lat/lon (or Payload location is off). Heard-here is working. Remote ID needs a Location message (protocol 2) on BLE FFFA."],
                ["Heard-here pin stayed where I was louder", "Expected. Heard-here holds closest approach (louder RSSI), not the last hear. A keep-alive every ~10 s refreshes the same lat/lon. This is not DF."],
                ["Drone pin is kilometers away", "Expected for advertised position. Fieldwatch did not DF it. The aircraft encoded that WGS84."],
                ["Café APs filled the map", "All signatures is on. Turn it off. Extra attention + Payload location is the field default."],
                ["Marker vanished after ~2 min", "The radio left earshot, scanning stopped, or Fieldwatch sent a gone event. Start scanning again if you still want it."],
                ["I want one bag tag on the map", "Watchlist On, bookmark that MAC (Alert on). Extra attention can stay on. Do not use All signatures."],
                ["Custom sensor with lat/lon in the ad", "Decode fields: ids latitude and longitude (scale as the spec). Payload location On. No TAK checkbox. §5.8.3, §9.6."],
            ],
            [2.0 * inch, 4.5 * inch],
        ),
        P(
            "<b>What it is not.</b> Not a team SA substitute (Fieldwatch is not your self-marker). "
            "Not Wi-Fi NAN Remote ID. Not encrypted ads. Not a range ring. Filters, Pause, and "
            "Display do not change the feed. Stop on the scan notification stops sending.",
            "body_left",
        ),
        P(
            "<b>Write it down.</b> ATAK is the live overlay. Fieldwatch still has Debrief / Log export "
            "for the sit file. The CoT remarks are not a report. If you share a screen of the "
            "map, remember the MAC is full — Privacy mode was off to publish.",
            "body_left",
        ),
    ]

    # 13 Field hygiene
    flow += [
        PageBreak(),
        P("13. Field Hygiene", "h1"),
        P(
            "Chapter 12 is the playbook. This chapter is how to keep the phone collecting while you run those strategies."
        ),
        P("13.1 Detection quality", "h2"),
        bullets([
            "Keep the phone out of a Faraday-ish bag and off a metal dashboard.",
            "Body blocking is real: a BLE tag behind you will look 10–20 dB weaker.",
            "5 GHz APs have shorter range; losing them while 2.4 GHz remains is geometry, not “they turned off.”",
            "If Wi-Fi counts freeze, the OS is throttling or Location dropped. Settings shows the throttle hint on API 30+.",
            "Airplane mode with Wi-Fi and Bluetooth manually re-enabled can reduce cellular noise but is not required and may confuse some OEMs.",
        ]),
        P("13.2 Battery, screen, and background", "h2"),
        P(
            "Two jobs. A sit or drive where you want every scan the OS will give: §4.5 "
            "(High performance, Keep screen on, Unrestricted background, optional Faster Wi-Fi) "
            "and accept the drain. This section is the all-day / pocket compromise after that picture is stable."
        ),
        bullets([
            "Start High performance (§4.5). Drop to Balanced once the picture is stable if you need the battery. Battery saver is overnight / bag carry — you will miss short BLE bursts.",
            "<b>Keep screen on</b> (Settings, on by default) holds the display while Fieldwatch is in front so Samsung does not park BLE on screen-off. Turn it off when you pocket the phone.",
            "<b>Allow background usage</b> opens Fieldwatch’s Battery page (that switch). <b>Unrestricted battery</b> opens the same page; select Unrestricted. Some phones (Samsung among them) do not open onto that choice — tap Allow background usage to click through and select it. Background usage lets the scan run when the app is not in front; Unrestricted stops the OEM freezing it to save battery. Fieldwatch’s switches follow those Android grants. They do not keep the screen on and do not lift Wi-Fi or BLE scan quotas. Samsung: also do not sleep Fieldwatch under Background usage limits. §4.5.3.",
            "<b>Night mode</b> (Settings → Appearance) is the red field overlay.",
            "A cheap USB battery pack is more useful than arguing with the saver slider if you need low-latency BLE all day.",
        ]),
        P("13.3 Crowded plazas", "h2"),
        P(
            "A saturated street will produce tens of thousands of BLE ads and thousands of "
            "rotated MACs. If the list ever went empty and the phone felt stuck, that was "
            "the heap hitting the 256 MB cap and Samsung then parking the scanner. Current "
            "builds cap the live set, batch ingest, and drop overflow ads, so you will still "
            "see a busy list without seeing every random address. Prefer Strength list "
            "over Hybrid if the UI feels heavy, and turn logging off if you do not need the file."
        ),
        P("13.4 Ethical and legal considerations", "h2"),
        P(
            "Fieldwatch only processes signals that devices choose to broadcast to every listener "
            "in range. That does not make every use lawful or appropriate. You are responsible "
            "for local law on wireless interception, stalking, and recording. In particular:"
        ),
        bullets([
            "Do not use matches to harass, follow, or dox people.",
            "Do not claim a LiteOn OUI is “a Flock camera” in a report without corroboration.",
            "Do not attempt to access, disable, or tamper with infrastructure you observe.",
            "Logs can contain MAC addresses of neighbors’ phones and TVs. Treat exports as sensitive.",
            "If you are on property where radio monitoring is restricted, leave.",
            "Do not use Fieldwatch where safety is in question. Experimental use only. See the Notice page.",
        ]),
        callout(
            "Operational security",
            "Fieldwatch does not hide the fact that the phone’s Wi-Fi and Bluetooth are on. "
            "A scan notification is visible on the lock screen unless you change the channel. "
            "The app does not provide anonymity.",
            "warn",
        ),
    ]

    # 14 Technical specifications / How it works
    flow += [
        PageBreak(),
        P("14. Technical specifications / How it works", "h1"),
        P(
            "This chapter is the pipeline, not a dump of every Kotlin file. A hear on this "
            "phone is a Wi-Fi access-point beacon or a Bluetooth LE advertisement. Fieldwatch "
            "matches it locally, draws Live, and can sit, debrief, or publish TAK — all on the "
            "handset. There is no Fieldwatch server."
        ),
        P("14.1 From the air to a row", "h2"),
        P(
            "Six stages, left to right, then the next row. Filters hide radios on Live; Display "
            "(Tune) hides fields on the row. Matching, the log, and Debrief still see what was "
            "heard. Fig. 20, then §7 (hear), §9 (match), §8 (filter), §5.3 (Display), §10 (watch), "
            "§11 (log / sits / Debrief), §5.8 (TAK)."
        ),
        diagram(
            "how-it-works.png",
            "Fig. 20 — How a hear becomes a row. Source paths are under "
            "<font face='Courier'>app/src/main/java/app/fieldwatch/</font>.",
        ),
        PageBreak(),
        P(
            "The six stages, in order. Section numbers jump to the field write-up."
        ),
        numbered([
            "<b>On the air.</b> Wi-Fi access-point beacons (SSID, BSSID, channel, vendor IE) and BLE advertisements (name, company ID, service UUID, manufacturer data). A laptop that only joined someone else’s network does not appear. Fieldwatch does not pair or connect to hear BLE. §1.1, §3.1, §7.1–7.2.",
            "<b>ScanService.</b> A foreground service with the persistent “Fieldwatch scanning” notification. Home leaves it running; swipe-away or Stop ends it. Wi-Fi is a batch radio — High performance asks about every 30 s, which is the OS cap. BLE streams in between. §4.5, §7.1, §10.3.",
            "<b>Catalog match.</b> SignatureEngine scores OUI, name glob, UUID, and manufacturer data against the stock pack plus rows you add. Decode fields map cleartext BLE bytes after a match; encrypted ads stay hex. §7.3–7.4, §9, §9.6.",
            "<b>Live + Tune.</b> FilterEngine decides who appears on the Live display. Tune (top right) is Display: Radar, Strength list, Timeline, Hybrid, By class, plus sort and fields. Live cap is about 400 radios. §4.4, §5.3, §6, §8.",
            "<b>Watchlist and Hunt.</b> Bookmark a signature to beep and/or speak. Extra attention families and drones ship watched. Hunt walks one BLE radio by RSSI. Named radios are one-MAC aliases with an optional alert. §5.5, §10, §12.8, §12.13.",
            "<b>Log, sits, TAK.</b> Rotating log on disk. A sit is a named window of everything heard. Debrief and AI Export use the open sit, a selected saved sit, or last 15 minutes. TAK / CoT is off by default. Privacy mode masks the screen and pauses the feed; the log still holds full MACs and GPS. §5.6–5.8, §11.",
        ]),
        P("14.2 Limits that are not bugs", "h2"),
        P(
            "Stock Android will not give Fieldwatch Wi-Fi clients, Classic Bluetooth inquiry, "
            "cellular / LTE / C-V2X, or a bearing. Those absences are the platform, not a "
            "broken install. Chapter 3 is the full list. Chapter 7 is the APIs. Faster Wi-Fi "
            "AP scans (§7.1.1) is the optional way to shrink the Wi-Fi batch gap after you turn "
            "off OS scan throttling in Developer options."
        ),
        P(
            "Offline-first: configuration, signatures, watches, logs, and sits live in the app "
            "private directory. Settings → Update stock catalog from GitHub can overlay stock "
            "rows when you ask; watches stay local. No account, no telemetry, no Fieldwatch cloud. "
            "§1.2, §5.7, §9.3."
        ),
        P("14.3 Where the pieces live", "h2"),
        table(
            ["Stage", "Tree (under app.fieldwatch)"],
            [
                ["Wi-Fi / BLE radios", "radio/WifiRadio.kt, BleRadio.kt"],
                ["Foreground scan", "radio/ScanService.kt, Permissions.kt"],
                ["Catalog match", "domain/SignatureEngine.kt, DefaultCatalog.kt"],
                ["Live + Tune", "ui/LiveScreens.kt, FieldwatchAppUi.kt, domain/FilterEngine.kt"],
                ["Watchlist / Hunt", "alert/Alerter.kt, domain/Hunt.kt, domain/RadioBookmarks.kt"],
                ["Log / sits / TAK", "data/LogStore.kt, data/SitStore.kt, domain/TakPublish.kt"],
                ["Settings / catalog pull", "ui/screen/SettingsScreen.kt, data/ConfigStore.kt, data/CatalogRemote.kt"],
            ],
            [2.2 * inch, 4.3 * inch],
        ),
        P(
            "That map is for someone reading the source next to this book. You do not need it "
            "to run the app. Chapter 12 is still the playbook when you have a question in the field."
        ),
    ]

    # Appendix
    flow += [
        PageBreak(),
        P("15. Appendix", "h1"),
        P("A. Glossary", "h2"),
    ]
    flow.append(table(
        ["Term", "Definition"],
        [
            ["AP", "Wi-Fi access point. Every Fieldwatch Wi-Fi row is one of these (router, hotspot, mesh, soft-AP). Associated clients are not reported. On the Live display it is the small Wi-Fi icon at the start of the subtitle, not the circle and not a two-letter tag. Detail spells “Wi-Fi access point.” Subtitle None drops the icon with the second line. §1.1, §5.4."],
            ["LE", "Bluetooth Low Energy advertiser. Headphones, phones, tags, unnamed BLE rows. Fieldwatch listens; it does not pair for detection. On the Live display it is the small Bluetooth icon at the start of the subtitle, not the circle and not a two-letter tag. Detail spells “BLE advertiser.” §1.1, §5.4."],
            ["Class glyph", "Circle on a Live display row, Filters class chips, and By class headers. First matching signature class, or ? if unmatched. The glanceable mark; radio kind is the Wi-Fi / Bluetooth icon on the subtitle. §1.1, §5.4, §9.5."],
            ["BSSID", "The AP’s MAC address in a scan result. A full MAC-prefix rule is that BSSID."],
            ["Company ID", "16-bit Bluetooth SIG manufacturer identifier in AD type 0xFF. Looked up offline (~4012 names)."],
            ["CoD", "Bluetooth Class of Device. 24-bit major / minor / service-class bitfield, decoded when advertised or provided by the stack."],
            ["BLE", "Bluetooth Low Energy. Advertisements are connectionless broadcast packets."],
            ["Classic Bluetooth", "BR/EDR (headsets, file-send, HC-05/HC-06 serial). Fieldwatch does not run Classic inquiry. A dual-mode BLE advertiser may claim Classic in flags or CoD; that is not a Classic scan. §2.2, §3.5, §7.2."],
            ["Signature", "A named bundle of match rules plus optional cluster flags."],
            ["Decode fields", "Optional BLE cleartext map on a signature (Signatures → row → Decode fields). After the rules hit, device detail / Share / AI Export / Debrief notable BLE parse manufacturer or service-data bytes into labels (temp, model, Remote ID, …). TAK / CoT also reads numeric ids latitude / longitude for advertised-position pins. Not a matcher. Not pairing or GATT. Encrypted ads stay hex. The Live display does not parse these; a hexagon on that signature’s chip means a map exists (§5.4). Byte 0 is after the company ID (manufacturer) or the first service-data byte. How to build a map: §9.6.1–§9.6.5. Stock maps: §9.6.6. TAK ids: §5.8.3."],
            ["Decode hexagon", "Small hexagon inside a signature name chip (same color as the name) when that signature has a Decode fields map. Dual-chip radios mark only the mapped name(s). Catalog check, not a parse of this packet. Hidden when Display → Signature names is off. Extra attention “!”, the cyan Observer notes chip, and the phosphor alerted bell are separate chips. Same mark on the Signatures list, By class signature rows, and detail Decoded fields. §5.4, §9.6."],
            ["Signature family (detail)", "Card on device detail, above Create signature from device. Same on-air ID rules as Signature candidates, for this radio: Strong family, Possible family, This radio only, or Already tagged. Counts distinct MACs in the log and on the air now. Verdict only — Create from device still pins this MAC. Already tagged is not a veto: a second UUID/OUI signature can dual-label (iBeacon + store). Candidates skip tagged radios. §5.5, §9.2, §9.2.1."],
            ["Named radios", "Settings list of one-MAC custom names, optional Observer notes (up to 280 characters), and optional alerts (detail Save name / Save notes, or the bookmark icon). Rename, notes, Alert on/off, remove one, or Clear all. Does not include signature bookmarks. Privacy mode masks MAC tails. Orphans (gone or rotated) stay until you delete them. Filters → Named radios only (any custom name). Watched only needs Alert on. Debrief, Compare, Path, and AI Export list heard radios with notes in an Observer notes section. §5.5, §5.7, §8.1, §10.1."],
            ["Privacy mode", "Settings switch, off by default. Masks the last three octets of MACs on the screen and in Debrief / AI Export / detail Share (AA:BB:CC:**:**:**). GPS last-fix and sit-report coordinates show as masked; street names omitted. Logs, matching, filters, Hunt math, Moving with you, and saved signatures stay full. Pauses a TAK / CoT feed so full MACs and coordinates are not sent. §5.7, §5.8."],
            ["TAK / CoT feed", "Settings switch, off by default. UDP Cursor-on-Target markers to ATAK / WinTAK / iTAK. Destination chips: This phone (127.0.0.1:10011), LAN multicast (239.2.3.1:6969), Custom. UDP only — not a TAK server TCP client. Heard-here Extra attention at operator GPS at the loudest hear (callsign ends in (here)); advertised lat/lon on the aircraft (Remote ID keeps one moving marker via sticky UAS ID, plus a pilot pin when op_lat/op_lon decoded). Gone radios are dropped. Settings shows last send. Privacy mode pauses it. Not DF, not a Remote ID plugin, not the Live display. §5.8, §12.15."],
            ["Night mode", "Settings → Appearance, off by default. Red-on-black field display: text, chips, RSSI, Hunt, Extra attention. Phone brightness is unchanged. Fig. 9, §5.7."],
            ["Heard here (TAK)", "CoT pin at this phone’s GPS at the loudest hear so far. The other radio is in earshot, not on that point. Walking away does not drag it. Callsign ends in (here); Extra attention is Maroon. Needs GPS tagging and a live fix. Extra attention uses this unless a payload lat/lon exists. Not DF."],
            ["Advertised position (TAK)", "CoT pin from decode field ids latitude / longitude (optional alt_geo). Stock Remote ID Location. Sticky across ASTM message types. UAS ID is the TAK uid so one aircraft moves instead of leaving MAC dots. op_lat / op_lon are a second (pilot) pin. GPS tagging can be off."],
            ["Payload location", "TAK What-to-send chip, on by default when you turn the feed on. Selects radios with sticky advertised lat/lon. Required for stock Remote ID (no Extra attention mark)."],
            ["Reports", "Bottom tab. Sits (optional named window), Path, Debrief (text/PDF), Sit export, Compare sits, AI Export, Signature candidates, Log export (Format + radios), Reset / clear log. The selected sit drives Path, Debrief, Sit export, and Compare’s this-sit side. Config for GPS, place names, and logging on/off stays on Settings. §5.6."],
            ["Sit (named)", "Optional window of watching, started from Reports → Start sit. Path, Debrief, Compare this-sit, and AI Export use that start/end instead of the last 15 minutes in RAM, and keep radios the Live list has dropped. One at a time. Live list, Filters, Hunt, TAK unchanged if you never start one. Not DF. §5.6."],
            ["Path", "Reports card. North-up plot of this phone for the selected sit (or last 15 minutes). Extra attention (red) and bookmarked (blue) dots at strongest RSSI. Observer notes only if that radio is bookmarked. Thick green = stay. Time ticks. OSM tiles when Online place names and maps is on and the phone is online; otherwise the trace only. Also a letter-size figure on Debrief / Compare PDF. §5.6.1."],
            ["Sit export", "Reports card under Sit report. Same Format chips as Log export, different file: one row per unique radio in the selected sit (or last 15 minutes). Logging can be off. GPX/KML include the operator path as a track. Not the rotating log. Privacy mode does not mask the file. §5.6.2."],
            ["Log export", "Reports card. Share/Save of the rotating session file (JSON lines on disk; CSV / GPX / KML / WiGLE at export). One line per hear while logging was on. Needs Write to disk. Clearing the log does not delete sits. §5.6.3, §11.6."],
            ["Observer notes", "Optional 280-character field on a Named radio (same KIND+MAC as the custom name). Cyan block on detail; cyan notes chip on Live next to Extra attention “!”. Debrief lists them after Where you were; Compare after Windows. Path and AI Export list heard radios with the note. Not catalog Notes and not Extra attention gold. BLE privacy addresses hide the pencil. Settings backup includes the note. §5.5, §5.6."],
            ["Signature candidates", "Reports action. Re-matches the rotating log against the current catalog, then lists unmatched families that share a unique on-air ID on two or more radios. Randomized addresses and house-like names are skipped. Create signature opens the editor as a draft (shared rule, no MAC pin). Save returns to the list and re-runs it. Offline. §5.6.4, §9.2.1, §11.5."],
            ["vendor_ie (log)", "Last CSV column / JSON field on new Wi-Fi rows: pipe-separated vendor-IE OUIs, up to eight. Empty on BLE and on older 17-column rows. Signature candidates uses product IEs; WPA/RSN/P2P/Qualcomm chip IEs are logged but not clustered. §11.2, §11.5."],
            ["Alerted (list)", "Phosphor notification pip on a Live display row (list, hybrid, timeline, By class) after a watchlist alert this session. Lasts until you leave Fieldwatch. Distinct from Extra attention “!” and from the one-second flash. Newest alert ranks by the same event. On radar the same radios keep a phosphor ring after the ping. §5.3, §5.4, §6.1."],
            ["Notes (signature)", "Editor field on a signature. Shows on radio detail as a quiet Notes card for matching radios, and in Share / AI Export. Stock copy is what the family is and how it is typically used — not the match recipe (company IDs, UUIDs). Not Extra attention: no Live “!”, not amber, not Debrief. Dual-chip radios list each family. §5.5, §9.3."],
            ["Extra attention", "Optional field on a signature, separate from Notes. If it is not empty, a match gets a “!” on the Live display, an amber Extra attention card on detail, and a line in Debrief / AI Export (amber PDF callout). Empty = no mark. The “!” is its own chip, not the decode hexagon, not the cyan Observer notes chip, and not the phosphor alerted bell. Stock fills it on Hobby BLE serial, Axon, WatchGuard Video, Digital Ally, Reveal Media, Wolfcom, Ray-Ban / Meta glasses, Snap Spectacles, Brilliant Frame, Even G1, Fieldy, Plaud Note, Limitless, Bee, Omi, Friend, Hak5 Pineapple, Flipper Zero, Pwnagotchi, Marauder / Deauther, GhostESP, Bruce, Porkchop, Cradlepoint, AirLink, Compex, Novatel Wireless, Utility Inc, Panasonic i-PRO / Arbitrator, and roadside / public camera + ALPR (Flock, Penguin, Pigvision, FS Ext Battery, Genetec AutoVu, Rekor, Motorola Vigilant, Verkada, Avigilon, Axis, Hikvision, Dahua, Hanwha Wisenet, Uniview, Rhombus, Hayden AI, Miovision, Tattile, LVT LiveView) — those rows also ship with the bookmark on. Many camera/ALPR rows are name-only; cellular units stay quiet. Pattern match, not identity, not a safety finding. §5.5, §9.3, §9.5, §12.14."],
            ["Hobby BLE serial", "Catalog signature (on). BLE advertised names for cheap UART modules (HMSoft, JDY, CC41, AT-09, BT05, ESP32 BLE). Not Classic HC-05/HC-06. Extra attention cautions that the same boards have been used in some pump/ATM overlays; look with your eyes if it is loud next to a card reader. Not proof. Turn the row off if those names are local noise."],
            ["Axon", "Catalog signature (on). IEEE OUI 00:25:DF plus Axon Body / Fleet / Dock names. Extra attention: body-worn, in-car, dock, or TASER. Public safety class — used in law enforcement, not exclusive to it. Not that officer. Quiet LTE units will not appear."],
            ["WatchGuard Video", "Catalog signature (on). IEEE OUI 00:1D:96 (WatchGuard Video, not the firewall company). Extra attention: body-worn / in-car. Now Motorola. Public safety class — used in law enforcement, not exclusive to it."],
            ["Ray-Ban / Meta glasses", "Catalog signature (on). BLE company IDs 0x01AB / 0x058E / 0x0D53 and Ray-Ban names. Extra attention. Quest and other Meta wearables can match the same IDs."],
            ["Snap Spectacles", "Catalog signature (on). BLE company ID 0x03C2 plus Spectacles names. Extra attention. Not proof of recording."],
            ["Hak5 Pineapple", "Catalog signature (on). Setup SSID Pineapple_XXXX. Extra attention: admin AP, not every cloned café SSID."],
            ["Flipper Zero", "Catalog signature (on). OUI 0C:FA:22 and BLE name Flipper*. Extra attention. Custom firmware can hide it."],
            ["Pwnagotchi", "Catalog signature (on). Classic BSSID de:ad:be:ef:de:ad. Extra attention."],
            ["Marauder / Deauther", "Catalog signature (on). MarauderAP / Deauther default names. Extra attention. DIY boards match too."],
            ["Porkchop", "Catalog signature (on). SSID/name PORKCHOP; BACON fake-AP vendor IE 50:52:4B. Extra attention. Cardputer / CYD firmware, not every ESP32."],
            ["Moving with you", "Filter: loud BLE (about −75 dBm or stronger on the trail) with a GPS trail along your path, still being heard. Wi-Fi access points are excluded — hear-time GPS on a loud AP looks like co-travel. “Still here” is not a fixed radius: allowed distance = the larger of 50 m or (recent speed × 15 s), plus 25 m of GPS slack. Walking holds a house-length; highway hold is a handful of advertisements so a car tag does not blink off between packets. Passing BLE still fails the trail-moved gates. Needs live tagging (not stale last-known) and ~45 m of path. Bag/car tag yes; a second iPhone usually no (BLE MAC rotation). The switch starts a BLE follow test (clears Signatures only / Show only / Named radios only / Watched only; Hide these stays). The preset replaces the whole filter. Live display → Start over clears path and trails. Debrief still writes the same co-travel assessment for the last 15 minutes even if this filter is off and even if the Live display is not on the list. §8.5."],
            ["Start over", "Live display button while Moving with you is on. Clears the operator GPS path and radio GPS trails. List and log stay. Path meter returns to 0 m."],
            ["Live display", "The first bottom tab (labeled Live on the phone). The on-screen picture of radios: radar, list, timeline, hybrid, or By class. Filters change who appears here; Tune (Display) changes the view and how each row looks. Not “live vs recorded” — Debrief, the log, and the TAK feed are separate. §4.4, §5.1–5.3."],
            ["By class", "Live display view (Display → By class). Outline of the filtered set: every class A–Z by name → signatures A–Z → radios → detail. Unmatched last. Show all (default) keeps empty classes; Collapse empty hides zeros. Those chips scroll with the list. Class headers use the same glyphs as Live display rows (unmatched = ?). Counts are radios, not packets. Radio rows follow Display (bars, chips, Frequency, first/last). Dual-chip radios sit in each class they matched. A watchlist hit opens that class and signature so the row can flash, and the jump keeps those headers on screen when the radio is close enough. Not a Report. §6.5."],
            ["Display (Live display)", "Tune (sliders icon, top right of Live). How the list looks for this mission: View, Sort, Brief hold, Title line, Subtitle line, bars, signature names, Frequency, first/last. View is Radar, Strength list, Timeline, Hybrid, or By class. The circle on each row is a class glyph, not radio kind — that is the Wi-Fi / Bluetooth icon on the subtitle. Filters hide radios; Display hides fields. Not on Settings. §4.4, §5.3, §6.5."],
            ["Sort (Display)", "Order of the list, hybrid, and timeline. Strongest signal; Strongest averaged over 30 s (the default); Newest heard; Newest alert; Newest arrival; New at bottom; Name A–Z (Title line); Signatures first. Does not hide radios. Radar still plots by RSSI radius. §5.3."],
            ["Preset (Filters)", "Chip at the top of Filters. A tap replaces the whole filter, not Display. Short stock set: All traffic, Wi-Fi only, BLE only, Strong signal, Moving with you, Watched only — plus chips you saved. Class Show only (Cameras, Drones, Finder tags, …) is the chips further down; Save current as… if you want that sit as a preset. Show-only presets imply Signatures only (switch dimmed). Long-press any chip to delete it. Stock chips you remove stay gone until Restore default signatures &amp; presets. Reset filter clears this tab; it is not undo. §8.4."],
            ["Signature class", "Bucket on every signature: Finder tags, Retail beacons, Signage, Wearables, Surveillance, Drones, Pentest, Public safety, Vehicle, Glasses, Audio, Cameras, Thermostats, Access control, Health, Home IoT, ISP / routers, Mesh, Phones / PCs, Other. Public safety is Axon / WatchGuard Video and public-safety vehicle APs (Cradlepoint, AirLink, Compex, Novatel, Utility Inc). Those radios are used in law enforcement; they are not exclusive to it — government, municipal, and other corporate fleets likely run some of the same kit. Health is clinic / home-medical BLE (Honeywell Xenon HC scanners, Omron cuffs, Withings scales, Dexcom). Cameras is consumer / action cameras, not poles (Surveillance) and not Axon. Access control is door locks and readers (August / Schlage and ASSA ABLOY / SALTO / dormakaba / Paxton), not cameras. Glasses is Meta / Snap. Audio is AirPods / Sony / Bose / JBL / Sonos. Color is the Live display chip; class is the filter. Custom rows default to Other. An old Body-worn class folded into Wearables. The Locks class label is now Access control; the stored value is still LOCK. §8.3, §9.5."],
            ["Signature pack", "JSON file from Settings → Export signatures (fieldwatch-signatures-YYYYMMDD.json). Stock plus your edits. No logs, GPS, filters, or watchlist. Import skips the same id or the same match rules, merges extra rules onto a stock row, and renames a colliding name to “Name (imported)”. Restore defaults still wipes customs. A settings pack is a different file. §5.7, §9.3."],
            ["Settings pack", "JSON file from Settings → Export settings (fieldwatch-settings-YYYYMMDD.json). Settings switches, the current filter, filter presets, named radios, and signature watches. Not the catalog, logs, or GPS. Import replaces those setup fields; the catalog stays. First-run disclaimer is not overwritten. Factory-reset / new-phone backup. §5.7."],
            ["Show only / Hide these", "Filters → signature classes. Show only keeps radios matching the class chips you pick (unmatched stay hidden; Signatures only is implied and the switch is dimmed). Hide these drops those classes and leaves unmatched. Live display only — matching, log, and Debrief still see them. Show only with no class picked leaves the Live display unchanged. §8.1–8.4."],
            ["Title line", "Display → first line of each list, hybrid, or timeline row. Advertised name, Name + type, or MAC (default). Stays bold even when it is a MAC. Name A–Z sorts by this line (so the default is MAC order). Radar labels ignore it."],
            ["Subtitle line", "Display → second line. Always starts with a small Wi-Fi icon (access point) or Bluetooth icon (BLE advertiser). Then Advertised name, Name + type (default), or MAC, plus rand/pair/gone. pair is Fast Pair pairing-mode seen this session. None hides the line so more rows fit; crumbs move onto the title. The kind icon does not move onto the title. Unnamed BLE on this line is unnamed (the icon already marks LE). Vendor is not on this line."],
            ["Brief hold", "Display → how long a radio stays after the last packet (Off, 10, 30, or 60 s). Last RSSI, rank, and radar ring are frozen; they do not decay. Linger is the longer of this and Stale after."],
            ["Gone / stale", "Last packet older than the longer of Stale after and Brief hold. Held Wi-Fi / BLE recycle do not count. Still listed until that linger; dim on radar only when marked gone."],
            ["Keep screen on", "Settings switch, on by default. Holds the display while Fieldwatch is visible. Turn off when you pocket the phone. Not the same as unrestricted background. Max-collection sit: §4.5."],
            ["Max collection (§4.5)", "Checklist to hear as often as stock Android allows: system Location / Wi-Fi / Bluetooth, every Fieldwatch permission (Precise Location, Nearby Wi-Fi, Bluetooth scan+connect, Notifications), Unrestricted battery / do not sleep the app, Keep screen on while you watch, High performance, optional Faster Wi-Fi AP scans. Battery and heat are the cost. Does not lift monitor-mode / client / Classic limits."],
            ["Faster Wi-Fi AP scans", "Settings switch, off by default. About 8 s AP batches instead of ~30/40/55 s. Fieldwatch reads the OS Wi-Fi scan-throttle flag (Android 11+) and will not turn this on until Developer options → Wi-Fi scan throttling is Off. Purpose: more chances to hear a signed AP (OUI / factory SSID) while it is in range — important on a drive. Not Hunt, not clients, not continuous RF. More battery and heat. §7.1.1, §10.3.1."],
            ["Manufacturer data", "BLE AD type 0xFF: 16-bit company ID plus vendor bytes."],
            ["Signatures only", "Filter that hides devices with no signature match. Dimmed while class Show only or Show only selected signatures is narrowing the Live display. §8.1–8.3."],
            ["Watched only", "Filter that hides radios that are not a bookmarked signature match and not a Named radio with Alert on. Always AND. Hide these still applies (Watched only + Hide Surveillance drops bookmarked cameras). Label-only names stay on Named radios only. Live display shows a Watched only strip while this is on. Moving with you clears it. §8.1."],
            ["Named radios only", "Filter that hides radios without a custom name. Alert can be off. Not Signatures only or Watched only. Random / privacy MACs stay pinned to that address. §5.5, §8.1."],
            ["Hide Fast Pair account-key", "Filter that hides Fast Pair-only radios whose payload is the longer account-key filter, not the 3-byte pairing-mode model ID. Pairing-mode stays (chip Fast Pair pairing, subtitle pair). Dual-chip radios stay. Hide selected Fast Pair drops both. Always AND. §8.1, §9.5."],
            ["Show only selected signatures", "Filters collapsing list, grouped Class A–Z like the Signatures tab. Only radios matching the signatures you pick stay on the Live display. Empty list = no extra include. Separate picks from Hide selected. §8.1–8.3."],
            ["Hide selected signatures", "Filter switch for one family. When on, matching devices drop off the Live display. Picks stay stored if you turn the switch off. Empty list hides nothing. Prefer class Hide these when the whole Finder tags / ISP bucket is clutter."],
            ["Pause (Live display)", "Freezes the Live display. Radios and the log keep running. Filters and Settings still update; the new filter applies when you run the Live display again. Detail opened from a paused row is that snapshot, even if the radio has since gone. Tap the Live tab again (the control then reads Live) to run again. Mark seen while paused uses the frozen list."],
            ["Mark seen", "Live display button, above the tabs, only while New detections only is on. Adds what is on the Live display to already-seen. While paused, uses the frozen list."],
            ["Reset seen", "Live display button, above the tabs, only while New detections only is on. Clears already-seen to zero so those radios can show as new. Does not clear the log."],
            ["Debrief (text)", "Reports share of the field sit report as plain text. Opens with DISCLAIMER (hobby / as-is; hypotheses not identity; local law; 15-minute live set). Last 15 minutes in the live map (about 400 radios, not the log) unless a sit is selected. Ignores Live display view and Filters. Where you were, then Observer notes if any, tracking assessment, amber co-travel callouts. Custom names. Radios you passed are omitted. On a drive, tap more than once. §11.4.1."],
            ["Debrief (PDF)", "Same sit report, letter-size typeset PDF. Disclaimer at the top, then FIELDWATCH header, numbered sections (Observer notes after Where you were), bold stay/transit and Label: kickers, operator-path figure, amber co-travel callouts, takeaway. Same window as the text share. Long trip: tap more than once (§11.4.1). Share as application/pdf."],
            ["Compare sits", "Reports card under Sit report. This sit (open, selected, or last 15 minutes) vs a second saved sit. Presence only — only in this sit, only in the second, in both. Kind + MAC. Text, PDF, and AI Export. Observer notes after Windows. Not a radio fix. §5.6."],
            ["Online place names and maps", "Settings switch, on by default. Debrief and AI Export reverse-geocode GPS stamps via the system geocoder when online. Reports → Path loads OpenStreetMap tiles under the trace (fill the plot, clip, extra map around the route). Offline, no tiles, or Privacy mode: Debrief coordinates only, Path is the north-up trace — no error dialog. Turn off to keep streets and maps out together."],
            ["New at bottom", "Display → Sort. First-seen order, oldest at top; new radios append; gone radios drop. List follows the bottom unless you scroll up."],
            ["unnamed LE", "BLE Advertised name / Name + type when there is no advertised name and no useful decode. On the subtitle the Bluetooth icon already marks LE, so the body is unnamed (not “unnamed LE” twice). Title Advertised name still shows unnamed LE. The title is the MAC unless you change it."],
            ["AI Export", "Three buttons. Reports sit: onboard Debrief + compact rates / Extra attention / Observer notes (addendum, not a roster). Reports Compare: overlap + exclusive Extra attention / Named / Observer notes. Device detail: that one radio (dump + registry decode). All paste into a chat, all open with the experimental disclaimer, all are hypotheses — not identity. Treat as sensitive."],
            ["Signature color (stock)", "Built-in rows share a palette by class: red pentest, amber cameras/ALPR/UniFi Protect/DJI, purple phones/Find My, cyan wearable trackers, green mesh, orange glasses and audio, teal public safety and vehicle, silver home IoT/ISP Wi-Fi (including UniFi AP)/retail signage/Unknown. Unmatched radios use RSSI color (≥−55 green, −55 to −70 amber, −70 to −85 orange, else red). Change any row. §9.5."],
            ["UniFi AP", "Wi-Fi-only catalog signature (ISP / routers). Factory SSIDs UniFi* / UAP-* / UBNT* plus Ubiquiti IEEE OUIs on the BSSID or a vendor IE. Virtual BSSIDs miss the MAC OUI but still hit on a Ubiquiti vendor IE. A separate UniFi row is name-only on either radio. UniFi Protect cameras stay Surveillance. §9.5, Appendix B."],
            ["Tag detections with GPS", "Settings switch, on by default. Current GPS/network updates while scanning; stamps each hear (detail, Moving with you, Debrief, log lat/lon, heard-here TAK pins). Last-known older than 30 s ignored. Operator phone at hear-time, not the other radio. Advertised TAK pins (Remote ID) do not need this. High-accuracy Location or the path stays 0. §5.7, §5.8."],
            ["Share as text", "Device-detail button. Plain dump of the open radio (identity, signal, decode, session). Not the sit report and not the rotating log."],
            ["Voice (watchlist)", "Settings → Voice on watched signature, on by default. What to say (signature watches): Class, Signature, or Class + signature. Default Class + signature. A named radio speaks its watch name instead, including a custom name from Named radios. Independent of Beep. Not Hunt. Overlapping speech is dropped. On-device TTS. Test alert plays the signature mix. Jump works with voice alone. §5.7, §10.2.1."],
            ["Hunt", "Device-detail, BLE only. Full-screen closer/further from smoothed RSSI. Rings around YOU contract on Closer and expand on Further. Very Close at about −45 dBm or louder (look around; still not meters). Loudest-this-hunt + hunt sparkline. Beep / Vibrate at the bottom (off by default; faster as RSSI gets louder; silent when Quiet/Gone; not the watchlist chirp, never speaks). Optional body-block turn for a crude heading (§12.13). Walls, metal, people, and floors change RSSI without a change in range. Not distance, not DF. Wi-Fi omitted (OS scan throttle; Faster Wi-Fi AP scans still batch, not Hunt)."],
            ["lat / lon (log)", "CSV columns and JSON fields on each new log row when Tag detections with GPS is on and a fix exists. Operator phone at hear-time, not the other radio. Empty/null otherwise. In CSV these columns sit before vendor_ie so older files still parse."],
            ["Where you were", "Debrief / AI Export section. Operator path split into stays (~40 m) and transits. Lat/lon once per stay, optional street name, loud radios heard there. Not lat/lon on every inventory line."],
            ["Fast Pair", "Google tap-to-pair. Service UUID 0xFE2C. Catalog signature (on): Android phones and many buds. Three-byte model ID in pairing mode (Live display chip Fast Pair pairing); longer payloads are an account-key filter. Filters → Hide Fast Pair account-key drops account-key-only chips. Hide selected Fast Pair drops both. §8.1, §9.5."],
            ["iBeacon", "Apple manufacturer layout 0x02/0x15: UUID + major + minor + calibrated TX."],
            ["Appearance", "BLE GAP field: what the device claims to be (headphones, mouse, watch…). Used in the detail “What this looks like” guess."],
            ["New detections only", "Filters switch. Mark seen / Reset seen are on the Live display above the tabs while the filter is on. Already-seen grows only on first turn-on (plus the next Wi-Fi scan) or Mark seen. Reset seen clears it to zero. New radios stay while heard, then at least Brief hold after the last packet. Live display hint: New only · N hidden."],
            ["Sit", "One session of watching radios — a room, a walk, or a drive. Path, Debrief, Sit export, and Compare this-sit use the selected sit, or last 15 minutes in memory. Log export is the rotating session file. §5.6, §11.4."],
            ["Pipeline", "How a hear becomes a row: on the air → ScanService → catalog match → Live + Tune → watchlist / Hunt → log, sits, TAK. Fig. 20, Chapter 14."],
            ["Hear-time", "The moment Fieldwatch heard that packet. GPS on a detection is this phone at hear-time, not the other radio’s location."],
            ["Chip (signature)", "The colored signature name on a Live display row. A pattern hit, not identity. Display → Signature names hides chips without dropping the radio."],
            ["OUI", "Organizationally Unique Identifier — first 24 bits (three bytes) of a MAC, assigned to a vendor. The same module vendor appears in many products."],
            ["Randomized MAC", "Locally administered unicast address (U/L bit set). Common on phones and some tags."],
            ["RSSI", "Received signal strength indicator, dBm. Larger (closer to 0) is stronger."],
            ["Sparkline", "RSSI graph on Hybrid and detail. Packet order (newest right), fixed −30 to −100 dBm grid — not a clock and not auto-scaled."],
            ["Trend mark", "&gt;&gt; &gt; = &lt; &lt;&lt; next to RSSI. Recent packets getting stronger or weaker."],
            ["Service UUID", "BLE identifier for a service. 16-bit aliases expand to the Bluetooth base UUID."],
            ["SSID", "Wi-Fi network name. May be hidden (empty in the scan result)."],
            ["Promiscuous / monitor mode", "Wi-Fi chip receiving all frames on a channel. Stock Android apps cannot enable this. Fieldwatch is startScan() AP beacons + BLE advertisements only."],
            ["Cellular-first / quiet radio", "Newer camera/ALPR poles that backhaul on LTE/5G and leave Wi-Fi/BLE off except for install or maintenance. Expected to be invisible to Fieldwatch most of the time."],
            ["Rule enable", "Switch on each rule in the signature editor. Off keeps the rule in the list but it does not match."],
        ],
        [1.6 * inch, 4.9 * inch],
    ))
    flow += [
        Spacer(1, 10),
        P("B. Default pre-loaded signatures", "h2"),
        P(
            "All of the following use match-any unless noted. Confidence comments are operational, "
            "not legal findings. Exact OUI lists can be inspected in the in-app editor. "
            "Every stock signature always labels when its rules hit. "
            "Hide a noisy family on Filters (Hide these for a class, or Hide selected for one row). "
            "Chip colors are by class (§9.5). Extra attention and a stock bookmark ship on the "
            "body-cam, camera-glasses, recording-wearable, pentest, public-safety vehicle AP, and roadside / public camera + ALPR rows noted below, plus every built-in Drone-class row (DJI, Remote ID, Skydio, Autel, Parrot, HOVERAir). Consumer cameras, ISP "
            "gateways, and office mice will label when their factory names or OUIs are heard. "
            "The table is A–Z by signature name."
        ),
    ]
    stock_sigs = [
            ["Flock Safety Cameras", "OUI B4:1E:52; ~28 additional field OUIs (LiteOn / Espressif / related); names Flock, FLCK, CONDOR, FALCON, SPARROW; globs Flock-*, Flock-??????. Extra attention filled. Stock bookmark.", "Roadside ALPR / camera pole. High only for B4:1E:52 or a Flock-* SSID. Other OUIs are component vendors — false positives expected. Beeps on a new match."],
            ["Raven / ShotSpotter", "Names RAVEN, ShotSpotter, SoundThinking; UUIDs 3100–3500; mfg 0x09C8; OUI D4:11:D6", "UUID range and 0x09C8 are the stronger digital fingerprints."],
            ["Apple AirTags", "Name AirTag / Find My; mfg data 0x004C / 12; UUID FD44", "Offline Finding. iPhones also send 0x12 — dropped when Continuity (Apple Device) is on the same radio unless the name is AirTag or UUID FD44. Not Continuity 0x10 and not AirPods (0x07)."],
            ["Apple Device", "Apple 0x004C types 0x10 / 0x0F / 0x0B / 0x05 / 0x0C–0x0E / 0x08 / 0x0A; names iPhone, iPad, MacBook", "Phone / tablet / Mac Continuity. OF 0x12 on the same radio is not a second AirTag chip. A street of iPhones will light this up."],
            ["Apple audio", "0x004C / 07 Proximity Pairing; 0x004C / 09 AirPlay; names AirPods, Beats", "AirPods / Beats / AirPlay. Not tags, not Nearby Info phones."],
            ["Microsoft Device", "Company ID 0x0006; names Surface, Xbox", "Swift Pair / Nearby Sharing on Windows PCs."],
            ["Tesla", "Company 0x022B; UUIDs FE96/FE97; names Tesla / Cybertruck; BLE glob S????????????????C (VIN SHA1 phone-key); Apple iBeacon UUID 74278BDA-…; Wi-Fi TeslaGW* / tesla-vehicle / TeslaWallConnector* / Cybertruck*", "Phone-key ads are S + 16 hex + C. Tesla also uses iBeacon layout so iOS can find the car — Fieldwatch labels Tesla, not iBeacon. Vehicle class."],
            ["Ford", "Company 0x0723; BLE names Ford / Lincoln", "Phone-as-key / infotainment when that ID is advertised. Not a dealer SSID."],
            ["Honda", "Company 0x0915; BLE names Honda / Acura", "Not Sony Honda Mobility 0x0EDE."],
            ["Hyundai", "Company 0x0826; BLE names Hyundai / Genesis", "When that company ID is advertised."],
            ["Toyota", "Company 0x0977; BLE names Toyota / Lexus; Wi-Fi TOYOTA* / LEXUS*", "When that company ID or factory SSID is advertised."],
            ["Nissan", "Company 0x0BA6; BLE names Nissan / Infiniti", "When that company ID is advertised."],
            ["Subaru", "Company 0x0A10; BLE name Subaru", "Not Starlink satellite internet."],
            ["BMW", "Company 0x05EB; BLE name BMW; Wi-Fi BMW_*", "Not “BMW of …” dealer names."],
            ["Volkswagen", "Company 0x011F; UUIDs FE30/FE31; names Volkswagen / VW; Wi-Fi My VW*", "Skoda / SEAT / Porsche are separate IDs."],
            ["Porsche", "Company 0x0120; BLE name Porsche; Wi-Fi Porsche_WLAN*", "Not Volkswagen 0x011F."],
            ["Jaguar Land Rover", "Company 0x020B; names Jaguar / Land Rover / Range Rover", "When that company ID is advertised."],
            ["BYD", "Company 0x0C34; BLE name BYD", "Pattern match, not a model."],
            ["Tesla tsTPMS", "BLE names tsTPMS*", "Tesla BLE tire sensors. Not company 0x022B on this row and not UUID 0x1122. Decode fields (awake ads): pressure, temperature, battery. Vehicle class."],
            ["Google", "Company 0x00E0; names Pixel / Chromecast", "Pixel / Chromecast. Fast Pair UUID FE2C is the Fast Pair row."],
            ["Fast Pair", "BLE UUID 0xFE2C", "Android phones and many buds/speakers. Pairing-mode 3-byte model ID (Live display: Fast Pair pairing / pair crumb) or a longer account-key filter (plaza noise). Filters → Hide Fast Pair account-key drops account-key-only chips. Hide selected Fast Pair drops both. Not a person. Not every unnamed LE. Phones / PCs class."],
            ["Sony", "Company 0x012D; names Sony / WH-1000 / WF-1000", "Headphones, TVs, cameras. Not Sony Ericsson 0x0056."],
            ["Bose", "Company 0x009E; UUIDs FE21 / FEBE; name Bose", "Headphones / speakers. FEBE is the usual LE speaker/QC advertisement."],
            ["Garmin", "Company 0x0087; UUID FE1F; name Garmin", "Watches / inReach."],
            ["Pokemon GO Plus", "Names Pokemon GO Plus*; UUIDs 138C35B6 (Plus +) and 21c50462-… (original)", "Nintendo clip-on. Not company 0x0553 or OUI 60:1A:C7 (Joy-Con / Switch). Wearable class."],
            ["Fieldy", "BLE names Fieldy*. Extra attention filled. Stock bookmark.", "Field Labs AI note-taker pendant. Not Arduino/ESP32 example UUID 4FAFC201-…, not company 0xFEFE. Not proof of recording. Beeps on a new match."],
            ["Plaud Note", "BLE names Plaud Note*. Extra attention filled. Stock bookmark.", "Plaud Note / NotePin AI recorder. Not proof of recording. Beeps on a new match."],
            ["Amazon", "Company 0x0171; names Echo / Amazon / Fire TV", "Echo / Fire."],
            ["Fitbit", "Company 0x018E; UUIDs FD62/FD63; name Fitbit", "Wearable. Identity only — advertisements do not carry steps; modern dumps are GATT/encrypted. No Decode fields map."],
            ["Oura", "Company 0x02B2; name Oura", "Ring."],
            ["Logitech", "Company 0x01DA; UUID FE61; Logitech / Logi", "Mice / keyboards. Office noise."],
            ["JBL / Harman", "Company 0x0057; JBL / Harman", "Audio."],
            ["Sonos", "Company 0x05A7; UUID FE07; name Sonos", "Speakers (S41 / S57 LE)."],
            ["GoPro", "UUIDs FEA5/FEA6; GoPro*", "Action cameras. Cameras class, not Glasses. Decode fields: company 0xF202 schema / awake / Wi-Fi AP / pairing / model / offload (§9.6)."],
            ["Osmo", "0x08AA model IDs 0x0006–0x0022; OsmoAction* / OsmoPocket* / Osmo360* / OsmoNano* / XtraEdgePro*", "DJI Osmo Action / Pocket / 360 / Nano cameras. Not Osmo Mobile gimbals. Not DJI aircraft (those stay DJI). Cameras class. Decode fields: 0x08AA model id (§9.6)."],
            ["Insta360", "Company 0x10D7; Insta360* / X3 * / X4 * / X5 * / Ace Pro* / GO 3* / ONE X* / ONE RS*", "Arashi Vision action / 360 cameras. Cameras class, not Surveillance."],
            ["DJI", "Company 0x08AA; DJI* on BLE and Wi-Fi", "Drones / RC / setup AP. Osmo cameras are the Osmo row. OcuSync is not an AP. In-flight ASTM Remote ID is the Remote ID row. Drones class. Decode fields: 0x08AA model id (§9.6). Stock bookmark."],
            ["Remote ID", "BLE UUID 0xFFFA (ASTM F3411 / FAA Remote ID)", "In-flight digital license plate. DJI, Skydio, Autel, Parrot, HOVERAir, Dronetag / Aerobits / BlueMark modules. Not FIDO FFF9 or Thread FFFB. Wi-Fi NAN often misses on stock Android. Not a tail number. Drones class. Decode fields: Open Drone ID app/counter/message; protocol 2 Basic ID / location / Self ID / System / Operator ID (§9.6). Stock bookmark."],
            ["Skydio", "Names Skydio*", "US public-safety / enterprise drones. RID in flight is often Wi-Fi beacon (easy to miss) — UUID FFFA is the Remote ID row. Drones class. Stock bookmark."],
            ["Autel", "Names Autel*", "Autel Robotics drones. Not EVO* and not SSID default-ssid. Drones class. Stock bookmark."],
            ["Parrot", "Names ANAFI* / Bebop*", "Parrot drones. Not company 0x0043 (automotive). Disco* not used. Drones class. Stock bookmark."],
            ["HOVERAir", "Wi-Fi Hover* / HoverX1_*; names HOVERAir*", "Zero Zero Robotics flying cameras. Drones class. Stock bookmark."],
            ["Starlink", "Wi-Fi SSIDs STARLINK* / Starlink*; SpaceX OUI 00:26:12", "BSSID often randomized; name is the usual hit."],
            ["Meraki", "Wi-Fi SSIDs Meraki* plus Cisco Meraki IEEE OUIs", "Not Cisco Systems OUIs. Renamed SSID still hits on BSSID. Cisco vendor IE 00:00:0C on the same beacon stays Meraki only. ISP / routers class."],
            ["Cisco", "Wi-Fi Cisco*; tsunami; Cisco Systems and Cisco SPVTG IEEE OUIs", "Not a Cisco substring (Francisco). AP beacons only. Meraki / Cisco-Linksys have their own OUI lists. ISP / routers class."],
            ["Aruba", "Wi-Fi Aruba* / SetMeUp* / InstantOn* plus Hewlett Packard Enterprise IEEE OUIs", "HPE Aruba Instant / Instant On. Not a lone instant word. Not HP Inc. printers. ISP / routers class."],
            ["Ruckus", "Wi-Fi Ruckus* / Configure.Me* plus Ruckus Wireless IEEE OUIs", "Renamed SSID still hits on BSSID. ISP / routers class."],
            ["Fortinet", "Wi-Fi Fortinet* / FortiAP* / FAP-config* plus Fortinet IEEE OUIs", "Renamed SSID still hits on BSSID. ISP / routers class."],
            ["MikroTik", "Wi-Fi MikroTik* plus Routerboard.com IEEE OUIs", "Renamed SSID still hits on BSSID."],
            ["EnGenius", "Wi-Fi EnGenius* / EnMGMT* plus EnGenius IEEE OUIs", "Cloud APs; EnMGMT* while unclaimed."],
            ["Zyxel", "Wi-Fi Zyxel* plus Zyxel IEEE OUIs", "Renamed SSID still hits on BSSID."],
            ["Peplink", "Wi-Fi Peplink* / Pepwave* plus Peplink IEEE OUIs", "Travel / branch routers. Not MAX-*."],
            ["OpenWrt", "Wi-Fi OpenWrt*", "Factory OpenWrt SSID. No OpenWrt IEEE OUI."],
            ["Arris", "Wi-Fi Arris* / SURFboard* plus Arris and CommScope IEEE OUIs", "Cable gateways. Ruckus keeps its own OUI list. ISP-issued names after provision often miss."],
            ["Mist", "Mist Systems IEEE OUIs", "Juniper Mist campus APs. Cloud SSIDs are site names. ISP / routers class."],
            ["T-Mobile", "Wi-Fi TMOBILE-* / T-Mobile*", "Home Internet / hotspot. Often HUMAX / Arcadyan / Askey OEM."],
            ["HUMAX", "HUMAX IEEE OUIs", "5G / cable gateways. Often T-Mobile Home Internet."],
            ["Sagemcom", "Sagemcom Broadband IEEE OUIs", "ISP gateways (Comcast and others)."],
            ["Arcadyan", "Arcadyan IEEE OUIs", "ISP gateways / mesh. Verizon / T-Mobile OEM."],
            ["Askey", "Askey Computer IEEE OUIs", "ISP / 5G gateways. T-Mobile OEM."],
            ["Calix", "Calix IEEE OUIs", "Fiber gateways (GigaSpire class)."],
            ["Nokia", "Nokia Solutions and Networks IEEE OUIs", "Gateways / small cells. Not Nokia phones."],
            ["AirTies", "AirTies IEEE OUIs", "ISP mesh extenders."],
            ["Tenda", "Wi-Fi Tenda* plus Tenda IEEE OUIs", "Consumer APs / routers."],
            ["Sercomm", "Sercomm IEEE OUIs", "ISP cable / fiber OEM gateways."],
            ["Luxul", "Luxul IEEE OUIs", "SMB APs."],
            ["Sophos", "Sophos IEEE OUIs", "Firewall / AP. ISP / routers class."],
            ["AUMOVIO", "AUMOVIO IEEE OUIs", "Ex-Continental vehicle Wi-Fi. Vehicle class."],
            ["CenturyLink", "Wi-Fi CenturyLink*", "Factory gateway SSID."],
            ["GM hotspot", "Wi-Fi myCadillac* / myGMC* / myBuick* / CADILLAC* / BUICK* / CHEVROLET*", "GM in-car hotspots. myChevrolet* stays on Chevrolet hotspot."],
            ["Audi MMI", "Wi-Fi Audi_MMI_*", "Audi in-car hotspot."],
            ["Extreme", "Extreme Networks IEEE OUIs", "Campus APs. Cloud SSIDs are site names. ISP / routers class."],
            ["Adtran", "Wi-Fi Adtran* plus Adtran IEEE OUIs", "Fiber gateways. Common CenturyLink / Lumen / Quantum Fiber OEM."],
            ["Cambium", "Wi-Fi Cambium* / cnPilot* / IgniteNet* plus Cambium and IgniteNet IEEE OUIs", "WISP / cnPilot APs. Quantum Fiber factory names can also hit."],
            ["TRENDnet", "Wi-Fi TRENDnet* plus TRENDnet IEEE OUIs", "Consumer APs. Renamed SSID still hits on BSSID."],
            ["Cudy", "Wi-Fi Cudy* plus Cudy IEEE OUIs", "Travel / home routers. Factory Cudy-XXXX."],
            ["SnapAV", "Wi-Fi Control4* / Wattbox* plus SnapAV IEEE OUIs", "Control4 / Wattbox home-AV APs. Home IoT class."],
            ["Vantiva", "Wi-Fi Technicolor* / THOMSON* / Vantiva* plus Vantiva and Technicolor IEEE OUIs", "Ex-Technicolor ISP gateways. ISP names after provision often miss."],
            ["Hitron", "Wi-Fi Hitron* plus Hitron IEEE OUIs", "Cable gateways. Common Xfinity OEM."],
            ["Actiontec", "Wi-Fi Actiontec* plus Actiontec IEEE OUIs", "FiOS / Frontier gateways. Verizon names stay on Verizon."],
            ["Buffalo", "Wi-Fi Buffalo* / AirStation* plus BUFFALO.INC IEEE OUIs", "AirStation / routers. Renamed SSID still hits on BSSID."],
            ["Grandstream", "Wi-Fi Grandstream* / GWN* plus Grandstream IEEE OUIs", "GWN APs. Glob is prefix, not a mid-name GWN word."],
            ["Edgecore", "Edgecore IEEE OUIs", "Campus / open Wi-Fi APs. Cloud SSIDs are site names. ISP / routers class."],
            ["WatchGuard AP", "WatchGuard Technologies IEEE OUIs 00:01:21 / 00:90:7F", "Firewall / AP. Not WatchGuard Video 00:1D:96. ISP / routers class."],
            ["Mojo", "Mojo Networks IEEE OUIs", "Now Arista Cognitive Wi-Fi. No Mojo* SSID glob. ISP / routers class."],
            ["Winegard", "Wi-Fi Winegard* plus Winegard IEEE OUI 00:17:1A", "RV / marine Wi-Fi. Vehicle class."],
            ["Inseego", "Wi-Fi Inseego* plus Inseego Wireless IEEE OUIs", "5G / MiFi hotspots. Not a bare MiFi*."],
            ["Franklin", "Wi-Fi RG3100* plus Franklin Technology Inc. IEEE OUI 50:FB:FF", "5G / LTE home-internet gateway (often carrier-issued). Not Franklin Electric. Not Qualcomm chip IEs. ISP / routers class."],
            ["Synology", "Wi-Fi Synology* plus Synology IEEE OUIs", "NAS / router APs."],
            ["NETGEAR", "Wi-Fi NETGEAR* / Orbi* plus NETGEAR IEEE OUIs", "Renamed SSID still hits on BSSID."],
            ["TP-Link", "Wi-Fi TP-Link* / TP-LINK* / Deco* plus TP-Link IEEE OUIs", "Not Tapo names. Tapo cameras on a TP-Link OUI can also hit this row."],
            ["ASUS", "Wi-Fi ASUS* plus ASUSTeK IEEE OUIs", "Renamed SSID still hits on BSSID. Laptop hotspot can hit."],
            ["Linksys", "Wi-Fi Linksys* / Velop* plus Linksys IEEE OUIs", "Some Velop use Belkin OUIs."],
            ["Eero", "Wi-Fi eero* plus eero inc. IEEE OUIs", "Not Amazon Technologies (Echo)."],
            ["Google Wifi", "Google Wifi / Nest Wifi", "Not Pixel BLE. Not Nest-* cameras. Not Google Inc IEEE OUIs."],
            ["Huawei", "Wi-Fi HUAWEI* / Huawei* plus Huawei IEEE OUIs", "CPE and phones share those OUIs. A public-OUI phone hotspot also hits; randomized hotspots need the factory SSID. Not Honor. ISP / routers."],
            ["Plume", "Wi-Fi Plume* / SuperPod* plus Plume Design IEEE OUI 60:B4:F7", "SuperPod / HomePass mesh. House SSIDs still hit on that OUI. ISP-branded pods often use Arris / Sercomm / Hitron instead. ISP / routers."],
            ["Phone hotspot", "Wi-Fi AndroidAP* / Galaxy-* / Galaxy * / Pixel-* / Pixel *", "Factory personal-hotspot SSIDs. BSSID usually randomized. iPhone stays Apple Device. Generic DIRECT-* is unmatched unless a product family also hits (Raven, Roku, Epson). Custom names do not match. Phones / PCs."],
            ["D-Link", "Wi-Fi D-Link* / DIR-* plus D-Link IEEE OUIs", "Renamed SSID still hits on BSSID."],
            ["Belkin", "Wi-Fi Belkin* plus Belkin IEEE OUIs", "Some Linksys Velop land here."],
            ["Xfinity", "xfinitywifi / XFSETUP* / Xfinity* plus Comcast IEEE OUIs", "Most boxes are Arris / Hitron OEM."],
            ["Spectrum", "SpectrumSetup* / MySpectrumWiFi* / Spectrum Mobile / Spectrum Free Trial", "Charter gateway and mobile hotspots. No Charter IEEE OUI."],
            ["AT&amp;T", "attwifi / ATT-WIFI* / ATT-GUEST* / ATT??????? plus AT&amp;T and 2Wire IEEE OUIs", "Pace-style factory names. Many boxes are Arris OEM."],
            ["Verizon", "Verizon-* / Fios-* / MyVerizon* plus Verizon IEEE OUIs", "Not Verizon Connect / Telematics. Many FiOS boxes are Actiontec OEM."],
            ["GL.iNet", "GL-iNet* / GL-MT* / GL-AR* / GL-AXT* plus GL Technologies IEEE OUI 94:83:C4", "Travel routers. Not a bare GL-*. Many boards still use chip-module prefixes."],
            ["Ruijie", "Wi-Fi @Reyee* / Reyee* / Ruijie* plus Ruijie Networks IEEE OUIs", "Reyee campus / SMB APs. Renamed SSID still hits on BSSID. ISP / routers class."],
            ["DWnet", "DWnet Technologies IEEE OUIs", "Consumer / SMB APs. Cloud SSIDs are house names. ISP / routers class."],
            ["WAVLINK", "Wi-Fi WAVLINK* plus Winstars IEEE OUI 80:3F:5D", "Consumer APs. ISP / routers class."],
            ["Samsung SmartTags", "Names SmartTag / Smart Tag / Galaxy SmartTag; UUID FD5A; mfg 0x0075", "FD5A is the usual SmartTag service."],
            ["Tile Trackers", "Name Tile; UUIDs FEED, FEDD; mfg 0x00C7", "Older Tiles are noisier on name than on UUID. Decode fields: FEED 8-byte rotating private id (not a serial). §9.6."],
            ["iBeacon", "Apple 0x004C type 0x02 length 0x15; names *iBeacon*", "Protocol, not a vendor. Dropped when a product signature already labeled the radio (Sony TV, Tesla phone-key). Minew / Estimote / Kontakt / Target Atrius basket still dual-label. Not Nearby Info 0x10 / AirTags 0x12 / AirPods 0x07. Not Eddystone FEAA."],
            ["Target Atrius basket", "Apple iBeacon UUID 5993A94C-7D97-4DF7-9ABF-E493BFD5D000; service 0xB1BB", "Target shopping-basket / Atrius tags. Two stores: hundreds of unnamed radios, unique major/minor, TX 0xC3. Not Acuity company 0x0346 on those radios. Dual-labels with iBeacon. Retail beacons class."],
            ["Minew", "IEEE OUI AC:23:3F; names Minew*", "Shenzhen Minew beacons / sensors. Field AC:23:3F often also iBeacon or Eddystone."],
            ["Estimote", "BLE company 0x015D; names Estimote*", "Location beacons / stickers. Decode fields: frame type (Nearable / Telemetry). Packed sensors are not expanded. §9.6."],
            ["Kontakt.io", "BLE company 0x01FD; names Kontakt*", "Kontakt Micro-Location beacons. Decode fields: UUID FE6A Location packet (battery / TX / channel / moving). §9.6."],
            ["Penguin", "Name / glob Penguin*. Extra attention filled. Stock bookmark.", "Flock-family / roadside camera provisioning name. Name-only. Low uniqueness. Beeps on a new match."],
            ["Pigvision", "Name / glob Pigvision*. Extra attention filled. Stock bookmark.", "Flock-family / roadside camera name. Name-only. Beeps on a new match."],
            ["FS Ext Battery", "Name FS Ext Battery; globs FS_*, FS Ext*; Silicon Labs OUIs 04:0D:84, 1C:34:F1, 38:5B:44, 94:34:69, B4:E3:F9, F0:82:C0, 58:8E:81, EC:1B:BD, 90:35:EA. Extra attention filled. Stock bookmark. Surveillance class.", "Usually a Flock-style camera pack. Name is medium confidence. OUI-only hits are low confidence. Beeps on a new match."],
            ["Raven / ShotSpotter", "Names RAVEN / ShotSpotter / SoundThinking; UUIDs 3100–3500; mfg 0x09C8; OUI D4:11:D6", "Flock Raven or ShotSpotter-style acoustic gunshot sensor. Wi-Fi Direct SSIDs such as DIRECT-rR-Raven-* hit on the Raven name, not a catch-all DIRECT- prefix. Surveillance class."],
            ["Digital Ally", "IEEE 00:23:BD; names FirstVu / Digital Ally / EVO-HD / VuLink", "Body-worn or in-car camera. Extra attention. Quiet LTE units stay off-air."],
            ["Limitless Pendant", "BLE service 632de001-604c-446b-a80f-7963e950f3fb; name Limitless", "Wearable conversation recorder. Extra attention."],
            ["Bee Pendant", "BLE service 03d5d5c4-a86c-11ee-9d89-8f2089a49e7e; Bee Pioneer", "Amazon Bee Pioneer recorder. Extra attention."],
            ["Omi", "Name Omi / OpenGlass; BLE 23ba7924. Not Arduino 19B10000.", "Pendant or OpenGlass camera glasses. Extra attention."],
            ["Friend Pendant", "BLE service 1a3fd0e7-b1f3-ac9e-2e49-b647b2c4f8da; Friend Pendant", "Wearable necklace that listens. Extra attention."],
            ["Chipolo", "Name / glob Chipolo*", "Find Hub / Find My tags. Name-only."],
            ["Pebblebee / moto tag", "Pebblebee*, moto tag / Moto Tag", "Find Hub locators. Name-only."],
            ["Verkada", "Name / glob Verkada*. Extra attention filled. Stock bookmark.", "Cloud cameras / LPR on buildings and some public sites. Name-only. Beeps on a new match."],
            ["Motorola Vigilant", "Vigilant, Vigilant Solutions, Motorola Vigilant. Extra attention filled. Stock bookmark.", "LPR used by agencies and parking. Name-only when advertised. Beeps on a new match."],
            ["eufy Security", "eufy / EufyCam / eufy*", "Anker cameras and tags."],
            ["Wyze", "Wyze / WyzeCam / Wyze*", "Consumer cameras. Cameras class, not Surveillance."],
            ["Ring", "Ring-*, Ring Doorbell / Camera / Setup", "Avoids a bare Ring substring."],
            ["Arlo", "Arlo / Arlo* / ARLO_VMB_*; Arlo Technology IEEE OUIs", "Cameras and VMB base-station APs. Renamed SSID still hits on BSSID."],
            ["Nest", "Nestcam, Nest Cam, Nest-Hello, Nest-*", "Avoids a lone Nest word. Cameras, not the Nest Thermostat BLE row."],
            ["Nest Thermostat", "BLE company 0x01B5 (Nest Labs); names Nest Thermostat*", "Not Google 0x00E0. Nest Temperature Sensors on E / 3rd-gen can hit 0x01B5. Wall units often Wi-Fi-only after setup."],
            ["Nest Weave", "BLE UUIDs 0xFEAF / 0xFEB0 (Nest Labs Weave-over-BLE)", "Field N02QP is Protect 2nd gen (Topaz2, Weave product 0x0009). Same UUID on other Weave cameras/thermostats/sensors. Not Matter 0xFFF6. Decode fields: FEAF identification block (vendor / product / pairing / device id). §9.6."],
            ["ecobee", "BLE company 0x07D6; names ecobee*", "Premium BLE (setup / Spotify). Room sensors can hit the same ID."],
            ["Sensi", "BLE names Sensi*", "Emerson / Copeland setup BLE. Not Emerson 0x04DF."],
            ["Honeywell Home", "BLE Honeywell Home* / Lyric Thermostat / Lyric T* / Amazon Smart Thermostat*", "Resideo-built. Not Honeywell 0x0526 or Resideo 0x0B01 (too broad). T9/T10 sensors are 900 MHz."],
            ["Honeywell Xenon HC", "BLE Xenon_*HC* / Xenon_CCB-U00-H* / 1962h* / 1952h* / 1902h*", "Healthcare barcode scanner / charge-comm base. Not warehouse CCB-U00-G, not Honeywell Home. Health class."],
            ["Omron", "BLE company 0x020E; names OMRON* / HEM-* / BLESmart_*", "Omron Healthcare cuffs and scales. Not industrial OMRON 0x02D5. Health class."],
            ["Withings", "BLE Withings* / WBS0* / BPM Connect", "Body scales and BPM Connect. Not Nokia phones or ISP gateways. Health class."],
            ["Dexcom", "BLE Dexcom*", "G6 / G7 glucose sensors. Pattern match, not a patient. Health class."],
            ["Haiku Fan", "BLE UUID E0FC1000-1FB1-4168-96DF-B3F057A86E01; names Haiku Fan / Mammoth Fan", "Big Ass Fans. Custom 128-bit service."],
            ["Tuya", "BLE company 0x07D0; UUID FD50; names TUYA*", "Plugs / lights / cameras / sensors. Not two-letter TY. Decode fields: bound flag and protocol version (UUID bytes encrypted). §9.6."],
            ["ASSA ABLOY", "BLE 0x012E / HID 0x0124 / Yale 0x0BDE; UUID FCBF; Seos UUID 00009800-…; names Seos / Yale*", "Access control class. Locks, readers, Seos credentials. Phones on HID Mobile Access can hit Seos. Not Apple FCB2."],
            ["August", "BLE company 0x01D1; UUID FE24; names August*", "August Home locks. Field L40A33A. Not ASSA 0x012E."],
            ["Schlage", "BLE Allegion 0x013B; UUID FCF4; names SCHLAGE*", "Encode and other Allegion BLE."],
            ["Nuki", "Custom UUIDs a92ee000–a92ee300 / a92ae200; names Nuki*", "Keyturner / Ultra / Opener. No SIG company ID."],
            ["SALTO", "BLE company 0x0199; names SALTO*", "Access control class. Commercial lock or reader."],
            ["dormakaba", "BLE company 0x0C64; names dormakaba* / Saflok* / Oracode*", "Access control class. Hotel / commercial locks."],
            ["Lockly", "BLE names LOCKLY*", "Name only. Not Nordic 0x0059."],
            ["Kevo", "BLE Unikey 0x015E; names Unikey* / Kevo*", "Kwikset Kevo."],
            ["Master Lock", "BLE company 0x014B; names Master Lock*", "Bluetooth padlocks. Not a bare Master substring."],
            ["igloohome", "BLE company 0x05BA; names igloohome*", "Keyboxes / locks. Not a bare igloo."],
            ["Tedee", "BLE company 0x0725; names Tedee*", "Retrofit locks."],
            ["Paxton", "BLE company 0x0196; names Paxton* / Net2*", "Access control class. Net2 door reader or access panel."],
            ["Kwikset", "BLE names Kwikset*", "Not Spectrum Brands 0x0356. Kevo is the Unikey row."],
            ["myQ", "BLE company 0x0878 (Chamberlain); UUID 26D91A37-…; names MyQ-*", "Garage door hubs."],
            ["Hatch", "BLE company 0x0434; OUI C8:FA:9C; names Hatch Rest* / Restore* / Mini*", "Hatch Baby sound machines. Not 180A/180F. Home IoT."],
            ["Orbit B-hyve", "Names bhyve* / B-hyve*; OUI 44:67:55; UUID FE32", "Orbit hose timers. Not Pro-Mark company 0x047F alone. Home IoT."],
            ["Samsung appliance", "Wi-Fi [fridge]* / [oven]* / [range]* / [cooktop]* / [refrigerator]*", "Family Hub / range setup APs. Not SmartTags. Home IoT."],
            ["EcoWater", "Wi-Fi H2O- plus 12 characters", "Water-softener setup AP (often the MAC with no colons). Not a bare H2O word. Home IoT."],
            ["Retail LED sign", "BLE UUID 56D63956-93E7-11EE-B9D1-0242AC120002", "LED message displays. Advertised name is the sign text (FOOD, STORY BAORD), not a product name. Not Earda OUI F0:96:02. Signage class."],
            ["Electronic shelf label", "BLE UUID 0x1857 (SIG ESL Service)", "BT 5.4 PAwR ESL. Most Hanshow / SES-imagotag tags use a proprietary radio and will miss. Signage class."],
            ["Chevrolet hotspot", "Wi-Fi myChevrolet*", "GM in-car hotspot. Factory SSID; BSSID often randomized."],
            ["Rivian", "BLE company 0x0941; names Rivian*", "Phone-as-key / camp speaker / sensors."],
            ["Govee", "BLE names Govee* / GBK_* / ihoment_* / GV5108* / GVH5*", "Lights and sensors (H5075 / H510x hygrometers). Identity is name-only. Decode fields: 0xEC88 H5074/H5075 and 0x0001 H510x temp / humidity / battery after that match. Lights may not fit those layouts. §9.6."],
            ["HP", "BLE company 0x0065; UUID FE78; ENVY*; Wi-Fi HP-Print*", "Printers. Office / home noise."],
            ["Epson", "Wi-Fi / BLE names *EPSON-ET-* / *EPSON-WF-*", "EcoTank and WorkForce. Not Seiko Epson 0x0040. Home IoT."],
            ["LG webOS TV", "BLE names [LG] webOS* / webOS TV*; UUID FEB9", "Living-room TVs. Not LG company 0x00C4. Unnamed FEB9 can be other LG radios. Home IoT."],
            ["Roku", "Wi-Fi Roku IEEE OUIs (BSSID or vendor IE C8:3A:6B); DIRECT-roku* / Roku-*", "Streaming stick / Roku TV. Hidden Wi-Fi Direct remote APs hit on the vendor IE even with a randomized BSSID. Not WPS 00:50:F2 or P2P 50:6F:9A. Home IoT."],
            ["Nespresso", "BLE company 0x0225; Vertuo/Venus/Barista/Mini UUIDs; names Vertuo* / Venus_*", "Coffee machines. Not a bare Venus word. Home IoT."],
            ["RadiaCode", "BLE UUID E63215E5-7003-49D8-96B0-B024798FB901; names RadiaCode*", "Radiation detectors (101/102/103/Zero). Field 0x77AC is not SIG assigned. Home IoT."],
            ["Shokz", "BLE names OpenRun / LE-OpenRun / OpenFit / Shokz", "Bone-conduction headphones. Not Battery 0x180F and not Qualcomm FD92. Audio class."],
            ["Mercedes MBUX", "Wi-Fi MBUX*", "In-car hotspot. Factory SSID."],
            ["Motive", "Wi-Fi Motive * / Motive_* / Motive Hotspot* / KeepTruckin*", "KeepTruckin ELD / fleet hotspot. Not substring Motive (Locomotive). Vehicle class."],
            ["PeopleNet", "Wi-Fi PNet* plus PeopleNet IEEE OUI 98:5D:46", "Fleet ELD / truck hotspot. Not Motive. Vehicle class."],
            ["Uconnect", "Wi-Fi Uconnect*", "Stellantis in-car hotspot. Vehicle class."],
            ["CarPlay", "Wi-Fi CarPlay* / name contains CarPlay", "Alpine / head-unit in-car hotspot. Vehicle class, not ISP."],
            ["CARLINK", "Wi-Fi CARLINK-?????? (6 hex); Panasonic Automotive OUI CC:57:63; Zhuolian 68:8F:C9", "CarPlay / Android Auto adapter or head-unit hotspot. Alps Alpine E0:2D:F0 is common on the same cluster but is not an OUI-only rule (Toyota / Lexus head units). Vendor IE 00:A0:40 (old Apple) is not cataloged. Vehicle class, not ISP."],
            ["Cradlepoint", "Wi-Fi IBR* / IBR600* / IBR1100* / IBR1700* / R-series / Cradlepoint* plus CradlePoint IEEE OUIs 00:30:44 / 00:E0:1C. Extra attention filled. Stock bookmark.", "Ericsson Cradlepoint vehicle routers. Common in US police fleets; also government, municipal, and other corporate fleets. Hidden/renamed SSIDs still hit on BSSID. Pattern match, not that agency. Beeps on a new match. Public safety class — used in law enforcement, not exclusive to it."],
            ["AirLink", "Wi-Fi AirLink* plus Sierra Wireless / AirLink Communications IEEE OUIs. Extra attention filled. Stock bookmark.", "Sierra Wireless AirLink vehicle gateways. Common in US police fleets; also government, municipal, and other corporate fleets. Hidden/renamed SSIDs still hit on BSSID. Not AirLink WiFi Networking 00:23:D3. Pattern match, not that agency. Beeps on a new match. Public safety class — used in law enforcement, not exclusive to it."],
            ["Compex", "Wi-Fi 114K-* plus Compex IEEE OUIs 04:F0:21 / 00:80:48 / 00:40:29. Extra attention filled. Stock bookmark.", "Some public-safety vehicle APs. Same OUIs on other Compex radios. Government, municipal, and other corporate fleets likely run some of the same kit. Pattern match, not that agency. Beeps on a new match. Public safety class — used in law enforcement, not exclusive to it."],
            ["Novatel Wireless", "Inseego IEEE OUI 28:80:A2. Extra attention filled. Stock bookmark.", "Ex-Novatel Wireless. Reported in public-safety vehicle AP work. Same prefix can also hit the Inseego row. Government, municipal, and other corporate fleets likely run some of the same kit. Pattern match, not that agency. Beeps on a new match. Public safety class — used in law enforcement, not exclusive to it."],
            ["Utility Inc", "Utility, Inc IEEE OUIs 00:09:BC / 00:16:ED. Extra attention filled. Stock bookmark.", "Reported in public-safety vehicle AP work. Government, municipal, and other corporate fleets likely run some of the same kit. Pattern match, not that agency. Beeps on a new match. Public safety class — used in law enforcement, not exclusive to it."],
            ["Samsara", "BLE company 0x0B6B; names Samsara*", "Fleet ELD / trailer / gateway. Vehicle class."],
            ["Goodyear TPMS", "BLE company 0x0B99", "Intelligent tire / SightLine-class BLE. Not 315/433 MHz factory stems. Randomized MAC; vendor is the company ID. Vehicle class."],
            ["Schrader TPMS", "BLE company 0x0601", "AirCheck BLE / trailer / RV. Not Nokia 0x0001 clones. Vehicle class."],
            ["Pacific TPMS", "BLE company 0x0E32", "Pacific Industrial OEM TPMS. Vehicle class."],
            ["Huf", "BLE company 0x070A", "Huf Hülsbeck TPMS and vehicle access. Not only a valve stem. Vehicle class."],
            ["FOBO TPMS", "BLE company 0x0127 (Salutica); UUID 00EE; names FOBO*", "Motorcycle / car aftermarket BLE TPMS. Vehicle class."],
            ["Aftermarket TPMS", "BLE names TPMS*; UUID FBB0; manufacturer 0x0001 data 80/81/82/83", "Cheap valve-cap BLE TPMS (TPMS1 / FBB0 family). Not a bare Nokia 0x0001 match. Decode fields: wheel, pressure kPa, temperature, battery, alarm. Vehicle class. §9.6."],
            ["SYTPMS", "BLE exact name BR; UUID 0x27A5", "SYTPMS / BR bicycle or scooter BLE TPMS. Decode fields: gauge psi, temperature, battery, motion. Vehicle class. §9.6."],
            ["TireCheck", "BLE company 0x0BA2; names TireCheck*", "TireCheck BLE tire sensor. Vehicle class."],
            ["TPMS service", "BLE UUID 0x1860 (SIG TPMS Service)", "Any sensor advertising the Bluetooth SIG Tire Pressure Monitoring System service. Vehicle class."],
            ["Ruuvi", "BLE company 0x0499; names Ruuvi*", "Broadcast temp / humidity / pressure / motion tags. Home IoT. Decode fields: Data Format 5 (RAWv2) and Format 3 humidity/pressure/accel/battery. Format 3 temperature is sign-magnitude, not a plain int. §9.6."],
            ["Blue Maestro", "BLE company 0x0133", "Tempo Disc temp / humidity loggers. Home IoT. Decode fields: battery, interval, logs, temperature, humidity. §9.6."],
            ["SensorPush", "BLE UUIDs EF090000-…090AA9 / …090AB0; names SensorPush*", "HT / HTP temp / humidity. Custom 128-bit services. Home IoT."],
            ["Tapo", "Tapo / Tapo*", "TP-Link cameras."],
            ["Reolink", "Reolink / Reolink*", "Consumer cameras."],
            ["Hikvision", "Hikvision / HIKVISION / Hikvision*. Extra attention filled. Stock bookmark.", "Commercial CCTV and some public poles. Name-only. Beeps on a new match."],
            ["Dahua", "Dahua / DAHUA / Dahua*. Extra attention filled. Stock bookmark.", "Commercial CCTV and some public poles. Name-only. Beeps on a new match."],
            ["Meshtastic", "Meshtastic / Meshtastic_*; UUID 6ba1b218…", "LoRa mesh nodes. Strong name + service UUID."],
            ["Helium", "Helium / Helium*", "LoRaWAN / Helium hotspot names."],
            ["Genetec AutoVu", "Genetec, AutoVu. Extra attention filled. Stock bookmark.", "Municipal / parking ALPR. Name-only. Beeps on a new match."],
            ["BlueTOAD Spectra", "IEEE OUI 00:14:7B (Iteris); names BlueTOAD* / Vantage Velocity / Spectra CV / TrafficCast / VantageARGUS / BlueARGUS. No Extra attention. Not a stock bookmark.", "Iteris roadside Bluetooth travel-time reader (Vantage Velocity, now Spectra / Spectra CV). Samples passing phones and in-car Bluetooth; matching at two points gives speed. Quiet / Ethernet-only cabinets and 5.9 GHz C-V2X will not appear. Iteris OUI can also hit other Iteris roadside kit. Pattern match, not that cabinet."],
            ["BlipTrack", "IEEE OUI 00:0E:A5 (BLIP Systems); names BlipTrack* / BLIP Systems. No Extra attention. Not a stock bookmark.", "Roadside Bluetooth/Wi-Fi travel-time sensor. Same job as BlueTOAD. Quiet / Ethernet-only cabinets may not advertise. Pattern match, not that cabinet."],
            ["Hanwha Wisenet", "IEEE OUI 00:09:18 (Samsung Techwin); names Wisenet* / *_WISENET / Hanwha*. Extra attention filled. Stock bookmark.", "Hanwha Vision / Wisenet cameras. Commercial CCTV and some public poles. Setup SSID is the stronger hit. Beeps on a new match."],
            ["Uniview", "IEEE Zhejiang Uniview OUIs 14:BA:88 / 48:EA:63 / 6C:F1:7E / 88:26:3F / C4:79:05; names Uniview* / UNV-* / Uniarch*. Extra attention filled. Stock bookmark.", "Uniview / UNV cameras. Commercial CCTV and some public poles. Beeps on a new match."],
            ["Rhombus", "IEEE OUI CC:47:BD; names Rhombus*. Extra attention filled. Stock bookmark.", "Rhombus cloud cameras. BLE often only while unregistered or offline. Beeps on a new match."],
            ["MeshCore", "BLE names MeshCore / MeshCore_*. No Extra attention.", "MeshCore LoRa companion. Not Nordic UART UUID 6E400001 (every ESP32 serial board)."],
            ["goTenna", "BLE UUID 1276aaee-df5e-11e6-bf01-fe55135034f3; names goTenna*. No Extra attention.", "goTenna Mesh or Pro companion. The UHF mesh is inaudible. Pro is sold to agencies. Pattern match, not that operator."],
            ["SenseCAP", "Wi-Fi SenseCAP / SenseCAP_*. No Extra attention.", "Seeed SenseCAP LoRaWAN / Helium gateway setup AP. Quiet on Ethernet. Helium-named units can also hit Helium."],
            ["RAK WisGate", "Wi-Fi RAK7* / RAK7268* / WisGate*. No Extra attention.", "RAKwireless WisGate LoRaWAN gateway setup AP. Quiet on Ethernet."],
            ["GhostESP", "Wi-Fi GhostNet / GhostNet*. Extra attention filled. Stock bookmark.", "GhostESP ESP32 audit firmware default AP. Same boards are DIY. Not proof of an attack. Beeps on a new match."],
            ["Bruce", "Wi-Fi BruceNet / BruceNet*. Extra attention filled. Stock bookmark.", "Bruce ESP32 pentest firmware default AP. Evil-portal SSIDs look like ordinary Wi-Fi and miss. Not proof of an attack. Beeps on a new match."],
            ["Rekor", "Rekor / Rekor*. Extra attention filled. Stock bookmark.", "Highway / transit ALPR. Name-only. Beeps on a new match."],
            ["Axon", "OUI 00:25:DF (Axon Enterprise); names Axon Body / Fleet / Dock / BWCDEVICE / Axon*; UUID 0xFE6C (Axon Signal). Extra attention filled. Stock bookmark.", "Public safety class — used in law enforcement, not exclusive to it (government, municipal, and other corporate fleets likely run some of the same kit). Body-worn, in-car, dock, or TASER. Body 3/4 often advertise BLE on the public OUI. Not that officer. Not Axon Networks 00:58:28. ZTE Axon phones can hit the name. Beeps on a new match."],
            ["WatchGuard Video", "OUI 00:1D:96; names WatchGuard / VISTA WiFi / VISTA XLT. Extra attention filled. Stock bookmark.", "Public safety class — used in law enforcement, not exclusive to it (government, municipal, and other corporate fleets likely run some of the same kit). WatchGuard Video (now Motorola) body-worn / in-car. Not WatchGuard firewall 00:01:21. Patrol units may stay quiet. Beeps on a new match."],
            ["Ray-Ban / Meta glasses", "BLE company IDs 0x01AB, 0x058E, 0x0D53; names Ray-Ban / Meta View / Oakley Meta. Extra attention filled. Stock bookmark.", "Often Ray-Ban Meta. Same IDs on Quest and other Meta wearables. Not proof of recording. Beeps on a new match."],
            ["Snap Spectacles", "BLE company ID 0x03C2; Spectacles names. Extra attention filled. Stock bookmark.", "Snap Spectacles or other Snap BLE. Not proof of recording. Beeps on a new match."],
            ["Avigilon", "Avigilon / Avigilon*. Extra attention filled. Stock bookmark.", "Motorola cameras / LPR on municipal poles and commercial sites. Name-only. Beeps on a new match."],
            ["Axis", "AXIS-* / Axis Camera. Extra attention filled. Stock bookmark.", "Municipal / public CCTV poles. Name-only. Beeps on a new match."],
            ["UniFi", "UniFi, Ubiquiti, UAP-* (either radio)", "Name only. Use UniFi AP when you want BSSID matching. ISP / routers class."],
            ["UniFi AP", "Wi-Fi UniFi* / UAP-* / UBNT* plus Ubiquiti IEEE OUIs (BSSID or vendor IE)", "Campus / city / house APs. ISP / routers class, not Surveillance. Virtual BSSIDs miss the MAC OUI; a Ubiquiti vendor IE still hits."],
            ["UniFi Protect", "BLE names UVC G3/G4/G6 Instant", "Protect Instant cameras in BLE setup. Not AP BSSID OUIs. Custom 16-bit 252A/2529 not used. Surveillance class."],
            ["Hobby BLE serial", "BLE names HMSoft / HM-10 / CC41 / AT-09 / JDY-08/10/16/31 / BT05 / MLT-BT05 / ESP32 (BLE only). Extra attention filled. Stock bookmark.", "Cheap UART modules. Same boards appear in DIY and in some pump/ATM overlays. Not a skimmer detector. Not Classic HC-05/HC-06. Beeps on a new match. Hide on Filters if those names are local noise."],
            ["Hak5 Pineapple", "Names Pineapple_XXXX / Hak5 / WiFi Pineapple. Extra attention filled. Stock bookmark.", "Setup/management AP. Not Alfa OUI 00:C0:CA. PineAP clones look like ordinary SSIDs. Beeps on a new match."],
            ["Flipper Zero", "OUI 0C:FA:22; BLE names Flipper*. Extra attention filled. Stock bookmark.", "Flipper Devices IEEE (2024). Custom firmware can rename. Not proof of an attack. Beeps on a new match."],
            ["Pwnagotchi", "MAC DE:AD:BE:EF:DE:AD; name pwnagotchi. Extra attention filled. Stock bookmark.", "Handshake-collector beacon. Beeps on a new match."],
            ["Marauder / Deauther", "Names MarauderAP / Marauder / Deauther. Extra attention filled. Stock bookmark.", "ESP32 Marauder or Spacehuhn-style defaults. Same boards are DIY. Beeps on a new match."],
            ["Porkchop", "Names PORKCHOP / M5PORKCHOP; vendor IE 50:52:4B. Extra attention filled. Stock bookmark.", "M5PORKCHOP Cardputer or CYD port. CYD remote AP is named PORKCHOP. BACON fake APs brand 50:52:4B. Not Espressif OUI. BLE spam not matched. Beeps on a new match."],
        ]
    stock_sigs.sort(key=lambda row: row[0].replace("&amp;", "&").lower())
    flow.append(table(
        ["Signature", "Primary rules", "Confidence notes"],
        stock_sigs,
        [1.45 * inch, 2.7 * inch, 2.35 * inch],
    ))
    flock_ouis = [
        "70:C9:4E", "3C:91:80", "D8:F3:BC", "80:30:49", "B8:35:32", "14:5A:FC", "74:4C:A1",
        "08:3A:88", "9C:2F:9D", "C0:35:32", "94:08:53", "E4:AA:EA", "F4:6A:DD", "F8:A2:D6",
        "24:B2:B9", "00:F4:8D", "D0:39:57", "E8:D0:FC", "E0:4F:43", "B8:1E:A4", "70:08:94",
        "3C:71:BF", "58:00:E3", "5C:93:A2", "64:6E:69", "48:27:EA", "A4:CF:12", "82:6B:F2",
    ]
    oui_cols = 4
    oui_w = 6.5 * inch / oui_cols
    oui_head = [Paragraph(
        "<b>Flock-related OUI inventory</b> (Flock Safety Cameras, in addition to B4:1E:52)",
        S["cell_b"],
    )] + [""] * (oui_cols - 1)
    oui_body = [
        [Paragraph(flock_ouis[i + j] if i + j < len(flock_ouis) else "", S["cell"]) for j in range(oui_cols)]
        for i in range(0, len(flock_ouis), oui_cols)
    ]
    oui_table = Table(
        [oui_head] + oui_body,
        colWidths=[oui_w] * oui_cols,
        rowHeights=[20] + [16] * len(oui_body),
        splitByRow=0,
    )
    oui_table.setStyle(TableStyle([
        ("SPAN", (0, 0), (-1, 0)),
        ("BACKGROUND", (0, 0), (-1, 0), HEADER_BG),
        ("TEXTCOLOR", (0, 0), (-1, 0), ACCENT_DK),
        ("GRID", (0, 0), (-1, -1), 0.35, RULE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, PANEL]),
        ("FONTNAME", (0, 1), (-1, -1), "Courier"),
    ]))
    flow += [
        Spacer(1, 10),
        oui_table,
        P("C. Troubleshooting", "h2"),
        P(
            "If the Live display does not match the chapters above, start here. The log and Reports still "
            "run even when the list is filtered or quiet. A pattern match is not identity."
        ),
    ]
    flow.append(table(
        ["Symptom", "Likely cause", "Action"],
        [
            ["Permission gate on every launch", "A required runtime permission was denied or reset by the OS.", "Grant Location (Precise), Nearby Wi-Fi, Bluetooth scan/connect, Notifications. Turn system Location on. Full list: §4.5.2."],
            ["I want max hear / the phone gets hot", "High performance, Keep screen on, and Faster Wi-Fi use the radios and the display hard.", "Expected for a sit. Walk through §4.5. Plug in or use a pack. Drop to Balanced and turn Faster Wi-Fi off when you are done. Empty list is still not “safe.”"],
            ["Zero Wi-Fi rows, BLE works", "Between OS scan windows, throttle, Wi-Fi off, or Location off.", "Enable Wi-Fi and Location. Read the header: Wi-Fi next Ns or waiting on OS. Last APs should stay held; a new batch arrives about every 30 s on high performance, or ~8 s if Faster Wi-Fi AP scans is on and Developer options Wi-Fi scan throttling is off."],
            ["Zero BLE rows after it was working", "Samsung parked the scanner.", "Watch for BLE cycling or BLE parked · restarting in the header. Keep screen on while you watch. Allow background usage and Unrestricted battery if you leave the app. Toggle intensity only if it stays dead."],
            ["UI freeze then both lists empty in a crowd", "A plaza of rotating BLE addresses filled memory.", "The live set is capped at about 400 radios. Turn logging off if you do not need the file. Prefer Strength list over Hybrid in a dense crowd."],
            ["Radar looks empty, list does not", "Gone radios are drawn dim on radar, or a Live display filter is hiding them.", "Dim blips are radios past Stale / Brief hold. Check that the Live display filter is not Signatures only with no matches."],
            ["Everything is “gone”", "Stale window shorter than the advertisement interval, or the service was killed.", "Raise stale to 90–120 s, or raise intensity. Confirm the scan notification is still present. Exempt from battery optimization."],
            ["Service dies or BLE dies when the screen locks", "OEM battery manager and/or Samsung screen-off BLE policy.", "Settings → Keep screen on while watching. Settings → Allow background usage and Unrestricted battery. Leave Fieldwatch in Recents (do not swipe it away) if you want it to keep scanning."],
            ["Scan / beeps continue after I kill the app", "The scan service is still running.", "Swipe Fieldwatch out of Recents, or tap Stop on the scan notification. The Home button leaves scanning running."],
            ["Cannot delete a signature", "Delete is on the editor page, not the list.", "Open the signature and tap Delete signature at the bottom. Confirm. Restore defaults if you removed a built-in by mistake."],
            ["No Decode fields row on a signature", "That row is Wi-Fi-only (hidden SSID / vendor IE / radio=Wi-Fi), or you are on the Live display not the editor.", "Open a BLE signature (Ruuvi, Remote ID, Govee, …). Decode fields sits under Add rule. §9.6."],
            ["Decoded fields missing on detail", "The signature has no map, this advertisement is encrypted/short, or the gate (Only if) did not match.", "Open the signature → Decode fields. A note on detail means a map exists but this packet did not fit. Usual miss: offset 0 counted the company ID. Encrypted Fitbit / Find My ads stay hex. §9.6.1 / §9.6.5."],
            ["Preview is empty but the hex looks right", "Offset includes the company ID, wrong endian, or Only if hex does not match this packet.", "Count pairs on detail Raw payload (first pair = 0). Try BE if the spec is big-endian. Check Only if length vs Hex length. Open stock Ruuvi and copy the card shape. §9.6.3–§9.6.4."],
            ["Custom name edit is gone on detail", "BLE address is random / privacy (IEEE local bit or Android Random type).", "Expected on BLE — a name would not follow a rotation. Wi-Fi always has the pencil, including locally administered vehicle / mesh BSSIDs. A name you already saved still shows. Bookmark can still watch this MAC."],
            ["I want a Cameras / Drones / Surveillance chip", "Those class sits are not stock presets.", "Filters → Show only, pick the class chip, Save current as… Restore default signatures & presets puts the short stock set back and wipes custom chips."],
            ["Filters chips look dead while Pause is on", "Pause freezes only the Live display.", "Expected. Filters and Settings still take taps. The frozen list does not change until you tap Live again while you are already on that tab."],
            ["I deleted a preset but the Live display still looks the same", "Delete removes the snapshot, not the filter that is on.", "Expected. Apply another chip or tap Reset filter to change what the Live display shows."],
            ["Cannot find Radar / list / By class / how to change the view", "Display is closed, or you are looking on Settings / Filters.", "Live display → tap Tune (sliders icon, top right). View is the first dropdown. Tap Tune again or tap the dimmed list to close. §4.4, §5.3, Fig. 3."],
            ["Cannot find Title line / Subtitle line / Frequency under RSSI", "Display is closed, or you are looking on Settings.", "Live display → tap Tune (sliders icon, top right). Title and Subtitle are dropdowns under Brief hold. Frequency is a switch; channel · MHz sit under the RSSI, not on the identity line. Fig. 3."],
            ["List still shows “Apple, Inc. · AirTag…”", "Display → Subtitle is Name + type, which uses the same type guess as detail.", "IEEE vendor is on the detail page. To stop seeing the guess, set Subtitle to Advertised name or None."],
            ["A “!” on a Live display row", "A matched signature has Extra attention filled (stock: Hobby BLE serial, Axon, WatchGuard Video, Ray-Ban / Meta glasses, Snap Spectacles, Fieldy, Plaud Note, Hak5 Pineapple, Flipper Zero, Pwnagotchi, Marauder / Deauther, GhostESP, Bruce, Porkchop, Cradlepoint, AirLink, Compex, Novatel Wireless, Utility Inc, Flock, Penguin, Pigvision, FS Ext Battery, Genetec AutoVu, Rekor, Motorola Vigilant, Verkada, Avigilon, Axis, Hikvision, Dahua, Hanwha Wisenet, Uniview, Rhombus).", "Open detail and read Extra attention. Pattern match, not identity. Meta company IDs also match Quest. Fieldy / Plaud Note are recording wearables, not proof someone is recording you. Camera / ALPR rows are roadside or public CCTV / plate readers, not that pole. Hide that family on Filters if it is local noise. Not the decode hexagon and not the phosphor alerted bell."],
            ["A bell on a Live display row", "That radio already fired a watchlist alert this session (beep / voice / flash).", "Expected. It stays until you leave Fieldwatch. Extra attention is the red “!”. Newest alert sorts by the same event. Radar keeps a phosphor ring on that blip after the ping."],
            ["Hexagon on a signature chip, no Decoded fields on detail", "The hexagon means that signature has a map, not that this advertisement parsed. Govee lights share the Govee name with hygrometers; lights usually only send a name.", "Open detail: a note means the map did not fit this packet. Hygrometers are H5074/H5075 (0xEC88) or H510x (0x0001). Display → Signature names off hides the hexagon. To drop a dummy map: Signatures → row → Decode fields → Remove decode map. §5.4, §9.6."],
            ["Phones / PCs is a wall of Fast Pair", "Those are mostly already-paired account-key ads (buds or phones on an account), not someone pairing.", "Filters → Hide Fast Pair account-key. Pairing-mode still shows (chip Fast Pair pairing). Hide selected Fast Pair if you want none of them. Pocket Androids without Fast Pair stay unmatched; Apple Device is the loud Continuity crowd. §9.5."],
            ["I never see Android under Phones / PCs", "Apple Continuity is always-on; most Android phones do not broadcast a stable “I am a phone” payload.", "Expected. Fast Pair is the Android-shaped chip (often unnamed). Google is Pixel / Chromecast only. Phone hotspot only while the phone is an AP with a factory SSID. Quiet pocket Androids are unmatched BLE. §9.5."],
            ["List is a wall of text / I want less on each row", "Display extras are on, or Subtitle is still MAC.", "§5.3 / §12.11: Subtitle None, bars/Frequency/first-last off. That does not hide radios. Then Filters if you still have the wrong set."],
            ["Cannot find Mark seen / Reset seen", "Those buttons appear only while New detections only is on.", "Turn on Filters → New detections only. Mark seen and Reset seen appear on the Live display, just above the tabs."],
            ["Cannot find Start over", "It is on the Live display, not Filters.", "Turn on Filters → Moving with you. Start over sits above the tabs. It clears the GPS path, not the live list."],
            ["Watchlist never fires", "Alerts off, both Beep and Voice off, or the hit is hidden by New detections only / another filter.", "Enable Watchlist alerts, then Beep, Voice, or both. Confirm the family is bookmarked and the row would show on the Live display. Tap Test alert; raise media volume. Jump works with any of those cues. System notification is optional and off by default."],
            ["Watchlist beeps but does not speak", "Voice on watched signature was turned off, media volume is down, or the phone has no text-to-speech pack.", "Settings → Voice on watched signature (ships on). Raise media volume. Tap Test alert — Class + signature says “finder tags, Apple AirTags” after the pip if Beep is also on. Hunt never speaks."],
            ["Finder tags / Surveillance shows nothing", "Show only that class, and no matching radios are on the air.", "Empty Live display means none of that class is in earshot (a bag AirTag is the check for Finder tags). Hide these on another class does not mute labels."],
            ["Signatures only switch does nothing under Show only", "Show only already hides unmatched radios.", "The switch is dimmed while Show only has a class or selected signatures picked. Turn Show only off to use Signatures only, or use Hide these if unmatched radios should stay."],
            ["Moving with you empty after Show only Finder tags", "Class Show only, Signatures only, Named radios only, or Watched only was still on. Finder tags rotate MACs, so they often fail co-travel, and unmatched radios were hidden.", "Tap the Moving with you preset, or turn the switch on. Either one clears Show only, Signatures only, Named radios only, and Watched only. Hide these stays if you were hiding a bag tag. The path still needs about 50 m."],
            ["Watched only is empty", "No bookmarked signature is matching, and no Named radio has Alert on — or Hide these dropped the ones that did.", "Bookmark a family on Signatures, or turn Alert on a Named radio (detail bookmark). Label-only names stay on Named radios only. Hide these still drops watched cameras. Turn the switch off to see the rest of the field."],
            ["I hid AirTags, then they came back", "Class Hide these or Hide selected was turned off. Hiding only runs while that mode is on.", "Turn Hide these (Finder tags) or Hide selected back on. Reset filter is what forgets the picks."],
            ["Pause, then detail says left range", "The radio aged out after you opened detail from a running list.", "Pause first, then tap the row. Detail uses the frozen snapshot."],
            ["Only one signature chip on radar", "Radar labels the blip with the first match.", "Expected. List / Hybrid / Timeline show up to three; detail lists all."],
            ["New signature labels half the cafe", "Rule too broad (OUI of a common chip, or RADIO_KIND in OR).", "Hide it on Filters, or delete a custom row. Tighten to MAC, name, UUID, or manufacturer data."],
            ["Create-from-device matches all BLE", "The signature includes a Radio kind rule with match-any.", "Delete that signature. Open detail → Create signature from device again and keep MAC, name, or UUID rules only. Do not add Radio kind alone."],
            ["Export share sheet empty / fails", "No log lines yet, or the viewer cannot take a content URI.", "Wait for a few observations. Share to Files or Drive, not to an app that rejects text/plain."],
            ["Debrief looks empty / thin", "Live display has few radios in the last 15 minutes, unnamed BLE already evicted (~3 min), or a drive already filled the ~400 cap so earlier streets dropped.", "Scan a few minutes first. Text and PDF use the same memory snapshot, not the on-disk log. On a trip, tap Debrief every 10–15 min or at stops (§11.4.1) — radios moving with you stay in each file. Use Log export for the full hour."],
            ["Cannot find Log export / Sit export / Debrief / Signature candidates", "Those buttons are on the Reports tab.", "Bottom bar → Reports. Sit export is under Sit report. Signature candidates is under Catalog. Settings still has GPS, place names, and logging on/off."],
            ["Cannot find Debrief / AI Export", "Those buttons are on the Reports tab.", "Bottom bar → Reports. Settings still has Tag GPS, Online place names, and logging on/off. One-radio AI Export is on the device-detail page, not Reports."],
            ["Signature candidates is empty", "Logging was off, the log is short, or leftover radios are randomized / house-like / one-off MACs.", "Logging on in Settings, sit a while, then tap Signature candidates again. A family needs two distinct radios sharing a unique ID. One loud unmatched MAC is Create from device, not a family."],
            ["No vendor-IE families (Roku-class hidden APs)", "Older log rows have no vendor_ie column.", "Expected until new Wi-Fi packets are written after this build. Name globs and stable OUIs still mine from old parts. Log export of a new sit if you need IEs off-phone."],
            ["I Saved a candidate but it is still on the list", "The draft did not match those radios (rule too tight, or you changed it), or Save did not finish.", "Open Signatures and confirm the row is there. Save from a candidate draft returns to the list and re-runs it; that family should drop if the new rule hits. Cancel leaves the list unchanged."],
            ["Detail AI Export vs Reports AI Export", "Two different prompts.", "Detail = this radio (what is it?). Reports = onboard Debrief plus working data, asking for a statistical addendum (not a rewrite). Do not treat a one-radio decode as a following test."],
            ["Hunt Reset / Back to detail are hard to reach", "The cue text grew and pushed the buttons off screen.", "Reset, Back, then Beep / Vibrate sit at the bottom of Hunt. Cue and hint stay a fixed height so the dBm number does not jump."],
            ["Hunt says Further but I am walking toward it", "A wall, car, crowd, or floor is in the path; or the phone grip changed.", "Open a doorway or walk around metal and watch whether Closer returns. Reset this hunt after a body-block turn. Do not convert dBm to feet. §12.13."],
            ["Hunt went Quiet / Gone", "No ads for ~8 s, or the BLE address rotated off the Live display.", "Wait. If Gone, back to the Live display and pick the new row if it is still the same family. Randomized MACs do not stitch."],
            ["Where you were has coords but no street", "Online place names is off, or on with no internet / no geocoder.", "Expected. Stays still print lat/lon. Distance does not need internet. Turn the switch off if you want no lookup attempt."],
            ["Last fix or Debrief shows “masked” instead of lat/lon", "Privacy mode is on.", "Expected. Settings → Privacy mode hides GPS coordinates on the screen and in sit reports. Street names are omitted too. The log still has lat/lon. A TAK / CoT feed is paused. Turn Privacy mode off when you need the pin or the overlay."],
            ["The whole UI went red / I want green chips back", "Night mode is on.", "Settings → Appearance → Night mode Off. Restore defaults also turns Night mode off. Fig. 9."],
            ["ATAK map stays empty", "TAK feed off, Privacy mode on, wrong destination, or no qualifying radio with a pin.", "Settings → TAK / CoT feed On, Privacy mode Off. Extra attention and Payload location on. Destination: This phone for ATAK CIV on this handset, LAN multicast for other ATAKs. Confirm Feed status shows a send. Heard-here also needs GPS tagging and a live fix. §5.8, §12.15."],
            ["Remote ID is on the Live display but not on ATAK", "Payload location chip off, no Location message yet, or 0,0 / invalid coords.", "What to send → Payload location On. Wait for an ASTM Location message (type 1, protocol 2); Basic ID has no lat/lon but a previous Location sticks this session. 0,0 is rejected."],
            ["TAK pins sit on me, not on the other radio", "Heard-here: that family has no advertised lat/lon.", "Expected for Extra attention (Axon, glasses, Flipper, …). Remote ID Location is advertised position. GPS tagging off stops heard-here only."],
            ["TAK heard-here pin did not follow me when I walked away", "Heard-here holds the loudest hear.", "Expected. Walk closer to move it. Keep-alives stay on the same lat/lon. Not DF. §5.8.7."],
            ["Debrief is still last 15 minutes", "No named sit is running or selected.", "Expected. Reports → Start sit. While a sit is open, or you pick a saved sit in the radio list, Debrief follows that window. §5.6."],
            ["ATAK filled with café APs", "All signatures is on.", "Turn All signatures off. Field default is Extra attention + Payload location."],
            ["TAK marker vanished", "The radio left the feed, scanning stopped, or Privacy mode paused it.", "Expected. Fieldwatch sends a gone event (stale=now) when a radio leaves. Privacy pause does not; ATAK then stale-times out ~120 s. A rotated BLE MAC without a sticky UAS ID is a new uid."],
            ["Remote ID is a cloud of dots on ATAK", "UID was the BLE MAC, which Remote ID rotates.", "1.0.3 keys the aircraft on sticky UAS ID. One marker should move. Until the first Basic ID packet, it still keys on MAC, then jumps once."],
            ["GPS path stays 0 m while I drive / Moving with you says keep moving", "The phone is not giving a live fix.", "Turn on Tag detections with GPS and high-accuracy Location. Scanning must be running. Wait until the path meter is not 0, then walk or drive."],
            ["Second iPhone in the car did not show under Moving with you", "iOS rotates the BLE address, so Fieldwatch sees a new radio with an empty GPS trail.", "Expected. Use a tag with a stable MAC (AirTag/Tile in the bag) as the confidence check. Phones will not stitch as one follower."],
            ["Moving with you lists house APs after I get home", "Wi-Fi access points are excluded from this filter.", "Expected. A loud AP you drive past paints your hear-time path and would look like co-travel, so APs never qualify. Bag and car BLE tags should stay on."],
            ["Moving with you flashes on the highway", "Passing BLE only overlaps for a few seconds, so it fails the trail-moved gates. A tag in the car should not flash: “still here” grows with speed (about 400 m of phone travel at 55 mph) so a few quiet seconds are not a miss. §8.5.", "Keep Tag detections with GPS on. Passing phones will still appear and leave — that is the filter working. Roadside APs stay off. A tag in the car should stay; if it does not, Start over after you have ~50 m of path and confirm the tag is still advertising."],
            ["LE color / “What this looks like” flips on and off", "BLE advertisements rotate payloads (for example iBeacon, then Nearby).", "Fieldwatch keeps manufacturer records per company and type for that radio, so a hit should stay labeled when the payload rotates."],
            ["Debrief PDF will not open in the other app", "The other app did not get read permission, or it rejects application/pdf.", "Share to Files, Drive, or a PDF viewer."],
            ["Restore defaults wiped custom signatures", "Restore rewrites config.json from the catalog.", "Expected. Export signatures from Settings before Restore if you want a backup. Import that JSON to get customs back (stock ids merge extra rules; they are not cloned). Named radios and Settings switches go with Export settings."],
            ["Import signatures says not a Fieldwatch pack", "The file is a log, a photo, a settings pack, or config.json, not an exported catalog pack.", "Export from Settings → Export signatures. The file starts with format fieldwatch-signatures. Settings packs start with fieldwatch-settings — use Import settings. Logs stay on Reports."],
            ["Import settings says that is a signature pack", "You picked fieldwatch-signatures JSON.", "Use Import signatures for the catalog. Settings backup is Export settings / Import settings (fieldwatch-settings)."],
            ["Import settings did not bring my custom signatures", "Settings pack does not include the catalog.", "Expected. Import signatures for rows you added or edited. Import settings for switches, presets, and named radios."],
            ["Import added nothing", "Every row in the pack already matches an id or the same rules on this phone.", "Expected for a second import of the same pack, or two phones on the same stock catalog. Only new customs and extra rules on stock rows are added."],
            ["Counts freeze", "Process still up but scans failing, or you are looking at a stale filter.", "Swipe the scan notification. If it is gone, relaunch. Reset filter. Reboot radios (Airplane 5 s)."],
        ],
        [1.7 * inch, 2.2 * inch, 2.6 * inch],
    ))
    flow += [
        Spacer(1, 12),
        P("D. Document control", "h2"),
        table(
            ["Field", "Value"],
            [
                ["Product", "Fieldwatch"],
                ["Author", "Off Grid Pete LLC"],
                ["Copyright", "Copyright (c) 2026 Off Grid Pete LLC. All rights reserved."],
                ["Instagram", "@OffGridPete"],
                ["X", "@OGridPete"],
                ["Document", "User Manual and Technical Documentation"],
                ["Application ID", "app.fieldwatch"],
                ["Software version", "1.1.10 (versionCode 20), field build of 26 September 2026"],
                ["Document version", "1.1.10"],
                ["Document date", "26 September 2026"],
                ["License", "MIT License (see LICENSE); third-party: NOTICE"],
                ["Platform", "Android 10+ (minSdk 29), targetSdk 35"],
                ["Classification", "Unclassified. Operationally sensitive if filled with site logs."],
            ],
            [2.0 * inch, 4.5 * inch],
        ),
        Spacer(1, 10),
        P("E. About this document", "h2"),
        P(
            "This manual describes Fieldwatch as it ships. The document version and date are in "
            "Document control. The software is licensed under the MIT License (LICENSE; also on the Notice page). Third-party notices: NOTICE."
        ),
        Spacer(1, 10),
        P(
            "Fieldwatch is by Off Grid Pete LLC (Instagram @OffGridPete, X @OGridPete). "
            "Copyright (c) 2026 Off Grid Pete LLC. All rights reserved. "
            "Settings shows the copyright and those accounts. Pattern matches are not identity. "
            "Receive-only observation does not authorize access to systems you did not already have a right to use.",
            "caption",
        ),
    ]
    return wrap_body_around_figures(prevent_orphan_headings(flow))


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    doc = FieldwatchDoc(
        OUT,
        pagesize=letter,
        title="Fieldwatch — User Manual and Technical Documentation",
        author="Fieldwatch",
        subject="Passive Signal Intelligence for Android",
        creator="Fieldwatch documentation build",
    )
    cover_frame = Frame(0, 0, PAGE_W, PAGE_H, id="cover")
    body_frame = Frame(
        0.75 * inch, 0.6 * inch, 7.0 * inch, 9.55 * inch, id="body",
        showBoundary=0,
    )
    doc.addPageTemplates([
        PageTemplate(id="cover", frames=cover_frame, onPage=draw_cover),
        PageTemplate(id="body", frames=body_frame, onPage=draw_body),
    ])
    doc.multiBuild(story())
    os.makedirs(os.path.dirname(DIST_OUT), exist_ok=True)
    shutil.copy2(OUT, DIST_OUT)
    print("Wrote", OUT)
    print("Copied", DIST_OUT)


if __name__ == "__main__":
    main()
