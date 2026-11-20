"""Create a fictitious document for camera, gallery and OCR checks."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from reportlab.pdfgen.canvas import Canvas

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "docs" / "examples"
DEST.mkdir(parents=True, exist_ok=True)
font_root = Path("C:/Windows/Fonts")
font_file = font_root / "arial.ttf" if font_root.exists() else Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
page = Image.new("RGB", (1240, 1754), "white")
draw = ImageDraw.Draw(page)
title = ImageFont.truetype(str(font_file), 54)
body = ImageFont.truetype(str(font_file), 35)
draw.text((100, 140), "RACHUNEK ZA INTERNET", fill="black", font=title)
draw.line((100, 240, 1140, 240), fill="black", width=3)
lines = ["Dokument testowy - dane fikcyjne", "Numer: FV/11/2026", "Data: 20.11.2026", "",
         "Usluga: Internet domowy", "Kwota do zaplaty: 129,00 PLN", "Termin platnosci: 30.11.2026", "",
         "Notatka: Zachowaj dokument w archiwum.", "Wyszukaj slowo internet po wykonaniu OCR."]
for i, text in enumerate(lines):
    draw.text((100, 310 + i*78), text, fill="black", font=body)
page.save(DEST / "rachunek-testowy.jpg", quality=95)
canvas = Canvas(str(DEST / "rachunek-testowy.pdf"), pagesize=(595.28, 841.89))
canvas.drawImage(str(DEST / "rachunek-testowy.jpg"), 0, 0, width=595.28, height=841.89)
canvas.setTitle("Rachunek testowy do skanowania")
canvas.save()
print(DEST)
