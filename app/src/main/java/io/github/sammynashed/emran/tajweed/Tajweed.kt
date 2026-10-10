package io.github.sammynashed.emran.tajweed

/**
 * Colour-coded tajweed rules for the Uthmani text Emran bundles.
 *
 * ## What this is, and what it deliberately is not
 *
 * This marks up the *written* text according to the rules of tajweed. It is a
 * reading aid: it shows where a rule applies so the reader can learn to apply
 * it. It listens to nothing and judges nobody.
 *
 * That distinction is the whole reason this exists. RATL's `SPEC.md` §0.1 rules
 * tajweed *diagnosis* out of scope, because phoneme-level assessment sits at
 * roughly coin-flip quality (IQRA 2026 baseline F1 = 0.4414) and "shipping a
 * tajweed verdict at that quality in a religious application is worse than
 * shipping nothing". Marking the text is a different problem entirely: the
 * rules below are decided by orthography, not by inference, so each one is
 * either right or a bug - never a guess.
 *
 * ## What is covered
 *
 * The rules whose triggers are unambiguous in the Uthmani script:
 * nūn sākinah and tanwīn (iẓhār, idghām, iqlāb, ikhfāʼ), mīm sākinah, ghunnah
 * on a doubled nūn or mīm, qalqalah, and madd where the text carries an
 * explicit maddah.
 *
 * Deliberately **not** covered: the finer madd gradations that depend on stop
 * position and recitation style, and anything requiring a qirāʼah-specific
 * judgement. A rule that cannot be decided from the text alone is left unmarked
 * rather than approximated - an unmarked letter teaches nothing, but a wrongly
 * marked one teaches something false.
 */
object Tajweed {

    // ---- Arabic orthography -------------------------------------------------

    private const val SHADDA = 'ّ'

    /**
     * Sukūn, both spellings.
     *
     * The bundled text writes it as U+06E1 (small high dotless head of khah),
     * the Uthmani convention, not the U+0652 most code expects. Both appear in
     * the corpus, so both count.
     */
    private val SUKUN = setOf('ْ', 'ۡ')

    /** Fatḥatān, ḍammatān, kasratān - a tanwīn behaves as a nūn sākinah. */
    private val TANWEEN = setOf('ً', 'ٌ', 'ٍ')

    /** Short vowels; their presence proves a letter is *not* sākin. */
    private val HARAKAT = setOf('َ', 'ُ', 'ِ')

    /** The maddah sign, which marks an obligatory lengthening. */
    private const val MADDAH = 'ٓ'

    /** Small high mīm: the script's own iqlāb marker. */
    private const val SMALL_HIGH_MEEM = 'ۢ'

    private const val NOON = 'ن'
    private const val MEEM = 'م'
    private const val BAA = 'ب'

    /** Letters of madd. */
    private val MADD_LETTERS = setOf('ا', 'و', 'ي', 'ى', 'ٰ')

    /** Throat letters - nūn sākinah before one of these is pronounced clearly. */
    private val IDHAR_LETTERS = setOf(
        'ء', 'أ', 'ؤ', 'إ', 'ئ', // hamza forms
        'ه', 'ع', 'ح', 'غ', 'خ',
    )

    /** Idghām with ghunnah: ي ن م و. */
    private val IDGHAM_GHUNNAH_LETTERS = setOf('ي', 'ن', 'م', 'و')

    /** Idghām without ghunnah: ل ر. */
    private val IDGHAM_PLAIN_LETTERS = setOf('ل', 'ر')

    /** Qalqalah letters: ق ط ب ج د. */
    private val QALQALAH_LETTERS = setOf('ق', 'ط', 'ب', 'ج', 'د')

    /**
     * Everything left over triggers ikhfāʼ - the fifteen letters that are
     * neither throat letters, nor idghām letters, nor bāʼ.
     */
    private val ARABIC_LETTERS = ('ء'..'ي').toSet() + setOf('ٱ', 'ٰ')

    private fun isMark(c: Char): Boolean =
        c in 'ً'..'ٟ' || c == 'ٰ' || c in 'ۖ'..'ۭ'

    private fun isLetter(c: Char): Boolean =
        (c in 'ء'..'ي' || c == 'ٱ') && !isMark(c)

    // ---- Structure ----------------------------------------------------------

    /** One written letter together with every mark hanging off it. */
    private data class Unit(
        val letter: Char,
        val start: Int,
        val end: Int,
        val marks: Set<Char>,
    ) {
        val isShadda get() = SHADDA in marks
        val hasTanween get() = marks.any { it in TANWEEN }
        val hasVowel get() = marks.any { it in HARAKAT }
        val hasMaddah get() = MADDAH in marks
        val hasSmallMeem get() = SMALL_HIGH_MEEM in marks

        /**
         * Sākin: carrying a sukūn, or carrying no vowel at all.
         *
         * The second half is not a shortcut. The text is fully vocalised, so a
         * consonant with no ḥarakah, no tanwīn and no shadda is sākin by
         * omission - and the Uthmani script relies on that. In iqlāb it writes
         * the small high mīm *instead of* the sukūn (`مِنۢ بَعۡدِ`), so a rule
         * that insists on an explicit sukūn finds none of the 510 iqlābs in the
         * Qur'an. Requiring one was exactly that bug.
         */
        val isSakin
            get() = marks.any { it in SUKUN } || (!hasVowel && !hasTanween && !isShadda)
    }

