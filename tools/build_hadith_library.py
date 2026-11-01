#!/usr/bin/env python3
"""
Build Emran's hadith packs (.hdp, format EHP2) from openly licensed sources.

Sources, each in its own folder or file:
  --api      fawazahmed0/hadith-api (Unlicense): ara-<book>.min.json as f_<book>.json,
             eng-<book>.min.json as e_<book>.json
  --muslim-extra  mhashim6/Open-Hadith-Data (ODbL + DbCL):
             Sahih_Muslim/sahih_muslim_ahadith_mushakkala_mufassala.utf8.csv
  --numbers  Jaguar16/open-hadith-data collections-json muslim.json, used ONLY to confirm
             Sahih Muslim reference numbers, never for text
  --second-grades  the same project's collections-json.zip: its gradings are a second, independent
             opinion. A Sunan hadith it grades weak is removed, whatever hadith-api says.
  --old      the previous assets/hadith folder (EHP1 packs): tells where each old hadith
             lives now, so bookmarks can move, and supplies Arabic chapter titles

Rules, decided by the app's owner on 2026-11-01:
  * Bukhari and Muslim are kept whole, without Muslim's Introduction.
  * Abu Dawud, Tirmidhi, Nasa'i and Ibn Majah keep a hadith only when Al-Albani grades it
    exactly Sahih and no listed scholar grades it weak, in either dataset. A label this script
    cannot place as positive counts as doubtful and removes the hadith.
  * A reference number is shown only when it is known. Unsure means no number, never a guess.

Pack layout, integers big-endian:
    magic "EHP2", u16 chapters, u32 hadith, u32 header length, then per chapter
    u16 id, u16 count, u32 first, u32 last, u32 offset, u32 compressed, u32 raw,
    str Arabic title, str English title (str = u16 length + UTF-8).
    Blobs: raw deflate of columns, one entry per hadith in chapter order: varint internal
    number, vstr Arabic, vstr narrators, vstr English, vstr reference, vstr grade.

The internal number is hadith-api's hadithnumber x 100, plus 1.. for hadith inserted after it.
It is a stable key for bookmarks and is never shown. The reference is what people see.

Usage: build_hadith_library.py --api D --muslim-extra F --numbers F --old D --out D --report F
"""
import argparse, collections, csv, difflib, hashlib, json, re, struct, sys, unicodedata, zipfile, zlib
from pathlib import Path

csv.field_size_limit(10**8)

BOOKS = [
    # id, English title, Arabic title, visible, trimmed by the sahih rule
    ("bukhari", "Sahih al-Bukhari", "صحيح البخاري", True, False),
    ("muslim", "Sahih Muslim", "صحيح مسلم", True, False),
    ("abudawud", "Sahih selection from Sunan Abi Dawud", "مختارات صحيحة من سنن أبي داود", True, True),
    ("tirmidhi", "Sahih selection from Jami' at-Tirmidhi", "مختارات صحيحة من جامع الترمذي", True, True),
    ("nasai", "Sahih selection from Sunan an-Nasa'i", "مختارات صحيحة من سنن النسائي", True, True),
    ("ibnmajah", "Sahih selection from Sunan Ibn Majah", "مختارات صحيحة من سنن ابن ماجه", True, True),
    # Hidden until a scholar reviews them: no gradings in any dataset checked. Flip to True to rebuild.
    ("nawawi40", "Forty Hadith of an-Nawawi", "الأربعون النووية", False, False),
    ("qudsi40", "Forty Hadith Qudsi", "الأربعون القدسية", False, False),
]
# Riyad as-Salihin, Bulugh al-Maram, Sunan ad-Darimi and Muwatta Malik are also hidden. They have no
# usable gradings; their last packs are in git history (assets/hadith, commit aac9ee4).

DIA = re.compile("[ؐ-ًؚ-ٰٟۖ-ۭ࣓-ࣿـ]")
BLOCK = re.compile(
    r"da.?[iey]+f|dha.?[iey]+f|d[aā].?[iī]f|za.?[iey]+f|weak|munkar|shadh|shaz|mawd|maud|ba[tṭ]il|matruk|mursal|"
    r"munqat|mu.?allal|mudtarib|majhul|no basis|la asl|fabricat|unknown|maqtu|mauquf|mawquf|muquf",
    re.I,
)
POSITIVE = re.compile(r"sa[hḥ][iī][hḥ]|[hḥ]asan|agreed upon|muttafaq|mutawatir", re.I)


