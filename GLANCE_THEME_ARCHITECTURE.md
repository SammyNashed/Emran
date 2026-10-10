# Glance Theme Architecture for Emran

## Architecture Overview

The Emran Glance Theme Engine brings the full power and visual identity of Emran's Compose themes (`AppColors`) directly into the Glance home screen widget ecosystem.

### Folder Structure
```
app/src/main/java/com/example/glance/
└── theme/
    ├── GlanceColors.kt       // Pure Glance-compatible palette definition
    ├── EmranGlanceTheme.kt   // CompositionLocalProvider & Theme Resolver
    └── Modifiers.kt          // Extension functions for easy modifier styling
```

### Theme Engine API

Widgets no longer contain raw hex codes or theme resolution logic. Instead, they wrap their content in the `EmranGlanceTheme` composable and access colors declaratively:

```kotlin
EmranGlanceTheme(widgetState = state) {
    Box(modifier = GlanceModifier.appBackground()) {
        Text(
            text = "Prayer Time",
            style = TextStyle(color = EmranGlanceTheme.colors.textPrimary)
        )
    }
}
```

**Available Colors:**
- `background`: Primary widget background
- `surface`: Elevated cards and secondary backgrounds
- `primary`: Accent elements and primary actions
- `secondary`: Secondary accents
- `textPrimary`: Main readable text (derived from `onBackground`)
- `textSecondary`: Muted text (derived from `grayMuted`)
- `success`: Positive indicators (e.g. active streaks)
- `warning`: Attention indicators
- `error`: Destructive actions or missed items
- `divider`: Subtle line separations (derived with lower opacity)
- `border`: Outline borders for cards

## Glance Limitations & Visual Compromises

Glance uses Android's legacy `RemoteViews` under the hood, meaning modern Compose graphics rendering is not entirely available.

### 1. Glassmorphism & Blur
- **How Compose does it:** Real-time `RenderEffect.createBlurEffect()` or `Modifier.blur()`.
- **How Glance differs:** `RemoteViews` completely lacks support for real-time background blur or rendering effects.
- **How Emran approximates it:** We use solid opaque or slightly translucent colors for surfaces. 
- **Future Improvements:** Android 12+ supports `S` blur flags on Windows, but not inside RemoteViews dynamically. If Glance adds dynamic background blur support natively, we will swap `surface` definitions.

### 2. Gradients
- **How Compose does it:** `Brush.linearGradient()` or `Brush.radialGradient()`.
- **How Glance differs:** Glance `ColorProvider` is strictly a solid color provider. Gradients require creating actual Android XML Drawable resources (e.g., `<shape><gradient></gradient></shape>`).
- **How Emran approximates it:** We rely on solid, highly curated `AppColors`. The theme engine focuses on contrast and solid aesthetic pairings rather than dynamic gradients.
- **Future Improvements:** If Glance introduces a `BrushProvider`, we can map Compose brushes.

### 3. Shadows
- **How Compose does it:** `Modifier.shadow()` or `elevation` inside `Surface()`.
- **How Glance differs:** `RemoteViews` cannot cast dynamic shadows reliably.
- **How Emran approximates it:** We use high-contrast borders (`EmranGlanceTheme.colors.border`) or distinct background vs. surface color differences to establish hierarchy instead of relying on z-depth shadows.

### 4. Transparency & Alpha Composition
- **How Compose does it:** `.copy(alpha = 0.5f)` computed instantly on any `Color`.
- **How Glance differs:** We cannot easily apply dynamic alpha values to `ColorProvider` objects inside the Composable tree.
- **How Emran approximates it:** The Theme Engine pre-computes derived `ColorProvider`s with baked-in alpha channels (e.g., `divider` is pre-calculated at `0.12f` alpha) during the state resolution phase.

### 5. Rounded Corners
- **How Compose does it:** `Modifier.clip(RoundedCornerShape(16.dp))`.
- **How Glance differs:** `Modifier.cornerRadius` is supported on Android 12+, but for backward compatibility, it often falls back to XML shape drawables or ignores it entirely on older APIs.
- **How Emran approximates it:** We use `GlanceModifier.cornerRadius` and rely on the OS to clip the bounds gracefully.

## Recommendations for the Next Prayer Widget
1. **Never use `Color(...)` directly** in the widget. Always use `EmranGlanceTheme.colors`.
2. **Avoid overlapping translucent surfaces**, as Glance's compositor can produce unexpected color blending.
3. **Use explicit borders** where you would normally use shadows in the main app to maintain visual separation.
