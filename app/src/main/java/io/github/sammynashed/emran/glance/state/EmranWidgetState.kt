package io.github.sammynashed.emran.glance.state

import io.github.sammynashed.emran.ui.theme.EmranTheme
import com.squareup.moshi.JsonClass

/**
 * The core, generic presentation state for all Emran widgets.
 * Widgets should be pure UI clients; they do not calculate anything.
 * Instead, the WidgetStateMapper transforms business models into this flat structure.
 */
@JsonClass(generateAdapter = true)
data class EmranWidgetState(
    // 1. Schema Versioning for forward compatibility
    val schemaVersion: Int = 1,
    
    // 2. Instance Configuration
    val widgetId: Int = 0,
    val widgetType: WidgetType = WidgetType.PRAYER, // Support for multiple widget types
    
    // 3. Theme Configuration
    val followsAppTheme: Boolean = true,
    val themeOverride: EmranTheme? = null,
    /**
     * Whether to render the light variant of [themeOverride]. Defaults to false
     * so widgets serialised by an older build keep their dark appearance.
     */
    val isLightMode: Boolean = false,
    
    // 4. Loading/Error States
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    
    /**
     * The app's language ("en" / "ar"), mirrored into the widget so it follows
     * the in-app setting rather than the device locale -- a user running Emran in
     * Arabic on an English phone should get an Arabic widget.
     */
    val language: String = "en",

    /** True when the clock should render 24-hour, mirroring the device setting. */
    val use24Hour: Boolean = false,

    // 5. Prayer Domain (Structured Data)
    val nextPrayerName: String? = null,
    val nextPrayerEpochMillis: Long? = null,
    val currentPrayerName: String? = null,
    val currentPrayerEpochMillis: Long? = null,
    val locationName: String? = null,

    /**
     * Today's five obligatory prayers in order, for the schedule widget.
     * Parallel lists rather than a list of pairs, because Moshi handles flat
     * primitive lists without a custom adapter.
     */
    val todayPrayerNames: List<String> = emptyList(),
    val todayPrayerEpochMillis: List<Long> = emptyList(),
    
    // 6. Calendar Domain (Structured Data)
    val hijriDay: Int? = null,
    val hijriMonth: Int? = null,
    val hijriYear: Int? = null,
    
    val quranProgressPages: Int = 0,
    val quranTotalPages: Int = 0,

    /** Surahs finished in the current khatmah, and how many khatmat came before. */
    val surahsCompleted: Int = 0,
    val khatmahCount: Int = 0,

    /** This widget's glass tint (WidgetArt.Tint name). In the state so a change redraws it at once. */
    val tint: String = "AUTO",

    /** Today's ayah (see data/DailyAyat): its text, where it is from, and Saheeh's translation. */
    val ayahText: String? = null,
    val ayahSurah: Int = 0,
    val ayahNumber: Int = 0,
    val ayahSurahAr: String? = null,
    val ayahSurahEn: String? = null,
    val ayahEnglish: String? = null,
)