def words(t):
    t = DIA.sub("", unicodedata.normalize("NFC", t or ""))
    for a, b in [("أ", "ا"), ("إ", "ا"), ("آ", "ا"), ("ٱ", "ا"), ("ى", "ي"), ("ئ", "ي"), ("ؤ", "و"), ("ء", ""), ("ة", "ه")]:
        t = t.replace(a, b)
    return re.sub(r"[^ء-ي ]", " ", t).split()


def shingles(w):
    return [" ".join(w[i:i + 4]) for i in range(len(w) - 3)]


def clean(s):
    return " ".join((s or "").replace("‏", "").replace("‎", "").split())


class Index:
    def __init__(self):
        self.ix = collections.defaultdict(set)

    def add(self, key, text):
        for s in shingles(words(text)):
            self.ix[s].add(key)

    def best(self, text):
        sh = shingles(words(text))
        c = collections.Counter()
        for s in sh:
            for k in self.ix.get(s, ()):
                c[k] += 1
        if not c or not sh:
            return None, 0.0
        k, n = max(c.items(), key=lambda kv: (kv[1], str(kv[0])))
        return k, n / len(sh)


SECOND_WEAK = re.compile(r"da.?[iīy]+f|d[aā].?[iī]f|weak|munkar|shadh|mawd|maud|isn[aā]d da", re.I)


def second_opinion(zip_path, book):
    """Returns a function: does the second dataset grade this text weak?"""
    d = json.loads(zipfile.ZipFile(zip_path).read(f"{book}.json"))
    hs = [h for b in d.get("books") or [] for h in b.get("hadiths") or []]
    ix = Index()
    for i, h in enumerate(hs):
        ix.add(i, h.get("text_ar") or "")

    def is_weak(text):
        i, s = ix.best(text)
        if i is None or s < 0.7:
            return False
        h = hs[i]
        return bool(SECOND_WEAK.search(f'{h.get("grade_en") or ""} {h.get("source_grade") or ""}'))

    return is_weak


def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def vstr(s):
    b = s.encode("utf-8")
    return varint(len(b)) + b


def ustr(s):
    b = s.encode("utf-8")
    assert len(b) < 65536
    return struct.pack(">H", len(b)) + b


def read_old(path):
    """Old EHP1 pack as a list of (chapter id, number, Arabic), plus chapter titles."""
    f = Path(path).read_bytes()
    assert f[:4] == b"EHP1"
    nch, _, _ = struct.unpack(">HII", f[4:14])
    i, rows, titles = 14, [], {}
    for _ in range(nch):
        cid, cnt, _a, _b, off, cl, ul = struct.unpack(">HHIIIII", f[i:i + 24])
        i += 24
        t = []
        for _x in range(2):
            n = struct.unpack(">H", f[i:i + 2])[0]
            i += 2
            t.append(f[i:i + n].decode())
            i += n
        titles[cid] = t
        raw = zlib.decompress(f[off:off + cl], -15)
        j, nums = 0, []

        def vi():
            nonlocal j
            r = s = 0
            while True:
                c = raw[j]
                j += 1
                r |= (c & 127) << s
                s += 7
                if c < 128:
                    return r

        for _h in range(cnt):
            nums.append(vi())
        for k in range(cnt):
            n = vi()
            rows.append((cid, nums[k], raw[j:j + n].decode()))
            j += n
    return rows, titles


def muslim_label(arabicnumber):
    """1907.01 -> 1907a, 101 -> 101."""
    s = str(arabicnumber)
    if "." not in s:
        return s
    whole, frac = s.split(".")
    v = int(frac.ljust(2, "0")[:2])
    return whole if v == 0 else whole + chr(ord("a") + v - 1)


def section_of(meta):
    """
    The chapter of a hadith, from its own reference. hadith-api's section ranges can't be trusted (Muslim's
    chapter 16 claims to start at hadith 388), and many rows carry book 0 by mistake, so book 0 counts only
    inside section 0's own range, and otherwise the hadith stays in the chapter before it.
    """
    zero = meta["section_details"].get("0") or {}
    first, last = zero.get("hadithnumber_first"), zero.get("hadithnumber_last")
    has_zero = bool(first) and bool((meta["sections"].get("0") or "").strip())

    def find(h, previous):
        book = (h.get("reference") or {}).get("book") or 0
        if book > 0:
            return int(book)
        if has_zero and float(first) <= float(h["hadithnumber"]) <= float(last):
            return 0
        return previous

    return find


