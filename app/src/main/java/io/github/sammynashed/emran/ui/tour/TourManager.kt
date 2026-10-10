package io.github.sammynashed.emran.ui.tour

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TourStepType {
    INFORMATIONAL,
    INTERACTIVE
}

data class TourStep(
    val id: String,
    val title: String, // Simplified to single string for now. Can do bilingual later.
    val description: String,
    val titleAr: String,
    val descriptionAr: String,
    val type: TourStepType,
    val targetRoute: String, // Used to know if we are on the right screen
    /**
     * True for a step that describes something present only in some states. If its target has
     * not appeared once the screen settles, the tour moves on rather than explain what is not there.
     */
    val optional: Boolean = false
)

class TourManager(private val preferences: SharedPreferences) {
    
    private val _isActive = MutableStateFlow(false)
    val isActive = _isActive.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex = _currentStepIndex.asStateFlow()

    private val _successStepId = MutableStateFlow<String?>(null)
    val successStepId = _successStepId.asStateFlow()

    fun triggerSuccess(stepId: String) {
        if (_isActive.value && _successStepId.value == null) {
            val step = steps.getOrNull(_currentStepIndex.value)
            if (step != null && step.id == stepId) {
                _successStepId.value = stepId
            }
        }
    }

    fun clearSuccess() {
        _successStepId.value = null
    }

