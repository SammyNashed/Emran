<!-- Banner & Title -->
<p align="center">
  <img src="app/src/main/res/mipmap-xxhdpi/ic_launcher.png" alt="Emran App Icon" width="128" height="128" />
</p>

<h1 align="center">Emran</h1>
<p align="center">
  <strong>الصلاة عمران الروح — "Salah is the prosperity of the soul."</strong>
</p>

<p align="center">
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-M3-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" /></a>
  <a href="https://developer.android.com/training/data-storage/room"><img src="https://img.shields.io/badge/Database-Room-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Room" /></a>
  <a href="https://developer.android.com/jetpack/androidx/releases/glance"><img src="https://img.shields.io/badge/Widget-Jetpack%20Glance-4285F4.svg?style=flat-square&logo=android&logoColor=white" alt="Glance Widget" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-green.svg?style=flat-square" alt="GPL-3.0" /></a>
  <img src="https://img.shields.io/badge/Network-Only%20when%20you%20ask-blue.svg?style=flat-square" alt="Network only for downloads you start" />
</p>

<p align="center">
  <strong>Emran</strong> is a free and open-source Islamic companion app that helps Muslims build and sustain consistency in their daily prayers. It is fully offline: prayer calculations, Quran text, Adhkar and all personal data live on the device. The app uses the network for one thing only, optional downloads you start yourself (the Hifz speech model and extra Hadith collections). There is no telemetry, no analytics and no account.
</p>

---

## 🔒 Privacy

Emran is fully offline. It does hold the `INTERNET` permission, for exactly one
purpose: **optional downloads that you start with a tap** - the Hifz speech model,
and the larger Hadith collections (the small ones ship inside the app). Nothing
runs in the background, and every download is checked against a SHA-256 before it
is used. There is no telemetry, no analytics, no ad SDK and no account, and
nothing you record ever leaves your device. It never collects, shares or sells data.

Permissions requested, and why each one is needed:

| Permission | Why |
| :--- | :--- |
| `INTERNET` | Only for the downloads you start yourself (Hifz model, extra Hadith books). Never used for analytics or syncing |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Deliver the adhan at the exact prayer instant rather than in a batched window |
| `RECEIVE_BOOT_COMPLETED` | Restore the alarm schedule after a reboot, otherwise the adhan stops silently |
| `POST_NOTIFICATIONS` | Show prayer alerts on Android 13+ |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional, user-initiated from Settings; keeps the adhan reliable under Doze |
| `VIBRATE` | Haptic feedback for the tasbeeh counter and Qada incrementors |

---

## ✨ Features

### 1. Daily Prayer Ledger & Notifications
* **Offline prayer calculation** for any coordinate or bundled city, using twelve
  published conventions: Muslim World League, ISNA, Egyptian General Authority,
  Umm al-Qura, Karachi, Dubai, Qatar, Kuwait, Singapore (MUIS), Diyanet (Turkey),
  Tehran, and Ja'fari — plus a **Custom** mode for user-defined angles.
