#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Import a third-party adhkar dataset into Emran's asset format.

Emran ships a verified core of 55 supplications rather than a full Hisn
al-Muslim, because an invented attribution or a mis-vocalised du'a is worse than
a missing one. This script exists so a larger vetted dataset can be merged in
without hand-editing several thousand lines of JSON -- and, more importantly,
without silently accepting entries that fail basic checks.

Anything that does not validate is reported and left out. Review those by hand
against a printed copy before adding them.

Usage:
    python tools/import_adhkar.py source.json --folder morning
    python tools/import_adhkar.py source.json --folder morning --out merged.json
    python tools/import_adhkar.py --validate-only

See docs/adhkar-format.md for the schema.
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import unicodedata

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET_PATH = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "adhkar.json")

SUPPORTED_ICONS = {"WbSunny", "NightsStay", "Mosque", "Bedtime", "Brightness5", "Book", "Home", "Praying", "Shield", "Favorite", "Cloud", "Restaurant", "Groups", "Flight"}

# Arabic letters, and the harakat that must be present for the text to render
# correctly in hafs_quran.ttf at display size.
ARABIC_LETTER = re.compile(r"[ء-ي]")
ARABIC_HARAKAT = re.compile(r"[ً-ْٰ]")


class ValidationError(Exception):
    pass


def load_json(path):
    with io.open(path, encoding="utf-8") as fh:
        return json.load(fh)


def validate_item(item, index, seen_ids):
    """Returns a list of problem strings; empty means the item is acceptable."""
    problems = []
    label = item.get("id") or "index %d" % index

    text_ar = (item.get("textAr") or "").strip()
    text_en = (item.get("textEn") or "").strip()

    if not text_ar:
        problems.append("%s: textAr is empty" % label)
    else:
        if not ARABIC_LETTER.search(text_ar):
            problems.append("%s: textAr contains no Arabic letters" % label)
        elif not ARABIC_HARAKAT.search(text_ar):
            problems.append(
                "%s: textAr has no harakat -- unvocalised text renders poorly "
                "next to the vocalised entries" % label
            )

    if not text_en:
        problems.append("%s: textEn is empty" % label)

    reps = item.get("reps")
    if not isinstance(reps, int) or isinstance(reps, bool) or reps < 1:
        problems.append("%s: reps must be an integer >= 1 (got %r)" % (label, reps))

    item_id = item.get("id")
    if item_id:
        if item_id in seen_ids:
            problems.append("%s: duplicate id" % label)
        seen_ids.add(item_id)

    if not (item.get("source") or item.get("sourceAr")):
        problems.append(
            "%s: no source. Every shipped du'a names a surah/ayah or a "
            "collection and hadith number" % label
        )

    if not item.get("transliteration"):
        problems.append("%s: no transliteration (warning only)" % label)

    return problems


def validate_document(doc):
    """Validates a whole adhkar.json document. Returns (errors, warnings)."""
    errors = []
    warnings = []
    seen_ids = set()

    if "folders" not in doc:
        raise ValidationError("document has no 'folders' key")

    for folder in doc["folders"]:
        for key in ("id", "titleEn", "titleAr", "icon", "color", "items"):
            if key not in folder:
                errors.append("folder %r: missing %r" % (folder.get("id"), key))

        icon = folder.get("icon")
        if icon and icon not in SUPPORTED_ICONS:
            errors.append(
                "folder %r: icon %r is not rendered by AdhkarScreen.kt "
                "(supported: %s)" % (folder.get("id"), icon, ", ".join(sorted(SUPPORTED_ICONS)))
            )

        color = folder.get("color", "")
        if not re.fullmatch(r"[0-9A-Fa-f]{6}", color or ""):
            errors.append("folder %r: color %r must be RRGGBB with no '#'" % (folder.get("id"), color))

        for index, item in enumerate(folder.get("items", [])):
            for problem in validate_item(item, index, seen_ids):
                if problem.endswith("(warning only)"):
                    warnings.append("%s / %s" % (folder.get("id"), problem))
                else:
                    errors.append("%s / %s" % (folder.get("id"), problem))

    return errors, warnings


