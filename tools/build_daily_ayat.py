"""Build Emran's ayah assets.

1. assets/ayah/<surah>.json for all 114 surahs: Al-Mukhtasar fi Tafsir al-Quran al-Karim (Tafsir Center for
   Quranic Studies, Arabic + its official English edition, from QuranEnc.com) and Saheeh International, one
   entry per ayah: {"ar": [...], "en": [...], "tr": [...], "g": {"3": [3, 4], ...}}. "g" marks the few ayat
   Al-Mukhtasar explains together with a neighbour (their text starts "3 - 4 - "); the prefix is stripped and
   the range kept so the screen can say so. Any ayah the user picks is explained from these files.
2. assets/daily_ayat_ar.json: the words of the famous ayat that rotate as the ayah of the day (so Home never
   parses the whole Quran), and the Kotlin list printed to stdout for data/DailyAyat.kt.

Al-Muyassar (used before) explains ayat in groups, which is why 93:5 showed the explanation of 93:1.
Usage: python3 tools/build_daily_ayat.py <cache-dir>   (cache: <lang>-<surah>.json from QuranEnc,
       sahih-<surah>.json from api.alquran.cloud; fetch them with curl -A, QuranEnc refuses Python's agent)
"""
import json, os, re, sys

ASSETS = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets')
CACHE = sys.argv[1]
MAX_WORDS = 30

# Famous ayat that stand on their own. Order does not matter: every phone shuffles them its own way.
FAMOUS = [
    (1, 2), (1, 5), (1, 6), (2, 2), (2, 32), (2, 43), (2, 45), (2, 110), (2, 115), (2, 127), (2, 148), (2, 152), (2, 153), (2, 155), (2, 156), (2, 157), (2, 163), (2, 183), (2, 186), (2, 195), (2, 201), (2, 208), (2, 238), (2, 245), (2, 250), (2, 256), (2, 263), (2, 269), (2, 280),
    (3, 8), (3, 9), (3, 31), (3, 92), (3, 102), (3, 104), (3, 110), (3, 133), (3, 134), (3, 139), (3, 147), (3, 160), (3, 169), (3, 173), (3, 185), (3, 190), (3, 191), (3, 193), (3, 200),
    (4, 28), (4, 86), (4, 110), (4, 147), (5, 8), (5, 35), (6, 17), (6, 103), (6, 160), (6, 162),
    (7, 23), (7, 31), (7, 55), (7, 56), (7, 180), (7, 199), (7, 201), (7, 204), (7, 205),
    (8, 2), (8, 30), (8, 33), (8, 46), (9, 51), (9, 105), (9, 119), (9, 128), (9, 129),
    (10, 57), (10, 58), (10, 62), (11, 6), (11, 114), (11, 115), (12, 18), (12, 53), (12, 86), (12, 87), (12, 92),
    (13, 24), (13, 28), (13, 29), (13, 39), (14, 7), (14, 24), (14, 27), (14, 34), (14, 40), (14, 41),
    (15, 9), (15, 49), (15, 97), (15, 98), (15, 99), (16, 18), (16, 43), (16, 53), (16, 90), (16, 96), (16, 97), (16, 125), (16, 127), (16, 128),
    (17, 9), (17, 23), (17, 24), (17, 32), (17, 36), (17, 37), (17, 44), (17, 70), (17, 79), (17, 80), (17, 81), (17, 82), (17, 85),
    (18, 10), (18, 46), (18, 107), (18, 109), (19, 4), (19, 65), (19, 96), (20, 2), (20, 5), (20, 8), (20, 14), (20, 25), (20, 26), (20, 46), (20, 114), (20, 124), (20, 132),
    (21, 35), (21, 47), (21, 83), (21, 87), (21, 89), (21, 107), (22, 32), (22, 37), (22, 77),
    (23, 1), (23, 2), (23, 3), (23, 97), (23, 98), (23, 115), (23, 118), (25, 58), (25, 63), (25, 70), (25, 74),
    (26, 62), (26, 78), (26, 80), (26, 83), (26, 88), (27, 62), (28, 24), (28, 56), (28, 88),
    (29, 2), (29, 45), (29, 57), (29, 64), (29, 69), (30, 21), (30, 41), (30, 60),
    (31, 13), (31, 14), (31, 17), (31, 18), (31, 19), (32, 16), (32, 17), (33, 3), (33, 21), (33, 41), (33, 42), (33, 56), (33, 70), (33, 71),
    (35, 2), (35, 5), (35, 15), (35, 28), (36, 58), (36, 82), (36, 83), (37, 180), (37, 181), (37, 182),
    (38, 29), (39, 9), (39, 10), (39, 18), (39, 36), (39, 53), (39, 54), (40, 44), (40, 55), (40, 60), (40, 65),
    (41, 30), (41, 34), (41, 46), (42, 11), (42, 19), (42, 25), (42, 30), (42, 36), (42, 40), (42, 43),
    (43, 67), (46, 13), (47, 7), (47, 19), (48, 1), (49, 10), (49, 13), (50, 16), (50, 18),
    (51, 21), (51, 22), (51, 55), (51, 56), (51, 58), (52, 48), (53, 3), (53, 4), (53, 39), (53, 42),
    (54, 17), (54, 49), (55, 13), (55, 26), (55, 27), (55, 60), (56, 79), (57, 3), (57, 16),
    (59, 18), (59, 21), (59, 24), (61, 2), (61, 3), (62, 9), (62, 10), (64, 11), (64, 16),
    (65, 3), (67, 1), (67, 2), (67, 13), (67, 14), (67, 15), (68, 4), (70, 5),
    (71, 10), (73, 8), (73, 9), (74, 7), (75, 22), (76, 9), (82, 6), (84, 6),
    (85, 14), (87, 1), (87, 14), (87, 16), (87, 17), (89, 27), (89, 28), (91, 9), (91, 10),
    (93, 3), (93, 4), (93, 5), (93, 7), (93, 11), (94, 1), (94, 4), (94, 5), (94, 6), (94, 7), (94, 8),
    (95, 4), (96, 1), (96, 5), (96, 19), (97, 1), (97, 3), (97, 5), (99, 7), (99, 8),
    (108, 1), (108, 2), (110, 1), (110, 3), (112, 1), (112, 2), (112, 3), (112, 4),
    (113, 1), (114, 1),
]

