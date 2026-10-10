const fs = require('fs');

const obsidianBgColor = "#0B0C0E"; 
const obsidianFgColor1 = "#F8FAFC";
const obsidianFgColor2 = "#94A3B8";

const bsBgColor = "#262626";
const bsFgColor1 = "#A3A3A3";
const bsFgColor2 = "#6B7280";

function makeBg(hex) {
    return `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="${hex}" android:pathData="M0,0h108v108h-108z" />
</vector>`;
}

function makeFg(c1, c2) {
    return `<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M 31,75 L 31,53 A 3,3 0 0 1 37,53 L 37,75 Z M 41,75 L 41,43 A 3,3 0 0 1 47,43 L 47,75 Z M 51,75 L 51,33 A 3,3 0 0 1 57,33 L 57,75 Z M 61,75 L 61,43 A 3,3 0 0 1 67,43 L 67,75 Z M 71,75 L 71,53 A 3,3 0 0 1 77,53 L 77,75 Z M 24,75 H 84 V 77 A 1.5,1.5 0 0 1 82.5,78.5 H 25.5 A 1.5,1.5 0 0 1 24,77 Z"
        android:strokeWidth="0.8"
        android:strokeColor="#FFFFFF"
        android:strokeAlpha="0.15">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="31"
                android:startY="33"
                android:endX="77"
                android:endY="78"
                android:type="linear">
                <item android:color="${c1}" android:offset="0.0" />
                <item android:color="${c2}" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
</vector>`;
}

function makeMipmap(name) {
    return `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background_${name}" />
    <foreground android:drawable="@drawable/ic_launcher_foreground_${name}" />
</adaptive-icon>`;
}

// 1. Write Drawables
fs.writeFileSync('app/src/main/res/drawable/ic_launcher_background_obsidian.xml', makeBg(obsidianBgColor));
fs.writeFileSync('app/src/main/res/drawable/ic_launcher_foreground_obsidian.xml', makeFg(obsidianFgColor1, obsidianFgColor2));

fs.writeFileSync('app/src/main/res/drawable/ic_launcher_background_black_stone.xml', makeBg(bsBgColor));
fs.writeFileSync('app/src/main/res/drawable/ic_launcher_foreground_black_stone.xml', makeFg(bsFgColor1, bsFgColor2));

// 2. Write Mipmap
fs.writeFileSync('app/src/main/res/mipmap-anydpi-v26/ic_launcher_obsidian.xml', makeMipmap("obsidian"));
fs.writeFileSync('app/src/main/res/mipmap-anydpi-v26/ic_launcher_round_obsidian.xml', makeMipmap("obsidian"));

fs.writeFileSync('app/src/main/res/mipmap-anydpi-v26/ic_launcher_black_stone.xml', makeMipmap("black_stone"));
fs.writeFileSync('app/src/main/res/mipmap-anydpi-v26/ic_launcher_round_black_stone.xml', makeMipmap("black_stone"));

// 3. Update AndroidManifest.xml
let manifest = fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8');

const additionalAliases = `        <activity-alias
            android:name=".MainActivityAliasObsidian"
            android:targetActivity=".MainActivity"
            android:exported="true"
            android:enabled="false"
            android:icon="@mipmap/ic_launcher_obsidian"
            android:roundIcon="@mipmap/ic_launcher_round_obsidian"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivityAliasBlack_stone"
            android:targetActivity=".MainActivity"
            android:exported="true"
            android:enabled="false"
            android:icon="@mipmap/ic_launcher_black_stone"
            android:roundIcon="@mipmap/ic_launcher_round_black_stone"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

    </application>`;

manifest = manifest.replace("</application>", additionalAliases);
fs.writeFileSync('app/src/main/AndroidManifest.xml', manifest);


// 4. Update PrayerViewModel.kt
let vm = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/PrayerViewModel.kt', 'utf8');
vm = vm.replace(
    '"Desert", "Aurora"', 
    '"Desert", "Aurora", "Obsidian", "Black_stone"'
);

// We should also replace the activeAlias logic because BLACK_STONE needs lowercasing
// Currently it is: val activeAlias = if (theme == "BLOOD_MOON") "Blood_moon" else theme.lowercase().replaceFirstChar { it.uppercase() }
// Let's replace the whole assignment:
vm = vm.replace(
    `val activeAlias = if (theme == "BLOOD_MOON") "Blood_moon" else theme.lowercase().replaceFirstChar { it.uppercase() }`,
    `val activeAlias = when (theme) {
            "BLOOD_MOON" -> "Blood_moon"
            "BLACK_STONE" -> "Black_stone"
            else -> theme.lowercase().replaceFirstChar { it.uppercase() }
        }`
);

fs.writeFileSync('app/src/main/java/com/example/ui/viewmodel/PrayerViewModel.kt', vm);

console.log("Assets created, Manifest updated, ViewModel updated.");
