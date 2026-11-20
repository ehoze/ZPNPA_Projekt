"""Build a printable project report from repository documentation.

Usage: python tools/make_report.py
Optional dependency: pip install -r tools/requirements-docs.txt
"""
from pathlib import Path
from html import escape
import re

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, Image,
    PageBreak, Preformatted, KeepTogether,
)
from reportlab.graphics.shapes import Drawing, Rect, String, Line, Polygon, Ellipse, Circle

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "artifacts" / "Skaner-dokumentacja.pdf"
FONT_ROOT = Path("C:/Windows/Fonts")
if FONT_ROOT.exists():
    regular, bold = FONT_ROOT / "arial.ttf", FONT_ROOT / "arialbd.ttf"
else:
    FONT_ROOT = Path("/usr/share/fonts/truetype/dejavu")
    regular, bold = FONT_ROOT / "DejaVuSans.ttf", FONT_ROOT / "DejaVuSans-Bold.ttf"
pdfmetrics.registerFont(TTFont("Report", str(regular)))
pdfmetrics.registerFont(TTFont("ReportBold", str(bold)))
pdfmetrics.registerFontFamily("Report", normal="Report", bold="ReportBold", italic="Report", boldItalic="ReportBold")

styles = getSampleStyleSheet()
for style in styles.byName.values():
    style.fontName = "Report"
styles.add(ParagraphStyle("BodyPL", fontName="Report", fontSize=9, leading=14, spaceAfter=7))
styles.add(ParagraphStyle("CellPL", parent=styles["BodyPL"], fontSize=8, leading=11, spaceAfter=0))
styles["Title"].fontName = "ReportBold"
styles["Title"].fontSize = 24
styles["Title"].leading = 30
for name in ("Heading1", "Heading2", "Heading3"):
    styles[name].fontName = "ReportBold"
    styles[name].textColor = colors.HexColor("#265E51")
styles["Heading1"].fontSize = 16
styles["Heading2"].fontSize = 12
styles["Heading3"].fontSize = 10


