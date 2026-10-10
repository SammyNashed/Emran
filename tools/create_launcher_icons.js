const fs = require('fs');
const themes = {
    "GOLDEN": ["Golden", "#D4AF37", "#FDE047"],
    "EMERALD": ["Emerald", "#10B981", "#34D399"],
    "OCEAN": ["Ocean", "#3B82F6", "#60A5FA"],
    "AMYTHIST": ["Amythist", "#8B5CF6", "#C084FC"],
    "SUNFIRE": ["Sunfire", "#EF4444", "#F87171"],
    "SAPPHIRE": ["Sapphire", "#2563EB", "#3B82F6"],
    "LAVENDER": ["Lavender", "#A78BFA", "#C4B5FD"],
    "MINT": ["Mint", "#06B6D4", "#67E8F9"],  
    "FOREST": ["Forest", "#4ADE80", "#86EFAC"],
    "BLOOD_MOON": ["Blood_moon", "#DC2626", "#FCA5A5"],
    "MONOCHROME": ["Monochrome", "#E2E8F0", "#F8FAFC"],
    "SAKURA": ["Sakura", "#FBCFE8", "#FDF2F8"],
    "NEBULA": ["Nebula", "#C084FC", "#E9D5FF"],
    "DESERT": ["Desert", "#F59E0B", "#FCD34D"],
    "AURORA": ["Aurora", "#2DD4BF", "#99F6E4"]
};

const fg = (c1, c2) => `<vector xmlns:android="http://schemas.android.com/apk/res/android"
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
                <item android:color="${c2}" android:offset="0.0" />
                <item android:color="${c1}" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
</vector>`;

const bg = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#0A0A0A" android:pathData="M0,0h108v108h-108z" />
</vector>`;

const ada = (name_lower) => `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background_${name_lower}" />
    <foreground android:drawable="@drawable/ic_launcher_foreground_${name_lower}" />
</adaptive-icon>`;

const aliasXml = (name, name_lower) => `
        <activity-alias
            android:name=".MainActivityAlias${name}"
            android:targetActivity=".MainActivity"
            android:exported="true"
            android:enabled="false"
            android:icon="@mipmap/ic_launcher_${name_lower}"
            android:roundIcon="@mipmap/ic_launcher_round_${name_lower}"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>`;

const drw = "app/src/main/res/drawable";
const mip = "app/src/main/res/mipmap-anydpi-v26";

let aliases = [];

for (let key in themes) {
    let [name, c1, c2] = themes[key];
    let name_lower = name.toLowerCase();
    
    fs.writeFileSync(`${drw}/ic_launcher_foreground_${name_lower}.xml`, fg(c1, c2));
    fs.writeFileSync(`${drw}/ic_launcher_background_${name_lower}.xml`, bg);
    fs.writeFileSync(`${mip}/ic_launcher_${name_lower}.xml`, ada(name_lower));
    fs.writeFileSync(`${mip}/ic_launcher_round_${name_lower}.xml`, ada(name_lower));
    
    aliases.push(aliasXml(name, name_lower));
}

let manifestPath = "app/src/main/AndroidManifest.xml";
let manifest = fs.readFileSync(manifestPath, 'utf8');
manifest = manifest.replace("<!-- Broadcast Receivers -->", aliases.join("") + "\n\n        <!-- Broadcast Receivers -->");
fs.writeFileSync(manifestPath, manifest);
console.log("Assets and manifest updated");
