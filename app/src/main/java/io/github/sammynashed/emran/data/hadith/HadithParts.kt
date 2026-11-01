package io.github.sammynashed.emran.data.hadith

/**
 * A hadith pulled apart for display: the chain of narrators, the line that leads into the words, and the words.
 *
 * Deliberately strict. A wrongly split name is worse than no split at all, so a chain that branches, has a side
 * remark, or holds any word this cannot place as part of a name is kept as a plain [isnad] paragraph. No word of the
 * hadith is ever dropped except the transmission words themselves (حدثنا, عن) between names in a [chain].
 */
class HadithParts(
    /** Narrators in order, when the chain could be split safely. Empty otherwise. */
    val chain: List<String>,
    /** "قال رسول الله ﷺ": the end of the chain. Blank when [chain] is empty. */
    val lead: String,
    /** The chain as written, when it could not be split. Blank when [chain] is used, or the hadith has no chain. */
    val isnad: String,
    val words: String,
) {
    companion object {
        private val DIACRITICS = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
        private fun bare(s: String) = DIACRITICS.replace(s, "").replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي').filter { it.isLetter() }

        /** Words that hand the hadith on, and so separate one narrator from the next. */
        private val MARKERS = setOf(
            "حدثنا", "حدثني", "اخبرنا", "اخبرني", "انبانا", "سمعت", "عن",
        )
        private val OPENERS = setOf("وحدثنا", "وحدثني", "واخبرنا", "واخبرني") + MARKERS

        /** Anything here means the chain is more than a list of names, so it stays a paragraph. */
        private val NOT_A_NAME = setOf(
            "ح", "قال", "قالا", "قالوا", "قالت", "قالتا", "كلاهما", "كلاهم", "جميعا", "يعني", "وهو", "هو", "هي", "ان", "انه",
            "انها", "سمع", "يقول", "تقول", "حدثه", "حدثها", "حدثهم", "اخبره", "اخبرها", "اخبرهم", "به", "مثله", "نحوه",
            "اسناده", "الاسناد", "كان", "كانت", "في", "علي", "من", "الي", "لما", "يوم", "رفعه", "يرفعه", "يبلغ", "لفظ",
            "واللفظ", "حديث", "حديثه", "ذكر", "مرفوعا", "قراءة", "فيما", "قرات", "قري", "اذا", "ثم", "لا", "ما", "له", "لي",
            "لنا", "رجل", "امراة", "بعض", "اصحاب", "شيخ",
        )

        /** Names that really do begin with the letter و, which otherwise marks a second narrator joined on. */
        private val WAW_NAMES = setOf(
            "وكيع", "وهب", "وهيب", "وائل", "واصل", "واقد", "واسع", "وضاح", "وردان", "ورقاء", "وحشي", "وبرة", "وثاب", "وعلة",
        )

        private val HONORIFIC = Regex("[-ـ–—]?\\s*رَ?ضِ?[يى]َ?\\s*[^،\"]{0,6}لل[^،\"]{0,6}\\s*عَ?نْ?[^،\"\\s]{0,9}\\s*[-ـ–—]?")

        /** Ends a chain: the Prophet ﷺ is named. Longest forms first. */
        private val LEADS = listOf(
            "قال قال رسول الله", "قال قال النبي", "ان رسول الله", "ان النبي", "عن رسول الله", "عن النبي",
            "سمعت رسول الله", "سمعت النبي", "قال رسول الله", "قال النبي",
        )

        private const val MAX_NAME_WORDS = 7
        private const val MAX_CHAIN = 10

        /** [reject], for tests, is told why a chain stayed a paragraph. */
        fun of(arabic: String, reject: ((String) -> Unit)? = null): HadithParts {
            val text = arabic.trim()
            val open = text.indexOf('"')
            if (open < 1) return withoutQuotes(text, reject)
            val close = text.lastIndexOf('"')
            val before = text.substring(0, open).trim()
            val inner = if (close > open) text.substring(open + 1, close) else text.substring(open + 1)
            val after = if (close > open) text.substring(close + 1).trim() else ""
            val words = (inner.trim() + if (after.any { it.isLetter() }) " $after" else "").trim()
            if (words.isEmpty()) return HadithParts(emptyList(), "", "", text)
            val split = splitChain(before, reject)
            return if (split != null) HadithParts(split.first, split.second, "", words)
            else HadithParts(emptyList(), "", before.prophetSign(), words)
        }

        /** No quotation marks: the words start after the first "قال رسول الله ﷺ", if there is one. */
        private fun withoutQuotes(text: String, reject: ((String) -> Unit)?): HadithParts {
            val found = PROPHET_SAID.find(text) ?: return HadithParts(emptyList(), "", "", text)
            val before = text.substring(0, found.range.last + 1).trim()
            val words = text.substring(found.range.last + 1).trimStart(' ', ':', '،', '.', '\u200F').trim()
            if (words.isEmpty()) return HadithParts(emptyList(), "", "", text)
            val split = splitChain(before, reject)
            return if (split != null) HadithParts(split.first, split.second, "", words)
            else HadithParts(emptyList(), "", before.prophetSign(), words)
        }

        private const val MARK = "[\\u064B-\\u065F\\u0670]*"
        private val PBUH = "ص${MARK}ل${MARK}[ىي]${MARK}\\s+(?:ا${MARK})?ل${MARK}ل${MARK}ه${MARK}\\s+ع${MARK}ل${MARK}ي${MARK}ه${MARK}\\s+و${MARK}س${MARK}ل${MARK}م${MARK}"
        private val PBUH_RE = Regex(PBUH)
        private val PROPHET_SAID = Regex("ق${MARK}ا${MARK}ل${MARK}\\s+(?:ق${MARK}ا${MARK}ل${MARK}\\s+)?(?:ر${MARK}س${MARK}و${MARK}ل${MARK}\\s+ا${MARK}ل${MARK}ل${MARK}ه${MARK}|ا${MARK}ل${MARK}ن${MARK}ب${MARK}ي${MARK})\\s+$PBUH(?:\\s*:)?")

        internal fun withSign(s: String) = PBUH_RE.replace(s, "ﷺ")
        private fun String.prophetSign() = withSign(this)

        /** The chain's names and lead, or null when it cannot be told apart with certainty. */
        private fun splitChain(isnad: String, reject: ((String) -> Unit)?): Pair<List<String>, String>? {
            fun no(why: String): Pair<List<String>, String>? { reject?.invoke(why); return null }
            val clean = HONORIFIC.replace(isnad, " ").replace("،", " ").replace(":", " ").replace("‏", " ")
                .replace(Regex("\\s+"), " ").trim()
            if (clean.any { it == '-' || it == 'ـ' || it == '(' || it == '[' || it == '.' }) return no("punctuation")
            val tokens = clean.split(' ')
            val bareTokens = tokens.map(::bare)

            // Where the Prophet ﷺ is named, as the very end of the text.
            var leadAt = -1
            for (lead in LEADS) {
                val parts = lead.split(' ')
                val from = tokens.size - parts.size - 5
                for (i in maxOf(from, 0)..tokens.size - parts.size) {
                    if ((0 until parts.size).all { bareTokens[i + it] == parts[it] }) { leadAt = i; break }
                }
                if (leadAt >= 0) break
            }
            if (leadAt < 2) return no("no lead")
            val leadTail = bareTokens.subList(leadAt, tokens.size).joinToString(" ")
            if (!leadTail.matches(LEAD_OK)) return no("lead shape: $leadTail")

            var end = leadAt
            // "عن أبي هريرة قال قال رسول الله": the first قال belongs to the last narrator.
            if (end > 0 && bareTokens[end - 1] == "قال") end--

            if (bareTokens[0] !in OPENERS) return no("opens with ${bareTokens[0]}")
            val names = ArrayList<String>()
            var cur = ArrayList<String>()
            for (i in 0 until end) {
                val b = bareTokens[i]
                val marker = b in MARKERS || (i == 0 && b in OPENERS)
                if (marker) {
                    if (cur.isNotEmpty()) { names += cur.joinToString(" "); cur = ArrayList() }
                    continue
                }
                if (b == "قال" && i + 1 < end && bareTokens[i + 1] in MARKERS) continue // Bukhari: "قال : حدثنا"
                if (b.isEmpty() || b in NOT_A_NAME) return no("word $b")
                if (b.startsWith("و") && b !in WAW_NAMES) return no("waw $b")
                cur += tokens[i]
                if (cur.size > MAX_NAME_WORDS) return no("long name")
            }
            if (cur.isNotEmpty()) names += cur.joinToString(" ")
            if (names.size < 2 || names.size > MAX_CHAIN) return no("chain size")
            val lead = tokens.subList(if (bareTokens[leadAt] == "قال" && leadTail.startsWith("قال قال")) leadAt + 1 else leadAt, tokens.size)
                .joinToString(" ").prophetSign()
            return names to lead
        }

        private val LEAD_OK = Regex("^(قال |ان |عن |سمعت )*(رسول الله|النبي)( صلي الله عليه وسلم)?( قال| يقول| انه قال)?$")
    }
}
