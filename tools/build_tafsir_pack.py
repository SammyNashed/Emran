#!/usr/bin/env python3
"""Builds one Emran tafsir pack from a tafsir_api edition (spa5k/tafsir_api, via jsDelivr).

usage: build_tafsir_pack.py <edition-slug> <id> <lang ar|en> "<Arabic name>" "<English name>" <out-dir> [--zip out.emtafsir]

Pack layout (a folder, or the same files zipped as .emtafsir):
  meta.json        {"id","lang","nameAr","nameEn","source"}
  <surah>.json     {"t":["ayah 1 text", ...], "g":{"ayah":[first,last]}}   (t has one string per ayah;
                   an ayah explained together with a neighbour has "" in t and a "g" range whose first
                   ayah holds the text)
"""
import json, sys, os, urllib.request, zipfile, time

AYAT = [7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6]
BASES = ["https://cdn.jsdelivr.net/gh/spa5k/tafsir_api@main/tafsir/%s/%d.json",
         "https://raw.githubusercontent.com/spa5k/tafsir_api/main/tafsir/%s/%d.json"]

def fetch(slug, s):
    # jsDelivr answers 403 now and then when asked quickly: go slowly and back off.
    time.sleep(0.25)
    for attempt in range(8):
        try:
            with urllib.request.urlopen(BASES[attempt % 2 if attempt > 1 else 0] % (slug, s), timeout=40) as r:
                d = json.loads(r.read().decode("utf-8"))
            return d["ayahs"] if isinstance(d, dict) else d
        except Exception:
            time.sleep(2 * (attempt + 1))
    raise SystemExit(f"failed {slug} {s}")

def main():
    slug, pid, lang, name_ar, name_en, out = sys.argv[1:7]
    zip_out = sys.argv[sys.argv.index("--zip") + 1] if "--zip" in sys.argv else None
    os.makedirs(out, exist_ok=True)
    total = 0
    for s in range(1, 115):
        path = os.path.join(out, f"{s}.json")
        if os.path.exists(path):  # resume
            total += os.path.getsize(path)
            continue
        items = fetch(slug, s)
        n = AYAT[s - 1]
        t = [""] * n
        have = []
        for it in items:
            a = int(it["ayah"])
            if 1 <= a <= n and it["text"].strip():
                t[a - 1] = it["text"].strip()
                have.append(a)
        g = {}
        have.sort()
        for k, a in enumerate(have):
            nxt = have[k + 1] if k + 1 < len(have) else n + 1
            if nxt - a > 1:
                for m in range(a, nxt):
                    g[str(m)] = [a, nxt - 1]
        # ayat before the first entry (rare) take the first entry's text
        if have and have[0] > 1:
            for m in range(1, have[0]):
                g[str(m)] = [1, have[0] - 1] if False else [have[0], have[0]]
        body = json.dumps({"t": t, "g": g}, ensure_ascii=False, separators=(",", ":"))
        with open(os.path.join(out, f"{s}.json"), "w", encoding="utf-8") as f:
            f.write(body)
        total += len(body.encode())
    meta = {"id": pid, "lang": lang, "nameAr": name_ar, "nameEn": name_en, "source": "spa5k/tafsir_api (" + slug + ")"}
    with open(os.path.join(out, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False)
    if zip_out:
        with zipfile.ZipFile(zip_out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for fn in os.listdir(out):
                z.write(os.path.join(out, fn), fn)
    print(f"{pid}: {total/1e6:.2f} MB raw" + (f", zip {os.path.getsize(zip_out)/1e6:.2f} MB" if zip_out else ""))

main()
