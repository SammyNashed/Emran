package io.github.sammynashed.emran.tajweed

import androidx.compose.ui.graphics.Color

/**
 * The tajweed rules Emran marks, with the colour each is drawn in.
 *
 * Colours follow the convention readers already know from printed tajweed
 * muṣḥafs - madd in red, ghunnah in green, ikhfāʼ in violet - rather than
 * Emran's own palette, because the point is to be recognisable to someone who
 * has seen a tajweed muṣḥaf before, not to match the app.
 *
 * Each rule carries its own explanation in both languages. A colour with no
 * explanation is decoration; the legend is what makes this teach anything.
 */
enum class TajweedRule(
    val light: Color,
    val dark: Color,
    val nameEn: String,
    val nameAr: String,
    val hintEn: String,
    val hintAr: String,
) {
    MADD(
        light = Color(0xFFC62828),
        dark = Color(0xFFEF9A9A),
        nameEn = "Madd",
        nameAr = "المد",
        hintEn = "Hold the vowel longer than usual.",
        hintAr = "تُمَدُّ الحركة أطول من المعتاد.",
    ),
    GHUNNAH(
        light = Color(0xFF2E7D32),
        dark = Color(0xFFA5D6A7),
        nameEn = "Ghunnah",
        nameAr = "الغنة",
        hintEn = "A doubled nūn or mīm: hold the nasal sound for two counts.",
        hintAr = "نون أو ميم مشددة: تُغَنُّ بمقدار حركتين.",
    ),
    IKHFA(
        light = Color(0xFF6A1B9A),
        dark = Color(0xFFCE93D8),
        nameEn = "Ikhfāʼ",
        nameAr = "الإخفاء",
        hintEn = "Hide the nūn between clear and merged, with a nasal sound.",
        hintAr = "تُخفى النون بين الإظهار والإدغام مع الغنة.",
    ),
    IDGHAM_GHUNNAH(
        light = Color(0xFFEF6C00),
        dark = Color(0xFFFFCC80),
        nameEn = "Idghām with ghunnah",
        nameAr = "إدغام بغنة",
        hintEn = "Merge the nūn into the next letter, keeping the nasal sound.",
        hintAr = "تُدغم النون في الحرف التالي مع بقاء الغنة.",
    ),
    IDGHAM_PLAIN(
        light = Color(0xFF8D6E63),
        dark = Color(0xFFBCAAA4),
        nameEn = "Idghām without ghunnah",
        nameAr = "إدغام بغير غنة",
        hintEn = "Merge the nūn into lām or rāʼ with no nasal sound.",
        hintAr = "تُدغم النون في اللام أو الراء بلا غنة.",
    ),
    IQLAB(
        light = Color(0xFFAD1457),
        dark = Color(0xFFF48FB1),
        nameEn = "Iqlāb",
        nameAr = "الإقلاب",
        hintEn = "The nūn becomes a mīm before bāʼ, with a nasal sound.",
        hintAr = "تُقلب النون ميمًا قبل الباء مع الغنة.",
    ),
    IDHAR(
        light = Color(0xFF0277BD),
        dark = Color(0xFF81D4FA),
        nameEn = "Iẓhār",
        nameAr = "الإظهار",
        hintEn = "Pronounce the nūn clearly, with no nasal hold.",
        hintAr = "تُظهر النون بوضوح بلا غنة.",
    ),
    IKHFA_SHAFAWI(
        light = Color(0xFF7B1FA2),
        dark = Color(0xFFE1BEE7),
        nameEn = "Ikhfāʼ shafawī",
        nameAr = "إخفاء شفوي",
        hintEn = "A mīm sākinah before bāʼ is hidden, with a nasal sound.",
        hintAr = "تُخفى الميم الساكنة قبل الباء مع الغنة.",
    ),
    IDGHAM_SHAFAWI(
        light = Color(0xFF00838F),
        dark = Color(0xFF80DEEA),
        nameEn = "Idghām shafawī",
        nameAr = "إدغام شفوي",
        hintEn = "A mīm sākinah merges into the mīm that follows.",
        hintAr = "تُدغم الميم الساكنة في الميم التي بعدها.",
    ),
    QALQALAH(
        light = Color(0xFF1565C0),
        dark = Color(0xFF90CAF9),
        nameEn = "Qalqalah",
        nameAr = "القلقلة",
        hintEn = "Give the letter a slight echo: ق ط ب ج د.",
        hintAr = "يُقلقل الحرف بصدى خفيف: ق ط ب ج د.",
    ),
    ;

    fun nameFor(lang: String): String = if (lang == "ar") nameAr else nameEn

    fun hintFor(lang: String): String = if (lang == "ar") hintAr else hintEn

    fun colorFor(darkTheme: Boolean): Color = if (darkTheme) dark else light
}