def inline(text):
    text = escape(text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"`([^`]+)`", r"<font name='Courier'>\1</font>", text)
    text = re.sub(r"\[([^\]]+)\]\((https?://[^)]+)\)", r"<link href='\2' color='#265E51'>\1</link>", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    return text


def box(drawing, x, y, w, h, label, small=False):
    drawing.add(Rect(x, y, w, h, rx=6, ry=6,
        fillColor=colors.HexColor("#EEF5F0"), strokeColor=colors.HexColor("#73988A")))
    lines = label.split("\n")
    for i, line in enumerate(lines):
        drawing.add(String(x + w / 2, y + h / 2 + (len(lines)-1)*6-i*12-3,
            line, fontName="Report", fontSize=8 if small else 9, textAnchor="middle"))


def arrow(drawing, x1, y1, x2, y2):
    drawing.add(Line(x1, y1, x2, y2, strokeColor=colors.HexColor("#53645D")))
    if y1 != y2:
        sign = 1 if y2 > y1 else -1
        points = [x2, y2, x2-3, y2-sign*6, x2+3, y2-sign*6]
    else:
        sign = 1 if x2 > x1 else -1
        points = [x2, y2, x2-sign*6, y2-3, x2-sign*6, y2+3]
    drawing.add(Polygon(points, fillColor=colors.HexColor("#53645D"), strokeColor=None))


def diagram(code):
    d = Drawing(500, 300)
    if "erDiagram" in code:
        d = Drawing(500, 215)
        box(d, 10, 15, 215, 180, "DOCUMENTS\n\nid PK\ntitle, text, searchText\ncreatedAt, pageCount")
        box(d, 285, 15, 205, 180, "PAGES\n\ndocumentId PK i FK\nposition PK\nfileName")
        arrow(d, 225, 105, 285, 105)
        d.add(String(255, 120, "1 : N", fontName="Report", fontSize=10, textAnchor="middle"))
    elif "flowchart LR" in code:
        d = Drawing(500, 310)
        d.add(Rect(150, 0, 345, 310, fillColor=None, strokeColor=colors.HexColor("#73988A")))
        d.add(String(320, 295, "Skaner dokumentów", fontName="ReportBold", fontSize=9, textAnchor="middle"))
        d.add(Circle(55, 195, 12, fillColor=None, strokeColor=colors.black))
        for points in [(55,183,55,140),(30,166,80,166),(55,140,35,110),(55,140,75,110)]:
            d.add(Line(*points, strokeColor=colors.black))
        d.add(String(55, 90, "Użytkownik", fontName="Report", fontSize=9, textAnchor="middle"))
        labels = ["Skanuj i koryguj", "Znajdź dokument", "Przeglądaj strony", "Edytuj nazwę i tekst", "Eksportuj PDF", "Usuń dokument", "Ustaw blokadę i odblokuj"]
        for i, label in enumerate(labels):
            y = 265 - i*40
            d.add(Ellipse(330, y, 145, 16, fillColor=colors.HexColor("#EEF5F0"), strokeColor=colors.HexColor("#73988A")))
            d.add(String(330, y-3, label, fontName="Report", fontSize=9, textAnchor="middle"))
            d.add(Line(82, 160, 185, y, strokeColor=colors.HexColor("#53645D")))
    else:
        box(d, 170, 252, 160, 38, "Compose i Navigation")
        box(d, 170, 190, 160, 38, "ScannerViewModel")
        arrow(d, 250, 252, 250, 228)
        box(d, 170, 125, 160, 40, "DocumentRepository")
        arrow(d, 250, 190, 250, 165)
        labels = [(5, "Room i SQLCipher"), (175, "Skany AES-GCM"), (345, "ML Kit OCR i PDF")]
        for x, label in labels:
            box(d, x, 55, 150, 40, label, small=True)
            arrow(d, 250, 125, x+75, 95)
        box(d, 175, 0, 150, 32, "Android Keystore", small=True)
        arrow(d, 250, 55, 250, 32)
    return d


def image_cell(text, folder):
    match = re.search(r"!\[([^\]]*)\]\(([^)]+)\)", text)
    if not match:
        return Paragraph(inline(text), styles["CellPL"])
    path = folder / match[2]
    if not path.exists():
        return Paragraph("Zrzut ekranu do uzupełnienia: " + escape(match[1]), styles["CellPL"])
    width, height = ImageReader(str(path)).getSize()
    ratio = min(225/width, 430/height)
    return Image(str(path), width=width*ratio, height=height*ratio)


def render_markdown(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    story = []
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if not line:
            i += 1
            continue
        if line.startswith("```"):
            language = line[3:]
            block = []
            i += 1
            while i < len(lines) and not lines[i].strip().startswith("```"):
                block.append(lines[i])
                i += 1
            if language == "mermaid":
                drawing = diagram("\n".join(block))
                if story and isinstance(story[-1], Paragraph) and story[-1].style.name.startswith("Heading"):
                    story.append(KeepTogether([story.pop(), drawing]))
                else:
                    story.append(drawing)
            else:
                wrapped = []
                for part in block:
                    while len(part) > 92:
                        wrapped.append(part[:92])
                        part = part[92:]
                    wrapped.append(part)
                story.append(Preformatted("\n".join(wrapped), styles["Code"]))
            story.append(Spacer(1, 10))
        elif line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                cells = [cell.strip() for cell in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r"[-: ]+", cell) for cell in cells):
                    rows.append(cells)
                i += 1
            count = len(rows[0])
            if count == 2:
                widths = [250, 250] if any("![" in cell for row in rows for cell in row) else [150, 350]
            elif count == 3 and rows[0][0] == "ID":
                widths = [35, 160, 305]
            elif count == 3 and rows[0][1] == "Liczba":
                widths = [280, 60, 160]
            elif count == 3:
                widths = [160, 220, 120]
            else:
                widths = [500/count]*count
            table = Table([[image_cell(cell, path.parent) for cell in row] for row in rows], colWidths=widths,
                repeatRows=1, hAlign="LEFT")
            table.setStyle(TableStyle([
                ("BACKGROUND", (0,0), (-1,0), colors.HexColor("#D8EDE3")),
                ("VALIGN", (0,0), (-1,-1), "TOP"),
                ("GRID", (0,0), (-1,-1), .35, colors.HexColor("#CBD7CF")),
                ("LEFTPADDING", (0,0), (-1,-1), 7), ("RIGHTPADDING", (0,0), (-1,-1), 7),
                ("TOPPADDING", (0,0), (-1,-1), 7), ("BOTTOMPADDING", (0,0), (-1,-1), 7),
            ]))
            story.extend([table, Spacer(1, 12)])
            continue
        elif line.startswith("#"):
            level = len(line) - len(line.lstrip("#"))
            text = line.lstrip("# ")
            story.append(Paragraph(inline(text), styles["Heading" + str(min(level,3))]))
        elif line.startswith("- ") or re.match(r"\d+\. ", line):
            story.append(Paragraph(inline(line), styles["BodyPL"]))
        else:
            parts = [line]
            while i+1 < len(lines) and lines[i+1].strip() and not re.match(r"[#|`-]|\d+\. ", lines[i+1].strip()):
                i += 1
                parts.append(lines[i].strip())
            story.append(Paragraph(inline(" ".join(parts)), styles["BodyPL"]))
        i += 1
    return story


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("Report", 8)
    canvas.setFillColor(colors.HexColor("#53645D"))
    canvas.drawString(45, 27, "Skaner dokumentów z OCR · Projekt 12")
    canvas.drawRightString(550, 27, str(doc.page))
    canvas.restoreState()


if __name__ == "__main__":
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    story = [Spacer(1, 115), Paragraph("Skaner dokumentów z OCR", styles["Title"]),
        Spacer(1, 20), Paragraph("Projekt 12 · ZPNPA", styles["Title"]), Spacer(1, 25),
        Paragraph("Dokumentacja funkcjonalna i techniczna, diagramy, instrukcja uruchomienia oraz wyniki testów.", styles["BodyPL"]),
        Paragraph("Kotlin · Jetpack Compose · MVVM · ML Kit · szyfrowane archiwum", styles["BodyPL"]), PageBreak()]
    for index, name in enumerate(["README.md", "docs/DOKUMENTACJA.md", "docs/TESTY.md", "docs/OBRONA.md"]):
        if index:
            story.append(PageBreak())
        source = ROOT / name
        if source.exists():
            story.extend(render_markdown(source))
    SimpleDocTemplate(str(OUTPUT), pagesize=(595.28, 841.89), leftMargin=45,
        rightMargin=45, topMargin=45, bottomMargin=45,
        title="Skaner dokumentów z OCR", author="Projekt ZPNPA").build(story, onFirstPage=footer, onLaterPages=footer)
    print(OUTPUT)
