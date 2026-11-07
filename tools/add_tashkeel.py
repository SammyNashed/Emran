#!/usr/bin/env python3
"""Adds tashkeel to Emran's calligraphy images, by hand-placed marks. Nothing is generated.

    python3 tools/add_tashkeel.py <clean png folder> <AMoshref-Thulth ttf (OFL)> <output folder> [--preview file]

Marks are (kind, x, y): x and y in tenths of the source image, 0-10; y may run past the image, which is padded.
f fatha, d damma, k kasra, s sukun, S shadda.
"""
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageChops

SRC, FONT, OUT = sys.argv[1:4]
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
GLYPH = {"f": "َ", "d": "ُ", "k": "ِ", "s": "ْ", "S": "ّ"}
PAD = 0.22

# word as read, then its marks.
TABLE = {
    "day_1_sun":  ("الأَحَد", "cal_day1", [("f", 7.5, -0.6), ("f", 5.0, 4.6)]),
    "day_2_mon":  ("الاثْنَيْن", "cal_day2", [("s", 6.0, 2.5), ("f", 4.8, 3.9), ("s", 2.4, 3.8)]),
    "day_3_tue":  ("الثُّلَاثَاء", "cal_day3", [("d", 7.2, 0.9), ("S", 7.2, 2.6), ("f", 4.2, 2.4), ("f", 3.2, 2.6)]),
    "day_4_wed":  ("الأَرْبِعَاء", "cal_day4", [("f", 8.0, -0.6), ("s", 6.2, 4.2), ("k", 4.8, 10.5), ("f", 3.7, 3.8)]),
    "day_5_thu":  ("الخَمِيس", "cal_day5", [("f", 7.4, 2.3), ("k", 5.4, 8.5)]),
    "day_6_fri":  ("الجُمُعَة", "cal_day6", [("d", 7.0, 4.3), ("d", 5.2, 4.3), ("f", 3.6, 4.3)]),
    "day_7_sat":  ("السَّبْت", "cal_day7", [("S", 6.0, 3.0), ("f", 6.0, 1.3), ("s", 4.5, 3.4)]),
    "month_01": ("مُحَرَّم", "cal_month_1", [("d", 8.8, -1.6), ("f", 6.3, -0.6), ("S", 4.6, -0.8), ("f", 4.6, -2.4)]),
    "month_02": ("صَفَر", "cal_month_2", [("f", 7.8, 0.2), ("f", 4.8, -0.9)]),
    "month_03": ("رَبِيع الأَوَّل", "cal_month_3", [("f", 9.4, 2.6), ("k", 7.7, 10.3), ("f", 3.8, -0.8), ("S", 3.05, 3.1), ("f", 3.05, 1.4)]),
    "month_04": ("رَبِيع الآخِر", "cal_month_4", [("f", 9.6, 3.6), ("k", 8.0, 10.2), ("k", 2.4, 9.6)]),
    "month_05": ("جُمَادَى الأُولَى", "cal_month_5", [("d", 9.0, 4.0), ("f", 7.6, 2.6), ("f", 5.8, 2.6), ("d", 3.0, -0.8)]),
    "month_06": ("جُمَادَى الآخِرَة", "cal_month_6", [("d", 8.9, 3.6), ("f", 7.6, 2.0), ("f", 6.0, 1.4), ("k", 2.0, 9.3), ("f", 1.0, 3.2)]),
    "month_07": ("رَجَب", "cal_month_7", [("f", 9.4, -1.5), ("f", 6.8, -1.5)]),
    "month_08": ("شَعْبَان", "cal_month_8", [("f", 8.8, 1.2), ("s", 6.4, 3.8), ("f", 4.6, 4.8)]),
    "month_09": ("رَمَضَان", "cal_month_9", [("f", 9.4, 3.4), ("f", 7.8, 3.8), ("f", 5.8, 2.4)]),
    "month_10": ("شَوَّال", "cal_month_10", [("f", 8.6, 2.0), ("S", 6.4, 3.8), ("f", 6.4, 2.4)]),
    "month_11": ("ذُو القَعْدَة", "cal_month_11", [("d", 9.3, 1.6), ("f", 5.3, 1.8), ("s", 3.0, 4.2), ("f", 2.0, 5.4)]),
    "month_12": ("ذُو الحِجَّة", "cal_month_12", [("d", 9.3, 2.0), ("k", 4.9, 9.8), ("S", 3.4, 4.2), ("f", 3.4, 2.6)]),
}

