package io.github.sammynashed.emran.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(val code: String, val displayName: String) {
    EN("en", "English"),
    AR("ar", "العربية")
}

object Localizations {
    private val translations = mapOf(
        "welcome" to mapOf(
            "en" to "Welcome to Emran",
            "ar" to "مرحباً بك في عمران"
        ),
        "skip" to mapOf(
            "en" to "SKIP",
            "ar" to "تخطي"
        ),
        "back" to mapOf(
            "en" to "BACK",
            "ar" to "السابق"
        ),
        "complete_setup" to mapOf(
            "en" to "COMPLETE SETUP",
            "ar" to "إكمال الإعداد"
        ),
        "toast_enter_username" to mapOf(
            "en" to "Please enter a username to proceed.",
            "ar" to "يرجى إدخال اسم المستخدم للمتابعة."
        ),
        "toast_select_city" to mapOf(
            "en" to "Please select a city to proceed.",
            "ar" to "يرجى تحديد مدينة للمتابعة."
        ),
        "app_name_sub" to mapOf(
            "en" to "Salah is the prosperity of the soul",
            "ar" to "الصلاة عمران الروح"
        ),
        "app_description" to mapOf(
            "en" to "Track, perfect, and maintain dynamic prayer consistency in your daily Salah. Define custom offsets and restore missed prayers cleanly.",
            "ar" to "تتبع، وحسّن، وحافظ على استمرارية صلاتك اليومية بفاعلية وخشوع. حدد فروق التوقيت واقضِ ما فاتك من صلوات بسلاسة."
        ),
        "select_language" to mapOf(
            "en" to "SELECT LANGUAGE",
            "ar" to "اختر اللغة"
        ),
        "select_aesthetic" to mapOf(
            "en" to "SELECT YOUR AESTHETIC PRESET",
            "ar" to "اختر المظهر والسِمة"
        ),
        "spiritual_milestones" to mapOf(
            "en" to "SPIRITUAL MILESTONES",
            "ar" to "المحطات الروحية"
        ),
        "reflections_intentions" to mapOf(
            "en" to "Reflections & Intentions",
            "ar" to "التأملات والنوايا"
        ),
        "milestones_desc" to mapOf(
            "en" to "A quiet, meditative journal to enrich focus, consistency, and presence in your daily prayer path.",
            "ar" to "دفتر تأملي هادئ لإثراء التركيز والانتظام والحضور في مسار صلاتك اليومية."
        ),
        "active_contemplation" to mapOf(
            "en" to "ACTIVE CONTEMPLATION",
            "ar" to "تأمل نشط"
        ),
        "ponder_later" to mapOf(
            "en" to "Ponder Later",
            "ar" to "تأمل لاحقاً"
        ),
        "conclude_reflection" to mapOf(
            "en" to "Conclude Reflection",
            "ar" to "إنهاء التأمل"
        ),
        "spiritual_attunement" to mapOf(
            "en" to "Spiritual Attunement (Level %s)",
            "ar" to "الانسجام الروحي (المستوى %s)"
        ),
        "towards_renewal" to mapOf(
            "en" to "%d%% towards renewal",
            "ar" to "%d%% نحو التجديد"
        ),
        "accept_invitation" to mapOf(
            "en" to "Accept Invitation →",
            "ar" to "قبول الدعوة ←"
        ),
        "milestones" to mapOf(
            "en" to "Milestones",
            "ar" to "المحطات"
        ),
        "spiritual_journaling" to mapOf(
            "en" to "Spiritual journaling",
            "ar" to "التأملات الروحية"
        ),
        "go_back" to mapOf(
            "en" to "Go Back",
            "ar" to "رجوع"
        ),
        "next" to mapOf(
            "en" to "Next",
            "ar" to "التالي"
        ),
        "continue_btn" to mapOf(
            "en" to "Continue",
            "ar" to "متابعة"
        ),
        "get_started" to mapOf(
            "en" to "Get Started",
            "ar" to "ابدأ الآن"
        ),
        "prayers" to mapOf(
            "en" to "Prayers",
            "ar" to "الصلوات"
        ),
        "qibla" to mapOf(
            "en" to "Qibla",
            "ar" to "القبلة"
        ),
        "forge" to mapOf(
            "en" to "Qada",
            "ar" to "قضاء"
        ),
        "dhikr" to mapOf(
            "en" to "Dhikr",
            "ar" to "الأذكار"
        ),
        "depth_memo" to mapOf(
            "en" to "Depth & Memorization",
            "ar" to "العمق والحفظ"
        ),
        "focus_presence" to mapOf(
            "en" to "Focus & Presence",
            "ar" to "التركيز والحضور"
        ),
        "consistency_sunnah" to mapOf(
            "en" to "Consistency & Sunnah",
            "ar" to "الاستمرارية والسنن"
        ),
        "intentionality" to mapOf(
            "en" to "Intentionality",
            "ar" to "استحضار النية"
        ),
        "depth" to mapOf(
            "en" to "Depth",
            "ar" to "العمق"
        ),
        "focus" to mapOf(
            "en" to "Focus",
            "ar" to "التركيز"
        ),
        "consistency" to mapOf(
            "en" to "Consistency",
            "ar" to "الاستمرارية"
        ),
        "intentional" to mapOf(
            "en" to "Intentional",
            "ar" to "النية"
        ),
        "heartfelt_growth" to mapOf(
            "en" to "Contemplation fulfilled. Heartfelt growth registered.",
            "ar" to "تم تحقيق التأمل. تم تسجيل النمو في ميزان الخشوع."
        ),
        "reflection_released" to mapOf(
            "en" to "Reflection released.",
            "ar" to "تفكر لاحقاً."
        ),
        "began_reflection" to mapOf(
            "en" to "Began reflection on '%s'.",
            "ar" to "بدأ التأمل في '%s'."
        ),

        // Widget home cards
        "widget_prayers_title" to mapOf(
            "en" to "Prayers",
            "ar" to "الصلوات"
        ),
        "widget_prayers_desc" to mapOf(
            "en" to "Prayer times",
            "ar" to "مواقيت الصلاة"
        ),
        "widget_qibla_title" to mapOf(
            "en" to "Qibla",
            "ar" to "القبلة"
        ),
        "widget_qibla_desc" to mapOf(
            "en" to "Align to Mecca",
            "ar" to "بوصلة مكة المكرمة"
        ),
        "widget_forge_title" to mapOf(
            "en" to "Qada",
            "ar" to "قضاء"
        ),
        "widget_forge_desc" to mapOf(
            "en" to "Pray missed Salah",
            "ar" to "اقض الصلاة الفائتة"
        ),
        "widget_dhikr_title" to mapOf(
            "en" to "Masbaha",
            "ar" to "المسبحة"
        ),
        "widget_dhikr_desc" to mapOf(
            "en" to "Dhikr remembrance",
            "ar" to "الأوراد والأذكار اليومية"
        ),
        "widget_quests_title" to mapOf(
            "en" to "Milestones",
            "ar" to "المحطات"
        ),
        "widget_quests_desc" to mapOf(
            "en" to "Spiritual journaling",
            "ar" to "التأملات الروحية"
        ),

        // Challenge 1: recitation_deepener
        "recitation_deepener_title" to mapOf(
            "en" to "The Recitation Deepener",
            "ar" to "مُعمِّق التلاوة"
        ),
        "recitation_deepener_desc" to mapOf(
            "en" to "Incorporate a new Surah from your recent memorization into your Fardh prayers.",
            "ar" to "أدخل سورة جديدة من حفظك الحديث في صلواتك الفريضة."
        ),

        // Challenge 2: translation_reflection
        "translation_reflection_title" to mapOf(
            "en" to "The Translation Reflection",
            "ar" to "تأمل معاني الآيات"
        ),
        "translation_reflection_desc" to mapOf(
            "en" to "Before Salah, read the translation/tafsir of the Surahs you plan to recite. Perform these prayers while consciously reflecting on the meaning.",
            "ar" to "اقرأ ترجمة أو تفسير السور التي تنوي قراءتها قبل الصلاة، ثم صلِّ وأنت حاضر الذهن متأملاً معانيها."
        ),

        // Challenge 3: tajweed_commitment
        "tajweed_commitment_title" to mapOf(
            "en" to "The Tajweed Commitment",
            "ar" to "الالتزام بالتجويد"
        ),
        "tajweed_commitment_desc" to mapOf(
            "en" to "Focus on perfecting the Makharij (points of articulation) for one specific Ayah in your daily prayers.",
            "ar" to "ركز على إتقان مخارج الحروف لآية معينة أثناء صلواتك اليومية."
        ),

        // Challenge 4: baqarah_journey
        "baqarah_journey_title" to mapOf(
            "en" to "The Surah Al-Baqarah Journey",
            "ar" to "رحلة مع سورة البقرة"
        ),
        "baqarah_journey_desc" to mapOf(
            "en" to "Complete a section (Juz) of Al-Baqarah and recite a portion of it during your Sunnah prayers.",
            "ar" to "أكمل قراءة جزء من سورة البقرة، واقرأ قسماً منه خلال صلواتك السنن."
        ),

        // Challenge 5: memorization_sustainer
        "memorization_sustainer_title" to mapOf(
            "en" to "The Memorization Sustainer",
            "ar" to "مثبِّت الحفظ"
        ),
        "memorization_sustainer_desc" to mapOf(
            "en" to "Maintain the recitation of a specific short Surah (Qisar Al-Swar) in all five prayers for a full week without switching.",
            "ar" to "حافظ على تلاوة سورة قصيرة معينة في جميع الصلوات الخمس لمدة أسبوع كامل."
        ),

        // Challenge 6: digital_fast
        "digital_fast_title" to mapOf(
            "en" to "The Digital Fast",
            "ar" to "الصيام الرقمي"
        ),
        "digital_fast_desc" to mapOf(
            "en" to "Perform all five prayers today without checking your phone or any notifications within 10 minutes before and after Salah.",
            "ar" to "أدِّ الصلوات الخمس اليوم دون تصفح هاتفك أو قراءة الإشعارات قبل وبعد الصلاة بـ 10 دقائق."
        ),

        // Challenge 7: slow_descent
        "slow_descent_title" to mapOf(
            "en" to "The Slow Descent",
            "ar" to "التأني والسكينة"
        ),
        "slow_descent_desc" to mapOf(
            "en" to "Observe the tuma'ninah (tranquility)—ensure you pause for a heartbeat between every movement of your Salah (Ruku to Sujood).",
            "ar" to "احرص على الطمأنينة الكاملة؛ توقف هنيهة بين كل ركن وآخر في صلاتك (من الركوع إلى السجود)."
        ),

        // Challenge 8: early_bird
        "early_bird_title" to mapOf(
            "en" to "The Early Bird",
            "ar" to "التبكير للصلاة"
        ),
        "early_bird_desc" to mapOf(
            "en" to "Arrive at the prayer location (or prepare your space) at least 3 minutes before the Adhan.",
            "ar" to "تهيأ للصلاة أو احضر في مكان مصلاك قبل الأذان بـ 3 دقائق على الأقل."
        ),

        // Challenge 9: one_world_prayer
        "one_world_prayer_title" to mapOf(
            "en" to "The \"One World\" Prayer",
            "ar" to "صلاة فك الارتباط"
        ),
        "one_world_prayer_desc" to mapOf(
            "en" to "Enter your Salah with the intention to disconnect from all worldly stressors. Complete the prayer without a single drifting thought of your to-do list.",
            "ar" to "ادخل صلاتك بنية الانفصال التام عن ملهيات الدنيا وضغوطها، وأتمها دون أي انشغال بمهامك اليومية."
        ),

        // Challenge 10: first_takbeer
        "first_takbeer_title" to mapOf(
            "en" to "The First Takbeer",
            "ar" to "تكبيرة الإحرام الأولى"
        ),
        "first_takbeer_desc" to mapOf(
            "en" to "Ensure you reach the prayer area in time to join the congregation for the Takbir al-Ihram (or begin immediately at the start of the time window).",
            "ar" to "احرص على إدراك تكبيرة الإحرام مع الجماعة أو البدء فور دخول وقت الصلاة مباشرة."
        ),

        // Challenge 11: sunnah_anchor
        "sunnah_anchor_title" to mapOf(
            "en" to "The Sunnah Anchor",
            "ar" to "مرساة السنن"
        ),
        "sunnah_anchor_desc" to mapOf(
            "en" to "Consistently perform the Sunnah prayers associated with Dhuhr and Maghrib for three consecutive days.",
            "ar" to "حافظ على النوافل والسنن الرواتب التابعة للظهر والمغرب لثلاثة أيام متتالية."
        ),

        // Challenge 12: night_connection
        "night_connection_title" to mapOf(
            "en" to "The Night Connection",
            "ar" to "مناجاة الليل"
        ),
        "night_connection_desc" to mapOf(
            "en" to "Perform at least two Raka'ats of Tahajjud or Witr before dawn, focusing on a specific Dua.",
            "ar" to "صلِّ ركعتين على الأقل قيام الليل (التهجد أو الوتر) قبل الفجر، مع الإلحاح بالدعاء."
        ),

        // Challenge 13: adhan_responder
        "adhan_responder_title" to mapOf(
            "en" to "The Adhan Responder",
            "ar" to "مجيب الأذان"
        ),
        "adhan_responder_desc" to mapOf(
            "en" to "Listen to the entire Adhan with focus and repeat the words, ending with the established Sunnah Dua.",
            "ar" to "أنصت للأذان كاملاً وردد كلماته، واختمه بالدعاء المأثور المسنون."
        ),

        // Challenge 14: post_salah_remembrance
        "post_salah_remembrance_title" to mapOf(
            "en" to "The Post-Salah Remembrance",
            "ar" to "أذكار ما بعد الصلاة"
        ),
        "post_salah_remembrance_desc" to mapOf(
            "en" to "Complete the daily Adhkar (remembrances) immediately after your Fardh prayers for a full week.",
            "ar" to "حافظ على الأذكار اليومية دبر الصلوات المكتوبة فور انتهائها طوال الأسبوع."
        ),

        // Challenge 15: house_of_prayer
        "house_of_prayer_title" to mapOf(
            "en" to "The House of Prayer",
            "ar" to "تجديد مصلاك"
        ),
        "house_of_prayer_desc" to mapOf(
            "en" to "Make one of your prayers today in a different room or area than usual to renew your sense of presence.",
            "ar" to "صلِّ صلاة واحدة اليوم في غرفة أو مكان مختلف عن المعتاد لتجديد حضورك الذهني."
        ),

        // Challenge 16: gratitude_prostration
        "gratitude_prostration_title" to mapOf(
            "en" to "The Gratitude Prostration",
            "ar" to "سجدة الشكر والامتنان"
        ),
        "gratitude_prostration_desc" to mapOf(
            "en" to "During your Sujood, spend a few extra moments specifically thanking Allah for three specific blessings you noticed today.",
            "ar" to "أطِل أمد سجودك لتشكر الله تعالى مستحظراً ثلاث نعم بعينها لمستها في يومك هذا."
        ),

        // Challenge 17: dua_deep_dive
        "dua_deep_dive_title" to mapOf(
            "en" to "The Dua Deep-Dive",
            "ar" to "أعماق الدعاء"
        ),
        "dua_deep_dive_desc" to mapOf(
            "en" to "Before you finish your prayer, formulate a heartfelt, specific prayer in your own language (internally) for someone else.",
            "ar" to "قبل أن تسلّم، ادعُ بصدق وإلحاح دعوة خاصة بظهر الغيب لشخص آخر تحبه."
        ),

        // Challenge 18: repentance_cycle
        "repentance_cycle_title" to mapOf(
            "en" to "The Repentance Cycle",
            "ar" to "دورة الإنابة"
        ),
        "repentance_cycle_desc" to mapOf(
            "en" to "Perform your Salah with the specific intention of seeking forgiveness for a minor daily shortcoming.",
            "ar" to "أدِّ صلاتك بنية التوبة والاستغفار عن زلل أو تقصير بدر منك خلال اليوم."
        ),

        // Challenge 19: heart_check
        "heart_check_title" to mapOf(
            "en" to "The Heart-Check",
            "ar" to "وقفة تفكر"
        ),
        "heart_check_desc" to mapOf(
            "en" to "Before you begin your prayer, pause for 30 seconds to acknowledge that you are standing before the Creator of the Universe.",
            "ar" to "قبل الشروع في الصلاة، تريّث لـ 30 ثانية مستحضراً جلال الوقوف بين يدي رب العالمين."
        ),

        // Compass directions
        "dir_n" to mapOf("en" to "N", "ar" to "ش"),
        "dir_s" to mapOf("en" to "S", "ar" to "ج"),
        "dir_e" to mapOf("en" to "E", "ar" to "ق"),
        "dir_w" to mapOf("en" to "W", "ar" to "غ"),

        // Badges and tiers names & descriptions
        "Novice Flame" to mapOf("en" to "Novice Flame", "ar" to "شعلة مبتدئة"),
        "Novice Flame_desc" to mapOf("en" to "Sustain a 3-day continuous prayer streak", "ar" to "حافظ على صلاتك لمدة ٣ أيام متتالية"),
        "Spark Initiate" to mapOf("en" to "Spark Initiate", "ar" to "مبتدئ الشرارة"),
        "Spark Initiate_desc" to mapOf("en" to "Sustain a 7-day continuous prayer streak", "ar" to "حافظ على صلاتك لمدة ٧ أيام متتالية"),
        "Torchbearer" to mapOf("en" to "Torchbearer", "ar" to "حامل المشعل"),
        "Torchbearer_desc" to mapOf("en" to "Sustain a 15-day continuous prayer streak", "ar" to "حافظ على صلاتك لمدة ١٥ يوماً متتالياً"),
        "Blazing Beacon" to mapOf("en" to "Blazing Beacon", "ar" to "المنارة المشتعلة"),
        "Blazing Beacon_desc" to mapOf("en" to "Sustain a 30-day continuous prayer streak", "ar" to "حافظ على صلاتك لمدة ٣٠ يوماً متتالياً"),
        "Eternal Fire" to mapOf("en" to "Eternal Fire", "ar" to "اللهب الخالد"),
        "Eternal Fire_desc" to mapOf("en" to "Sustain a 50+ day continuous prayer streak", "ar" to "حافظ على صلاتك لمدة ٥٠ يوماً أو أكثر متتالية"),

        "Seeker of Light" to mapOf("en" to "Seeker of Light", "ar" to "باحث عن النور"),
        "Seeker of Light_desc" to mapOf("en" to "Initial entry rank. Take your first steps to consistency.", "ar" to "مرتبة البداية. خطوتك الأولى نحو الاستمرارية."),
        "Musalat" to mapOf("en" to "Musalat", "ar" to "المصلّي"),
        "Musalat_desc" to mapOf("en" to "Completed at least 40% of standard prayers in the last 30 days.", "ar" to "حافظ على ٤٠٪ على الأقل من الصلوات المكتوبة في آخر ٣٠ يوماً."),
        "Steadfast" to mapOf("en" to "Steadfast", "ar" to "المثابر"),
        "Steadfast_desc" to mapOf("en" to "Completed at least 70% of standard prayers in the last 30 days.", "ar" to "حافظ على ٧٠٪ على الأقل من الصلوات المكتوبة في آخر ٣٠ يوماً."),
        "Guardian of the Pillar" to mapOf("en" to "Guardian of the Pillar", "ar" to "حامي الركن"),
        "Guardian of the Pillar_desc" to mapOf("en" to "Superb mastery: 90%+ completed in the last 30 days.", "ar" to "إتقان ممتاز: أكملت ٩٠٪+ من الصلوات المكتوبة في آخر ٣٠ يوماً."),

        "khushu_level_fmt" to mapOf("en" to "KHUSHU' LEVEL %d", "ar" to "مستوى الخشوع %d"),

        // Screen text translations
        "today_res_fardh" to mapOf("en" to "Reset Fardh Status", "ar" to "إعادة تعيين حالة الفرض"),
        "today_close" to mapOf("en" to "CLOSE", "ar" to "إغلاق"),
        "today_sunnah_header" to mapOf("en" to "SUNNAH & WAJIB PRAYERS", "ar" to "سنن وصلوات تطوعية وأخرى"),

        "qada_liquidation_quest" to mapOf("en" to "LIQUIDATION QUEST", "ar" to "مهمة قضاء الفوائت"),
        "qada_the_forge" to mapOf("en" to "Qada", "ar" to "قضاء"),
        "qada_total_pending" to mapOf("en" to "TOTAL PENDING QADA", "ar" to "إجمالي الفوائت المتبقية"),
        "qada_forge_target" to mapOf("en" to "FORGE TARGET: %d", "ar" to "الهدف المستهدف: %d"),
        "qada_percent_cleared" to mapOf("en" to "%.1f%% CLEARED", "ar" to "تم قضاء %.1f%%"),
        "qada_individual_ledgers" to mapOf("en" to "INDIVIDUAL FORGE LEDGERS", "ar" to "سجلات الصلوات الفردية"),
        "qada_missed_remaining" to mapOf("en" to "%d missed prayers remaining", "ar" to "متبقٍ %d صلاة فائتة"),
        "qada_all_clear" to mapOf("en" to "All clear! Alhamdulillah", "ar" to "تم قضائها بالكامل! الحمد لله"),
        "qada_configure_dialog_title" to mapOf("en" to "Configure Total Forge Base Target", "ar" to "تحديد خط الأساس لقضاء الصلوات"),
        "qada_configure_dialog_desc" to mapOf("en" to "Enter your starting missed prayer tally baseline. Your completion metrics will calculate from this starting depth.", "ar" to "أدخل خط الأساس لصلواتك الفائتة من البداية لتظهر لك نسب الإنجاز بدقة."),
        "qada_save" to mapOf("en" to "SAVE", "ar" to "حفظ"),
        "qada_cancel" to mapOf("en" to "CANCEL", "ar" to "إلغاء"),

        "fine_tune_locked_theme" to mapOf("en" to "Locked! Achieve Khushu' Level %d to unlock this beautiful theme preset.", "ar" to "مغلق! احصل على مستوى خشوع %d لفتح هذا المظهر الرائع."),
        "fine_tune_locked_icon" to mapOf("en" to "Locked! Achieve Khushu' Level %d to unlock this dynamic app icon preset.", "ar" to "مغلق! احصل على مستوى خشوع %d لفتح أيقونة التطبيق هذه."),
        "fine_tune_change" to mapOf("en" to "CHANGE", "ar" to "تغيير"),
        "fine_tune_search_hint" to mapOf("en" to "Search city or country...", "ar" to "ابحث عن المدينة أو الدولة..."),
        "fine_tune_close" to mapOf("en" to "CLOSE", "ar" to "إغلاق"),
        "fine_tune_hour" to mapOf("en" to "HOUR", "ar" to "ساعة"),
        "fine_tune_minute" to mapOf("en" to "MINUTE", "ar" to "دقيقة"),
        "fine_tune_save" to mapOf("en" to "SAVE", "ar" to "حفظ"),
        "fine_tune_cancel" to mapOf("en" to "CANCEL", "ar" to "إلغاء"),

        "badges_rank_achieve" to mapOf("en" to "Rank & Achievements", "ar" to "المرتبة والإنجازات الروحية"),
        "badges_track_journey" to mapOf("en" to "Track your spiritual journey achievements", "ar" to "تتبع وحقّق مسار إنجازاتك الروحية"),
        "badges_current_rank" to mapOf("en" to "CURRENT RANK", "ar" to "رتبتك الحالية"),
        "badges_active_streak" to mapOf("en" to "ACTIVE STREAK", "ar" to "سلسلة أيامك المتتالية"),
        "badges_active_streak_fmt" to mapOf("en" to "%d Days", "ar" to "%d يومًا"),
        "badges_30_day_status" to mapOf("en" to "30-DAY STATUS", "ar" to "مستوى التوازن لـ ٣٠ يوماً"),
        "badges_consistent_pct" to mapOf("en" to "%d%% Consistent", "ar" to "الانتظام والاستمرارية بنسبة %d%%"),
        "badges_streak_achieve_hdr" to mapOf("en" to "STREAK ACHIEVEMENTS", "ar" to "إنجازات السلسلة المتواصلة"),
        "badges_equipped" to mapOf("en" to "EQUIPPED", "ar" to "مُرتدى"),
        "badges_equip" to mapOf("en" to "EQUIP", "ar" to "ارتداء الوسام"),
        "badges_req_fmt" to mapOf("en" to "Req: %dd", "ar" to "مطلوب: %d يومًا"),
        "badges_consistency_ranks_hdr" to mapOf("en" to "CONSISTENCY RANKS", "ar" to "مراتب الالتزام والصلاة"),
        "badges_active_tier" to mapOf("en" to "ACTIVE", "ar" to "مفعّل"),
        "badges_min_pct_fmt" to mapOf("en" to "Min: %d%%", "ar" to "الحد الأدنى: %d%%"),

        "dhikr_next" to mapOf("en" to "Next Dhikr", "ar" to "الذكر التالي"),
        "dhikr_delete" to mapOf("en" to "Delete Counter", "ar" to "حذف العداد"),
        "dhikr_arabic_req" to mapOf("en" to "Arabic Text (Required)", "ar" to "النص العربي (مطلوب)"),
        "dhikr_arabic_placeholder" to mapOf("en" to "e.g. سُبْحَانَ ٱللَّٰهِ", "ar" to "مثال: سُبْحَانَ ٱللَّٰهِ"),
        "dhikr_trans_opt" to mapOf("en" to "English Translation (Optional)", "ar" to "الترجمة أو التفسير (اختياري)"),
        "dhikr_trans_placeholder" to mapOf("en" to "e.g. Subhanallah", "ar" to "مثال: سبحان الله"),
        "dhikr_target_goal" to mapOf("en" to "Target Goal", "ar" to "العدد المستهدف"),
        "dhikr_create" to mapOf("en" to "Create", "ar" to "إنشاء العداد"),
        "dhikr_cancel" to mapOf("en" to "Cancel", "ar" to "إلغاء"),
        "dhikr_new_tasbih" to mapOf("en" to "New Tasbih Counter", "ar" to "عداد تسبيح جديد"),

        "qibla_sensor_driven" to mapOf("en" to "SENSOR-DRIVEN COMPASS", "ar" to "بوصلة دقيقة بالاستشعار"),
        "qibla_precise_finder" to mapOf("en" to "Precise Qibla Finder", "ar" to "محدد دقيق لقبلة مكة"),
        "qibla_calculated_for" to mapOf("en" to "Calculated for %s: %.1f° %s", "ar" to "محسوبة لمدينة %s: %.1f° %s"),
        "qibla_aligned" to mapOf("en" to "QIBLA ALIGNED", "ar" to "اتجاه القبلة صحيح"),
        "qibla_steer" to mapOf("en" to "STEER PHONE TO %.1f°", "ar" to "وجّه الهاتف نحو %.1f°"),
        "qibla_mecca" to mapOf("en" to "MECCA", "ar" to "مكة المكرمة"),
        "qibla_sim_hdr" to mapOf("en" to "SIMULATOR SYSTEM", "ar" to "نظام محاكاة الاتجاه"),
        "qibla_sim_title" to mapOf("en" to "Touch/Drag Simulation", "ar" to "محاكاة الإمالة باللمس والجر"),
        "qibla_sim_desc" to mapOf("en" to "Drag or swipe across the compass dial above to rotate the phone manually and align with Mecca (~157°).", "ar" to "اسحب أو مرِّر عبر البوصلة أعلاه لتدوير الهاتف يدوياً ومحاكاة محاذاة القبلة ومكة المكرمة."),
        "qibla_sim_azimuth" to mapOf("en" to "SIMULATED PHONE AZIMUTH: %d°", "ar" to "زاوية الهاتف المحاكاة: %d°"),

        "quran_title" to mapOf("en" to "The Holy Quran", "ar" to "القرآن الكريم"),
        "quran_resume" to mapOf("en" to "Resume Reading", "ar" to "متابعة القراءة"),
        "quran_err_fmt" to mapOf("en" to "Error: %s", "ar" to "خطأ: %s"),
        "quran_retry" to mapOf("en" to "Retry", "ar" to "أعد المحاولة"),
        "quran_search_chapters" to mapOf("en" to "Search chapters...", "ar" to "ابحث عن السورة..."),
        "day_mode" to mapOf("en" to "Day Mode", "ar" to "الوضع القراءي المضيء"),
        "night_mode" to mapOf("en" to "Night Mode", "ar" to "الوضع القراءي المظلم"),
        "amoled_black" to mapOf("en" to "AMOLED Black", "ar" to "وضع سواد مطلق"),

        "qada_fard_tag" to mapOf("en" to "FARD", "ar" to "فرض"),

        "PRAYED_ON_TIME" to mapOf("en" to "Mark In-Time", "ar" to "صليتها في وقتها"),
        "PRAYED_LATE" to mapOf("en" to "Mark Late", "ar" to "صليتها متأخراً"),
        "QADA" to mapOf("en" to "Track as Qada (Make-up)", "ar" to "تسجيل كصلاة قضاء"),
        "MISSED" to mapOf("en" to "Track as Missed", "ar" to "تسجيل كفائتة غير مصلّاة"),
        "states_fardh_mandatory" to mapOf("en" to "FARDH (MANDATORY)", "ar" to "الفريضة (مكتوبة)"),
        "log_title_prefix" to mapOf("en" to "LOG %s", "ar" to "تسجيل %s"),
        "log_options_subtitle" to mapOf("en" to "Intention & Log options", "ar" to "خيارات النية والتسجيل اليدوي"),

        // Prayer names localizations
        "FAJR" to mapOf("en" to "Fajr", "ar" to "الفجر"),
        "DHUHR" to mapOf("en" to "Dhuhr", "ar" to "الظهر"),
        "ASR" to mapOf("en" to "Asr", "ar" to "العصر"),
        "MAGHRIB" to mapOf("en" to "Maghrib", "ar" to "المغرب"),
        "ISHA" to mapOf("en" to "Isha", "ar" to "العشاء"),

        "FAJR_SUNNAH_label" to mapOf("en" to "Sunnah (Before)", "ar" to "سنة الفجر"),
        "FAJR_SUNNAH_subtitle" to mapOf("en" to "2 Rakaats • Emphasized", "ar" to "ركعتان • سنة مؤكدة"),
        "FAJR_FARD_label" to mapOf("en" to "Fajr Fardh", "ar" to "فرض الفجر"),
        "FAJR_FARD_subtitle" to mapOf("en" to "2 Rakaats • Mandatory", "ar" to "ركعتان • فرض مكتوب"),
        "DHUHR_SUNNAH_BEFORE_label" to mapOf("en" to "Sunnah (Before)", "ar" to "سنة الظهر القبلية"),
        "DHUHR_SUNNAH_BEFORE_subtitle" to mapOf("en" to "4 Rakaats • Emphasized", "ar" to "٤ ركعات • سنة مؤكدة"),
        "DHUHR_FARD_label" to mapOf("en" to "Dhuhr Fardh", "ar" to "فرض الظُّهر"),
        "DHUHR_FARD_subtitle" to mapOf("en" to "4 Rakaats • Mandatory", "ar" to "٤ ركعات • فرض مكتوب"),
        "DHUHR_SUNNAH_AFTER_label" to mapOf("en" to "Sunnah (After)", "ar" to "سنة الظهر البعدية"),
        "DHUHR_SUNNAH_AFTER_subtitle" to mapOf("en" to "2 Rakaats • Emphasized", "ar" to "ركعتان • سنة مؤكدة"),
        "ASR_FARD_label" to mapOf("en" to "Asr Fardh", "ar" to "فرض العصر"),
        "ASR_FARD_subtitle" to mapOf("en" to "4 Rakaats • Mandatory", "ar" to "٤ ركعات • فرض مكتوب"),
        "MAGHRIB_FARD_label" to mapOf("en" to "Maghrib Fardh", "ar" to "فرض المغرب"),
        "MAGHRIB_FARD_subtitle" to mapOf("en" to "3 Rakaats • Mandatory", "ar" to "٣ ركعات • فرض مكتوب"),
        "MAGHRIB_SUNNAH_label" to mapOf("en" to "Sunnah (After)", "ar" to "سنة المغرب البعدية"),
        "MAGHRIB_SUNNAH_subtitle" to mapOf("en" to "2 Rakaats • Emphasized", "ar" to "ركعتان • سنة مؤكدة"),
        "ISHA_SUNNAH_BEFORE_label" to mapOf("en" to "Sunnah (Before)", "ar" to "سنة العشاء القبلية"),
        "ISHA_SUNNAH_BEFORE_subtitle" to mapOf("en" to "4 Rakaats • Optional", "ar" to "٤ ركعات • سنة مندوبة"),
        "ISHA_FARD_label" to mapOf("en" to "Isha Fardh", "ar" to "فرض العشاء"),
        "ISHA_FARD_subtitle" to mapOf("en" to "4 Rakaats • Mandatory", "ar" to "٤ ركعات • فرض مكتوب"),
        "ISHA_SUNNAH_AFTER_label" to mapOf("en" to "Sunnah (After)", "ar" to "سنة العشاء البعدية"),
        "ISHA_SUNNAH_AFTER_subtitle" to mapOf("en" to "2 Rakaats • Emphasized", "ar" to "ركعتان • سنة مؤكدة"),
        "ISHA_WITR_label" to mapOf("en" to "Witr", "ar" to "صلاة الوتر"),
        "ISHA_WITR_subtitle" to mapOf("en" to "3 Rakaats • Wajib", "ar" to "٣ ركعات • واجب / مؤكدة"),
        "JUMAH_SUNNAH_ENTER_label" to mapOf("en" to "Sunnah (Entering)", "ar" to "تحية المسجد"),
        "JUMAH_SUNNAH_ENTER_subtitle" to mapOf("en" to "2 Rakaats • Tahiyah", "ar" to "ركعتان • سنة دخول المسجد"),
        "JUMAH_SUNNAH_AFTER_label" to mapOf("en" to "Sunnah (After)", "ar" to "سنة الجمعة البعدية"),
        "JUMAH_SUNNAH_AFTER_subtitle" to mapOf("en" to "4 Rakaats • Emphasized", "ar" to "٤ ركعات • سنة مؤكدة")
    )

