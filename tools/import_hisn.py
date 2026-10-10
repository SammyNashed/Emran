#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Add the chapters of Hisn al-Muslim to app/src/main/assets/adhkar.json.

The five hand-curated folders (morning, evening, after prayer, before sleep,
upon waking) carry a primary hadith reference on every item. Hisn al-Muslim's
public dataset does not: it gives the book's own chapter, not the hadith
behind each du'a. So these items are attributed to what they really come from --
"Hisn al-Muslim, ch. N" -- and are put in their own `group: "hisn"` so the app can
show them apart from the verified core rather than pass them off as the same
standard. Nothing here invents a hadith number.

Chapters 27 (morning/evening), 28 (before sleep) and 1 (waking) are skipped: the
curated folders already cover them with better attribution.

Usage:
    python tools/import_hisn.py [--cache DIR]      # fetch (curl) + merge in place
"""
import argparse, io, json, os, re, subprocess, sys, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET = os.path.join(ROOT, "app", "src", "main", "assets", "adhkar.json")
API = "https://www.hisnmuslim.com/api"
SKIP_CHAPTERS = {27, 28, 1}
PALETTE = ["6366F1", "F97316", "14B8A6", "A855F7", "EAB308", "EC4899"]
# Hisn al-Muslim's chapters, grouped by theme so the app can show ten cards instead of
# 128 rows. (id, English, Arabic, icon, colour, chapter ids)
CATEGORIES = [
    ("home",     "Dress, Wudu & Home",       "اللباس والوضوء والبيت",    "Home",         "14B8A6", list(range(2, 12))),
    ("mosque",   "Mosque & Adhan",           "المسجد والأذان",            "Mosque",       "6366F1", list(range(12, 16))),
    ("prayer",   "Inside the Prayer",        "أذكار الصلاة",              "Praying",      "A855F7", list(range(16, 27))),
    ("night",    "Night & Sleep",            "الليل والنوم",              "Bedtime",      "3B82F6", list(range(29, 34))),
    ("hardship", "Hardship & Protection",    "الكرب والحماية",            "Shield",       "F97316", list(range(34, 47))),
    ("family",   "Family, Sickness & Death", "الأسرة والمرض والموت",      "Favorite",     "EC4899", list(range(47, 61))),
    ("weather",  "Weather & Moon",           "المطر والريح والهلال",      "Cloud",        "0EA5E9", list(range(61, 68))),
    ("food",     "Food & Fasting",           "الطعام والصيام",            "Restaurant",   "EAB308", list(range(68, 77))),
    ("manners",  "Manners & Marriage",       "الآداب والزواج",            "Groups",       "22C55E", list(range(77, 95)) + list(range(106, 115))),
    ("travel",   "Travel & Hajj",            "السفر والحج",               "Flight",       "F43F5E", list(range(95, 106)) + list(range(115, 122))),
    ("heart",    "Repentance & Remembrance", "التوبة والذكر",             "Brightness5",  "FDB813", list(range(122, 132))),
]
CATEGORY_OF = {cid: c[0] for c in CATEGORIES for cid in c[5]}
ARABIC_DIGITS = str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩")
KEYS = r"ARABIC_TEXT|LANGUAGE_ARABIC_TRANSLATED_TEXT|TRANSLATED_TEXT|REPEAT|AUDIO|ID"
LATIN = re.compile(r"[A-Za-z]{3}")
HARAKAT = re.compile(r"[\u064B-\u0652\u0670]")


def curl(url, dest):
    # curl rather than urllib: the host answers 403 to Python's default agent.
    subprocess.run(["curl", "-sSf", "-o", dest, url], check=True)


def fetch(cache):
    os.makedirs(cache, exist_ok=True)
    idx = {}
    for lang in ("en", "ar"):
        p = os.path.join(cache, "index_%s.json" % lang)
        if not os.path.exists(p):
            curl("%s/%s/husn_%s.json" % (API, lang, lang), p)
        raw = json.loads(io.open(p, encoding="utf-8-sig").read())
        rows = raw["English"] if lang == "en" else list(raw.values())[0]
        idx[lang] = {r["ID"]: r["TITLE"] for r in rows}
    for cid in idx["en"]:
        p = os.path.join(cache, "%d.json" % cid)
        if not os.path.exists(p):
            curl("%s/en/%d.json" % (API, cid), p)
            time.sleep(0.15)
    return idx


def parse_chapter(text):
    """The source is malformed JSON in places (renamed and missing keys), so read it leniently."""
    out = []
    for seg in re.split(r'(?=\{\s*"ID"\s*:)', text)[1:]:
        f = {}
        for k in ("ID", "ARABIC_TEXT", "LANGUAGE_ARABIC_TRANSLATED_TEXT", "TRANSLATED_TEXT", "REPEAT"):
            m = re.search(r'"%s"\s*:\s*(?:"(.*?)"|(\d+))\s*,?\s*(?="(?:%s)"|\}|\s*$)' % (k, KEYS), seg, re.S)
            if m:
                f[k] = m.group(1) if m.group(1) is not None else m.group(2)
        ar = f.get("ARABIC_TEXT", "").strip()
        a = f.get("TRANSLATED_TEXT", "").strip()
        b = f.get("LANGUAGE_ARABIC_TRANSLATED_TEXT", "").strip()
        en = a if LATIN.search(a) else (b if LATIN.search(b) else "")
        note = b if (LATIN.search(b) and en != b) else ""
        # Same bar as tools/import_adhkar.py: unvocalised Arabic renders badly beside the rest.
        if not ar or not en or not HARAKAT.search(ar):
            continue
        out.append({
            "id": int(f["ID"]),
            "ar": ar.replace('\\"', '"').replace("\\n", "\n"),
            "en": en.replace('\\"', '"').replace("\\n", "\n"),
            "note": note.replace('\\"', '"'),
            "reps": int(f.get("REPEAT") or 1),
        })
    return out


def unwrap(s):
    """Drop a pair of parentheses that wraps the whole text (the source wraps nearly every item)."""
    s = re.sub(r"\s+", " ", s).strip()
    while True:
        t = s[:-1] if s.endswith(").") else s
        if s.startswith("(") and t.endswith(")"):
            depth = 0
            for i, ch in enumerate(t):
                depth += (ch == "(") - (ch == ")")
                if depth == 0 and i < len(t) - 1:
                    break
            else:
                s = t[1:-1].strip()
                continue
        return s


def transliteration_of(note, english):
    """
    The source's second English field is usually the Latin transliteration of the du'a,
    but for narrated passages it just repeats the translation. Keep it only when it
    does not overlap the English much.
    """
    if not note:
        return None
    words = lambda t: set(re.findall(r"[a-z]{3,}", t.lower()))
    a, b = words(note), words(english)
    if a and len(a & b) / len(a | b) > 0.35:
        return None
    return unwrap(note)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=os.path.expanduser("~/.cache/emran-hisn"))
    args = ap.parse_args()

    idx = fetch(args.cache)
    doc = json.load(io.open(ASSET, encoding="utf-8"))
    doc["folders"] = [f for f in doc["folders"] if f.get("group") != "hisn"]  # idempotent

    added = 0
    for n, cid in enumerate(sorted(idx["en"])):
        if cid in SKIP_CHAPTERS:
            continue
        items = parse_chapter(io.open(os.path.join(args.cache, "%d.json" % cid), encoding="utf-8-sig").read())
        if not items:
            continue
        doc["folders"].append({
            "id": "hisn-%d" % cid,
            "group": "hisn",
            "category": CATEGORY_OF.get(cid, "heart"),
            "titleEn": idx["en"][cid].strip()[:1].upper() + idx["en"][cid].strip()[1:],
            "titleAr": idx["ar"][cid].strip(),
            "icon": "Book",
            "color": PALETTE[cid % len(PALETTE)],
            "items": [{
                "id": "hisn-%d-%d" % (cid, it["id"]),
                "textAr": unwrap(it["ar"]),
                "textEn": unwrap(it["en"]),
                "reps": it["reps"],
                "transliteration": transliteration_of(it["note"], it["en"]),
                "source": "Hisn al-Muslim, ch. %d" % cid,
                "sourceAr": "حصن المسلم، الباب %s" % str(cid).translate(ARABIC_DIGITS),
            } for it in items],
        })
        added += len(items)
    for f in doc["folders"]:
        for it in f["items"]:
            for k in [k for k, v in it.items() if v is None]:
                del it[k]
    doc["schemaVersion"] = 2
    doc["categories"] = [
        {"id": c[0], "titleEn": c[1], "titleAr": c[2], "icon": c[3], "color": c[4]} for c in CATEGORIES
    ]
    with io.open(ASSET, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
    print("added %d items in %d chapters" % (added, sum(1 for f in doc["folders"] if f.get("group") == "hisn")))


if __name__ == "__main__":
    main()
