package io.github.sammynashed.emran.data

import java.time.Duration
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diyanet (Turkey) publishes its times with fixed "temkin" minutes on top of the 18/17 degree angles. Without
 * them Emran was 5-7 minutes off on sunrise, Dhuhr, Asr and Maghrib in Istanbul. Reference: Aladhan method 13
 * (Diyanet), Istanbul, 1 November 2026.
 */
class MethodTemkinTest {
    @Test
    fun diyanetMatchesItsPublishedTimesInIstanbul() {
        val t = PrayerTimeCalculator.calculateTimes(
            LocalDate.of(2026, 11, 1), 41.0082, 28.9784, "Europe/Istanbul",
            PrayerTimeCalculator.CalculationMethod.TURKEY, PrayerTimeCalculator.AsrSchool.SHAFII,
        )
        val reference = mapOf(
            "fajr" to (t.fajr to "06:02"), "sunrise" to (t.sunrise to "07:28"), "dhuhr" to (t.dhuhr to "12:53"),
            "asr" to (t.asr to "15:41"), "maghrib" to (t.maghrib to "18:07"), "isha" to (t.isha to "19:28"),
        )
        for ((name, pair) in reference) {
            val (ours, theirs) = pair
            val (h, m) = theirs.split(":").map { it.toInt() }
            val diff = Duration.between(ours.toLocalDate().atTime(h, m), ours).toMinutes()
            assertTrue("$name: Emran ${ours.toLocalTime()} vs Diyanet $theirs", abs(diff) <= 1)
        }
    }
}
