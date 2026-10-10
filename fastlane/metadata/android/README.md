# F-Droid / fastlane metadata

Structure follows the [fastlane supply][supply] layout that F-Droid reads
directly from the repository.

```
fastlane/metadata/android/
  en-US/
    title.txt                 # <= 30 chars
    short_description.txt     # <= 80 chars
    full_description.txt      # <= 4000 chars
    changelogs/<versionCode>.txt
    images/
      icon.png                # 512x512
      featureGraphic.png      # 1024x500
      phoneScreenshots/       # 1.png, 2.png, ...
  ar/
    ...same layout...
```

## Still needed before submitting to F-Droid

- [ ] `images/icon.png` (512x512)
- [ ] `images/featureGraphic.png` (1024x500)
- [ ] `images/phoneScreenshots/*.png` for both locales

Screenshots should show: the home dashboard, today's prayers, the Qada forge,
the Quran reader, the Adhkar list and the Qibla compass. Capture them in both
light and dark mode if you want to show off the theme system.

[supply]: https://docs.fastlane.tools/actions/supply/