def grade_rule(h, audit):
    alb, blocked = False, False
    for g in h.get("grades") or []:
        label = clean(g.get("grade", ""))
        low = label.lower()
        if low in ("", "-"):
            audit["ignored"][label] += 1
            continue
        if BLOCK.search(label):
            audit["blocked"][label] += 1
            blocked = True
        elif POSITIVE.search(label):
            audit["positive"][label] += 1
        else:
            audit["doubtful"][label] += 1
            blocked = True
        if g.get("name") == "Al-Albani" and low == "sahih":
            alb = True
    return alb, blocked


def fill_muslim(old_rows, api, extra_csv, numbers_file, report):
    """Rows the old pack has and hadith-api lacks: text from Open-Hadith-Data, numbers only when certain."""
    index = Index()
    for h in api:
        index.add(h["hadithnumber"], h["text"])
    extra = []
    ei = Index()
    for row in csv.reader(open(extra_csv, encoding="utf-8-sig", newline="")):
        if len(row) < 2:
            continue
        w = clean(row[1]).split()
        extra.append(w)
        ei.add(len(extra) - 1, " ".join(w))
    jag = json.load(open(numbers_file, encoding="utf-8"))
    ji = Index()
    labels = {}
    for b in jag["books"]:
        for h in b["hadiths"]:
            labels[h["hadith_number"]] = h
            ji.add(h["hadith_number"], h.get("text_ar") or "")

    placed, skipped = [], []
    seq = []  # (old number, chapter, api number or None)
    for cid, num, text in sorted(old_rows, key=lambda r: r[1]):
        if cid == 0 or not text.strip():
            continue  # the Introduction stays out
        k, s = index.best(text)
        seq.append((num, cid, k if s >= 0.7 else None, text))

    prev = 0
    per_slot = collections.Counter()
    for num, cid, k, text in seq:
        if k is not None:
            prev = max(prev, k)
            continue
        i, s = ei.best(text)
        if i is None or s < 0.7:
            skipped.append({"old": num, "why": "no match in Open-Hadith-Data", "score": round(s, 2)})
            continue
        target = words(text)
        src = extra[i]
        sk = [(words(w) or [""])[0] for w in src]
        sm = difflib.SequenceMatcher(None, sk, target, autojunk=False)
        blocks = [b for b in sm.get_matching_blocks() if b.size >= 3]
        if not blocks:
            skipped.append({"old": num, "why": "could not cut out", "score": round(s, 2)})
            continue
        a0, a1 = blocks[0].a, blocks[-1].a + blocks[-1].size
        got = sum(b.size for b in blocks)
        ratio = got / max(len(target), a1 - a0)
        if ratio < 0.9:
            skipped.append({"old": num, "why": "cut-out differs from old text", "score": round(ratio, 2)})
            continue
        per_slot[prev] += 1
        placed.append({"old": num, "chapter_old": cid, "after": prev, "slot": per_slot[prev],
                       "arabic": " ".join(src[a0:a1]), "match": round(ratio, 3), "jag": ji.best(text)})
    report["muslim_filled"] = len(placed)
    report["muslim_skipped"] = skipped
    return placed, labels


def build_pack(rows_by_ch, titles, dest):
    blobs, table = [], []
    for cid in sorted(rows_by_ch):
        hs = sorted(rows_by_ch[cid], key=lambda r: r["n"])
        parts = [bytearray() for _ in range(6)]
        for h in hs:
            parts[0] += varint(h["n"])
            parts[1] += vstr(h["ar"])
            parts[2] += vstr("")
            parts[3] += vstr(h["en"])
            parts[4] += vstr(h["ref"])
            parts[5] += vstr(h["grade"])
        raw = b"".join(bytes(p) for p in parts)
        c = zlib.compressobj(9, zlib.DEFLATED, -15)
        z = c.compress(raw) + c.flush()
        ta, te = titles.get(cid, ("", ""))
        table.append((cid, len(hs), hs[0]["n"], hs[-1]["n"], len(z), len(raw), ta, te))
        blobs.append(z)
    head = 14 + sum(24 + 2 + len(t[6].encode()) + 2 + len(t[7].encode()) for t in table)
    out = bytearray(b"EHP2" + struct.pack(">HII", len(table), sum(t[1] for t in table), head))
    off = head
    for cid, n, first, last, cl, rl, ta, te in table:
        out += struct.pack(">HHIIIII", cid, n, first, last, off, cl, rl) + ustr(ta) + ustr(te)
        off += cl
    assert len(out) == head
    for b in blobs:
        out += b
    dest.write_bytes(bytes(out))
    return len(table), sum(t[1] for t in table)