    fun string(key: String, lang: String, default: String = key): String {
        return translations[key]?.get(lang) ?: translations[key]?.get("en") ?: default
    }
}

val LocalAppLanguage = compositionLocalOf { "en" }

@Composable
@ReadOnlyComposable
fun String.toAppNumerals(): String {
    return this.map { char ->
        when (char) {
            '٠' -> '0'
            '١' -> '1'
            '٢' -> '2'
            '٣' -> '3'
            '٤' -> '4'
            '٥' -> '5'
            '٦' -> '6'
            '٧' -> '7'
            '٨' -> '8'
            '٩' -> '9'
            else -> char
        }
    }.joinToString("")
}

@Composable
@ReadOnlyComposable
fun Int.toAppNumerals(): String = this.toString().toAppNumerals()

@Composable
@ReadOnlyComposable
fun Long.toAppNumerals(): String = this.toString().toAppNumerals()

@Composable
fun Text(
    text: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {},
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    bypassNumerals: Boolean = false
) {
    androidx.compose.material3.Text(
        text = if (bypassNumerals) text else text.toAppNumerals(),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

@Composable
fun Text(
    text: androidx.compose.ui.text.AnnotatedString,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {},
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    bypassNumerals: Boolean = false
) {
    val formattedText = if (bypassNumerals) text.text else text.text.toAppNumerals()
    val builder = androidx.compose.ui.text.AnnotatedString.Builder(formattedText)
    text.spanStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    text.paragraphStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    text.getStringAnnotations(0, text.length).forEach { builder.addStringAnnotation(it.tag, it.item, it.start, it.end) }
    
    androidx.compose.material3.Text(
        text = builder.toAnnotatedString(),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}


