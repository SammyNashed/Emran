package io.github.sammynashed.emran.data.prayerkit

import io.github.sammynashed.emran.data.prayerkit.RecitationRules.Recitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationRulesTest {
    private fun surah(n: Int, ayat: Int = 5) = Recitation(n, 1, ayat, whole = true)
    private val kursi = Recitation(2, 255, 255, whole = false)
    private val endOfBaqarah = Recitation(2, 285, 286, whole = false)

    private val asr = RecitationRules.unit("ASR_FARD")!!
    private val maghribSunnah = RecitationRules.unit("MAGHRIB_SUNNAH")!!
    private val witr = RecitationRules.unit("ISHA_WITR")!!

    @Test fun fardTakesARecitationOnlyInTheFirstTwoRakahs() {
        assertEquals(listOf(1, 2), asr.rakahsWithRecitation)
        assertEquals(listOf(1, 2), RecitationRules.unit("MAGHRIB_FARD")!!.rakahsWithRecitation)
        assertEquals(listOf(1, 2), RecitationRules.unit("FAJR_FARD")!!.rakahsWithRecitation)
    }

    @Test fun sunnahAndWitrTakeOneInEveryRakah() {
        assertEquals(listOf(1, 2), maghribSunnah.rakahsWithRecitation)
        assertEquals(listOf(1, 2, 3, 4), RecitationRules.unit("DHUHR_SUNNAH_BEFORE")!!.rakahsWithRecitation)
        assertEquals(listOf(1, 2, 3), witr.rakahsWithRecitation)
    }

    @Test fun jumuahIsTwoRakahs() {
        assertEquals(2, RecitationRules.unit("DHUHR_FARD", friday = true)!!.rakahs)
        assertEquals(4, RecitationRules.unit("DHUHR_FARD", friday = false)!!.rakahs)
    }

    @Test fun fatihaIsNeverOffered() {
        assertFalse(RecitationRules.allowed(asr, 1, surah(1, 7), emptyList()))
        assertFalse(RecitationRules.allowed(maghribSunnah, 1, surah(1, 7), emptyList()))
    }

    @Test fun fardSecondRakahOnlyGoesForwardOrRepeats() {
        val first = listOf(surah(112))
        assertTrue(RecitationRules.allowed(asr, 2, surah(113), first))
        assertTrue(RecitationRules.allowed(asr, 2, surah(112), first)) // repeating is permitted
        assertFalse(RecitationRules.allowed(asr, 2, surah(111), first))
        assertFalse(RecitationRules.allowed(asr, 2, kursi, first))
    }

    @Test fun nasIsHiddenInRakahOneOfFardButFineLast() {
        assertFalse(RecitationRules.allowed(asr, 1, surah(114), emptyList()))
        assertTrue(RecitationRules.allowed(asr, 2, surah(114), listOf(surah(113))))
        assertFalse(RecitationRules.allowed(witr, 2, surah(114), listOf(surah(113))))
        assertTrue(RecitationRules.allowed(witr, 3, surah(114), listOf(surah(112), surah(113))))
    }

    @Test fun passagesFollowTheirPlaceInTheMushaf() {
        assertTrue(RecitationRules.allowed(asr, 2, endOfBaqarah, listOf(kursi)))
        assertFalse(RecitationRules.allowed(asr, 2, kursi, listOf(endOfBaqarah)))
        assertTrue(RecitationRules.allowed(asr, 2, surah(3), listOf(kursi)))
    }

    @Test fun sunnahHasNoOrder() {
        assertTrue(RecitationRules.allowed(maghribSunnah, 2, surah(109), listOf(surah(112))))
        assertTrue(RecitationRules.allowed(maghribSunnah, 1, surah(114), emptyList()))
    }

    @Test fun fardRakahThreeTakesNothing() {
        assertFalse(RecitationRules.allowed(asr, 3, surah(113), listOf(surah(110), surah(112))))
    }

    @Test fun skippingExactlyOneShortSurahIsFlagged() {
        assertTrue(RecitationRules.skipsOneSurah(surah(112), surah(114)))
        assertFalse(RecitationRules.skipsOneSurah(surah(112), surah(113)))
        assertFalse(RecitationRules.skipsOneSurah(surah(110), surah(113)))
        assertFalse(RecitationRules.skipsOneSurah(null, surah(114)))
    }
}
