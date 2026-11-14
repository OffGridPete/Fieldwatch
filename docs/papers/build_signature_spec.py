#!/usr/bin/env python3
"""Proposal: Radio signature interchange.

Rebuild with the Xcode Python that already has reportlab:

    /Applications/Xcode.app/Contents/Developer/usr/bin/python3 docs/papers/build_signature_spec.py
"""

from reportlab.lib.colors import HexColor, white
from reportlab.lib.enums import TA_JUSTIFY, TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import (
    BaseDocTemplate,
    CondPageBreak,
    Frame,
    HRFlowable,
    KeepTogether,
    ListFlowable,
    ListItem,
    PageTemplate,
    Paragraph,
    Preformatted,
    Spacer,
    Table,
    TableStyle,
)

INK = HexColor("#1C1916")
GREEN = HexColor("#1B4332")
GREEN_SOFT = HexColor("#E6EFEA")
MUTED = HexColor("#5C564E")
RULE = HexColor("#D4CBBC")
WASH = HexColor("#F7F4EE")
AMBER = HexColor("#F8F1E3")
AMBER_INK = HexColor("#6B4E16")

PAGE_W, PAGE_H = letter
LEFT = 0.78 * inch
RIGHT = 0.78 * inch
TOP = 0.72 * inch
BOTTOM = 0.64 * inch
MEASURE = PAGE_W - LEFT - RIGHT
DATED = "14 November 2026"
RELEASE = "1"
# Newest revision last. Add a row for a later revision. Do not rewrite an earlier row.
HISTORY = [
    ("1", "14 November 2026", "Initial release. The format name is radio-signatures. A file that phones through Fieldwatch 1.1.44 must read keeps the name fieldwatch-signatures."),
]
OUT = "docs/papers/Radio_Signature_Interchange.pdf"


def styles():
    s = {}
    s["kicker"] = ParagraphStyle(
        "kicker", fontName="Times-Bold", fontSize=9, leading=12,
        textColor=GREEN, tracking=0.8, spaceAfter=6,
    )
    s["title"] = ParagraphStyle(
        "title", fontName="Times-Bold", fontSize=20, leading=24,
        textColor=INK, spaceAfter=6,
    )
    s["deck"] = ParagraphStyle(
        "deck", fontName="Times-Italic", fontSize=11.5, leading=15.5,
        textColor=MUTED, spaceAfter=8,
    )
    s["meta"] = ParagraphStyle(
        "meta", fontName="Times-Roman", fontSize=10, leading=13.5,
        textColor=INK, spaceAfter=1,
    )
    s["h1"] = ParagraphStyle(
        "h1", fontName="Times-Bold", fontSize=13, leading=16,
        textColor=GREEN, spaceBefore=12, spaceAfter=5, keepWithNext=True,
    )
    s["h2"] = ParagraphStyle(
        "h2", fontName="Times-Bold", fontSize=11.5, leading=14.5,
        textColor=INK, spaceBefore=9, spaceAfter=3, keepWithNext=True,
    )
    s["body"] = ParagraphStyle(
        "body", fontName="Times-Roman", fontSize=10.5, leading=14.2,
        textColor=INK, alignment=TA_JUSTIFY, spaceAfter=7,
    )
    s["bullet"] = ParagraphStyle(
        "bullet", parent=s["body"], leftIndent=14, bulletIndent=0,
        spaceAfter=2, alignment=TA_LEFT,
    )
    s["th"] = ParagraphStyle(
        "th", fontName="Times-Bold", fontSize=8.5, leading=11, textColor=GREEN,
    )
    s["td"] = ParagraphStyle(
        "td", fontName="Times-Roman", fontSize=8.5, leading=11, textColor=INK,
    )
    s["tdmono"] = ParagraphStyle(
        "tdmono", fontName="Courier", fontSize=8, leading=10.5, textColor=INK,
    )
    s["code"] = ParagraphStyle(
        "code", fontName="Courier", fontSize=8, leading=10.2,
        textColor=INK, alignment=TA_LEFT,
    )
    s["toc"] = ParagraphStyle(
        "toc", fontName="Times-Roman", fontSize=9, leading=12, textColor=INK,
    )
    s["call_title"] = ParagraphStyle(
        "call_title", fontName="Times-Bold", fontSize=10, leading=13,
        textColor=GREEN, spaceAfter=2,
    )
    s["call_body"] = ParagraphStyle(
        "call_body", fontName="Times-Roman", fontSize=10, leading=13.4,
        textColor=INK, alignment=TA_LEFT,
    )
    s["ref"] = ParagraphStyle(
        "ref", fontName="Times-Roman", fontSize=9.5, leading=12.8,
        textColor=INK, leftIndent=16, firstLineIndent=-16, spaceAfter=5,
    )
    s["footer"] = ParagraphStyle(
        "footer", fontName="Times-Roman", fontSize=8, leading=10, textColor=MUTED,
    )
    return s


S = styles()


def esc(text):
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


def P(text, style="body"):
    return Paragraph(text, S[style])


def bullets(items):
    flow = [Paragraph("•  " + item, S["bullet"]) for item in items]
    flow.append(Spacer(1, 6))
    return flow


def rule():
    return HRFlowable(width="100%", thickness=0.4, color=RULE, spaceBefore=1, spaceAfter=7)


def thick_rule():
    return HRFlowable(width="100%", thickness=1.25, color=GREEN, spaceBefore=1, spaceAfter=8)


def callout(title, body, wash=WASH, title_color=GREEN):
    title_style = ParagraphStyle("ct", parent=S["call_title"], textColor=title_color)
    data = [[Paragraph(title, title_style)], [Paragraph(body, S["call_body"])]]
    t = Table(data, colWidths=[MEASURE])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), wash),
        ("LEFTPADDING", (0, 0), (-1, -1), 9),
        ("RIGHTPADDING", (0, 0), (-1, -1), 9),
        ("TOPPADDING", (0, 0), (0, 0), 7),
        ("BOTTOMPADDING", (0, -1), (-1, -1), 8),
        ("TOPPADDING", (0, 1), (0, 1), 1),
        ("LINEBEFORE", (0, 0), (0, -1), 2.5, title_color),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ]))
    return KeepTogether([t, Spacer(1, 8)])


def table(headers, rows, widths):
    head = [Paragraph(h, S["th"]) for h in headers]
    data = [head]
    for row in rows:
        cells = []
        for i, cell in enumerate(row):
            style = "tdmono" if cell.startswith("`") else "td"
            text = esc(cell[1:]) if cell.startswith("`") else cell
            cells.append(Paragraph(text, S[style]))
        data.append(cells)
    t = Table(data, colWidths=widths, repeatRows=1)
    cmds = [
        ("BACKGROUND", (0, 0), (-1, 0), GREEN_SOFT),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 3),
        ("RIGHTPADDING", (0, 0), (-1, -1), 3),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("LINEBELOW", (0, 0), (-1, 0), 0.6, GREEN),
        ("LINEBELOW", (0, -1), (-1, -1), 0.4, GREEN),
    ]
    for i in range(1, len(data) - 1):
        cmds.append(("LINEBELOW", (0, i), (-1, i), 0.25, RULE))
    t.setStyle(TableStyle(cmds))
    return t


def code_block(text):
    body = Preformatted(text, S["code"])
    t = Table([[body]], colWidths=[MEASURE])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), WASH),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("LINEBEFORE", (0, 0), (0, -1), 2, GREEN),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ]))
    return KeepTogether([t, Spacer(1, 8)])


class SpecTemplate(PageTemplate):
    def __init__(self):
        frame = Frame(LEFT, BOTTOM, MEASURE, PAGE_H - TOP - BOTTOM, id="body", showBoundary=0)
        super().__init__(id="main", frames=[frame])

    def beforeDrawPage(self, canvas, doc):
        canvas.saveState()
        page = canvas.getPageNumber()
        y_head = PAGE_H - 0.40 * inch
        canvas.setStrokeColor(GREEN)
        canvas.setLineWidth(1.1)
        canvas.line(LEFT, y_head, PAGE_W - RIGHT, y_head)
        canvas.setFillColor(GREEN)
        canvas.setFont("Times-Bold", 8)
        canvas.drawString(LEFT, y_head + 5, "RADIO SIGNATURES")
        canvas.setFillColor(MUTED)
        canvas.setFont("Times-Italic", 8)
        canvas.drawRightString(PAGE_W - RIGHT, y_head + 5, "RSI-1  ·  Release 1")
        y_foot = 0.34 * inch
        canvas.setStrokeColor(RULE)
        canvas.setLineWidth(0.4)
        canvas.line(LEFT, y_foot + 12, PAGE_W - RIGHT, y_foot + 12)
        canvas.setFillColor(MUTED)
        canvas.setFont("Times-Roman", 8)
        canvas.drawString(LEFT, y_foot, "Off Grid Pete LLC  ·  Release 1  ·  14 November 2026")
        canvas.drawRightString(PAGE_W - RIGHT, y_foot, str(page))
        canvas.restoreState()