RANGE_AR = re.compile(r'^\s*(\d+)\s*-\s*(\d+)\s*-\s*')
RANGE_EN = re.compile(r'^\s*(\d+)\s*-\s*(\d+)\s*\.\s*')
NUM_EN = re.compile(r'^\s*\d+\s*\.\s*')


def load(name):
    return json.load(open(os.path.join(CACHE, name)))


quran = json.load(open(os.path.join(ASSETS, 'quran.json')))
by_id = {s['id']: s for s in quran}
os.makedirs(os.path.join(ASSETS, 'ayah'), exist_ok=True)
grouped = set()
for s in range(1, 115):
    n = by_id[s]['total_verses']
    ar = {int(r['aya']): r['translation'] for r in load(f'arabic-{s}.json')['result']}
    en = {int(r['aya']): r['translation'] for r in load(f'english-{s}.json')['result']}
    tr = {a['numberInSurah']: a['text'] for a in load(f'sahih-{s}.json')['data']['ayahs']}
    out = {'ar': [], 'en': [], 'tr': [], 'g': {}}
    for a in range(1, n + 1):
        ta, te = ar.get(a, ''), en.get(a, '')
        m = RANGE_AR.match(ta) or RANGE_EN.match(te)
        if m:
            out['g'][str(a)] = [int(m.group(1)), int(m.group(2))]
            grouped.add((s, a))
        ta = RANGE_AR.sub('', ta)
        te = NUM_EN.sub('', RANGE_EN.sub('', te))
        out['ar'].append(re.sub(r'\s+', ' ', ta).strip())
        out['en'].append(re.sub(r'\s+', ' ', te).strip())
        out['tr'].append(tr.get(a, ''))
    if not out['g']:
        del out['g']
    json.dump(out, open(os.path.join(ASSETS, 'ayah', f'{s}.json'), 'w'), ensure_ascii=False, separators=(',', ':'))

picked, words_out, seen = [], {}, set()
for s, a in FAMOUS:
    if (s, a) in seen:
        continue
    seen.add((s, a))
    text = by_id[s]['verses'][a - 1]['text']
    if len(text.split()) > MAX_WORDS:
        print(f'skip {s}:{a} ({len(text.split())} words)', file=sys.stderr); continue
    if (s, a) in grouped:
        print(f'skip {s}:{a} (explained with its neighbour)', file=sys.stderr); continue
    picked.append((s, a))
    words_out[f'{s}:{a}'] = {'text': text, 'surahAr': by_id[s]['name'], 'surahEn': by_id[s]['transliteration']}

json.dump(words_out, open(os.path.join(ASSETS, 'daily_ayat_ar.json'), 'w'), ensure_ascii=False, separators=(',', ':'))
for stale in ('daily_ayat_en.json', 'daily_ayat_tafsir.json'):
    p = os.path.join(ASSETS, stale)
    if os.path.exists(p):
        os.remove(p)
print(len(picked), 'famous ayat;', len(grouped), 'ayat explained in pairs', file=sys.stderr)
print(',\n'.join(', '.join(f'{s} to {a}' for s, a in picked[i:i + 10]) for i in range(0, len(picked), 10)))
