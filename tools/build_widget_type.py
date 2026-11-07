#!/usr/bin/env python3
"""Renders the words and digits Emran's widgets show into images, in the thmanyah typeface.

The font itself is NOT in this repository and must not be added: its license forbids hosting or
redistributing the files. Get it from https://font.thmanyah.com/ and pass the folder holding its otf files:

    python3 tools/build_widget_type.py "<path>/thmanyah typeface"

Output: app/src/main/assets/widget_type/ (index.json + one webp per word), read by glance/art/TypeArt.kt.
"""
import glob, json, os, shutil, sys
from PIL import Image, ImageDraw, ImageFont

ROOT = sys.argv[1]
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "widget_type")
K = "ـ"

def face(pattern):
    return glob.glob(os.path.join(ROOT, "**", pattern), recursive=True)[0]

FONT = {"R": face("thmanyahsans-Regular.otf"), "M": face("thmanyahsans-Bold.otf"),
        "D": face("thmanyahserifdisplay-Bold.otf"), "T": face("thmanyahserifdisplay-Regular.otf")}

W_EN = "Monday Tuesday Wednesday Thursday Friday Saturday Sunday".split()
M_EN = "January February March April May June July August September October November December".split()
HIJRI_EN = ["Muharram", "Safar", "Rabiʿ I", "Rabiʿ II", "Jumada I", "Jumada II", "Rajab", "Shaʿban", "Ramadan", "Shawwal", "Dhu al-Qaʿdah", "Dhu al-Hijjah"]
PRAYER_EN = "Fajr Sunrise Dhuhr Asr Maghrib Isha Jumu'ah".split()
MISC_EN = "Next prayer occasion Today day days until Eid al-Fitr al-Adha Day of Arafah Islamic New Year AM PM".split()
W_AR = "الأحد الاثنين الثلاثاء الأربعاء الخميس الجمعة السبت".split()
M_AR = "يناير فبراير مارس أبريل مايو يونيو يوليو أغسطس سبتمبر أكتوبر نوفمبر ديسمبر".split()
HIJRI_AR = ["محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"]
PRAYER_AR = "الفجر الشروق الظهر العصر المغرب العشاء الجمعة".split()
MISC_AR = ["الصلاة", "القادمة", "المناسبة", "اليوم", "يوم", "واحد", "أيام", "يومًا", "عيد", "الفطر", "الأضحى", "عرفة", "رأس", "السنة", "الهجرية", "ص", "م"] + list("حنثرخجس")
OCC_AR = ["رمضان", "عيد الفطر", "يوم عرفة", "عيد الأضحى", "رأس السنة الهجرية"]
HIJRI_CUT = [("مح", "رم"), ("ص", "فر"), ("ربي", "ع الأول"), ("ربي", "ع الآخر"), ("ج", "مادى الأولى"), ("ج", "مادى الآخرة"), ("ر", "جب"), ("شع", "بان"), ("رم", "ضان"), ("ش", "وال"), ("ذو ال", "قعدة"), ("ذو ال", "حجة")]
GREG_CUT = [("ي", "ناير"), ("فب", "راير"), ("م", "ارس"), ("أب", "ريل"), ("م", "ايو"), ("ي", "ونيو"), ("ي", "وليو"), ("أغ", "سطس"), ("سب", "تمبر"), ("أك", "توبر"), ("نوف", "مبر"), ("ديس", "مبر")]
DIGITS = list("0123456789")
AR_DIGITS = [chr(0x660 + i) for i in range(10)]
PUNCT = list(":%,/.·،()")

def words(*groups):
    out = []
    for g in groups:
        for item in g:
            for w in item.split():
                if w not in out:
                    out.append(w)
    return out

small = words(W_EN, [w[:3] for w in W_EN], [w[0] for w in W_EN], M_EN, [m[:3] for m in M_EN], HIJRI_EN, PRAYER_EN, MISC_EN,
              [x.upper() for x in W_EN], [x[:3].upper() for x in W_EN], [x.upper() for x in M_EN], [x[:3].upper() for x in M_EN], [x.upper() for x in HIJRI_EN],
              W_AR, M_AR, HIJRI_AR, PRAYER_AR, MISC_AR)