def build(story):
    doc = BaseDocTemplate(
        OUT,
        pagesize=letter,
        title="Radio Signature Interchange, RSI-1 Release 1",
        author="Off Grid Pete LLC",
        subject="Proposed specification for a shared radio signature file",
        creator="Off Grid Pete LLC",
    )
    doc.addPageTemplates([SpecTemplate()])
    doc.build(story)
    print("Wrote", OUT)


def story():
    w3 = [1.15 * inch, 1.85 * inch, MEASURE - 3.00 * inch]
    w2 = [1.55 * inch, MEASURE - 1.55 * inch]
    w_rule = [1.35 * inch, MEASURE - 1.35 * inch]
    flow = []

    flow += [
        P("SPECIFICATION PROPOSAL  ·  RSI-1", "kicker"),
        P("Radio signature interchange", "title"),
        P(
            "A shared library of radio signatures, so an app can recognize the radios another app has already learned.",
            "deck",
        ),
        thick_rule(),
        P("Proposed by Off Grid Pete LLC", "meta"),
        P(DATED, "meta"),
        P("Document RSI-1, Release 1. Supersedes nothing.", "meta"),
        Spacer(1, 6),
        P("Introduction", "h1"),
        P(
            "A radio app can hear a transmitter nearby and still have nothing useful to call it. The broadcast may carry a model string, a protocol name, or a string no person would recognize. Projects that want to name those radios each keep a private list, in a private format. Someone who has built a library in one app cannot give that library to another app and expect the same radios to come up under the same names.",
            "body",
        ),
        P(
            "A signature is one entry in such a library. It names a type of radio, and it gives the tests that recognize that type from a broadcast the receiver has already parsed. The tests look at information the radio sends in the clear: its name, a service identifier, a company identifier, part of its address, or a short prefix of the payload. When the tests match, the app can tell the user what type of radio it heard. The match names the type. It does not name a person, and on its own it does not prove which physical unit was heard.",
            "body",
        ),
        P(
            "The same signature can carry instructions for reading unencrypted information in that broadcast. A battery level, a status flag, or a short code is often sitting in bytes the radio has already transmitted. The instructions turn those bytes into a label a person can read. Those instructions apply only to bytes the radio has already sent in the clear. The app does not connect to the radio, and it does not decrypt anything. If there is nothing further to read, the signature can still name the radio.",
            "body",
        ),
        P(
            "This file is how those libraries move between apps. A person using one of these apps should be able to share a library with someone using another, and the second app should recognize the same radios. A project can publish its own library, under its own name, including radios that another project never hears. Fieldwatch already does something close to this. Releases through 1.1.44 read this kind of library and match the radios in it. Fieldwatch 1.2.0 is the release that will conform to this specification. Its library covers Bluetooth Low Energy and Wi-Fi. The sections below specify the file so that two readers agree on a match, and so that a radio or a field added later does not break a reader that is already in use. How another project takes part is section 26.",
            "body",
        ),
        callout(
            "Status",
            "This document is a proposal for comment. It is not an adopted standard, and it is not a Bluetooth SIG, IEEE, or IETF publication. Other open-source projects are invited to implement it and to propose a profile for a radio they already receive. Fieldwatch™ is a trademark of Off Grid Pete LLC. Registration pending. Fieldwatch is the first implementation, not the owner of every radio in this file. Releases through 1.1.44 already implement something close to this file. Fieldwatch 1.2.0 will conform to the specification, and it will keep publishing a baseline file that today's phones still load.",
            wash=AMBER,
            title_color=AMBER_INK,
        ),
        P("1.  What this document is", "h1"),
        P(
            "A signature here is a named set of tests against one radio a receiver has already parsed: a name, a service UUID, a company identifier, an address prefix, or a short payload prefix. The file that holds those tests is JSON. This proposal writes down the tests, the file, and the two kinds of parser that read it. Bluetooth Low Energy and Wi-Fi are the radios specified today. Section 17 is how another project adds a radio it hears and this phone does not.",
            "body",
        ),
        P(
            "The format name in this specification is <font face='Courier' size='9'>radio-signatures</font>. A file written for this specification uses that name. <font face='Courier' size='9'>fieldwatch-signatures</font> is the name of the library already on phones through Fieldwatch 1.1.44. Those phones accept that name, with <font face='Courier' size='9'>formatVersion</font> 1, and they reject any other format string. A file those phones must read keeps the older name. Catalog 113, the stock file current with this proposal, stays that way so those phones can still update. A later <font face='Courier' size='9'>formatVersion</font> uses <font face='Courier' size='9'>radio-signatures</font> and does not take a Fieldwatch name.",
            "body",
        ),
        P(
            "Two sorts of statement appear below. Normative statements use the words in section 3 and are the requirements. Informative statements explain why a requirement exists, or describe a parser that is already in the field. Examples and the Fieldwatch release plan are informative except where a paragraph says otherwise.",
            "body",
        ),
        P("Contents", "h2"),
    ]
    contents = [
        "Introduction",
        "1. What this document is",
        "2. Scope",
        "3. Requirements language",
        "4. Terms",
        "5. Parsers",
        "6. File format",
        "7. Versions and compatibility",
        "8. Signature identity",
        "9. The observation",
        "10. How a signature matches",
        "11. Rule kinds",
        "12. Names",
        "13. Service UUIDs",
        "14. Addresses and vendor fields",
        "15. Manufacturer data and service data",
        "16. Which radio a rule applies to",
        "17. Other radios",
        "18. Group profile",
        "19. Members that do not affect the match",
        "20. Decode profile",
        "21. Adding a name later",
        "22. Conformance",
        "23. Fieldwatch 1.2.0",
        "24. What a match does not mean",
        "25. Examples",
        "26. Taking part",
        "Appendix A. Test vectors",
        "Appendix B. Baseline registry",
        "References",
        "Revision history",
    ]
    left = contents[:16]
    right = contents[16:]
    while len(right) < len(left):
        right.append("")
    cells = []
    for a, b in zip(left, right):
        cells.append([Paragraph(a, S["toc"]), Paragraph(b, S["toc"])])
    toc = Table(cells, colWidths=[MEASURE / 2, MEASURE / 2])
    toc.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 1),
    ]))
    flow += [toc, Spacer(1, 8)]

    flow += [
        P("2.  Scope", "h1"),
        P(
            "This specification covers a file of signatures and the decision that one signature does or does not match one observation. A baseline observation is a Bluetooth Low Energy advertisement or a Wi-Fi beacon a receiver has already parsed into the fields in section 9. Other radios are section 17. A file may carry a signature for one of them. A parser matches it only when that parser implements the profile.",
            "body",
        ),
        P(
            "The following are out of scope: connecting to a device, reading GATT characteristics, transmitting, estimating distance from signal strength, deciding that a person is present, and any catalog policy that suppresses one matching signature because another also matched. A receiver may apply that kind of policy after the match. It is not part of interchange, and another implementation does not have to copy it to conform.",
            "body",
        ),
        P("3.  Requirements language", "h1"),
        P(
            "The key words MUST, MUST NOT, REQUIRED, SHALL, SHALL NOT, SHOULD, SHOULD NOT, RECOMMENDED, MAY, and OPTIONAL are to be interpreted as described in RFC 2119 and RFC 8174 when, and only when, they appear in capitals.",
            "body",
        ),
        P("4.  Terms", "h1"),
    ]
    flow.append(table(
        ["Term", "Meaning"],
        [
            ["Baseline file", "A file that uses formatVersion 1 and only the names in Appendix B. The stock Fieldwatch catalog is a baseline file."],
            ["Baseline parser", "A parser with the behavior of Fieldwatch releases through 1.1.44. Informative. It defines what a baseline file must avoid."],
            ["Conforming parser", "A parser that implements the requirements in this proposal. Fieldwatch 1.2.0 is intended to be the first Fieldwatch release that conforms. Releases through 1.1.44 are close, and they are the baseline parser."],
            ["Publisher", "The party that assigned a signature id. Optional in the file. Absent on every signature published before this proposal."],
            ["Radio profile", "The fields and rule kinds defined for one radio token. BLE and WIFI are specified. Further profiles are optional and empty until an implementer who receives that radio writes them. Section 17."],
            ["Match", "The result that a signature's rules accept an observation. A match names a pattern. It does not by itself name a person or prove a model."],
        ],
        w2,
    ))
    flow.append(Spacer(1, 8))

    flow += [
        P("5.  Parsers", "h1"),
        P(
            "A parser is the program that reads the JSON and decides which signatures match an observation. The file format and the parser are specified together because a file that is safe for one parser can be unreadable to another. Fieldwatch releases through 1.1.44 already have a parser in the field, and that parser is close to this proposal. This proposal does not change that parser. It defines the parser Fieldwatch 1.2.0 will use, and it constrains future files so the parser already installed keeps working.",
            "body",
        ),
        P("5.1  What every parser already has in common", "h2"),
        P(
            "A baseline parser and a conforming parser both accept JSON as defined in RFC 8259, encoded in UTF-8. Both accept a leading Unicode byte order mark and ignore it. Both ignore object members they do not recognize. Both apply default values where this document gives a default, so a file written before a member existed still loads.",
            "body",
        ),
        P(
            "Ignoring an unknown member is what makes an added field safe. The member is in the file for a parser that knows it. A parser that does not know it skips the member and matches on the members it does know. This is already how Fieldwatch reads the file.",
            "body",
        ),
        P("5.2  The baseline parser", "h2"),
        P(
            "The baseline parser is informative. It is the parser in Fieldwatch releases through 1.1.44, and it is the reason the public catalog cannot yet contain every idea in this proposal.",
            "body",
        ),
    ]
    flow += bullets([
        "It accepts <font face='Courier' size='9'>formatVersion</font> 1. If <font face='Courier' size='9'>formatVersion</font> is greater than 1, it rejects the whole file and asks for a newer app. It does not apply any signature from that file.",
        "If a rule kind, a radio value, a signature class, or a decode type is a string the parser does not know, it rejects the whole file the same way. One unrecognized name on one signature is enough.",
        "If a decode source is a string it does not know, it drops that decode map and keeps the signature. Matching is unchanged. This is the one unknown name the baseline parser already softens.",
        "It identifies a signature by <font face='Courier' size='9'>id</font> alone. It does not store <font face='Courier' size='9'>publisher</font>.",
        "A stock update whose <font face='Courier' size='9'>catalogVersion</font> is less than or equal to the version already stored is ignored. A higher catalog version is applied. Unknown members have already been dropped by then, so they are not saved.",
    ])
    flow += [
        P("5.3  The conforming parser", "h2"),
        P("A conforming parser MUST do all of the following.", "body"),
    ]
    flow += bullets([
        "Accept every baseline file and produce the same matches as sections 10 through 16 for every signature that uses only baseline rule kinds.",
        "Ignore unknown object members. Where this proposal defines a member, store it. Where a member is absent, apply the default in section 6.",
        "On <font face='Courier' size='9'>formatVersion</font> 1, an unknown rule kind or an unknown radio token MUST NOT match. The parser MUST read the rest of the file and MUST NOT reject the file. If <font face='Courier' size='9'>matchAny</font> is true, the parser ignores that rule and evaluates the others. If <font face='Courier' size='9'>matchAny</font> is false, the signature MUST NOT match, because the unknown rule is a required test the parser cannot perform. If no enabled rule remains that the parser understands, the signature MUST NOT match. Section 17 is the same rule for a radio the parser does not implement.",
        "An unknown signature class MUST NOT prevent a match. The parser SHOULD keep the raw class string. If it cannot, it MUST treat the class as <font face='Courier' size='9'>OTHER</font> for display only.",
        "An unknown decode type MUST cause that field alone to be omitted. An unknown decode source MUST cause the whole decode object to be omitted. In both cases the signature MUST still match on its rules.",
        "If <font face='Courier' size='9'>formatVersion</font> is greater than the highest version the parser implements, the parser SHOULD still read the file, apply every member it understands, ignore the rest, and report that the file is newer than the parser. It SHOULD NOT refuse the file only because the number is higher.",
        "Identity MUST follow section 8. Adding <font face='Courier' size='9'>publisher</font> to a signature whose stored copy has no publisher MUST NOT create a second signature.",
    ])
    flow += [
        P(
            "The last requirement is the one that protects a phone already running 1.1.44. That phone never stores <font face='Courier' size='9'>publisher</font>. Fieldwatch 1.2.0 will see a stock file that has the field and a phone copy that does not. Those two copies are the same signature.",
            "body",
        ),
        P("5.4  What a publisher of a file owes each parser", "h2"),
    ]
    flow.append(table(
        ["If the file will be read by", "The file MUST"],
        [
            ["A baseline parser, including Fieldwatch 1.1.44", "Stay a baseline file: formatVersion 1, and only names from Appendix B. It MAY add object members those parsers will ignore, including publisher."],
            ["Only conforming parsers", "Use format radio-signatures. Use formatVersion 1 for any addition that an older match would still get right by ignoring the new member. Raise formatVersion only for a change described in section 7. A formatVersion greater than 1 uses radio-signatures."],
        ],
        [2.15 * inch, MEASURE - 2.15 * inch],
    ))
    flow.append(Spacer(1, 8))
    flow.append(callout(
        "Why the public catalog stays on format 1",
        "The stock file at fieldwatch-signatures-v2.json is what current phones download. A formatVersion above 1, or one rule kind or radio token those phones do not know, makes Update stock catalog refuse the entire file. New members that those phones ignore are safe to add only in a release whose parser stores them. Until that release is the one on the phone, a catalog number spent on an ignored field is stored, and a later file with the same number is skipped. Section 23 is the order that avoids that.",
    ))

    flow += [
        P("6.  File format", "h1"),
        P(
            "A file is one JSON object. A conforming parser MUST accept the members in this section. A writer MUST emit UTF-8 JSON. A writer SHOULD emit <font face='Courier' size='9'>format</font> and <font face='Courier' size='9'>formatVersion</font>. Integers MUST be within the range that can be represented exactly in IEEE 754 binary64, which covers every field in this document.",
            "body",
        ),
        P("6.1  Top-level members", "h2"),
    ]
    flow.append(table(
        ["Member", "Requirement"],
        [
            ["`format", "String. The name of this format is radio-signatures. A file written for this specification MUST use that name. A conforming parser MUST accept radio-signatures and fieldwatch-signatures. A file that a Fieldwatch parser through 1.1.44 must read MUST be fieldwatch-signatures with formatVersion 1. Those parsers reject any other format string. A Fieldwatch parser also accepts the historical name spectre-signatures. Other parsers MAY reject that name. A formatVersion greater than 1 MUST use radio-signatures."],
            ["`formatVersion", "Integer. MUST be 1 or greater. The baseline and this proposal's additions are version 1. Absent means 1."],
            ["`catalogVersion", "Integer. Optional. Identifies one publisher's snapshot of a stock catalog. It is not a schema version. Absent means 0. A consumer MUST NOT interpret it as formatVersion."],
            ["`exportedAt", "String. Optional. When the file was written, for humans. Not used in matching."],
            ["`appVersion", "String. Optional. The application that wrote the file. Not used in matching."],
            ["`publisher", "String. Optional. The publisher of this file as a whole, using the token in section 8. A signature may name a different publisher."],
            ["`fleets", "Array of signature objects. The name fleets is historical and is retained so existing files parse. The array MUST be present. An empty array is a valid file with nothing to match."],
        ],
        w_rule,
    ))
    flow.append(Spacer(1, 8))
    flow += [
        P("6.2  Signature object", "h2"),
        P(
            "Each element of <font face='Courier' size='9'>fleets</font> is an object. Members that do not affect the match are still part of the file so an application can round-trip a catalog. They are specified in section 19.",
            "body",
        ),
    ]
    flow.append(table(
        ["Member", "Requirement"],
        [
            ["`id", "String. REQUIRED. Stable key assigned by the publisher. MUST be non-empty. MUST NOT change for a published signature. The Fieldwatch stock ids in use today remain the ids."],
            ["`publisher", "String. Optional. Token from section 8. Absent means unspecified."],
            ["`name", "String. REQUIRED. Human label. Not used in matching."],
            ["`rules", "Array of rule objects. REQUIRED. The array may be empty only for a group signature under section 18. Otherwise an empty rules array matches nothing."],
            ["`matchAny", "Boolean. Default true. Section 10."],
            ["`kind", "String. Signature class. Default OTHER. Does not affect the match. Appendix B lists the baseline classes."],
        ],
        w_rule,
    ))
    flow.append(Spacer(1, 8))
    flow += [
        P("6.3  Rule object", "h2"),
    ]
    flow.append(table(
        ["Member", "Requirement"],
        [
            ["`kind", "String. REQUIRED. One of the names in section 11, or a name added under section 21."],
            ["`text", "String. Default empty. Meaning depends on kind."],
            ["`companyId", "Integer. Default 0. A Bluetooth company identifier, written in decimal. The same number as the 16-bit identifier assigned by the Bluetooth SIG."],
            ["`dataPrefixHex", "String. Default empty. Hexadecimal payload prefix. Separators and case are ignored."],
            ["`radio", "String or null. A radio token. Baseline values are BLE, WIFI, or null. Default null. JSON null and an omitted member are the same. Section 16. Any other token is a profile under section 17, and a baseline file MUST NOT use it."],
            ["`enabled", "Boolean. Default true. A rule with enabled false MUST NOT match and MUST NOT count as a remaining rule under matchAny false."],
        ],
        w_rule,
    ))
    flow.append(Spacer(1, 8))

    flow += [
        P("7.  Versions and compatibility", "h1"),
        P(
            "Two integers travel in the file, and they answer different questions. <font face='Courier' size='9'>formatVersion</font> is the schema. <font face='Courier' size='9'>catalogVersion</font> is one publisher's revision of the signatures inside a stock file. Fieldwatch catalog 113 and formatVersion 1 are both current as of this proposal. Another publisher uses its own catalog number and the same format version.",
            "body",
        ),
        P("A change falls into one of three classes.", "body"),
    ]
    flow += bullets([
        "<b>Ignored by an older parser.</b> A new optional member with a default, such as publisher. formatVersion stays 1. A baseline parser loads the file and matches as before. A conforming parser stores the member.",
        "<b>A new registered name</b> (a rule kind, a radio token, a class, a decode type). formatVersion stays 1. A conforming parser skips the piece it does not know, as in section 5.3. An unknown radio token on a signature with <font face='Courier' size='9'>matchAny</font> false fails that signature closed, under section 17. A baseline parser rejects the whole file. A file that baseline parsers must read MUST NOT use the new name.",
        "<b>A change an older matcher would get wrong</b> if it applied the rules it knows and ignored the rest. That change MUST increase formatVersion. An example would be a new rule kind that is defined to exclude observations which the older rules would have accepted. Skipping that rule would label the wrong radios. A baseline parser will reject the file. A conforming parser built before the new version reports that the file is newer and applies only what it understands, and it MUST NOT present that partial result as complete.",
    ])
    flow.append(P(
        "Writers MUST NOT increase formatVersion for a change in the first class. Doing so turns a compatible file into a file the baseline parser refuses. A file whose <font face='Courier' size='9'>formatVersion</font> is greater than 1 MUST set <font face='Courier' size='9'>format</font> to <font face='Courier' size='9'>radio-signatures</font>. The name <font face='Courier' size='9'>fieldwatch-signatures</font> is not used for a later version of this format.",
        "body",
    ))

    flow += [
        P("8.  Signature identity", "h1"),
        P(
            "The id is the stable name of a signature. Fieldwatch stock ids look like <font face='Courier' size='9'>fleet-samsung-tv</font>. Those strings are in use on phones, including mute, extra rules a person added, and bookmarks. This proposal does not rename them.",
            "body",
        ),
        P(
            "A publisher token lets two parties use the same id text without becoming the same signature. The token MUST match <font face='Courier' size='9'>[a-z][a-z0-9.-]{0,63}</font>. The token for signatures assigned by this project is <font face='Courier' size='9'>fieldwatch</font>. Another publisher SHOULD use a domain name it controls, in lowercase, such as <font face='Courier' size='9'>example.com</font>.",
            "body",
        ),
        P("Two signature objects are the same signature when all of the following hold.", "body"),
    ]
    flow += bullets([
        "The id strings are equal, compared as Unicode code points, case-sensitive.",
        "The publishers are compatible. They are compatible when they are equal, or when either publisher is absent.",
    ])
    flow.append(P(
        "If both publishers are present and they differ, the signatures are different even when the id text is the same. A consumer MUST keep both. A consumer MUST NOT satisfy this rule by deleting the publisher and comparing ids alone. That would merge two parties' work.",
        "body",
    ))
    flow.append(P(
        "A writer that did not assign a signature MUST preserve the publisher it read, including preserving absence. A writer MAY set <font face='Courier' size='9'>publisher</font> to its own token on a signature it assigns. Fieldwatch MAY set <font face='Courier' size='9'>fieldwatch</font> on a stock id it assigned when the stored publisher is absent. It MUST NOT set that token on a custom signature.",
        "body",
    ))
    flow.append(callout(
        "Same id, new field, one row",
        "A phone stored fleet-samsung-tv with no publisher. A later file has id fleet-samsung-tv and publisher fieldwatch. The publishers are compatible because one is absent. The update replaces the stock rules and keeps the phone's mute and any extra rules. It does not add a second Samsung TV signature.",
    ))

    flow += [
        P("9.  The observation", "h1"),
        P(
            "Matching takes an observation the receiver has already parsed. A conforming matcher MUST be able to read the following. It MUST NOT require signal strength, position, or a raw frame beyond the bytes a decode profile needs.",
            "body",
        ),
    ]
    flow.append(table(
        ["Field", "Use"],
        [
            ["Radio", "BLE or WIFI for a baseline observation. A profile in section 17 may name another radio and the fields that radio provides. Those fields are not part of a baseline observation."],
            ["Address", "48-bit MAC-48 address, as transmitted."],
            ["Name", "Advertised name or SSID. Empty if the frame has none. A blank name is not a match for a name rule."],
            ["Hidden SSID", "True when a Wi-Fi beacon sets the hidden-SSID flag."],
            ["Service UUIDs", "UUIDs advertised as services."],
            ["Service data", "Pairs of UUID and payload bytes."],
            ["Manufacturer records", "Pairs of company identifier and payload bytes. The company identifier is the 16-bit Bluetooth value."],
            ["Vendor IE OUIs", "Organizationally unique identifiers carried in Wi-Fi vendor-specific information elements."],
            ["Last heard", "Used only by the group profile."],
        ],
        w2,
    ))
    flow.append(Spacer(1, 8))
    flow.append(P(
        "The fields in the table are the baseline fields. A profile added under section 17 names any further fields that radio provides, and a core matcher of baseline files does not have to read them. Hexadecimal strings in rules and in observations are compared after removing separators (colon, hyphen, space) and after converting letters to uppercase. A leading <font face='Courier' size='9'>0x</font> is not required and MUST be ignored if a writer included it on an address. Company identifiers are numbers, not hex strings.",
        "body",
    ))

    flow += [
        P("10.  How a signature matches", "h1"),
        P(
            "Only enabled rules are considered. A signature with no remaining enabled rules does not match, unless section 18 says it is a group signature with an empty rule list. A rule the parser cannot perform is not a remaining rule that passed. Section 5.3 says when that fails the signature.",
            "body",
        ),
        P(
            "If <font face='Courier' size='9'>matchAny</font> is true, the signature matches when any remaining rule matches. If <font face='Courier' size='9'>matchAny</font> is false, the signature matches when every remaining rule matches. A signature has no third mode. A writer that needs both a required rule and a list of alternatives MUST use two signatures.",
            "body",
        ),
        P(
            "Each signature is tested on its own. A match on one signature MUST NOT, by this specification, remove a match on another. An application may hide a label afterward. That step is outside conformance.",
            "body",
        ),
        P(
            "Rule evaluation order MUST NOT change the result. A matcher MAY stop early when <font face='Courier' size='9'>matchAny</font> is true and one rule has already matched. The set of matching signatures MUST be the same either way.",
            "body",
        ),

        P("11.  Rule kinds", "h1"),
        P(
            "The baseline registry has eleven kinds. A conforming parser MUST implement each of them. The subsections give the match. Section 16 further restricts the match by radio.",
            "body",
        ),
    ]
    flow.append(table(
        ["Kind", "What it tests"],
        [
            ["`NAME_CONTAINS", "The name contains text, case-insensitive. An empty name does not match. An empty text does not match."],
            ["`NAME_GLOB", "The whole name matches the pattern in section 12."],
            ["`SERVICE_UUID", "An advertised service UUID or a service-data UUID is equivalent under section 13."],
            ["`SERVICE_DATA", "A service-data record matches section 15."],
            ["`MANUFACTURER_ID", "A manufacturer record has companyId equal to the rule's companyId. Zero matches company 0 only, not every company."],
            ["`MANUFACTURER_DATA", "A manufacturer payload starts with dataPrefixHex. Section 15."],
            ["`OUI", "The address, and on Wi-Fi the vendor IE, starts with the prefix. Section 14."],
            ["`MAC_PREFIX", "The address starts with the prefix. Vendor IEs are not tested."],
            ["`VENDOR_IE_OUI", "A Wi-Fi vendor IE starts with the prefix. The address is not tested."],
            ["`RADIO_KIND", "The observation's radio equals the rule's radio. A null radio matches either baseline radio, BLE or WIFI. It does not match a radio added under section 17."],
            ["`HIDDEN_SSID", "The observation is Wi-Fi and the hidden-SSID flag is set. text is ignored."],
        ],
        [1.55 * inch, MEASURE - 1.55 * inch],
    ))
    flow.append(Spacer(1, 8))

    flow += [
        P("12.  Names", "h1"),
        P(
            "Name comparison uses the advertised name as a character string. It is case-insensitive under Unicode simple case folding for the letters A to Z, which is the comparison Fieldwatch implements with an ignore-case match. A name rule MUST NOT match when the advertised name is empty or missing.",
            "body",
        ),
        P(
            "<font face='Courier' size='9'>NAME_CONTAINS</font> matches when <font face='Courier' size='9'>text</font> occurs as a substring. <font face='Courier' size='9'>NAME_GLOB</font> matches the entire name. The pattern is interpreted as follows, and then anchored at both ends so a pattern without a wildcard is an exact name.",
            "body",
        ),
    ]
    flow += bullets([
        "<font face='Courier' size='9'>*</font> matches any sequence of characters, including the empty sequence.",
        "<font face='Courier' size='9'>?</font> matches exactly one character.",
        "Every other character matches itself. Parentheses, brackets, dots, and hyphens are literal. They are not regular-expression syntax.",
        "A pattern that is empty MUST NOT match.",
    ])
    flow.append(P(
        "So <font face='Courier' size='9'>TD-H9*</font> matches <font face='Courier' size='9'>TD-H9</font> and <font face='Courier' size='9'>TD-H9-0536</font>, and does not match <font face='Courier' size='9'>XTD-H9</font>. <font face='Courier' size='9'>ASB??????</font> matches exactly six characters after ASB. <font face='Courier' size='9'>ICOM BT(IC-705)</font> matches that name only, including the parentheses. <font face='Courier' size='9'>walkie-talkie</font> does not match <font face='Courier' size='9'>walkie-talkie 2</font> or <font face='Courier' size='9'>walkie talkie</font>.",
        "body",
    ))

    flow += [
        P("13.  Service UUIDs", "h1"),
        P(
            "UUID text is compared case-insensitively after hyphens are removed. A 16-bit Bluetooth UUID, a 32-bit Bluetooth UUID, and a 128-bit UUID on the Bluetooth base are the same identifier and MUST match each other. The Bluetooth base tail is the 96 bits <font face='Courier' size='9'>0000-1000-8000-00805F9B34FB</font>.",
            "body",
        ),
    ]
    flow += bullets([
        "Four hex digits <font face='Courier' size='9'>XXXX</font> match <font face='Courier' size='9'>XXXX</font>, <font face='Courier' size='9'>0xXXXX</font>, <font face='Courier' size='9'>0000XXXX</font>, and <font face='Courier' size='9'>0000XXXX-0000-1000-8000-00805F9B34FB</font>.",
        "Eight hex digits match that 32-bit value on the Bluetooth base. They match a 16-bit short form only when the high half is <font face='Courier' size='9'>0000</font>.",
        "A 128-bit value matches a shorter form only when it ends in the Bluetooth base tail, and the 16-bit short form only when it also begins with <font face='Courier' size='9'>0000</font>.",
        "Any other 128-bit value MUST be stored in full. A writer MUST NOT shorten it to four or eight hex digits. A parser MUST NOT treat it as equal to a different UUID that shares those digits.",
    ])
    flow.append(P(
        "A <font face='Courier' size='9'>SERVICE_UUID</font> rule looks at advertised service UUIDs and at the UUID of each service-data record. It does not look at GATT characteristics that were not advertised. The KISS TNC UUID <font face='Courier' size='9'>00000001-ba2a-46c9-ae49-01b0961f68bb</font> does not end in the Bluetooth base tail, so it matches only that 128-bit value, not the 16-bit value <font face='Courier' size='9'>0001</font>.",
        "body",
    ))

    flow += [
        P("14.  Addresses and vendor fields", "h1"),
        P(
            "An address prefix matches when the address hex starts with the rule prefix. A 24-bit OUI is six hex digits. A longer prefix is a longer address match. Separators in the rule are ignored, so <font face='Courier' size='9'>00:14:7B</font> and <font face='Courier' size='9'>00147B</font> are the same prefix.",
            "body",
        ),
        P(
            "<font face='Courier' size='9'>OUI</font> also matches a Wi-Fi vendor IE whose hex starts with the prefix. Two protocol OUIs MUST NOT match through a vendor IE, because they mark WPA and RSN rather than a product: <font face='Courier' size='9'>0050F2</font> and <font face='Courier' size='9'>000FAC</font>. They MAY still match an address, if a rule names that prefix and the address starts with it.",
            "body",
        ),
        P(
            "On Wi-Fi, a 24-bit <font face='Courier' size='9'>OUI</font> rule also matches an individually addressed, locally administered BSSID after the local bit (bit 1 of the first octet) is cleared. Guest and extra SSIDs often set that bit on the burned-in vendor prefix. The matcher MUST NOT clear the bit on a group address. <font face='Courier' size='9'>MAC_PREFIX</font> and <font face='Courier' size='9'>VENDOR_IE_OUI</font> do not use this recovery.",
            "body",
        ),
        P(
            "A prefix of a length other than six hex digits matches only by a hex prefix on the address or, for <font face='Courier' size='9'>OUI</font> and <font face='Courier' size='9'>VENDOR_IE_OUI</font>, on a vendor IE. It does not use the 24-bit local-bit recovery.",
            "body",
        ),

        P("15.  Manufacturer data and service data", "h1"),
        P(
            "<font face='Courier' size='9'>MANUFACTURER_DATA</font> requires a non-empty <font face='Courier' size='9'>dataPrefixHex</font>. A record matches when its payload hex starts with that prefix. If <font face='Courier' size='9'>companyId</font> is 0, any company identifier is accepted. If it is any other value, the record's company identifier MUST be equal to it.",
            "body",
        ),
        P(
            "<font face='Courier' size='9'>SERVICE_DATA</font> uses <font face='Courier' size='9'>text</font> as a UUID and <font face='Courier' size='9'>dataPrefixHex</font> as payload bytes. If the UUID is present, the record UUID MUST be equivalent under section 13, and the payload MUST start with the prefix. An empty prefix accepts any payload. If the UUID is empty and the prefix is present, the rule matches a record whose payload contains that prefix or the same bytes reversed in pairs. If both are empty, the rule MUST NOT match.",
            "body",
        ),

        P("16.  Which radio a rule applies to", "h1"),
        P(
            "When <font face='Courier' size='9'>radio</font> is <font face='Courier' size='9'>BLE</font> or <font face='Courier' size='9'>WIFI</font>, the rule MUST be evaluated only against that radio. A rule that does not apply does not match, and under <font face='Courier' size='9'>matchAny</font> false it is ignored rather than counted as a failure. That exception is only for a radio token the parser implements, when the observation is a different radio the parser also implements. It is how one signature carries a Bluetooth name and a Wi-Fi name as alternatives. An unknown radio token is not this case. Section 17 applies, and under <font face='Courier' size='9'>matchAny</font> false the signature does not match.",
            "body",
        ),
        P("When <font face='Courier' size='9'>radio</font> is null or omitted, the rule's kind decides among the baseline radios only. The omission MUST NOT expand onto a radio token added later.", "body"),
    ]
    flow += bullets([
        "Name rules, <font face='Courier' size='9'>OUI</font>, and <font face='Courier' size='9'>MAC_PREFIX</font> apply to both baseline radios, BLE and WIFI. They do not apply to a later radio token.",
        "<font face='Courier' size='9'>SERVICE_UUID</font>, <font face='Courier' size='9'>SERVICE_DATA</font>, <font face='Courier' size='9'>MANUFACTURER_ID</font>, and <font face='Courier' size='9'>MANUFACTURER_DATA</font> apply to Bluetooth Low Energy only. These fields are Low Energy advertisements.",
        "<font face='Courier' size='9'>VENDOR_IE_OUI</font> and <font face='Courier' size='9'>HIDDEN_SSID</font> apply to Wi-Fi only.",
        "<font face='Courier' size='9'>RADIO_KIND</font> with a null radio matches either baseline radio and does not match a later token. The rule does not constrain anything among baseline radios. Writers SHOULD omit such a rule.",
    ])
    flow.append(P(
        "Fieldwatch releases through 1.1.44 already scope a signature to Bluetooth when every rule is Bluetooth-scoped, including a service or company rule whose radio member was omitted. On a signature that also contains a Wi-Fi rule, those releases can still test an omitted-radio service rule against Wi-Fi. A conforming parser MUST apply the list above even on a mixed signature. The stock catalog sets <font face='Courier' size='9'>radio</font> explicitly on new service and company rules, so baseline files do not depend on the mixed case.",
        "body",
    ))
    flow.append(P(
        "A signature for Classic Bluetooth, or for any radio added later, MUST set <font face='Courier' size='9'>radio</font> on each rule that should match that radio. An omitted-radio name rule MUST NOT match a Classic inquiry that advertises the same name.",
        "body",
    ))

    flow += [
        P("17.  Other radios", "h1"),
        P(
            "The baseline radios are Bluetooth Low Energy and Wi-Fi. Other receivers hear more. Kismet, for example, reports Bluetooth Classic inquiry, IEEE 802.15.4, and 433 MHz sensor broadcasts in addition to Wi-Fi and Low Energy. This file can carry signatures for those radios. A parser matches a rule only for a profile it implements. Fieldwatch implements BLE and Wi-Fi only, and this proposal does not require it to match the others.",
            "body",
        ),
        P(
            "A radio token is an open name, registered the same way as a rule kind under section 21. The baseline tokens are <font face='Courier' size='9'>BLE</font> and <font face='Courier' size='9'>WIFI</font>. A new token is written into a revision of this document together with the observation fields that radio provides.",
            "body",
        ),
        P(
            "A profile is the set of fields and rule kinds defined for one radio token. A product claims the profiles it implements. The BLE and Wi-Fi profiles are enough for a core matcher of baseline files. A profile for another radio, such as Classic Bluetooth or 802.15.4, is OPTIONAL. Naming an example token in this document does not register the profile.",
            "body",
        ),
        P(
            "An observation for a radio the parser does not implement MUST NOT match any signature on the strength of rules the parser skipped.",
            "body",
        ),
    ]
    flow += bullets([
        "A rule whose radio token the parser does not implement MUST NOT match.",
        "If <font face='Courier' size='9'>matchAny</font> is true, the parser ignores that rule and evaluates the others.",
        "If <font face='Courier' size='9'>matchAny</font> is false, the signature MUST NOT match. The unknown rule is a required test the parser cannot perform.",
    ])
    flow += [
        P(
            "<font face='Courier' size='9'>NAME_CONTAINS</font>, <font face='Courier' size='9'>NAME_GLOB</font>, <font face='Courier' size='9'>OUI</font>, and <font face='Courier' size='9'>MAC_PREFIX</font> need only a name or an address. A profile whose observation has those fields MAY use them. <font face='Courier' size='9'>SERVICE_UUID</font>, manufacturer data, vendor information elements, and hidden SSID stay with the radios that have those fields. A test those kinds cannot state, such as a Bluetooth Classic class of device, is a new rule kind added with that profile.",
            "body",
        ),
        P(
            "This proposal does not define Classic Bluetooth, 802.15.4, or 433 MHz sensor fields. Those profiles are empty until an implementer who receives them writes the fields, the rule kinds, and the test vectors. An empty profile is not a contribution. A file that baseline parsers must read MUST NOT use a radio token other than <font face='Courier' size='9'>BLE</font>, <font face='Courier' size='9'>WIFI</font>, or null.",
            "body",
        ),

        P("18.  Group profile", "h1"),
        P(
            "The group profile is OPTIONAL. A core matcher MUST implement sections 10 through 16 and MUST NOT report a match that depends only on this section. Fieldwatch implements the profile. A signature uses it when <font face='Courier' size='9'>minPeers</font> is greater than 0, or <font face='Courier' size='9'>clusterByOui</font> is true, or <font face='Courier' size='9'>sequentialMac</font> is true.",
            "body",
        ),
        P(
            "The window is <font face='Courier' size='9'>peerWindowSec</font> seconds, or 60 when that member is 0 or absent. Only observations last heard inside the window are peers. The required count is <font face='Courier' size='9'>minPeers</font>, or 3 when the signature is in this profile and <font face='Courier' size='9'>minPeers</font> is 0.",
            "body",
        ),
        P(
            "An observation is eligible when its signature rules match, or when the rule list is empty, or when <font face='Courier' size='9'>clusterByOui</font> is true and the name is blank or the address is randomized. Peers are other eligible observations in the window. When <font face='Courier' size='9'>clusterByOui</font> is true, a peer MUST share the first 24 bits of the address. When <font face='Courier' size='9'>sequentialMac</font> is true, the absolute difference of the last 16 bits of the two addresses MUST be 64 or less. The signature matches an eligible observation when the peer count, including itself, reaches the required count.",
            "body",
        ),
        P(
            "The stock Fieldwatch catalog at the date of this proposal contains no group signature. A baseline file can omit the four members. The defaults are <font face='Courier' size='9'>minPeers</font> 0, <font face='Courier' size='9'>peerWindowSec</font> 60, and both flags false, which means the signature is not in this profile.",
            "body",
        ),

        P("19.  Members that do not affect the match", "h1"),
        P(
            "A conforming parser that stores and rewrites catalogs MUST preserve these members. A matcher that only reports matches MAY ignore them. None of them change sections 10 through 16, and none of them change the group test in section 18.",
            "body",
        ),
    ]
    flow.append(table(
        ["Member", "Meaning"],
        [
            ["`enabled", "Boolean, default true. The application is willing to use the signature. A stock update SHOULD preserve a local value of false, which is how a person mutes one stock signature."],
            ["`colorIndex", "Integer, default 0. An application palette slot, not a color value. Fieldwatch uses a fixed palette. Other applications MAY ignore it."],
            ["`notes", "String, default empty. Explanation for a person reading the signature. Not a match key."],
            ["`attentionNote", "String, default empty. Application caution text. Empty means the signature asks for no extra attention. Fieldwatch does not beep a signature whose attention note is empty, except a local policy for one class. That policy is not part of this file."],
            ["`builtIn", "Boolean, default false. True when the signature came from the application's stock catalog. A stock update uses it together with the id."],
            ["`kind", "Class token. Display and filtering. Baseline tokens are in Appendix B. Unknown tokens follow section 5.3."],
        ],
        w_rule,
    ))
    flow.append(Spacer(1, 8))
    flow.append(P(
        "A consumer that overlays a stock file onto a phone MUST keep, for the same signature under section 8, the local <font face='Courier' size='9'>enabled</font> value and any local rules that the incoming signature does not already contain. It MUST NOT clear bookmarks or other application settings as a side effect of reading the file.",
        "body",
    ))

    flow += [
        P("20.  Decode profile", "h1"),
        P(
            "The decode profile is OPTIONAL. It maps bytes that already matched into labeled numbers and text for display. It MUST NOT change whether the signature matches. A core matcher MAY ignore the <font face='Courier' size='9'>decode</font> member. Fieldwatch implements the profile. Omitting <font face='Courier' size='9'>decode</font> means the signature has no field map.",
            "body",
        ),
        P(
            "The object has <font face='Courier' size='9'>source</font> (<font face='Courier' size='9'>manufacturerData</font> or <font face='Courier' size='9'>serviceData</font>), an optional <font face='Courier' size='9'>companyId</font>, an optional <font face='Courier' size='9'>serviceUuid</font>, an optional <font face='Courier' size='9'>includeCompanyId</font> boolean, and <font face='Courier' size='9'>fields</font>, an array of field objects. When <font face='Courier' size='9'>includeCompanyId</font> is true, the two-byte little-endian company identifier is prepended to the manufacturer payload before offsets are applied.",
            "body",
        ),
        P(
            "A field has <font face='Courier' size='9'>id</font>, <font face='Courier' size='9'>label</font>, <font face='Courier' size='9'>offset</font> (zero-based), optional <font face='Courier' size='9'>length</font>, <font face='Courier' size='9'>type</font>, and <font face='Courier' size='9'>endian</font> (<font face='Courier' size='9'>le</font> or <font face='Courier' size='9'>be</font>, default <font face='Courier' size='9'>le</font>). Optional <font face='Courier' size='9'>bitOffset</font> and <font face='Courier' size='9'>bitWidth</font> apply to type <font face='Courier' size='9'>bits</font>. Optional <font face='Courier' size='9'>modulo</font> is applied to the raw numeric value before scale. The displayed number is raw × scale + offsetAdd, where a missing scale is 1 and a missing offsetAdd is 0. Optional <font face='Courier' size='9'>unit</font>, <font face='Courier' size='9'>enum</font> (a map from the raw key to a label), and <font face='Courier' size='9'>enumNotes</font> are display. Optional <font face='Courier' size='9'>live</font> and <font face='Courier' size='9'>liveEmphasis</font> are application hints for a live row. Optional <font face='Courier' size='9'>when</font> is a gate.",
            "body",
        ),
        P(
            "A gate has <font face='Courier' size='9'>offset</font>, <font face='Courier' size='9'>length</font> (default 1), <font face='Courier' size='9'>op</font>, and <font face='Courier' size='9'>valueHex</font>. The operations are <font face='Courier' size='9'>eq</font>, <font face='Courier' size='9'>neq</font>, <font face='Courier' size='9'>mask</font> (every 1-bit in the value is set in the payload), <font face='Courier' size='9'>nmask</font> (every 1-bit in the value is clear), and <font face='Courier' size='9'>len</font> (the payload length equals <font face='Courier' size='9'>length</font>; offset and value are ignored). Optional <font face='Courier' size='9'>and</font> is a second gate; both MUST pass. A field whose gate fails MUST be omitted from the display. The signature still matches.",
            "body",
        ),
        P("Baseline numeric types and their default widths are:", "body"),
    ]
    flow.append(table(
        ["Type", "Width and reading"],
        [
            ["`u8  i8  bool", "1 byte. bool is 0 or non-zero. i8 is signed."],
            ["`u16  i16", "2 bytes, endian as given."],
            ["`u24", "3 bytes, endian as given."],
            ["`u32  i32  f32", "4 bytes. f32 is IEEE 754 binary32."],
            ["`mac", "6 bytes, displayed as an address."],
            ["`bits", "The bit field given by bitOffset and bitWidth, reading across bytes as needed."],
            ["`utf8  hex", "length bytes. Default length 1 when length is omitted. utf8 is text. hex is hexadecimal."],
        ],
        [1.45 * inch, MEASURE - 1.45 * inch],
    ))
    flow.append(Spacer(1, 8))
    flow.append(P(
        "An unknown <font face='Courier' size='9'>source</font>, <font face='Courier' size='9'>type</font>, <font face='Courier' size='9'>endian</font>, or gate <font face='Courier' size='9'>op</font> follows section 5.3. The signature's rules still match. A baseline parser rejects the file on an unknown type, endian, or gate operation, and already drops an unknown source.",
        "body",
    ))

    flow += [
        P("21.  Adding a name later", "h1"),
        P(
            "A new rule kind, radio token, class, decode type, endian, or gate operation is added by writing it into a revision of this document, with the match or the reading specified tightly enough that two parsers agree. A radio token is added with the profile in section 17, written by an implementer who receives that radio. The revision keeps formatVersion 1 unless the new name would make a parser that skips it label a different radio than a parser that understands it.",
            "body",
        ),
        P(
            "Until a name is in the revision a parser implements, that parser treats it as unknown under section 5.3. Publishers that still need baseline parsers to read the file wait, and express the signature with names those parsers know. A tighter name glob is the usual way. A new kind is reserved for a test the existing kinds cannot state.",
            "body",
        ),
        P(
            "Class tokens are a vocabulary for display. Adding one does not change matching. The cost of adding one is only that a baseline parser rejects a file that contains it. A file for those parsers keeps using a class they know, or <font face='Courier' size='9'>OTHER</font>.",
            "body",
        ),

        P("22.  Conformance", "h1"),
        P("An implementation claims one or more of the following.", "body"),
    ]
    flow += bullets([
        "<b>Core matcher.</b> Given a baseline file and an observation on BLE or WIFI, it reports the same matching signature ids as sections 10 through 16. It may ignore group signatures, decode, application members, and any radio profile it does not implement.",
        "<b>Conforming parser.</b> It meets section 5.3, reads and writes the file in section 6, and includes a core matcher. If it stores catalogs, it preserves section 19 and identity in section 8. It MUST accept <font face='Courier' size='9'>fieldwatch-signatures</font> and <font face='Courier' size='9'>radio-signatures</font>.",
        "<b>Group profile.</b> It implements section 18 in addition to the core.",
        "<b>Decode profile.</b> It implements section 20. Failures inside a decode MUST NOT remove a match the rules produced.",
        "<b>Additional radio profile.</b> OPTIONAL. It implements one radio token registered under section 17, and it states which token. A product that does not implement the token leaves those rules unmatched, as section 5.3 requires. Fieldwatch claims the BLE and Wi-Fi profiles only.",
    ])
    flow.append(P(
        "Appendix A is the start of the conformance set. An implementation that disagrees with a vector is not conforming, even if the surrounding prose can be read another way. Where this document and a later agreed test set disagree, the test set is corrected by a revision of this document, not by silent drift.",
        "body",
    ))
    flow.append(P(
        "A baseline parser is not a conforming parser of this proposal. It is the installed base. Files intended for it follow section 5.4. No claim of conformance is withdrawn from Fieldwatch 1.1.44, because that release predates this document and correctly reads the baseline file.",
        "body",
    ))

    flow += [
        P("23.  Fieldwatch 1.2.0", "h1"),
        P(
            "Fieldwatch releases through 1.1.44 implement something close to this proposal. They read a baseline file and match the signatures in it. They are not yet conforming parsers. Fieldwatch 1.2.0 is the release that will conform. This section is the plan for that release. It binds 1.2.0 only in the sense that 1.2.0 is how this project will meet the conforming parser. Other products are not required to ship on the same day.",
            "body",
        ),
    ]
    flow += bullets([
        "Fieldwatch 1.2.0 adds the conforming parser in section 5.3. Behavior on a baseline file stays the same for every stock signature that exists at Release 1.",
        "The stock file those phones download stays <font face='Courier' size='9'>format</font> <font face='Courier' size='9'>fieldwatch-signatures</font> and <font face='Courier' size='9'>formatVersion</font> 1. It uses no radio token other than <font face='Courier' size='9'>BLE</font>, <font face='Courier' size='9'>WIFI</font>, or null. A file written for other readers uses <font face='Courier' size='9'>radio-signatures</font>.",
        "Each stock signature gains <font face='Courier' size='9'>publisher</font> <font face='Courier' size='9'>fieldwatch</font>. Ids are unchanged. The catalog number increases in that same release, so a phone whose parser can store the field will apply it. The field is omitted when empty, so custom signatures without a publisher stay that way.",
        "A phone that updates from an older app version treats a stored row with no publisher and a file row with publisher <font face='Courier' size='9'>fieldwatch</font> as the same signature when the ids are equal. Mute and extra rules remain.",
        "The public file does not contain a rule kind, class, radio, or decode type that 1.1.44 does not know. Those phones still update. The conforming parser implements the unknown-radio rule in section 5.3, so a later file from another project can name a radio this phone does not match without being rejected.",
        "Appendix A is added to the project's tests. A change to matching that fails a vector is a format change under section 7, not an unannounced improvement.",
    ])
    flow.append(P(
        "Publishing the publisher field before the release that stores it would move older phones onto a new catalog number and then throw the field away. The following file, still at that number, would be skipped. This plan avoids that by shipping the parser and the field together.",
        "body",
    ))

    flow += [
        P("24.  What a match does not mean", "h1"),
        P(
            "A match means the observation satisfied the rules. Names are chosen by the device and can be changed. Company identifiers are shared by many products from one company. An address prefix is shared by every interface that vendor shipped. A service UUID is often shared by a family, and sometimes by every device that speaks a published protocol.",
            "body",
        ),
        P(
            "A conforming application MUST NOT present a match, by itself, as identification of a person, as proof that a medical or safety event occurred, or as proof that a particular object is the one a rule was written about. Notes in the Fieldwatch catalog say this for health and finder signatures. The requirement belongs on the application, because the file cannot enforce a sentence.",
            "body",
        ),
        P(
            "The file contains no location, no owner, and no credential. A publisher SHOULD NOT put a serial number, a full address, or a person's name into a signature id, a note, or a pattern, beyond the model-level names the rule needs.",
            "body",
        ),

        P("25.  Examples", "h1"),
        P(
            "A baseline file. The format name here is <font face='Courier' size='9'>fieldwatch-signatures</font> so a Fieldwatch 1.1.44 parser can read it. A file written for this specification uses <font face='Courier' size='9'>radio-signatures</font>. Both parsers match a Bluetooth advertisement named TD-H9-0536, and both refuse a Wi-Fi network of that name because the rule sets the radio.",
            "body",
        ),
    ]
    flow.append(code_block(
        '{\n'
        '  "format": "fieldwatch-signatures",\n'
        '  "formatVersion": 1,\n'
        '  "catalogVersion": 113,\n'
        '  "fleets": [{\n'
        '    "id": "fleet-tidradio-td-h9",\n'
        '    "name": "TIDRADIO TD-H9",\n'
        '    "matchAny": true,\n'
        '    "kind": "TWO_WAY",\n'
        '    "rules": [\n'
        '      { "kind": "NAME_GLOB", "text": "TD-H9*", "radio": "BLE" }\n'
        '    ]\n'
        '  }]\n'
        '}'
    ))
    flow.append(P(
        "The same signature, once Fieldwatch 1.2.0 adds the publisher. A 1.1.44 parser ignores <font face='Courier' size='9'>publisher</font> and matches as before. A conforming parser stores the publisher and treats this row as the same signature as the one above.",
        "body",
    ))
    flow.append(code_block(
        '{\n'
        '  "id": "fleet-tidradio-td-h9",\n'
        '  "publisher": "fieldwatch",\n'
        '  "name": "TIDRADIO TD-H9",\n'
        '  "matchAny": true,\n'
        '  "kind": "TWO_WAY",\n'
        '  "rules": [\n'
        '    { "kind": "NAME_GLOB", "text": "TD-H9*", "radio": "BLE" }\n'
        '  ]\n'
        '}'
    ))
    flow.append(P(
        "A file a conforming parser accepts and a baseline parser rejects. <font face='Courier' size='9'>matchAny</font> is true, so the unknown rule is ignored and the name rule still matches. The same signature with <font face='Courier' size='9'>matchAny</font> false MUST NOT match, because the unknown rule is required and the parser cannot perform it. The parser does not reject the file.",
        "body",
    ))
    flow.append(code_block(
        '{\n'
        '  "id": "example.beacon",\n'
        '  "publisher": "example.com",\n'
        '  "name": "Example beacon",\n'
        '  "matchAny": true,\n'
        '  "rules": [\n'
        '    { "kind": "NAME_GLOB", "text": "EX-BEACON", "radio": "BLE" },\n'
        '    { "kind": "SOMETHING_NEW", "text": "later" }\n'
        '  ]\n'
        '}'
    ))
    flow.append(P(
        "This id is not a Fieldwatch stock id, and the publisher is present on both sides of any later merge, so it will not attach itself to a Fieldwatch row that happens to use the same id text.",
        "body",
    ))
    flow.append(P(
        "A signature for a radio Fieldwatch does not hear. The rule sets <font face='Courier' size='9'>radio</font>, so an omitted-radio name of the same text does not match this observation. A parser with no Classic profile does not match the signature. The file uses <font face='Courier' size='9'>format</font> <font face='Courier' size='9'>radio-signatures</font>, which is the name in this specification. It is not a baseline file, and a 1.1.44 parser would reject it. The Classic profile itself is not defined here.",
        "body",
    ))
    flow.append(code_block(
        '{\n'
        '  "format": "radio-signatures",\n'
        '  "formatVersion": 1,\n'
        '  "fleets": [{\n'
        '    "id": "example.classic-handset",\n'
        '    "publisher": "example.com",\n'
        '    "name": "Example Classic handset",\n'
        '    "matchAny": true,\n'
        '    "rules": [\n'
        '      { "kind": "NAME_GLOB", "text": "HANDSET-*", "radio": "BT_CLASSIC" }\n'
        '    ]\n'
        '  }]\n'
        '}'
    ))

    flow += [
        P("26.  Taking part", "h1"),
        P(
            "This document is a proposal from Off Grid Pete LLC, opened so other open-source projects can build receivers that read the same file. Fieldwatch is the first implementation and the publisher of the baseline profiles for Bluetooth Low Energy and Wi-Fi. Releases through 1.1.44 are close to this file. Fieldwatch 1.2.0 will conform to it. Fieldwatch does not have to be the only implementation.",
            "body",
        ),
        P(
            "A project takes part by implementing the core matcher and stating which profiles it implements, by reviewing this proposal, or by proposing a profile for a radio that project already receives. The proposal includes the observation fields, the rule kinds, and the test vectors. The profile should be written by someone who can hear that radio. An empty profile is not a contribution.",
            "body",
        ),
        P(
            "Comments and proposed profiles are invited at github.com/OffGridPete/Fieldwatch. A revision will name projects that contribute a profile. There is no fee and no separate organization in this proposal. If the set of implementers grows, a later revision can describe how changes are agreed. This revision does not create that body in advance.",
            "body",
        ),
        P(
            "Fieldwatch will keep publishing a baseline file that current phones load. Another project can publish its own file, with its own publisher token, including radios Fieldwatch does not match. A conforming parser reads the signatures it understands and leaves the rest unmatched.",
            "body",
        ),

        P("Appendix A.  Test vectors", "h1"),
        P(
            "Each row is one observation against one rule, unless a signature is named. Names are the advertised name. The radio is Bluetooth Low Energy unless the row says otherwise. A blank cell means the member is omitted.",
            "body",
        ),
    ]
    flow.append(table(
        ["Rule", "Observation", "Match"],
        [
            ["NAME_GLOB TD-H9*, radio BLE", "Name TD-H9-0536", "Yes"],
            ["NAME_GLOB TD-H9*, radio BLE", "Name td-h9", "Yes"],
            ["NAME_GLOB TD-H9*, radio BLE", "Name XTD-H9", "No"],
            ["NAME_GLOB TD-H9*, radio BLE", "Wi-Fi name TD-H9-0536", "No"],
            ["NAME_GLOB TD-H9*, radio BLE", "Empty name", "No"],
            ["NAME_GLOB ASB??????, radio BLE", "Name ASB99C2F0", "Yes"],
            ["NAME_GLOB ASB??????, radio BLE", "Name ASB99C2F", "No"],
            ["NAME_GLOB ASB??????, radio BLE", "Name ASB99C2F01", "No"],
            ["NAME_GLOB walkie-talkie, radio BLE", "Name Walkie-Talkie", "Yes"],
            ["NAME_GLOB walkie-talkie, radio BLE", "Name walkie-talkie 2", "No"],
            ["NAME_GLOB ICOM BT(IC-705), radio BLE", "Name ICOM BT(IC-705)", "Yes"],
            ["NAME_GLOB IC-705, radio WIFI", "Wi-Fi name IC-705", "Yes"],
            ["NAME_GLOB IC-705, radio WIFI", "Wi-Fi name IC-7050", "No"],
            ["NAME_GLOB IC-705, radio WIFI", "Bluetooth name IC-705", "No"],
            ["SERVICE_UUID FEA0, radio omitted", "Service 0000FEA0-0000-1000-8000-00805F9B34FB", "Yes"],
            ["SERVICE_UUID FEA0, radio omitted", "Wi-Fi, that UUID presented as a service", "No"],
            ["SERVICE_UUID 00000001-ba2a-46c9-ae49-01b0961f68bb", "That UUID, any case", "Yes"],
            ["SERVICE_UUID 00000001-ba2a-46c9-ae49-01b0961f68bb", "The same UUID with 0002 in the first group", "No"],
            ["SERVICE_UUID 00000001-ba2a-46c9-ae49-01b0961f68bb", "16-bit UUID 0001", "No"],
            ["MANUFACTURER_ID companyId 1704, radio BLE", "Company 1704", "Yes"],
            ["MANUFACTURER_ID companyId 1704, radio BLE", "Company 117", "No"],
            ["OUI 00147B", "Address 00:14:7B:01:02:03", "Yes"],
            ["OUI 0050F2", "Wi-Fi vendor IE 00:50:F2 only, other address", "No"],
            ["matchAny false, two name rules", "A name that meets only one", "No"],
            ["Unknown kind plus a matching name rule, matchAny true", "The name matches", "Yes on a conforming parser. The baseline parser rejects the file."],
            ["Unknown kind plus a matching name rule, matchAny false", "The name matches", "No on a conforming parser. The unknown rule is required. The baseline parser rejects the file."],
            ["NAME_GLOB TD-H9*, radio omitted", "Bluetooth Classic inquiry named TD-H9", "No. An omitted radio is BLE and WIFI only."],
            ["radio BT_CLASSIC, matchAny true, plus a matching BLE name rule. Parser has no Classic profile", "A BLE name that meets the BLE rule", "Yes. The Classic rule does not match. The BLE rule does."],
            ["radio BT_CLASSIC, matchAny false. Parser has no Classic profile", "A Classic inquiry the rule would have matched", "No. The parser cannot perform the required test, so the signature does not match."],
            ["publisher fieldwatch, id fleet-tidradio-td-h9", "Stored copy of that id with no publisher", "Same signature, not a second row"],
            ["publisher example.com, id fleet-tidradio-td-h9", "Stored fieldwatch row with that id", "Different signature"],
        ],
        [2.15 * inch, 2.15 * inch, MEASURE - 4.30 * inch],
    ))
    flow.append(Spacer(1, 8))

    flow += [
        P("Appendix B.  Baseline registry", "h1"),
        P(
            "These are the names a baseline parser accepts. A file that uses only these names, with formatVersion 1, is a baseline file. The class list is the Fieldwatch set at Release 1, including values kept so older files still parse.",
            "body",
        ),
        P("Rule kinds. " + ", ".join([
            "OUI", "MAC_PREFIX", "NAME_CONTAINS", "NAME_GLOB", "SERVICE_UUID",
            "SERVICE_DATA", "MANUFACTURER_ID", "MANUFACTURER_DATA", "RADIO_KIND",
            "HIDDEN_SSID", "VENDOR_IE_OUI",
        ]) + ".", "body"),
        P("Radios. Baseline tokens are BLE, WIFI, or null. A further token is registered with a profile under section 17 and is not a baseline name. A file that baseline parsers must read MUST NOT use it. This proposal registers no further token. BT_CLASSIC in the examples is an illustration, not a registered profile.", "body"),
        P("Classes. " + ", ".join([
            "FINDER", "BEACON", "SIGNAGE", "WEARABLE", "SURVEILLANCE", "DRONE",
            "HACKING", "BODYWORN", "LAW_ENFORCEMENT", "VEHICLE", "GLASSES",
            "AUDIO", "CAMERA", "THERMOSTAT", "LOCK", "HEALTH", "HOME", "ISP",
            "MESH", "TWO_WAY", "MILITARY", "PHONE", "OTHER",
        ]) + ". BODYWORN remains in the registry so older files parse. Fieldwatch displays it with wearables.", "body"),
        P("Decode sources. manufacturerData, serviceData.", "body"),
        P("Decode types. u8, i8, u16, i16, u24, u32, i32, f32, bits, utf8, hex, mac, bool.", "body"),
        P("Decode endian. le, be.", "body"),
        P("Decode gate operations. eq, neq, mask, nmask, len.", "body"),
        P("Publisher token reserved by this project. fieldwatch.", "body"),

        P("References", "h1"),
    ]
    refs = [
        "S. Bradner, Key words for use in RFCs to Indicate Requirement Levels, BCP 14, RFC 2119.",
        "B. Leiba, Ambiguity of Uppercase vs Lowercase in RFC 2119 Key Words, BCP 14, RFC 8174.",
        "T. Bray, The JavaScript Object Notation (JSON) Data Interchange Format, RFC 8259.",
        "IEEE, Guidelines for Use of Extended Unique Identifier (EUI), Organizationally Unique Identifier (OUI), and Company ID.",
        "Bluetooth Core Specification, Service UUID and the Bluetooth base UUID. Bluetooth SIG, Assigned Numbers, Company Identifiers.",
        "Fieldwatch stock file fieldwatch-signatures, formatVersion 1, as published by Off Grid Pete LLC. Catalog 113 is the stock snapshot current with Release 1.",
    ]
    for i, text in enumerate(refs, start=1):
        flow.append(Paragraph(f"{i}.  {esc(text)}", S["ref"]))

    flow.append(Spacer(1, 8))
    flow.append(P(
        "Comments and proposed profiles are invited at github.com/OffGridPete/Fieldwatch. Section 26 is how a project takes part. This document is RSI-1, Release 1, 14 November 2026. Fieldwatch™ is a trademark of Off Grid Pete LLC. Registration pending.",
        "body",
    ))
    flow.append(Spacer(1, 8))
    flow.append(P("Revision history", "h1"))
    flow.append(P(
        "Changes for each revision are recorded here. A later revision adds a row and leaves the earlier rows as they were published.",
        "body",
    ))
    flow.append(table(
        ["Revision", "Date", "Changes"],
        [[a, b, c] for a, b, c in HISTORY],
        [0.9 * inch, 1.5 * inch, MEASURE - 2.4 * inch],
    ))
    return flow


if __name__ == "__main__":
    build(story())
