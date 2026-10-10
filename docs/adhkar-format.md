# Adhkar data format

`app/src/main/assets/adhkar.json` holds every supplication Emran ships. It is a
plain JSON asset with no network dependency, parsed by
`data/assets/AdhkarRepository.kt`.

## Why the format looks like this

Two fields were added in schema version 2 that a naive du'a list would omit:

- **`source`** — a du'a with no attribution is one the user cannot verify. For
  religious content that is not a nice-to-have. Every item Emran ships names a
  surah and ayah, or a collection and hadith number.
- **`transliteration`** — a large share of Muslims do not read Arabic script but
  do want to *recite* rather than read a translation. A translation alone cannot
  be said aloud in prayer.

## Schema

```jsonc
{
  "schemaVersion": 2,
  "folders": [
    {
      "id": "morning",              // stable key, used for navigation
      "titleEn": "Morning Adhkar",
      "titleAr": "أذكار الصباح",
      "subtitleEn": "Recited after Fajr until sunrise",   // optional
      "subtitleAr": "تُقال بعد الفجر إلى طلوع الشمس",      // optional
      "group": "hisn",              // optional. "hisn" = imported Hisn al-Muslim chapter,
                                    // shown in its own section, attributed to the book's
                                    // chapter (tools/import_hisn.py). Omit for the core.
      "icon": "WbSunny",            // see the icon table below
      "color": "FDB813",            // RRGGBB, no leading '#'
      "items": [
        {
          "id": "ayat-al-kursi",     // optional but recommended: stable key
          "textAr": "...",           // required. Fully vocalised Arabic
          "textEn": "...",           // required. Translation
          "reps": 1,                 // required. 1 when not repeated
          "transliteration": "...",  // optional but strongly encouraged
          "source": "Sahih Muslim 2723",       // optional
          "sourceAr": "صحيح مسلم ٢٧٢٣",         // optional
          "virtue": "...",           // optional: the narrated reward
          "virtueAr": "..."          // optional
        }
      ]
    }
  ]
}
```

Every field except `textAr`, `textEn` and `reps` is optional, so a file written
against schema version 1 still parses.

### Supported `icon` values

`WbSunny`, `NightsStay`, `Mosque`, `Bedtime`, `Brightness5`. Anything else falls
back to `Book`. To add one, extend the `when` block in
`ui/screens/AdhkarScreen.kt`.

## Conventions

- **Arabic must be fully vocalised** (with harakat). It is rendered in
  `hafs_quran.ttf` at a large size; unvocalised text looks wrong next to it.
- **Qur'anic text** should use the Uthmani orthography, matching
  `assets/quran.json`.
- **`reps`** is the number of repetitions narrated for that dhikr. Use `1`, not
  `0`, when it is said once.
- **Numbers in `sourceAr`** use Arabic-Indic digits. The UI passes
  `bypassNumerals = true` for source lines so they are not converted twice.

## Current coverage

| Folder | Items |
| :-- | --: |
| Morning | 19 |
| Evening | 15 |
| After Prayer | 9 |
| Before Sleep | 8 |
| Upon Waking | 4 |
| **Total** | **55** |

This is a verified core, not the complete *Hisn al-Muslim*, which runs to roughly
130 chapters. Every entry above was included because its wording and attribution
could be stated with confidence. The remaining chapters are deliberately absent
rather than guessed at: an invented isnad or a mis-vocalised du'a is worse than a
missing one.

## Importing a larger vetted dataset

To expand coverage, obtain a dataset whose provenance you trust and can license
compatibly with GPL-3.0. Good candidates are the open Hisn al-Muslim JSON
collections that carry per-item hadith references.

`tools/import_adhkar.py` converts a flat list into Emran's shape and reports what
is missing rather than silently accepting it:

```bash
python tools/import_adhkar.py source.json --folder morning --out merged.json
```

It validates that:

- every item has non-empty `textAr`, `textEn` and an integer `reps >= 1`;
- `id` values are unique across the whole file;
- Arabic text actually contains Arabic characters and at least some harakat;
- `icon` values are ones the UI can render.

Items failing validation are listed and **not** written. Review them by hand
against a printed copy before adding them.
