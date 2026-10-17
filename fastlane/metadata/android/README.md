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

Screenshots are real captures from a phone (status bar cropped), shared by both
locales. The icon is rendered from the adaptive-icon vectors (`docs/icon.svg`).

[supply]: https://docs.fastlane.tools/actions/supply/
