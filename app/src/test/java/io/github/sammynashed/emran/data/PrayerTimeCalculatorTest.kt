package io.github.sammynashed.emran.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Tests for [PrayerTimeCalculator].
 *
 * The calculator had no tests at all, which is how a daylight-saving bug that
 * made prayer times an hour wrong across 77 of the app's 164 cities survived to
 * a release candidate. These tests are structured around that lesson:
 *
 *  1. **Independent cross-check.** Sunrise and sunset are verified against a
 *     separately implemented NOAA solar-position algorithm ([NoaaReference]
 *     below), not against values produced by the code under test. Two
 *     independent derivations agreeing to within a minute is real evidence;
 *     asserting that the implementation matches itself is not.
 *  2. **Invariants over magic numbers.** Prayer ordering, Asr school ordering
 *     and equinox symmetry must hold on every date at every latitude, which
 *     catches whole classes of error that a handful of pinned timestamps miss.
 *  3. **Explicit regression tests for the DST bug**, so it cannot come back.
 */
class PrayerTimeCalculatorTest {

    private val tolerance = Duration.ofMinutes(2)

    // -----------------------------------------------------------------------
    // 1. The daylight-saving regression
    // -----------------------------------------------------------------------

    /**
     * The original bug, stated directly: a fixed UTC offset and a real zone must
     * agree in winter and disagree by exactly one hour in summer.
     */
    @Test
    fun `chicago summer times use daylight offset not standard offset`() {
        val lat = 41.8781
        val lon = -87.6298
        val zone = ZoneId.of("America/Chicago")

        // January: Chicago is on CST (-6), which is what the old code hardcoded.
        val january = PrayerTimeCalculator.calculateTimes(
            date = LocalDate.of(2026, 1, 15),
            latitude = lat, longitude = lon, zone = zone
        )
        @Suppress("DEPRECATION")
        val januaryLegacy = PrayerTimeCalculator.calculateTimes(
            date = LocalDate.of(2026, 1, 15),
            latitude = lat, longitude = lon, timezone = -6.0
        )
        assertEquals(
            "In January the fixed -6 offset is correct, so both paths must agree",
            januaryLegacy.dhuhr, january.dhuhr
        )

        // July: Chicago is on CDT (-5). The old fixed -6 is an hour behind.
        val july = PrayerTimeCalculator.calculateTimes(
            date = LocalDate.of(2026, 7, 15),
            latitude = lat, longitude = lon, zone = zone
        )
        @Suppress("DEPRECATION")
        val julyLegacy = PrayerTimeCalculator.calculateTimes(
            date = LocalDate.of(2026, 7, 15),
            latitude = lat, longitude = lon, timezone = -6.0
        )
        assertEquals(
            "In July the zone-aware result must be exactly one hour later than the old fixed-offset result",
            60L,
            Duration.between(julyLegacy.dhuhr, july.dhuhr).toMinutes()
        )
    }

    /**
     * Southern-hemisphere DST runs the other way. Sydney is UTC+11 in January and
     * UTC+10 in July, so a naive "summer means +1" assumption fails here.
     */
    @Test
    fun `sydney daylight saving runs opposite to the northern hemisphere`() {
        val zone = ZoneId.of("Australia/Sydney")
        val january = PrayerTimeCalculator.calculateTimes(
            LocalDate.of(2026, 1, 15), -33.8688, 151.2093, zone
        )
        val july = PrayerTimeCalculator.calculateTimes(
            LocalDate.of(2026, 7, 15), -33.8688, 151.2093, zone
        )

        assertEquals(11 * 3600, january.zonedTimeOf(PrayerTimeCalculator.Prayer.DHUHR).offset.totalSeconds)
        assertEquals(10 * 3600, july.zonedTimeOf(PrayerTimeCalculator.Prayer.DHUHR).offset.totalSeconds)
    }