os.makedirs(OUT, exist_ok=True)
cells = []
for name, (word, out, marks) in TABLE.items():
    src = Image.open(os.path.join(SRC, name + ".png")).convert("RGBA")
    W, H = src.size
    pad = int(H * PAD)
    canvas = Image.new("RGBA", (W, H + pad * 2), (255, 255, 255, 0))
    canvas.alpha_composite(src, (0, pad))
    alpha = canvas.getchannel("A")
    # Mark size follows the pen: the words were written at about the same pen for a 300 px tall image.
    ref = max(H, 300)
    font = ImageFont.truetype(FONT, int(ref * 0.5))
    stroke = max(2, round(ref * 0.0125))
    ink = Image.new("L", canvas.size, 0)
    d = ImageDraw.Draw(ink)
    for kind, x, y in marks:
        if kind in "sS":
            # Drawn, not from the font: thickened glyphs close up, and a filled sukun reads as a letter's dot
            # (شعبان would turn into شغبان). The sukun stays an open ring; the shadda keeps three clear teeth.
            q = ref / 300 * 4  # drawn 4x and scaled down, for clean edges
            size = int(70 * q)
            tile = Image.new("L", (size, size), 0)
            td = ImageDraw.Draw(tile)
            c0 = size / 2
            if kind == "s":
                r = 13 * q
                td.ellipse((c0 - r, c0 - r, c0 + r, c0 + r), outline=255, width=round(5 * q))
            else:
                pts = [(c0 + 25 * q, c0 - 15 * q), (c0 + 16 * q, c0 + 9 * q), (c0, c0 - 9 * q), (c0 - 16 * q, c0 + 9 * q), (c0 - 25 * q, c0 - 15 * q)]
                td.line(pts, fill=255, width=round(8 * q), joint="curve")
                for px_, py_ in pts:
                    rr = 4 * q
                    td.ellipse((px_ - rr, py_ - rr, px_ + rr, py_ + rr), fill=255)
            small = tile.resize((size // 4, size // 4), Image.LANCZOS)
            ink.paste(255, (int(W * x / 10 - small.width / 2), int(pad + H * y / 10 - small.height / 2)), small)
            continue
        g = "ـ" + GLYPH[kind]
        base = "ـ"
        # Isolate the mark: draw it on a kashida, then take the kashida away.
        tmp = Image.new("L", canvas.size, 0); td = ImageDraw.Draw(tmp)
        tmp2 = Image.new("L", canvas.size, 0); td2 = ImageDraw.Draw(tmp2)
        at = (W / 2, H)
        td.text(at, g, font=font, fill=255, anchor="ms", direction="rtl", language="ar", stroke_width=stroke, stroke_fill=255)
        td2.text(at, base, font=font, fill=255, anchor="ms", direction="rtl", language="ar", stroke_width=stroke + 1, stroke_fill=255)
        mark = ImageChops.subtract(tmp, tmp2)
        box = mark.getbbox()
        if not box:
            raise SystemExit(f"no ink for {kind}")
        piece = mark.crop(box)
        px = W * x / 10 - piece.width / 2
        py = pad + H * y / 10 - piece.height / 2
        ink.paste(255, (int(px), int(py)), piece)
    canvas.putalpha(ImageChops.lighter(alpha, ink))
    bbox = canvas.getchannel("A").getbbox()
    canvas = canvas.crop(bbox)
    canvas.save(os.path.join(OUT, out + ".webp"), lossless=True, method=6)
    cells.append((name, word, canvas))

if PREVIEW:
    CW, CH, COLS = 500, 300, 4
    rows = (len(cells) + COLS - 1) // COLS
    sheet = Image.new("RGB", (COLS * CW, rows * CH), (30, 34, 46))
    for i, (name, word, im) in enumerate(cells):
        im = im.copy(); im.thumbnail((CW - 20, CH - 20))
        sheet.paste(im, (i % COLS * CW + (CW - im.width) // 2, i // COLS * CH + (CH - im.height) // 2), im)
    sheet.save(PREVIEW)