    private fun split(text: String): List<Unit> {
        val units = mutableListOf<Unit>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (!isLetter(c)) {
                i++
                continue
            }
            val start = i
            val marks = mutableSetOf<Char>()
            var j = i + 1
            while (j < text.length && isMark(text[j])) {
                marks += text[j]
                j++
            }
            units += Unit(c, start, j, marks)
            i = j
        }
        return units
    }

    // ---- The rules ----------------------------------------------------------

    /**
     * Every tajweed span in [verse], in document order.
     *
     * Spans cover the trigger letter and its marks. Where two rules could claim
     * the same letter the more specific one wins, so nothing is marked twice.
     */
    fun annotate(verse: String): List<TajweedSpan> {
        if (verse.isBlank()) return emptyList()
        val units = split(verse)
        val spans = mutableListOf<TajweedSpan>()
        val claimed = mutableSetOf<Int>()

        fun claim(index: Int, from: Int, to: Int, rule: TajweedRule) {
            if (index in claimed) return
            claimed += index
            spans += TajweedSpan(from, to, rule)
        }

        units.forEachIndexed { i, u ->
            val next = units.getOrNull(i + 1)

            // --- iqlab, on the script's own authority ---
            // The small high mim is not a hint to be corroborated: it is where
            // the orthography records the rule outright. For a tanwin before
            // ba' it is the *only* evidence left, because the spelling drops
            // the second vowel stroke that would otherwise mark the tanwin
            // (`أَلِيمُۢ بِمَا`). No structural test can recover that.
            if (u.hasSmallMeem) {
                claim(i, u.start, u.end, TajweedRule.IQLAB)
                return@forEachIndexed
            }

            // --- ghunnah: a doubled nun or mim is held, wherever it sits ---
            // Tested after iqlab on purpose. Five letters in the Qur'an are
            // both a doubled nun/mim and an iqlab (`صُمُّۢ بُكۡمٌ`, `غَمَّۢا بِغَمّٖ`),
            // and only one rule can own a letter. Iqlab wins because it is
            // pronounced with a ghunnah anyway, so nothing is lost by naming
            // the more specific rule - whereas ghunnah first silently swallowed
            // all five.
            if (u.isShadda && (u.letter == NOON || u.letter == MEEM)) {
                claim(i, u.start, u.end, TajweedRule.GHUNNAH)
                return@forEachIndexed
            }

            // --- nun sakinah and tanwin ---
            // A tanwin sits on THIS letter but behaves as a nun after it, so
            // both cases look one letter ahead for the trigger.
            val actsAsNoonSakinah =
                (u.letter == NOON && u.isSakin && !u.isShadda) || u.hasTanween
            if (actsAsNoonSakinah && next != null) {
                val rule = when {
                    next.letter == BAA -> TajweedRule.IQLAB
                    next.letter in IDHAR_LETTERS -> TajweedRule.IDHAR
                    next.letter in IDGHAM_GHUNNAH_LETTERS -> TajweedRule.IDGHAM_GHUNNAH
                    next.letter in IDGHAM_PLAIN_LETTERS -> TajweedRule.IDGHAM_PLAIN
                    next.letter in ARABIC_LETTERS -> TajweedRule.IKHFA
                    else -> null
                }
                if (rule != null) {
                    claim(i, u.start, u.end, rule)
                    return@forEachIndexed
                }
            }

            // --- mim sakinah ---
            if (u.letter == MEEM && u.isSakin && !u.isShadda && next != null) {
                val rule = when (next.letter) {
                    BAA -> TajweedRule.IKHFA_SHAFAWI
                    MEEM -> TajweedRule.IDGHAM_SHAFAWI
                    else -> null
                }
                if (rule != null) {
                    claim(i, u.start, u.end, rule)
                    return@forEachIndexed
                }
            }

            // --- madd: only where the text says so outright ---
            if (u.hasMaddah || (u.letter in MADD_LETTERS && next?.hasMaddah == true)) {
                claim(i, u.start, u.end, TajweedRule.MADD)
                return@forEachIndexed
            }

            // --- qalqalah: an echoing letter with no vowel of its own ---
            if (u.letter in QALQALAH_LETTERS && u.isSakin && !u.hasVowel) {
                claim(i, u.start, u.end, TajweedRule.QALQALAH)
            }
        }

        return spans.sortedBy { it.start }
    }
}

/** A run of [TajweedSpan.rule] covering `[start, end)` of the verse. */
data class TajweedSpan(val start: Int, val end: Int, val rule: TajweedRule)