    /**
     * The offset must flip across a spring-forward boundary.
     *
     * US DST began 2026-03-08 at 02:00 local. Every Chicago prayer that day falls
     * after 02:00 (Fajr is around 05:30), so the whole day is on CDT, while the
     * previous day is still on CST. A fixed-offset implementation reports the
     * same offset for both, which is precisely the bug this guards against.
     */
    @Test
    fun `offsets flip across a spring forward transition`() {
        val lat = 41.8781
        val lon = -87.6298
        val zone = ZoneId.of("America/Chicago")

        val dayBefore = PrayerTimeCalculator.calculateTimes(LocalDate.of(2026, 3, 7), lat, lon, zone)
        val transitionDay = PrayerTimeCalculator.calculateTimes(LocalDate.of(2026, 3, 8), lat, lon, zone)

        assertPrayersOrdered(dayBefore, "Chicago the day before spring-forward")
        assertPrayersOrdered(transitionDay, "Chicago on spring-forward day")

        assertEquals(
            "The day before the transition is still on CST",
            -6 * 3600,
            dayBefore.zonedTimeOf(PrayerTimeCalculator.Prayer.ISHA).offset.totalSeconds
        )
        for (prayer in PrayerTimeCalculator.Prayer.values()) {
            assertEquals(
                "$prayer on the transition day is after 02:00, so on CDT",
                -5 * 3600,
                transitionDay.zonedTimeOf(prayer).offset.totalSeconds
            )
        }
    }

    /**
     * The autumn transition is the harder direction, because 01:00-02:00 occurs
     * twice and a naive conversion can pick the wrong occurrence. Fajr is well
     * clear of the ambiguous hour, so ordering must simply hold.
     */
    @Test
    fun `fall back transition keeps prayers ordered and on the correct offset`() {
        val zone = ZoneId.of("America/Chicago")
        // US DST ended 2026-11-01 at 02:00 local.
        val transitionDay = PrayerTimeCalculator.calculateTimes(
            LocalDate.of(2026, 11, 1), 41.8781, -87.6298, zone
        )
        assertPrayersOrdered(transitionDay, "Chicago on fall-back day")
        for (prayer in PrayerTimeCalculator.Prayer.values()) {
            assertEquals(
                "$prayer on 2026-11-01 falls after the 02:00 rollback, so on CST",
                -6 * 3600,
                transitionDay.zonedTimeOf(prayer).offset.totalSeconds
            )
        }
    }

