#!/usr/bin/env python3
"""
Convert hadith JSON (AhmedBaset/hadith-json layout) into Emran's compact .hdp packs.

Why a custom container instead of shipping the JSON or a SQLite file:

* JSON must be parsed whole. Bukhari alone is 12 MB of text; parsing it costs
  tens of MB of heap. A pack is read one chapter at a time.
* A SQLite asset has to be copied out of the APK before it can be opened, so the
  same bytes sit on disk twice. A pack is read in place from the APK (or from the
  downloaded file), and every chapter is deflated on its own, so the file is
  already as small as it will get.
* Random access by chapter means opening a chapter inflates ~50-100 KB, not the
  whole book.

Layout (all integers big-endian):

    magic    "EHP1"
    u16      chapter count
    u32      hadith count
    u32      header length (everything before the first blob)
    per chapter, in order:
        u16  chapter id
        u16  hadith count in the chapter
        u32  first hadith number
        u32  last hadith number
        u32  offset of the blob, from the start of the file
        u32  compressed length
        u32  uncompressed length
        str  title (Arabic)      -- u16 length + UTF-8
        str  title (English)
    blobs: raw deflate (no zlib header) of four columns, each holding one entry per
           hadith in chapter order: varint numbers, then vstr Arabic, vstr narrators,
           vstr English (vstr = varint byte length + UTF-8)

Usage:  build_hadith_packs.py <input_dir> <output_dir>
Writes <output_dir>/<id>.hdp and <output_dir>/manifest.json
"""
import hashlib, json, struct, sys, zlib
from pathlib import Path

# id -> (source file, English title, Arabic title, bundled)
BOOKS = {
    "nawawi40":  ("nawawi40.json",          "Forty Hadith of an-Nawawi",   "الأربعون النووية",   True),
    "qudsi40":   ("qudsi40.json",           "Forty Hadith Qudsi",          "الأربعون القدسية",   True),
    "riyad":     ("riyad_assalihin.json",   "Riyad as-Salihin",            "رياض الصالحين",      True),
    "bukhari":   ("bukhari.json",           "Sahih al-Bukhari",            "صحيح البخاري",       False),
    "muslim":    ("muslim.json",            "Sahih Muslim",                "صحيح مسلم",          False),
    "abudawud":  ("abudawud.json",          "Sunan Abi Dawud",             "سنن أبي داود",       False),
    "tirmidhi":  ("tirmidhi.json",          "Jami' at-Tirmidhi",           "جامع الترمذي",       False),
    "nasai":     ("nasai.json",             "Sunan an-Nasa'i",             "سنن النسائي",        False),
    "ibnmajah":  ("ibnmajah.json",          "Sunan Ibn Majah",             "سنن ابن ماجه",       False),
    "malik":     ("malik.json",             "Muwatta Malik",               "موطأ مالك",          False),
    "darimi":    ("darimi.json",            "Sunan ad-Darimi",             "سنن الدارمي",        False),
    "bulugh":    ("bulugh_almaram.json",    "Bulugh al-Maram",             "بلوغ المرام",        False),
}


def varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def vstr(s: str) -> bytes:
    b = s.encode("utf-8")
    return varint(len(b)) + b


def ustr(s: str) -> bytes:
    b = s.encode("utf-8")
    assert len(b) < 65536
    return struct.pack(">H", len(b)) + b


def clean(s: str) -> str:
    # Collapse the runs of spaces the source leaves around isnad commas; it is
    # invisible in Arabic rendering and costs bytes.
    return " ".join(s.replace("‏", "").split()) if s else ""


def build(src: Path, out: Path):
    d = json.loads(src.read_text(encoding="utf-8"))
    chapters = {int(c["id"]): c for c in d["chapters"]}
    by_ch = {}
    for h in d["hadiths"]:
        # Some sources carry chapter ids as floats (1.0); normalise.
        by_ch.setdefault(int(h["chapterId"]), []).append(h)

    blobs, table = [], []
    for cid in sorted(by_ch):
        hs = sorted(by_ch[cid], key=lambda h: h["idInBook"])
        # Column-major inside a chapter: all numbers, then all Arabic, then all
        # narrators, then all English. Deflate only sees 32 KB at a time, so keeping
        # like text together (Arabic next to Arabic) shrinks the blob noticeably
        # compared with interleaving the four fields per hadith.
        nums, ars, nars, txts = bytearray(), bytearray(), bytearray(), bytearray()
        for h in hs:
            en = h.get("english") or {}
            if isinstance(en, str):
                narrator, text = "", en
            else:
                narrator, text = en.get("narrator", ""), en.get("text", "")
            nums += varint(h["idInBook"])
            ars += vstr(clean(h["arabic"]))
            nars += vstr(clean(narrator))
            txts += vstr(clean(text))
        raw = nums + ars + nars + txts
        comp = zlib.compressobj(9, zlib.DEFLATED, -15)
        z = comp.compress(bytes(raw)) + comp.flush()
        ch = chapters.get(cid, {"arabic": "", "english": ""})
        table.append((cid, len(hs), len(z), len(raw), ch.get("arabic", ""), ch.get("english", ""), hs[0]["idInBook"], hs[-1]["idInBook"]))
        blobs.append(z)

    header_len = 4 + 2 + 4 + 4 + sum(2 + 2 + 4 + 4 + 4 + 4 + 4 + 2 + len(t[4].encode()) + 2 + len(t[5].encode()) for t in table)
    body = bytearray(b"EHP1" + struct.pack(">HII", len(table), sum(t[1] for t in table), header_len))
    off = header_len
    for (cid, n, clen, rlen, tar, ten, first, last) in table:
        body += struct.pack(">HHIIIII", cid, n, first, last, off, clen, rlen) + ustr(tar) + ustr(ten)
        off += clen
    assert len(body) == header_len
    for b in blobs:
        body += b
    out.write_bytes(bytes(body))
    return len(table), sum(t[1] for t in table)


def main():
    inp, outp = Path(sys.argv[1]), Path(sys.argv[2])
    outp.mkdir(parents=True, exist_ok=True)
    manifest = []
    for bid, (fname, en, ar, bundled) in BOOKS.items():
        f = inp / fname
        if not f.exists():
            print("skip", bid, "(missing)")
            continue
        dest = outp / f"{bid}.hdp"
        chapters, count = build(f, dest)
        data = dest.read_bytes()
        manifest.append({
            "id": bid, "titleEn": en, "titleAr": ar, "bundled": bundled,
            "chapters": chapters, "hadiths": count, "bytes": len(data),
            "sha256": hashlib.sha256(data).hexdigest(),
        })
        print(f"{bid:9s} {count:5d} hadith {chapters:3d} ch  {len(data)/1024:8.0f} KB")
    (outp / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    print("total", sum(m["bytes"] for m in manifest) // 1024, "KB;",
          "bundled", sum(m["bytes"] for m in manifest if m["bundled"]) // 1024, "KB")


if __name__ == "__main__":
    main()