big_words = words(W_AR, M_AR, HIJRI_AR, PRAYER_AR, OCC_AR)
tracked = [x.upper() for x in W_EN + [w[:3] for w in W_EN] + M_EN]

SMALL_PX, BIG_PX, DIGIT_PX, TRACK_PX, TRACK = 72, 190, 220, 130, 0.28
shutil.rmtree(OUT, ignore_errors=True)
os.makedirs(OUT)
index = {"styles": {}, "cuts": {}}
count = [0]

def is_arabic(t):
    return any("؀" <= c <= "ۿ" for c in t)

def save(mask):
    count[0] += 1
    name = f"{count[0]}.webp"
    img = Image.new("RGBA", mask.size, (255, 255, 255, 0))
    img.putalpha(mask)
    img.save(os.path.join(OUT, name), lossless=True, quality=100, method=6)
    return name

def draw(font, key_text, px):
    text = key_text.replace("ʿ", "'")  # the font has no ayn mark; the widgets' names still look it up by their real spelling
    kw = {"direction": "rtl", "language": "ar"} if is_arabic(text) else {"direction": "ltr", "language": "en"}
    l, t, r, b = font.getbbox(text, anchor="ls", **kw)
    if r <= l or b <= t:
        return None
    pad = 2
    mask = Image.new("L", (r - l + pad * 2, b - t + pad * 2), 0)
    ImageDraw.Draw(mask).text((pad - l, pad - t), text, font=font, fill=255, anchor="ls", **kw)
    return [save(mask), font.getlength(text, **kw) / px, (l - pad) / px, (t - pad) / px, mask.width / px, mask.height / px]

def tracked_entry(font, text, px):
    adv = [font.getlength(c) for c in text]
    width = int(sum(adv) + TRACK * px * (len(text) - 1)) + 8
    asc, desc = font.getmetrics()
    mask = Image.new("L", (width, asc + desc + 8), 0)
    d = ImageDraw.Draw(mask)
    x = 4.0
    for c, a in zip(text, adv):
        d.text((x, 4 + asc), c, font=font, fill=255, anchor="ls")
        x += a + TRACK * px
    return [save(mask), (width - 8) / px, -4 / px, -(4 + asc) / px, mask.width / px, mask.height / px]

def style(key, entries):
    table = {}
    meta = None
    for px, toks in entries:
        font = ImageFont.truetype(FONT[key], px)
        asc, desc = font.getmetrics()
        cap = -font.getbbox("H", anchor="ls")[1] / px
        meta = meta or {"cap": cap, "space": font.getlength(" ") / px}
        for tok in toks:
            if tok in table:
                continue
            e = tracked_entry(font, tok, px) if key == "T" else draw(font, tok, px)
            if e:
                table[tok] = e
    index["styles"][key] = {**meta, "t": table}

style("R", [(DIGIT_PX, DIGITS + [":", "%"]), (SMALL_PX, small + PUNCT)])
style("M", [(DIGIT_PX, DIGITS + [":", "%"]), (SMALL_PX, small + PUNCT)])
style("D", [(DIGIT_PX, DIGITS + AR_DIGITS), (BIG_PX, big_words)])
style("T", [(TRACK_PX, tracked)])

# Month names with the stroke stretched wide enough for a two-digit day to stand on it.
font = ImageFont.truetype(FONT["D"], BIG_PX)
ar = {"direction": "rtl", "language": "ar"}
two = ImageFont.truetype(FONT["D"], int(BIG_PX * 0.8)).getlength("٢٢", **ar)
for prefix, cuts in (("h", HIJRI_CUT), ("g", GREG_CUT)):
    for i, (a, b) in enumerate(cuts):
        base = font.getlength(a + K + b, **ar)
        n = 1
        while font.getlength(a + K * n + b, **ar) - base < two * 1.7 and n < 80:
            n += 1
        word = a + K * n + b
        stroke = font.getlength(word, **ar) - base
        one = font.getlength(K, **ar)
        tail = font.getlength(K + b, **ar) - one
        e = draw(font, word, BIG_PX)
        index["cuts"][f"{prefix}{i + 1}"] = e + [(tail + (stroke + one) / 2) / BIG_PX]

json.dump(index, open(os.path.join(OUT, "index.json"), "w"), ensure_ascii=False, separators=(",", ":"))
size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
print(count[0], "images,", size // 1024, "KB")