    /**
     * Morocco shifts to UTC+0 during Ramadan and back afterwards. This is a real
     * tz-database rule, and it is exactly the kind of thing a hardcoded offset
     * table can never track.
     */
    @Test
    fun `casablanca observes the ramadan offset shift automatically`() {
        val zone = ZoneId.of("Africa/Casablanca")
        val offsets = listOf(
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 6, 15)
        ).map { date ->
            PrayerTimeCalculator.calculateTimes(date, 33.5731, -7.5898, zone)
                .zonedTimeOf(PrayerTimeCalculator.Prayer.DHUHR).offset.totalSeconds
        }
        assertTrue(
            "Casablanca must not have a single fixed offset all year, got $offsets",
            offsets.distinct().size > 1
        )
    }

    // -----------------------------------------------------------------------
    // 2. Independent cross-check of the solar geometry
    // -----------------------------------------------------------------------

    /**
     * Sunrise and sunset from the calculator must match an independently written
     * NOAA implementation. Cities span both hemispheres, both sides of the
     * antimeridian, and the full range of longitudes.
     */
    @Test
    fun `sunrise and sunset match an independent NOAA implementation`() {
        val cities = listOf(
            Triple("Makkah", 21.4225 to 39.8262, "Asia/Riyadh"),
            Triple("Chicago", 41.8781 to -87.6298, "America/Chicago"),
            Triple("London", 51.5074 to -0.1278, "Europe/London"),
            Triple("Jakarta", -6.2088 to 106.8456, "Asia/Jakarta"),
            Triple("Auckland", -36.8485 to 174.7633, "Pacific/Auckland"),
            Triple("Sao Paulo", -23.5558 to -46.6396, "America/Sao_Paulo"),
            Triple("Cairo", 30.0444 to 31.2357, "Africa/Cairo"),
            Triple("Karachi", 24.8607 to 67.0011, "Asia/Karachi")
        )
        val dates = listOf(
            LocalDate.of(2026, 3, 20),  // equinox
            LocalDate.of(2026, 6, 21),  // solstice
            LocalDate.of(2026, 9, 22),  // equinox
            LocalDate.of(2026, 12, 21), // solstice
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 1, 15)
        )

        for ((name, coords, zoneId) in cities) {
            val (lat, lon) = coords
            val zone = ZoneId.of(zoneId)
            for (date in dates) {
                val times = PrayerTimeCalculator.calculateTimes(date, lat, lon, zone)

                val expectedSunrise = NoaaReference.sunrise(date, lat, lon, zone)
                val expectedSunset = NoaaReference.sunset(date, lat, lon, zone)

                assertWithin(
                    "$name sunrise on $date",
                    expectedSunrise,
                    times.zonedTimeOf(PrayerTimeCalculator.Prayer.SUNRISE)
                )
                assertWithin(
                    "$name sunset (Maghrib) on $date",
                    expectedSunset,
                    times.zonedTimeOf(PrayerTimeCalculator.Prayer.MAGHRIB)
                )
            }
        }
    }

    /**
     * Solar noon must sit midway between sunrise and sunset. This is true by
     * definition and independent of any twilight convention, so it is a clean
     * check on the equation-of-time correction.
     */
    @Test
    fun `dhuhr is midway between sunrise and sunset`() {
        val zone = ZoneId.of("Europe/Istanbul")
        var date = LocalDate.of(2026, 1, 1)
        while (date.year == 2026) {
            val t = PrayerTimeCalculator.calculateTimes(date, 41.0082, 28.9784, zone)
            val sunrise = t.instantOf(PrayerTimeCalculator.Prayer.SUNRISE).epochSecond
            val sunset = t.instantOf(PrayerTimeCalculator.Prayer.MAGHRIB).epochSecond
            val dhuhr = t.instantOf(PrayerTimeCalculator.Prayer.DHUHR).epochSecond
            val midpoint = (sunrise + sunset) / 2

            assertTrue(
                "Dhuhr should be the solar transit on $date (off by ${abs(dhuhr - midpoint)}s)",
                abs(dhuhr - midpoint) <= 90
            )
            date = date.plusDays(1)
        }
    }

    /** On an equinox the day is close to 12 hours everywhere outside the poles. */
    @Test
    fun `equinox day length is close to twelve hours at all latitudes`() {
        val date = LocalDate.of(2026, 3, 20)
        for (lat in -60..60 step 10) {
            val t = PrayerTimeCalculator.calculateTimes(
                date, lat.toDouble(), 0.0, ZoneId.of("UTC")
            )
            val dayLength = Duration.between(
                t.instantOf(PrayerTimeCalculator.Prayer.SUNRISE),
                t.instantOf(PrayerTimeCalculator.Prayer.MAGHRIB)
            ).toMinutes()
            // Refraction and the sun's disc make the equinox day slightly longer
            // than 12h, increasingly so towards the poles.
            assertTrue(
                "Equinox day length at latitude $lat was $dayLength minutes",
                dayLength in 715..790
            )
        }
    }

    // -----------------------------------------------------------------------
    // 3. Invariants that must hold everywhere
    // -----------------------------------------------------------------------

    /** Prayers must always be in order, every day of the year, in every city. */
    @Test
    fun `prayers are strictly ordered every day of the year`() {
        val cities = listOf(
            Triple(21.4225, 39.8262, "Asia/Riyadh"),
            Triple(41.8781, -87.6298, "America/Chicago"),
            Triple(51.5074, -0.1278, "Europe/London"),
            Triple(-33.8688, 151.2093, "Australia/Sydney"),
            Triple(1.3521, 103.8198, "Asia/Singapore"),
            Triple(-36.8485, 174.7633, "Pacific/Auckland")
        )
        for ((lat, lon, zoneId) in cities) {
            val zone = ZoneId.of(zoneId)
            var date = LocalDate.of(2026, 1, 1)
            while (date.year == 2026) {
                val t = PrayerTimeCalculator.calculateTimes(date, lat, lon, zone)
                assertPrayersOrdered(t, "$zoneId on $date")
                date = date.plusDays(1)
            }
        }
    }

    /**
     * Every computed time must fall on the requested local date. This is the
     * antimeridian check: near Auckland, Fajr is computed from a UT value that
     * lands on the previous UTC day, and a naive `fixHour` wrap would move it a
     * full day.
     */
    @Test
    fun `all prayers land on the requested local date`() {
        val cities = listOf(
            Triple(-36.8485, 174.7633, "Pacific/Auckland"),
            Triple(21.4225, 39.8262, "Asia/Riyadh"),
            Triple(34.0522, -118.2437, "America/Los_Angeles"),
            Triple(64.1466, -21.9426, "Atlantic/Reykjavik")
        )
        for ((lat, lon, zoneId) in cities) {
            val zone = ZoneId.of(zoneId)
            for (month in 1..12) {
                val date = LocalDate.of(2026, month, 15)
                val t = PrayerTimeCalculator.calculateTimes(date, lat, lon, zone)
                for (prayer in PrayerTimeCalculator.Prayer.values()) {
                    val actual = t.timeOf(prayer).toLocalDate()
                    // Isha can legitimately spill past midnight at high latitude.
                    val acceptable = actual == date || actual == date.plusDays(1)
                    assertTrue(
                        "$zoneId $prayer on $date landed on $actual",
                        acceptable
                    )
                }
            }
        }
    }

    /** Hanafi Asr uses a longer shadow, so it is always later than Shafi'i Asr. */
    @Test
    fun `hanafi asr is always later than shafii asr`() {
        val zone = ZoneId.of("Asia/Karachi")
        var date = LocalDate.of(2026, 1, 1)
        while (date.year == 2026) {
            val shafii = PrayerTimeCalculator.calculateTimes(
                date, 24.8607, 67.0011, zone, school = PrayerTimeCalculator.AsrSchool.SHAFII
            )
            val hanafi = PrayerTimeCalculator.calculateTimes(
                date, 24.8607, 67.0011, zone, school = PrayerTimeCalculator.AsrSchool.HANAFI
            )
            assertTrue(
                "Hanafi Asr must be later on $date",
                hanafi.asr.isAfter(shafii.asr)
            )
            date = date.plusDays(1)
        }
    }

    /** A larger Fajr angle means the sun is further below the horizon, so earlier Fajr. */
    @Test
    fun `larger fajr angle produces an earlier fajr`() {
        val date = LocalDate.of(2026, 5, 10)
        val zone = ZoneId.of("Africa/Cairo")
        val isna = PrayerTimeCalculator.calculateTimes(
            date, 30.0444, 31.2357, zone, method = PrayerTimeCalculator.CalculationMethod.ISNA
        )
        val egyptian = PrayerTimeCalculator.calculateTimes(
            date, 30.0444, 31.2357, zone, method = PrayerTimeCalculator.CalculationMethod.EGYPTIAN
        )
        assertTrue(
            "Egyptian (19.5 deg) Fajr must precede ISNA (15 deg) Fajr",
            egyptian.fajr.isBefore(isna.fajr)
        )
    }

    // -----------------------------------------------------------------------
    // 4. Method-specific behaviour
    // -----------------------------------------------------------------------

    /** Umm al-Qura defines Isha as a fixed 90 minutes after Maghrib. */
    @Test
    fun `umm al qura isha is exactly ninety minutes after maghrib`() {
        val t = PrayerTimeCalculator.calculateTimes(
            LocalDate.of(2026, 5, 10), 21.4225, 39.8262, ZoneId.of("Asia/Riyadh"),
            method = PrayerTimeCalculator.CalculationMethod.UMM_AL_QURA
        )
        assertEquals(90L, Duration.between(t.maghrib, t.isha).toMinutes())
    }

    /**
     * The Ramadan interval is defined but deliberately inert until the Ramadan
     * module lands, so the default must stay at 90 minutes.
     */
    @Test
    fun `umm al qura ramadan interval is opt in`() {
        val date = LocalDate.of(2026, 3, 1)
        val zone = ZoneId.of("Asia/Riyadh")
        val default = PrayerTimeCalculator.calculateTimes(
            date, 21.4225, 39.8262, zone,
            method = PrayerTimeCalculator.CalculationMethod.UMM_AL_QURA
        )
        val ramadan = PrayerTimeCalculator.calculateTimes(
            date, 21.4225, 39.8262, zone,
            method = PrayerTimeCalculator.CalculationMethod.UMM_AL_QURA,
            useRamadanIshaInterval = true
        )
        assertEquals(90L, Duration.between(default.maghrib, default.isha).toMinutes())
        assertEquals(120L, Duration.between(ramadan.maghrib, ramadan.isha).toMinutes())
    }

    /** Ja'fari places Maghrib after sunset, at a solar depression of 4 degrees. */
    @Test
    fun `jafari maghrib falls after sunset`() {
        val t = PrayerTimeCalculator.calculateTimes(
            LocalDate.of(2026, 5, 10), 35.6892, 51.3890, ZoneId.of("Asia/Tehran"),
            method = PrayerTimeCalculator.CalculationMethod.JAFARI
        )
        assertTrue("Ja'fari Maghrib must be after sunrise-symmetric sunset", t.maghrib.isAfter(t.sunrise))
        val standard = PrayerTimeCalculator.calculateTimes(
            LocalDate.of(2026, 5, 10), 35.6892, 51.3890, ZoneId.of("Asia/Tehran"),
            method = PrayerTimeCalculator.CalculationMethod.MWL
        )
        assertTrue(
            "Ja'fari Maghrib is at 4 deg depression, so later than plain sunset",
            t.maghrib.isAfter(standard.maghrib)
        )
    }

    /** CUSTOM must actually honour the supplied angles. */
    @Test
    fun `custom parameters override the method defaults`() {
        val date = LocalDate.of(2026, 5, 10)
        val zone = ZoneId.of("Europe/Berlin")
        val custom = PrayerTimeCalculator.calculateTimes(
            date, 52.52, 13.405, zone,
            method = PrayerTimeCalculator.CalculationMethod.CUSTOM,
            custom = PrayerTimeCalculator.CustomParameters(fajrAngle = 12.0, ishaAngle = 12.0),
            highLatitudeRule = PrayerTimeCalculator.HighLatitudeRule.NONE
        )
        val mwl = PrayerTimeCalculator.calculateTimes(
            date, 52.52, 13.405, zone,
            method = PrayerTimeCalculator.CalculationMethod.MWL,
            highLatitudeRule = PrayerTimeCalculator.HighLatitudeRule.NONE
        )
        assertTrue("A 12 deg Fajr is later than an 18 deg Fajr", custom.fajr.isAfter(mwl.fajr))
        assertTrue("A 12 deg Isha is earlier than a 17 deg Isha", custom.isha.isBefore(mwl.isha))
    }

    /** Manual per-prayer corrections must be applied, and only to their own prayer. */
    @Test
    fun `adjustments shift only the prayer they target`() {
        val date = LocalDate.of(2026, 5, 10)
        val zone = ZoneId.of("Asia/Riyadh")
        val base = PrayerTimeCalculator.calculateTimes(date, 21.4225, 39.8262, zone)
        val adjusted = PrayerTimeCalculator.calculateTimes(
            date, 21.4225, 39.8262, zone,
            adjustments = PrayerTimeCalculator.Adjustments(fajr = -5, isha = 7)
        )
        assertEquals(-5L, Duration.between(base.fajr, adjusted.fajr).toMinutes())
        assertEquals(7L, Duration.between(base.isha, adjusted.isha).toMinutes())
        assertEquals(base.dhuhr, adjusted.dhuhr)
        assertEquals(base.asr, adjusted.asr)
    }

    // -----------------------------------------------------------------------
    // 5. High latitudes
    // -----------------------------------------------------------------------

    /**
     * Tromso is inside the Arctic Circle: in June the sun never sets and Fajr and
     * Isha have no astronomical time. Every rule must still yield real, ordered
     * times rather than NaN or a hardcoded fallback.
     */
    @Test
    fun `high latitude rules produce usable times under the midnight sun`() {
        val date = LocalDate.of(2026, 6, 21)
        val zone = ZoneId.of("Europe/Oslo")
        val tromsoLat = 69.6492
        val tromsoLon = 18.9553

        for (rule in PrayerTimeCalculator.HighLatitudeRule.values()) {
            val t = PrayerTimeCalculator.calculateTimes(
                date, tromsoLat, tromsoLon, zone, highLatitudeRule = rule
            )

            // The specific failure this guards against: NaN propagating through
            // the fallbacks and dumping Fajr, Sunrise, Maghrib and Isha onto the
            // same 12:00 sentinel. Under the midnight sun sunrise and sunset
            // legitimately coincide with solar midnight, so ties are expected --
            // but a tie at *noon* for four prayers is not.
            assertFalse(
                "$rule collapsed prayers onto the noon sentinel: ${
                    PrayerTimeCalculator.Prayer.values().map { it to t.timeOf(it) }
                }",
                listOf(t.fajr, t.sunrise, t.maghrib, t.isha).all { it == t.date.atTime(12, 0) }
            )

            assertPrayersNonDecreasing(t, "Tromso under midnight sun with rule=$rule")
            assertTrue("$rule: the day must be flagged as estimated", t.isEstimated)
        }
    }

    /** Polar night is the mirror case: the sun never rises. */
    @Test
    fun `high latitude rules produce usable times under polar night`() {
        val date = LocalDate.of(2026, 12, 21)
        val zone = ZoneId.of("Europe/Oslo")

        for (rule in PrayerTimeCalculator.HighLatitudeRule.values()) {
            val t = PrayerTimeCalculator.calculateTimes(
                date, 69.6492, 18.9553, zone, highLatitudeRule = rule
            )
            assertPrayersNonDecreasing(t, "Tromso under polar night with rule=$rule")
            assertFalse(
                "$rule: polar night must not report a true sunrise",
                t.hasTrueSunriseAndSunset
            )
        }
    }

    /** Normal latitudes must never be flagged as estimated. */
    @Test
    fun `temperate locations are not flagged as estimated`() {
        for (month in 1..12) {
            val t = PrayerTimeCalculator.calculateTimes(
                LocalDate.of(2026, month, 15), 30.0444, 31.2357, ZoneId.of("Africa/Cairo")
            )
            assertFalse("Cairo in month $month should not be estimated", t.isEstimated)
        }
    }

    /** Below the threshold, AUTOMATIC must change nothing at all. */
    @Test
    fun `automatic rule is a no-op at low latitude`() {
        val date = LocalDate.of(2026, 6, 21)
        val zone = ZoneId.of("Africa/Cairo")
        val automatic = PrayerTimeCalculator.calculateTimes(
            date, 30.0444, 31.2357, zone,
            highLatitudeRule = PrayerTimeCalculator.HighLatitudeRule.AUTOMATIC
        )
        val none = PrayerTimeCalculator.calculateTimes(
            date, 30.0444, 31.2357, zone,
            highLatitudeRule = PrayerTimeCalculator.HighLatitudeRule.NONE
        )
        assertEquals(none.fajr, automatic.fajr)
        assertEquals(none.isha, automatic.isha)
    }

    /** One-seventh keeps Fajr closer to sunrise than middle-of-night does. */
    @Test
    fun `one seventh rule bounds fajr more tightly than middle of night`() {
        val date = LocalDate.of(2026, 6, 21)
        val zone = ZoneId.of("Europe/Stockholm")
        val seventh = PrayerTimeCalculator.calculateTimes(
            date, 59.3293, 18.0686, zone,
            highLatitudeRule = PrayerTimeCalculator.HighLatitudeRule.ONE_SEVENTH_OF_NIGHT
        )
        val middle = PrayerTimeCalculator.calculateTimes(
            date, 59.3293, 18.0686, zone,
            highLatitudeRule = PrayerTimeCalculator.HighLatitudeRule.MIDDLE_OF_NIGHT
        )
        assertTrue(
            "One-seventh Fajr should be later (nearer sunrise) than middle-of-night Fajr",
            !seventh.fajr.isBefore(middle.fajr)
        )
    }

    // -----------------------------------------------------------------------
    // 6. Robustness
    // -----------------------------------------------------------------------

    @Test
    fun `unknown zone ids fall back instead of throwing`() {
        val zone = PrayerTimeCalculator.safeZone("Middle/Earth")
        assertEquals(ZoneId.systemDefault(), zone)
        assertEquals(ZoneId.systemDefault(), PrayerTimeCalculator.safeZone(null))
        assertEquals(ZoneId.systemDefault(), PrayerTimeCalculator.safeZone("  "))
        assertEquals(ZoneId.of("Asia/Riyadh"), PrayerTimeCalculator.safeZone("Asia/Riyadh"))
    }

    /** Every bundled preset must produce a sane day. This links the two files. */
    @Test
    fun `every bundled location preset yields ordered prayer times`() {
        val dates = listOf(
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 6, 21),
            LocalDate.of(2026, 7, 15)
        )
        for (preset in LocationPresets.list) {
            // Resolvable zone id -- guards against a typo in the preset table.
            assertEquals(
                "Preset '${preset.name}' has an unresolvable zone id '${preset.zoneId}'",
                preset.zoneId,
                ZoneId.of(preset.zoneId).id
            )
            for (date in dates) {
                val t = PrayerTimeCalculator.calculateTimes(
                    date, preset.latitude, preset.longitude, preset.zone
                )
                assertPrayersOrdered(t, "${preset.name} on $date")
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun sequenceOf(times: PrayerTimeCalculator.PrayerTimes) = listOf(
        "Fajr" to times.fajr,
        "Sunrise" to times.sunrise,
        "Dhuhr" to times.dhuhr,
        "Asr" to times.asr,
        "Maghrib" to times.maghrib,
        "Isha" to times.isha
    )

    /** Strict ordering, which must hold anywhere the sun actually rises and sets. */
    private fun assertPrayersOrdered(times: PrayerTimeCalculator.PrayerTimes, label: String) {
        val sequence = sequenceOf(times)
        for (i in 0 until sequence.size - 1) {
            val (currentName, current) = sequence[i]
            val (nextName, next) = sequence[i + 1]
            assertTrue(
                "$label: $currentName ($current) must precede $nextName ($next)",
                current.isBefore(next)
            )
        }
    }

    /**
     * Non-decreasing ordering, for inside the polar circles.
     *
     * Ties are correct there rather than a defect: when the sun does not cross
     * the horizon, sunrise, solar noon and sunset genuinely converge on the same
     * instant. What must never happen is a prayer going *backwards*.
     */
    private fun assertPrayersNonDecreasing(times: PrayerTimeCalculator.PrayerTimes, label: String) {
        val sequence = sequenceOf(times)
        for (i in 0 until sequence.size - 1) {
            val (currentName, current) = sequence[i]
            val (nextName, next) = sequence[i + 1]
            assertFalse(
                "$label: $currentName ($current) must not come after $nextName ($next)",
                current.isAfter(next)
            )
        }
    }

    private fun assertWithin(label: String, expected: ZonedDateTime, actual: ZonedDateTime) {
        val delta = Duration.between(expected, actual).abs()
        assertTrue(
            "$label: expected ~$expected but was $actual (off by ${delta.toSeconds()}s)",
            delta <= tolerance
        )
    }

    /**
     * A deliberately separate implementation of NOAA's sunrise/sunset algorithm.
     *
     * This exists so the tests do not validate the production code against its
     * own assumptions. It uses NOAA's polynomial series for the equation of time
     * and solar declination, which is a different formulation from the USNO
     * approximation the calculator uses; agreement to within a minute means both
     * are very likely right.
     */
    private object NoaaReference {

        fun sunrise(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): ZonedDateTime =
            solarEvent(date, lat, lon, zone, rising = true)

        fun sunset(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): ZonedDateTime =
            solarEvent(date, lat, lon, zone, rising = false)

        private fun solarEvent(
            date: LocalDate,
            lat: Double,
            lon: Double,
            zone: ZoneId,
            rising: Boolean
        ): ZonedDateTime {
            // Julian day at 00:00 UT.
            val jd = julianDay(date)

            // Iterate: evaluate the sun's position at the estimated event time.
            var minutesUt = 12.0 * 60.0
            repeat(3) {
                val t = julianCentury(jd + minutesUt / 1440.0)
                val decl = solarDeclination(t)
                val eqTime = equationOfTime(t)
                val ha = hourAngleSunrise(lat, decl)
                val signedHa = if (rising) ha else -ha
                minutesUt = 720.0 - 4.0 * (lon + signedHa) - eqTime
            }

            val instant = date.atStartOfDay(ZoneId.of("UTC")).toInstant()
                .plusSeconds(Math.round(minutesUt * 60.0))
            return instant.atZone(zone)
        }

        private fun julianDay(date: LocalDate): Double {
            var y = date.year
            var m = date.monthValue
            val d = date.dayOfMonth
            if (m <= 2) { y -= 1; m += 12 }
            val a = Math.floor(y / 100.0)
            val b = 2.0 - a + Math.floor(a / 4.0)
            return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5
        }

        private fun julianCentury(jd: Double) = (jd - 2451545.0) / 36525.0

        private fun geomMeanLongSun(t: Double): Double {
            var l = 280.46646 + t * (36000.76983 + t * 0.0003032)
            l %= 360.0
            return if (l < 0) l + 360.0 else l
        }

        private fun geomMeanAnomalySun(t: Double) =
            357.52911 + t * (35999.05029 - 0.0001537 * t)

        private fun eccentricityEarthOrbit(t: Double) =
            0.016708634 - t * (0.000042037 + 0.0000001267 * t)

        private fun sunEqOfCenter(t: Double): Double {
            val m = Math.toRadians(geomMeanAnomalySun(t))
            return sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(2 * m) * (0.019993 - 0.000101 * t) +
                sin(3 * m) * 0.000289
        }

        private fun sunTrueLong(t: Double) = geomMeanLongSun(t) + sunEqOfCenter(t)

        private fun sunApparentLong(t: Double): Double {
            val omega = 125.04 - 1934.136 * t
            return sunTrueLong(t) - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        }

        private fun meanObliquityOfEcliptic(t: Double) =
            23.0 + (26.0 + ((21.448 - t * (46.815 + t * (0.00059 - t * 0.001813)))) / 60.0) / 60.0

        private fun obliquityCorrection(t: Double): Double {
            val omega = 125.04 - 1934.136 * t
            return meanObliquityOfEcliptic(t) + 0.00256 * cos(Math.toRadians(omega))
        }

        fun solarDeclination(t: Double): Double {
            val e = Math.toRadians(obliquityCorrection(t))
            val lambda = Math.toRadians(sunApparentLong(t))
            return Math.toDegrees(asin(sin(e) * sin(lambda)))
        }

        /** Equation of time in minutes. */
        fun equationOfTime(t: Double): Double {
            val epsilon = Math.toRadians(obliquityCorrection(t))
            val l0 = Math.toRadians(geomMeanLongSun(t))
            val e = eccentricityEarthOrbit(t)
            val m = Math.toRadians(geomMeanAnomalySun(t))

            var y = tan(epsilon / 2.0)
            y *= y

            val eTime = y * sin(2.0 * l0) -
                2.0 * e * sin(m) +
                4.0 * e * y * sin(m) * cos(2.0 * l0) -
                0.5 * y * y * sin(4.0 * l0) -
                1.25 * e * e * sin(2.0 * m)

            return Math.toDegrees(eTime) * 4.0
        }

        /** Hour angle of sunrise, in degrees, at the standard -0.833 altitude. */
        fun hourAngleSunrise(lat: Double, declination: Double): Double {
            val latRad = Math.toRadians(lat)
            val declRad = Math.toRadians(declination)
            val cosHa = cos(Math.toRadians(90.833)) / (cos(latRad) * cos(declRad)) -
                tan(latRad) * tan(declRad)
            return Math.toDegrees(acos(cosHa.coerceIn(-1.0, 1.0)))
        }
    }
}