    val steps = listOf(
        TourStep(
            id = "spiritual_prayer_compass",
            title = "Prayer Times Wheel",
            description = "See upcoming and remaining prayer times at a glance using this beautifully designed spiritual clock.",
            titleAr = "عجلة مواقيت الصلاة",
            descriptionAr = "احرص على أداء الصلاة في وقتها مع هذه العجلة التفاعلية لمتابعة مواقيت اليوم والوقت المتبقي.",
            type = TourStepType.INFORMATIONAL,
            targetRoute = "home"
        ),
        TourStep(
            id = "today_prayers_widget_card",
            title = "Prayer Times",
            description = "This card opens today's prayer times. Each prayer has a bell: turn it off to silence that one adhan.",
            titleAr = "مواقيت الصلاة",
            descriptionAr = "تفتح هذه البطاقة مواقيت الصلاة. لكل صلاة جرس: أطفئه لإسكات أذان تلك الصلاة وحدها.",
            type = TourStepType.INFORMATIONAL,
            targetRoute = "home"
        ),
        TourStep(
            id = "home_widget_qibla",
            title = "Prayer Mat",
            description = "Tap this widget card to enter the interactive Prayer Mat.",
            titleAr = "سجادة الصلاة",
            descriptionAr = "اضغط على بطاقة القبلة لتشغيل سجادة الصلاة التفاعلية وتحديد اتجاه الكعبة الشريفة.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "home"
        ),
        TourStep(
            id = "qibla_prayer_mat_align",
            title = "Align Prayer Mat",
            description = "Slowly rotate your phone. The prayer mat will animate in real-time. When precisely aligned, it will glow and vibrate.",
            titleAr = "محاذاة السجادة",
            descriptionAr = "قم بتدوير جوالك ببطء، ستتحرك السجادة في الوقت الفعلي. عند الضبط الصحيح ستضيء وتهتز.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "compass"
        ),
        TourStep(
            id = "sub_screen_back_button",
            title = "Back to Home",
            description = "Tap back to return to the Home screen.",
            titleAr = "الرجوع للرئيسية",
            descriptionAr = "اضغط على زر الرجوع للرجوع إلى شاشة التطبيق الرئيسية.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "compass"
        ),
        TourStep(
            id = "home_widget_dhikr",
            title = "Tasbeeh & Dhikr",
            description = "Tap this widget card to open the interactive digital rosary.",
            titleAr = "مسبحة الأذكار",
            descriptionAr = "اضغط على هذه البطاقة لفتح المسبحة الإلكترونية ومتابعة تسابيحك واستغفارك.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "home"
        ),
        TourStep(
            id = "dhikr_main_clicker",
            title = "Tap to Count",
            description = "Tap once anywhere inside the glowing circular glass card to increment your tasbeeh count.",
            titleAr = "انقر للتسبيح",
            descriptionAr = "اضغط مرة واحدة داخل الدائرة الزجاجية المضيئة لزيادة العداد بمقدار تسبيحة واحدة.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "dhikr"
        ),
        TourStep(
            id = "dhikr_next_preset",
            title = "Next Preset Dhikr",
            description = "Tap 'Next Dhikr' to switch between classic praises like Al-Hamdulillah, SubhanAllah, and Allahu Akbar.",
            titleAr = "الذكر التالي",
            descriptionAr = "اضغط على 'الذكر التالي' للتبديل بين صيغ الأذكار الكلاسيكية كالحمد لله، سبحان الله، وغيرهما.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "dhikr"
        ),
        TourStep(
            id = "dhikr_fab_add",
            title = "Custom Tasbeeh",
            description = "Tap the (+) button in the bottom corner to add your own customized dhikr counter. You can add more than 10!",
            titleAr = "إضافة ذكر مخصص",
            descriptionAr = "اضغط على زر المجموع (+) لإضافة عداد مخصص للورد أو للاستغفار مع تحديد حد أقصى للتنبيه.",
            type = TourStepType.INFORMATIONAL,
            targetRoute = "dhikr"
        ),
        TourStep(
            id = "sub_screen_back_button",
            title = "Back to Home",
            description = "Tap back to return to the Home dashboard.",
            titleAr = "الرجوع للرئيسية",
            descriptionAr = "اضغط على زر الرجوع للرجوع للرئيسية.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "dhikr"
        ),
        TourStep(
            id = "home_widget_adhkar",
            title = "Adhkar Library",
            description = "Tap this widget card to explore a structured repository of daily supplications.",
            titleAr = "حصن المسلم",
            descriptionAr = "اضغط على بطاقة الأذكار للتصفح من بين مئات الأدعية والأذكار المنظمة لمختلف الأوقات.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "home"
        ),
        TourStep(
            id = "adhkar_first_folder",
            title = "Adhkar Categories",
            description = "Tap the first category to open its supplications. Further down there are 128 more chapters from Hisn al-Muslim, and the icons at the top search everything and open your bookmarks.",
            titleAr = "مجموعات الأذكار",
            descriptionAr = "اضغط على التصنيف الأول لعرض أذكاره. وفي الأسفل ١٢٨ باباً إضافياً من حصن المسلم، وأيقونات الأعلى للبحث في كل شيء وفتح المحفوظات.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "adhkar"
        ),
        TourStep(
            id = "adhkar_first_item",
            title = "Tap to Count, Hold to Read",
            description = "Tapping a supplication starts the masbaha counter for this whole category. Long-press the first one instead to open it full screen. Each card also has bookmark, copy and share buttons.",
            titleAr = "اضغط للعد، واستمر بالضغط للقراءة",
            descriptionAr = "الضغط على أي ذكر يبدأ المسبحة لهذا التصنيف كاملاً. أما الضغط المطوّل على الذكر الأول فيفتح نافذة القراءة الواسعة.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "adhkar_detail"
        ),
        TourStep(
            id = "adhkar_copy_button",
            title = "Copy to Clipboard",
            description = "Tap 'Copy' to copy the supplication securely. This copies the text instantly for you.",
            titleAr = "نسخ الذكر",
            descriptionAr = "اضغط على زر 'نسخ' لنسخ محتوى هذا الدعاء تلقائياً إلى حافظة جوالك لمشاركته.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "adhkar_detail"
        ),
        TourStep(
            id = "sub_screen_back_button",
            title = "Back to Categories",
            description = "Tap back to exit the supplication list.",
            titleAr = "الرجوع للتصنيفات",
            descriptionAr = "اضغط على زر الرجوع للعودة للتصنيف السابق.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "adhkar_detail"
        ),
        TourStep(
            id = "sub_screen_back_button",
            title = "Back to Home",
            description = "Tap back once more to return to the Home Screen.",
            titleAr = "المغادرة للرئيسية",
            descriptionAr = "اضغط على زر الرجوع للعودة بسلام للوحة التحكم الأساسية.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "adhkar"
        ),
        TourStep(
            id = "home_widget_hadith",
            title = "Hadith Library",
            description = "Tap this card to read hadith, search them, and download more collections.",
            titleAr = "مكتبة الأحاديث",
            descriptionAr = "اضغط على بطاقة الأحاديث لقراءة الأحاديث والبحث فيها وتنزيل المزيد من الكتب.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "home"
        ),
        TourStep(
            id = "hadith_first_book",
            title = "Books of the Sunnah",
            description = "Riyad as-Salihin and two collections of forty hadith come with the app. Bigger books such as Bukhari and Muslim are optional downloads, fetched only when you tap. Every hadith has bookmark, copy and share buttons.",
            titleAr = "كتب السنة",
            descriptionAr = "يأتي رياض الصالحين وأربعينان مع التطبيق. أما الكتب الكبيرة كالبخاري ومسلم فتنزيلها اختياري ولا يبدأ إلا عند ضغطك. ولكل حديث أزرار للحفظ والنسخ والمشاركة.",
            type = TourStepType.INFORMATIONAL,
            targetRoute = "hadith"
        ),
        TourStep(
            id = "sub_screen_back_button",
            title = "Back to Home",
            description = "Tap back to return to Home.",
            titleAr = "الرجوع للرئيسية",
            descriptionAr = "اضغط على زر الرجوع للرجوع للرئيسية.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "hadith"
        ),
        TourStep(
            id = "nav_quran",
            title = "Read Holy Quran",
            description = "Tap the Quran icon on the dock navigation menu to enter the reading section.",
            titleAr = "القرآن الكريم",
            descriptionAr = "انقر على أيقونة المصحف الشريف بالأسفل للانتقال لشاشة القراءة تلاوةً وحفظاً.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "home"
        ),
        TourStep(
            id = "quran_mode_cards",
            title = "Two Ways to Read",
            description = "Tilawah opens the mushaf with the tajweed rules coloured in. Hifz lets you recite from memory and watch the words appear as you say them. Pick one, then choose a surah.",
            titleAr = "طريقتان للقراءة",
            descriptionAr = "التلاوة تفتح المصحف مع تلوين أحكام التجويد. والحفظ يتيح لك التلاوة من الذاكرة وتظهر الكلمات كلما نطقتها. اختر إحداهما ثم اختر السورة.",
            type = TourStepType.INFORMATIONAL,
            targetRoute = "quran"
        ),
        TourStep(
            id = "nav_finetune",
            title = "Personalization Settings",
            description = "Finally, click Settings to customize calculation models, language preferences, location tracking, and premium themes.",
            titleAr = "لوحة الإعدادات",
            descriptionAr = "أخيراً، اضغط على تبويب التروس للانتقال للإعدادات وتخصيص الموقع، المذهب، اللغات، وطرق الحساب.",
            type = TourStepType.INTERACTIVE,
            targetRoute = "quran"
        )
    )

    fun startTour() {
        if (!preferences.getBoolean("has_completed_tour", false)) {
            _currentStepIndex.value = 0
            _isActive.value = true
        }
    }

    fun restartTour() {
        _currentStepIndex.value = 0
        _isActive.value = true
        preferences.edit().putBoolean("has_completed_tour", false).apply()
    }

    // Set when a step is skipped rather than completed, so the host can take the user to the
    // screen the *next* step is about. Completing an interactive step moves the app by itself
    // (the user taps the card); skipping it does not, and the tour would then describe
    // screens that are not showing.
    private var skipped = false

    fun skipStep() {
        skipped = true
        nextStep()
    }

    fun consumeSkipFlag(): Boolean = skipped.also { skipped = false }

    fun nextStep() {
        if (_currentStepIndex.value < steps.size - 1) {
            _currentStepIndex.value += 1
        } else {
            completeTour()
        }
    }

    fun completeTour() {
        _isActive.value = false
        preferences.edit().putBoolean("has_completed_tour", true).apply()
    }

    fun skipTour() {
        completeTour()
    }
}