def main():
    p = argparse.ArgumentParser()
    for a in ("--api", "--muslim-extra", "--numbers", "--second-grades", "--old", "--out", "--report"):
        p.add_argument(a, required=True)
    o = p.parse_args()
    api, old, out = Path(o.api), Path(o.old), Path(o.out)
    out.mkdir(parents=True, exist_ok=True)
    report = {"books": {}}
    audit = {k: collections.Counter() for k in ("positive", "blocked", "doubtful", "ignored")}
    manifest, migration = [], []

    for bid, en_t, ar_t, visible, trimmed in BOOKS:
        if not visible:
            continue
        name = bid.removesuffix("40")  # hadith-api calls them nawawi and qudsi
        a = json.load(open(api / f"f_{name}.json", encoding="utf-8"))
        e = {h["hadithnumber"]: h for h in json.load(open(api / f"e_{name}.json", encoding="utf-8"))["hadiths"]}
        hadiths = a["hadiths"]
        sec = section_of(a["metadata"])
        old_rows, old_titles = read_old(old / f"{bid}.hdp")
        placed, labels = ([], {})
        if bid == "muslim":
            placed, labels = fill_muslim(old_rows, hadiths, o.muslim_extra, o.numbers, report)

        rows, kept, dropped_weak, dropped_albani, last_sec = {}, 0, 0, 0, None
        second_weak = second_opinion(o.second_grades, bid) if trimmed else None
        stats = collections.Counter()
        by_num = sorted(hadiths, key=lambda h: float(h["hadithnumber"]))
        for pos, h in enumerate(by_num):
            num = float(h["hadithnumber"])
            key = round(num * 100)
            whole_num = int(num)
            s = sec(h, last_sec)
            if last_sec is not None and s is not None and s < last_sec:
                stats["chapter_out_of_order"] += 1
            last_sec = s
            text = clean(h["text"])
            if len(text) < 20:
                stats["empty"] += 1
                continue
            if bid == "muslim" and s == 0:
                stats["introduction"] += 1
                continue
            if trimmed:
                alb, blocked = grade_rule(h, audit)
                if not alb:
                    dropped_albani += 1
                    continue
                if blocked:
                    dropped_weak += 1
                    continue
                if second_weak(text):
                    stats["dropped_weak_second_dataset"] += 1
                    continue
            if bid == "muslim":
                ref = muslim_label(h["arabicnumber"]) if h.get("arabicnumber") else ""
            elif bid == "bukhari":
                # Rows with no text are numbers that share the text before them (5709 covers 5710-5712).
                nxt = whole_num
                q = pos + 1
                while q < len(by_num) and len(clean(by_num[q]["text"])) < 20:
                    nxt = int(float(by_num[q]["hadithnumber"]))
                    q += 1
                ref = str(whole_num) if nxt == whole_num else f"{whole_num}-{nxt}"
            else:
                ref = str(whole_num)
            rows[key] = {"n": key, "ch": s, "ar": text, "en": clean(e.get(h["hadithnumber"], {}).get("text", "")),
                              "ref": ref, "grade": "albani" if trimmed else "", "api": num}
            kept += 1

        if bid == "muslim":
            ordered = sorted(rows)
            for it in placed:
                base = round(it["after"] * 100)
                ch = rows[base]["ch"] if base in rows else rows[max(k for k in rows if k < base)]["ch"]
                rows[base + it["slot"]] = {"n": base + it["slot"], "ch": ch, "ar": it["arabic"], "en": "",
                                           "ref": "", "grade": "", "api": None, "jag": it["jag"]}
            # Numbers for rows that have none: confirmed by text in the numbers file, or by both
            # neighbours sharing one number. Otherwise the row stays without a number.
            seq = sorted(rows)
            whole = lambda r: int(re.match(r"\d+", r).group()) if r and re.match(r"\d+", r) else None
            res = collections.Counter()
            unresolved = []
            ji = None
            for idx, n in enumerate(seq):
                r = rows[n]
                if r["ref"]:
                    continue
                if "jag" not in r:
                    if ji is None:
                        ji = Index()
                        for key, v in labels.items():
                            ji.add(key, v.get("text_ar") or "")
                    r["jag"] = ji.best(r["ar"])
                pv = next((whole(rows[seq[j]]["ref"]) for j in range(idx - 1, -1, -1) if rows[seq[j]]["ref"]), None)
                nx = next((whole(rows[seq[j]]["ref"]) for j in range(idx + 1, len(seq)) if rows[seq[j]]["ref"]), None)
                key, score = r["jag"]
                w = whole(str(key)) if key else None
                if key and score >= 0.7 and w is not None and pv is not None and nx is not None and pv <= w <= nx:
                    r["ref"] = str(key) if score >= 0.98 else str(w)
                    res["confirmed by text"] += 1
                elif pv is not None and pv == nx:
                    r["ref"] = str(pv)
                    res["same number on both sides"] += 1
                    r["check"] = True
                else:
                    res["no number"] += 1
                    unresolved.append({"internal": n, "between": [pv, nx], "arabic": r["ar"][:80]})
                r.pop("jag", None)
            report["muslim_numbers"] = dict(res)
            report["muslim_no_number"] = unresolved

        # Bookmarks: where each old hadith lives now.
        ix = Index()
        for n, r in rows.items():
            ix.add(n, r["ar"])
        moved = 0
        for cid, num, text in old_rows:
            if not text.strip():
                continue
            n, s = ix.best(text)
            if n is not None and s >= 0.7:
                migration.append((bid, num, rows[n]["ch"], n))
                moved += 1

        # Arabic chapter titles: the old chapter most of this section's hadith came from.
        votes = collections.defaultdict(collections.Counter)
        for cid, num, text in old_rows:
            if not text.strip():
                continue
            n, s = ix.best(text)
            if n is not None and s >= 0.7:
                votes[rows[n]["ch"]][cid] += 1
        titles = {}
        for k, name in a["metadata"]["sections"].items():
            k = int(k)
            if votes[k]:
                oc = votes[k].most_common(1)[0][0]
                titles[k] = (old_titles[oc][0], name or old_titles[oc][1])
            else:
                titles[k] = ("", name)

        by_ch = collections.defaultdict(list)
        for r in rows.values():
            by_ch[r["ch"]].append(r)
        dest = out / f"{bid}.hdp"
        chapters, count = build_pack(by_ch, titles, dest)
        data = dest.read_bytes()
        manifest.append({"id": bid, "titleEn": en_t, "titleAr": ar_t, "bundled": True, "chapters": chapters,
                         "hadiths": count, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
        report["books"][bid] = {"hadith": count, "chapters": chapters, "kb": len(data) // 1024,
                                "dropped_not_albani_sahih": dropped_albani, "dropped_weak_elsewhere": dropped_weak,
                                "old_rows": len(old_rows), "old_rows_moved": moved,
                                "without_english": sum(1 for r in rows.values() if not r["en"]),
                                "without_number": sum(1 for r in rows.values() if not r["ref"]),
                                "untitled_chapters": [k for k in by_ch if not titles.get(k, ("", ""))[0]], **stats}
        print(bid, report["books"][bid], flush=True)

    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    with open(out / "migration_v2.tsv", "w", encoding="utf-8") as f:
        for r in migration:
            f.write("\t".join(map(str, r)) + "\n")
    report["labels"] = {k: {"distinct": len(v), "total": sum(v.values()), "top": v.most_common(40)} for k, v in audit.items()}
    report["labels"]["doubtful"]["all"] = list(audit["doubtful"].items())
    json.dump(report, open(o.report, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("total", sum(m["hadiths"] for m in manifest), "hadith,", sum(m["bytes"] for m in manifest) // 1024, "KB")


if __name__ == "__main__":
    main()