* **Correct across daylight saving time.** Locations are stored as IANA time
  zones (`America/Chicago`), not fixed UTC offsets, so times follow each region's
  actual rules for each date. See [Time zone handling](#-time-zone-handling).
* **High-latitude rules** for places where the sun never reaches the Fajr or Isha
  angle: middle of the night, one-seventh of the night, angle-based, or automatic.
* **Prayer tracking & ledger** — log all five daily prayers with categorised status.
* **Consistency streaks** with a full-screen celebration animation and haptics.
* **Adhan scheduling** via `AlarmManager` with a `WorkManager` fallback, and
  automatic rescheduling on boot.

### 2. The Forge (Qada Backlog Tracker)
* Calculate, manage and track missed prayers accumulated over months or years.
* Visual progress gauges and fluid counters with tactile feedback.

### 3. Qibla Compass
* Rotation-vector, accelerometer and magnetometer fusion with geomagnetic
  declination correction, plus live calibration guidance.

### 4. Quran Reader
* All 114 surahs offline in Uthmani script, rendered in a proper mushaf typeface.
* Navigate by Surah, Juz or page (604-page mapping), with search and bookmarks.
* *Audio recitation is not included — see [Roadmap](#-roadmap).*

### 5. Dhikr & Digital Masbaha
* Counter with custom targets, lap counting, sound and per-count micro-vibration
  for eyes-free use.

### 6. Adhkar Collection
* **55 supplications** across morning, evening, after-prayer, before-sleep and
  upon-waking, each with **Arabic, transliteration, translation and its source**
  (surah and ayah, or collection and hadith number).
* Narrated virtues shown where they exist, and built-in repetition counters.
* **128 more chapters from Hisn al-Muslim** (223 items) in their own section. These
  cite the book's chapter, not a hadith number, and are kept visibly separate from
  the verified core.
* **Search and bookmarks** across adhkar and hadith.
* Format and contribution guide: [`docs/adhkar-format.md`](docs/adhkar-format.md).

### 6b. Hadith Library
* **Riyad as-Salihin, the Forty Hadith of an-Nawawi and the Forty Hadith Qudsi**
  ship inside the app (about 0.5 MB together), Arabic with English.
* **Nine more collections are optional downloads** - Sahih al-Bukhari, Sahih Muslim,
  Abi Dawud, at-Tirmidhi, an-Nasa'i, Ibn Majah, Muwatta Malik, ad-Darimi and Bulugh
  al-Maram - fetched only when you tap, 0.4 to 2.6 MB each, removable at any time.
* Built to stay light: a compact per-chapter format read in place, so a chapter is
  decoded when opened and only a few are ever in memory. See
  [`tools/build_hadith_packs.py`](tools/build_hadith_packs.py).
* Search (Arabic ignoring diacritics, English, or a hadith number), bookmarks,
  copy and share, and a Hadith of the Day on the home screen.

### 7. Quests & Badges
* Consistency quests and unlockable achievements for long-term habit formation.

### 8. Home-Screen Widget
* Jetpack Glance widget showing the next prayer and its time.
* Background colours and accents follow the active app theme and light/dark mode
  immediately when either is changed.

---

## 🎨 Themes & Personalisation

**20 Material 3 presets**, each with a matching launcher icon, and each available
in **light, dark or follow-system**. The light variants are derived from the dark
ones so a preset keeps its accent identity in both modes — "Golden Oasis" stays
golden either way.

| Preset | Colour identity | Launcher icon |
| :--- | :--- | :--- |
| **Black Stone** | Kaaba black & Makkah gold | `ic_launcher_black_stone` |
| **Boreal Lights** | Aurora green & violet | `ic_launcher_aurora` |
| **Cosmic Noir** | Deep space dark | `ic_launcher_cosmic` |
| **Crimson Moon** | Blood moon red | `ic_launcher_blood_moon` |
| **Expressive** | Default vibrant modern **(default)** | `ic_launcher_expressive` |
| **Golden Oasis** | Sand & warm gold | `ic_launcher_golden` |
| **Ice Emerald** | Mint & jade green | `ic_launcher_emerald` |
| **Lavender Field** | Soft lavender & iris | `ic_launcher_lavender` |
| **Midnight Teal** | Emerald cyan & teal | `ic_launcher_teal` |
| **Nebula Spark** | Cosmic purple & cyan | `ic_launcher_nebula` |
| **Neon Sapphire** | Electric blue | `ic_launcher_sapphire` |
| **Pacific Sea** | Ocean blue & sky | `ic_launcher_ocean` |
| **Pine Evergreen** | Deep forest green | `ic_launcher_forest` |
| **Polar Glacier** | Soft mint glacier | `ic_launcher_mint` |
| **Pure Slate** | High-contrast monochrome | `ic_launcher_monochrome` |
| **Sahara Nomad** | Desert dune & amber | `ic_launcher_desert` |
| **Solar Flare** | Crimson & warm sunfire | `ic_launcher_sunfire` |
| **Sunset Rose** | Dusty rose & pink | `ic_launcher_rose` |
| **Tokyo Sakura** | Cherry blossom pink | `ic_launcher_sakura` |
| **Velvet Amethyst** | Deep violet & purple | `ic_launcher_amythist` |

---

## 🕰 Time zone handling

Worth calling out, because it is the part most prayer apps get quietly wrong.

Emran stores each location as an **IANA time zone identifier**, never as a fixed
UTC offset. Solar events are computed as absolute instants in universal time and
only converted to wall-clock time at the very end, through
`java.time`'s time zone database.

This matters more than it sounds. **77 of Emran's 164 bundled cities observe
daylight saving time.** An earlier version stored Chicago as `-6.0`, which is
correct in January and an hour wrong from March to November — so every prayer time
and every adhan alarm was an hour late for most of the year, everywhere in North
America and Europe. Working in instants also means Morocco's Ramadan shift and the
southern hemisphere's inverted summer are handled automatically, and a prayer that
falls on a spring-forward morning resolves to the correct side of the transition.

The calculation is covered by a test suite that cross-checks sunrise and sunset
against an **independently implemented NOAA solar algorithm** across eight cities
and six dates, rather than against the app's own arithmetic.

---

## 🛠 Architecture & Tech Stack

* **Language** — [Kotlin 2.2](https://kotlinlang.org/)
* **UI** — [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
* **Widgets** — [Jetpack Glance](https://developer.android.com/jetpack/androidx/releases/glance)
* **Architecture** — MVVM with `StateFlow` and `collectAsStateWithLifecycle()`
* **Database** — [Room](https://developer.android.com/training/data-storage/room) via KSP
* **Background** — `WorkManager` and `AlarmManager`
* **Navigation** — Navigation Compose
* **Testing** — JUnit, Robolectric, Roborazzi screenshot tests

---

## ⚡ Building

### Prerequisites
* Android Studio Koala or newer
* Android SDK 36 (`compileSdk` 36, `targetSdk` 36, `minSdk` 24)
* JDK 17

### Command line

```bash
git clone https://github.com/SammyNashed/Emran.git
cd Emran

./gradlew assembleDebug      # Debug APK
./gradlew testDebugUnitTest  # Run the test suite
./gradlew assembleRelease    # Release APK (unsigned unless a keystore is present)
./gradlew bundleRelease      # Release AAB
```

Release signing is opt-in. Without `KEYSTORE_PATH` and `STORE_PASSWORD` in the
environment, `assembleRelease` produces an unsigned APK rather than failing — so
F-Droid and contributors can build without any secrets.

---

## 🤝 Contributing

Contributions are welcome, particularly:

* **Translations.** UI strings live in `ui/localization/Localizations.kt`
  (English and Arabic today).
* **Adhkar content.** Every entry must carry a verifiable source — see
  [`docs/adhkar-format.md`](docs/adhkar-format.md) and `tools/import_adhkar.py`,
  which validates submissions and rejects unsourced or unvocalised entries.
* **Prayer calculation conventions.** New methods need a citation for their angles.

Anything touching prayer times must come with tests. That code is the reason
someone wakes for Fajr.

---

## ⚖️ License

Emran is licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE).

### A note on the author's intent

Emran was written for the Muslim community, freely, seeking the pleasure of Allah.
The author's wish is that **nobody profits from it commercially** — not others, and
not the author.

GPL-3.0 was chosen deliberately over a licence that bans commercial use outright.
A non-commercial licence is not open source: F-Droid and similar repositories will
not distribute it, which would cut Emran off from exactly the privacy-conscious
audience it is built for. GPL-3.0 instead requires that **any fork or modified
build publish its full source under the same terms**, which forbids the outcome
actually worth preventing — someone re-skinning Emran, adding advertising or a
subscription, and shipping it closed.

If you fork Emran, please honour that intent: keep it free, keep it open, and keep
it free of advertising.

---

## 📌 Roadmap

Currently deferred, and honestly labelled as such:

* **Fasting & Ramadan module** — partially implemented in the codebase but not yet
  finished or enabled for release.
* **Quran audio recitation** — planned; would require optional network access or
  bundled audio, which needs a decision on download size.
* **Additional widget sizes and layouts.**
* **Primary hadith references for the Hisn al-Muslim chapters** — the 55 core
  adhkar cite a hadith number; the 223 imported Hisn al-Muslim items cite the
  book's chapter only, because that is all the source dataset provides, and are
  kept in their own section so the two standards are never mixed. Adding the
  primary reference to each is the remaining work.

---

<p align="center">
  <em>Free, open source, and built to support daily Salah.</em>
</p>