def normalise_incoming(raw):
    """
    Maps a few common field spellings found in public datasets onto Emran's.
    Unknown shapes are returned untouched so validation can reject them loudly.
    """
    if not isinstance(raw, dict):
        return raw

    mapping = {
        "arabic": "textAr",
        "ar": "textAr",
        "text": "textAr",
        "content": "textAr",
        "english": "textEn",
        "en": "textEn",
        "translation": "textEn",
        "count": "reps",
        "repeat": "reps",
        "repetitions": "reps",
        "reference": "source",
        "note": "virtue",
        "fadl": "virtue",
        "latin": "transliteration",
        "translit": "transliteration",
    }

    out = {}
    for key, value in raw.items():
        out[mapping.get(key, key)] = value

    reps = out.get("reps")
    if isinstance(reps, str):
        digits = re.search(r"\d+", reps)
        out["reps"] = int(digits.group()) if digits else 1
    elif reps is None:
        out["reps"] = 1

    for field in ("textAr", "textEn", "transliteration", "source", "virtue"):
        if isinstance(out.get(field), str):
            out[field] = unicodedata.normalize("NFC", out[field]).strip()

    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", nargs="?", help="JSON file to import")
    parser.add_argument("--folder", help="Target folder id (e.g. morning)")
    parser.add_argument("--out", help="Where to write the merged file (default: stdout)")
    parser.add_argument("--validate-only", action="store_true",
                        help="Validate the shipped asset and exit")
    args = parser.parse_args()

    current = load_json(ASSET_PATH)

    if args.validate_only or not args.source:
        errors, warnings = validate_document(current)
        total = sum(len(f["items"]) for f in current["folders"])
        print("Validating %s" % ASSET_PATH)
        print("  folders: %d, items: %d, schemaVersion: %s"
              % (len(current["folders"]), total, current.get("schemaVersion", 1)))
        for w in warnings:
            print("  WARN  %s" % w)
        for e in errors:
            print("  ERROR %s" % e)
        print("  %d error(s), %d warning(s)" % (len(errors), len(warnings)))
        return 1 if errors else 0

    if not args.folder:
        parser.error("--folder is required when importing")

    target = next((f for f in current["folders"] if f["id"] == args.folder), None)
    if target is None:
        parser.error("no folder with id %r. Existing: %s"
                     % (args.folder, ", ".join(f["id"] for f in current["folders"])))

    incoming_raw = load_json(args.source)
    if isinstance(incoming_raw, dict):
        incoming_raw = incoming_raw.get("items", incoming_raw.get("content", []))

    seen_ids = {i.get("id") for f in current["folders"] for i in f["items"] if i.get("id")}

    accepted = []
    rejected = []
    for index, raw in enumerate(incoming_raw):
        item = normalise_incoming(raw)
        problems = [p for p in validate_item(item, index, set(seen_ids))
                    if not p.endswith("(warning only)")]
        if problems:
            rejected.append((index, problems))
        else:
            if item.get("id"):
                seen_ids.add(item["id"])
            accepted.append(item)

    target["items"].extend(accepted)
    current["schemaVersion"] = 2

    print("Accepted %d item(s) into folder %r." % (len(accepted), args.folder), file=sys.stderr)
    if rejected:
        print("Rejected %d item(s) -- review these by hand:" % len(rejected), file=sys.stderr)
        for index, problems in rejected:
            for p in problems:
                print("  [%d] %s" % (index, p), file=sys.stderr)

    payload = json.dumps(current, ensure_ascii=False, indent=2) + "\n"
    if args.out:
        with io.open(args.out, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(payload)
        print("Wrote %s" % args.out, file=sys.stderr)
    else:
        sys.stdout.write(payload)

    return 0


if __name__ == "__main__":
    sys.exit(main())
