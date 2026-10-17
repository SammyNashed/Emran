package io.github.sammynashed.emran.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Astronomical prayer time calculation.
 *
 * ## Why this works in terms of instants rather than clock offsets
 *
 * An earlier version of this file took a `timezone: Double` (for example `-6.0`
 * for Chicago) and produced a [LocalDateTime] directly from it. That is wrong for
 * roughly half the year anywhere that observes daylight saving time: Chicago is
 * UTC-6 in January and UTC-5 in July, so every prayer time and every adhan alarm
 * was an hour late all summer. The bug was invisible in Aleppo, where Emran was
 * developed, because Syria abolished DST in 2022.
 *
 * The fix is to never handle a UTC offset as a number at all. Solar events are
 * computed as absolute instants in UT, and only converted to wall-clock time at
 * the very end via a [ZoneId] such as `America/Chicago`. The JVM's tz database
 * then applies whatever offset was actually in force on that date, including
 * historical rule changes, and including days on which the offset itself changes
 * partway through. Fajr on a spring-forward morning is computed before the
 * transition and Isha after it, automatically.
 *
 * All intermediate arithmetic is done in "UT hours relative to 00:00 UT on the
 * requested date". Those values are deliberately *not* wrapped into `[0, 24)`:
 * for a location like Auckland, Fajr legitimately falls on the previous UT day,
 * and wrapping it would move the prayer 24 hours. Letting the value go negative
 * and adding it as a duration keeps the day boundary correct everywhere.
 *
 * Solar position uses the low-precision USNO approximation, which is accurate to
 * well under a minute of time for any year this app will plausibly run in.
 */
object PrayerTimeCalculator {

    const val DEFAULT_LATITUDE = 36.2021
    const val DEFAULT_LONGITUDE = 37.1343

    /** Aleppo. Syria has not observed DST since 2022, so this zone is a fixed +03. */
    const val DEFAULT_ZONE_ID = "Asia/Damascus"

    val DEFAULT_ZONE: ZoneId = ZoneId.of(DEFAULT_ZONE_ID)

    /** The standard depression of the sun's upper limb at sunrise/sunset, including refraction. */
    private const val SUNRISE_ANGLE = 0.833

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    /**
     * Twilight conventions published by the major calculation authorities.
     *
     * [ishaAngle] and [ishaIntervalMinutes] are mutually exclusive: a method
     * either defines Isha by solar depression, or as a fixed interval after
     * Maghrib. [maghribAngle] is zero for every Sunni method (Maghrib is sunset)
     * and non-zero only for the Ja'fari/Tehran conventions.
     */
    enum class CalculationMethod(
        val displayName: String,
        val fajrAngle: Double,
        val ishaAngle: Double,
        val ishaIntervalMinutes: Int = 0,
        val maghribAngle: Double = 0.0,
        /**
         * Umm al-Qura extends the Isha interval to 120 minutes during Ramadan.
         * Wired but inert: see the `useRamadanIshaInterval` parameter on
         * [calculateTimes], which defaults to false until the Ramadan module lands.
         */
        val ramadanIshaIntervalMinutes: Int = 0
    ) {
        MWL("Muslim World League", 18.0, 17.0),
        ISNA("Islamic Society of North America", 15.0, 15.0),
        EGYPTIAN("Egyptian General Authority of Survey", 19.5, 17.5),
        KARACHI("University of Islamic Sciences, Karachi", 18.0, 18.0),
        UMM_AL_QURA(
            displayName = "Umm al-Qura, Makkah",
            fajrAngle = 18.5,
            ishaAngle = 0.0,
            ishaIntervalMinutes = 90,
            ramadanIshaIntervalMinutes = 120
        ),
        DUBAI("Dubai (UAE)", 18.2, 18.2),
        QATAR("Qatar", 18.0, 0.0, ishaIntervalMinutes = 90),
        KUWAIT("Kuwait", 18.0, 17.5),
        SINGAPORE("Singapore (MUIS)", 20.0, 18.0),
        TURKEY("Diyanet, Turkey", 18.0, 17.0),
        TEHRAN(
            displayName = "Institute of Geophysics, Tehran",
            fajrAngle = 17.7,
            ishaAngle = 14.0,
            maghribAngle = 4.5
        ),
        JAFARI(
            displayName = "Shia Ithna Ashari (Ja'fari)",
            fajrAngle = 16.0,
            ishaAngle = 14.0,
            maghribAngle = 4.0
        ),

        /**
         * User-defined angles. The enum constant carries the MWL values purely as
         * a fallback; the real angles arrive via [CustomParameters].
         */
        CUSTOM("Custom", 18.0, 17.0);
    }

    /**
     * Overrides applied when [CalculationMethod.CUSTOM] is selected.
     * Setting [ishaIntervalMinutes] above zero switches Isha to interval mode.
     */
    data class CustomParameters(
        val fajrAngle: Double = 18.0,
        val ishaAngle: Double = 17.0,
        val ishaIntervalMinutes: Int = 0,
        val maghribAngle: Double = 0.0
    )

    /** Shadow-length multiplier defining the start of Asr. */
    enum class AsrSchool(val displayName: String, val shadowRatio: Double) {
        SHAFII("Shafi'i, Maliki, Hanbali", 1.0),
        HANAFI("Hanafi", 2.0)
    }

    /**
     * What to do at latitudes where the sun never reaches the Fajr or Isha
     * depression angle, so those prayers have no astronomical time at all.
     *
     * Above roughly 48 degrees this happens for part of the year, and above the
     * Arctic/Antarctic circles the sun may not rise or set for months. Without a
     * rule the calculation simply has no answer, which is how the previous
     * implementation ended up returning a hardcoded fallback and displaying
     * nonsense to users in Stockholm, Edinburgh and Toronto each summer.
     *
     * Each rule bounds Fajr and Isha to a fraction of the night, measured from
     * sunrise and sunset respectively. Which one is correct is a fiqh question,
     * not an astronomical one, so this is exposed as a user setting.
     */
    enum class HighLatitudeRule(val displayName: String) {
        /** No adjustment. Fajr/Isha may be unavailable; the fields become null-equivalent fallbacks. */
        NONE("None (astronomical only)"),

        /** Night is split in half: Fajr no earlier than midnight, Isha no later than midnight. */
        MIDDLE_OF_NIGHT("Middle of the night"),

        /** Fajr in the last seventh of the night, Isha in the first seventh. */
        ONE_SEVENTH_OF_NIGHT("One-seventh of the night"),

        /** Night is divided in proportion to the method's own twilight angles. */
        ANGLE_BASED("Angle-based (proportional)"),

        /** Applies [ANGLE_BASED] beyond 48 degrees of latitude, and [NONE] below it. */
        AUTOMATIC("Automatic");
    }

    /** Per-prayer manual corrections in minutes, as configured in Fine-Tune. */
    data class Adjustments(
        val fajr: Int = 0,
        val sunrise: Int = 0,
        val dhuhr: Int = 0,
        val asr: Int = 0,
        val maghrib: Int = 0,
        val isha: Int = 0
    ) {
        companion object {
            val NONE = Adjustments()

            /** Builds adjustments from the `"FAJR" -> minutes` map the ViewModel persists. */
            fun fromMap(map: Map<String, Int>) = Adjustments(
                fajr = map["FAJR"] ?: 0,
                sunrise = map["SUNRISE"] ?: 0,
                dhuhr = map["DHUHR"] ?: 0,
                asr = map["ASR"] ?: 0,
                maghrib = map["MAGHRIB"] ?: 0,
                isha = map["ISHA"] ?: 0
            )
        }
    }

    enum class Prayer { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }

    // -----------------------------------------------------------------------
    // Result
    // -----------------------------------------------------------------------

    /**
     * Prayer times for one day at one location.
     *
     * The [LocalDateTime] fields are wall-clock times **in [zone]**, which is not
     * necessarily the device's zone: a traveller can keep Emran set to their home
     * city. Anything that needs an absolute moment -- alarm scheduling, countdown
     * arithmetic, widget refresh -- must go through [zonedTimeOf] or [instantOf]
     * rather than assuming `ZoneId.systemDefault()`.
     */
    data class PrayerTimes(
        val fajr: LocalDateTime,
        val sunrise: LocalDateTime,
        val dhuhr: LocalDateTime,
        val asr: LocalDateTime,
        val maghrib: LocalDateTime,
        val isha: LocalDateTime,
        val date: LocalDate,
        val zone: ZoneId,
        /**
         * False inside the polar circles, where the sun does not cross the
         * horizon and sunrise, Dhuhr and sunset can coincide. The UI should warn
         * that the displayed times are a convention rather than an observation.
         */
        val hasTrueSunriseAndSunset: Boolean = true,
        /**
         * False when the sun never reaches the Fajr or Isha depression angle, so
         * those two times come from the selected [HighLatitudeRule] rather than
         * from astronomy.
         */
        val twilightIsAstronomical: Boolean = true
    ) {
        /** True when any displayed time is a convention rather than an observation. */
        val isEstimated: Boolean get() = !hasTrueSunriseAndSunset || !twilightIsAstronomical

        fun timeOf(prayer: Prayer): LocalDateTime = when (prayer) {
            Prayer.FAJR -> fajr
            Prayer.SUNRISE -> sunrise
            Prayer.DHUHR -> dhuhr
            Prayer.ASR -> asr
            Prayer.MAGHRIB -> maghrib
            Prayer.ISHA -> isha
        }

        /**
         * Resolves a prayer to an absolute moment.
         *
         * On the morning a zone springs forward, a computed time can land in the
         * hour that does not exist locally. [ZonedDateTime.of] resolves such gaps
         * forward rather than throwing, which is the behaviour we want: the adhan
         * fires at the first real instant at or after the astronomical time.
         */
        fun zonedTimeOf(prayer: Prayer): ZonedDateTime = timeOf(prayer).atZone(zone)

        fun instantOf(prayer: Prayer): Instant = zonedTimeOf(prayer).toInstant()

        fun epochMillisOf(prayer: Prayer): Long = instantOf(prayer).toEpochMilli()

        /** All five obligatory prayers in order, excluding sunrise. */
        fun fardPrayers(): List<Pair<Prayer, LocalDateTime>> = listOf(
            Prayer.FAJR to fajr,
            Prayer.DHUHR to dhuhr,
            Prayer.ASR to asr,
            Prayer.MAGHRIB to maghrib,
            Prayer.ISHA to isha
        )

        /** Applies per-prayer minute corrections, preserving [date] and [zone]. */
        fun withAdjustments(adjustments: Adjustments): PrayerTimes = copy(
            fajr = fajr.plusMinutes(adjustments.fajr.toLong()),
            sunrise = sunrise.plusMinutes(adjustments.sunrise.toLong()),
            dhuhr = dhuhr.plusMinutes(adjustments.dhuhr.toLong()),
            asr = asr.plusMinutes(adjustments.asr.toLong()),
            maghrib = maghrib.plusMinutes(adjustments.maghrib.toLong()),
            isha = isha.plusMinutes(adjustments.isha.toLong())
        )
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Computes the six daily solar events for [date] as observed at
     * ([latitude], [longitude]), expressed in [zone].
     *
     * [date] is the *local* date at the location, not a UTC date.
     */
    fun calculateTimes(
        date: LocalDate,
        latitude: Double = DEFAULT_LATITUDE,
        longitude: Double = DEFAULT_LONGITUDE,
        zone: ZoneId = DEFAULT_ZONE,
        method: CalculationMethod = CalculationMethod.MWL,
        school: AsrSchool = AsrSchool.SHAFII,
        highLatitudeRule: HighLatitudeRule = HighLatitudeRule.AUTOMATIC,
        adjustments: Adjustments = Adjustments.NONE,
        custom: CustomParameters? = null,
        useRamadanIshaInterval: Boolean = false
    ): PrayerTimes {
        // CUSTOM draws its angles from `custom`; every other method uses its own
        // published constants. A null `custom` under CUSTOM falls back to MWL-ish
        // defaults rather than failing.
        val effectiveCustom = custom.takeIf { method == CalculationMethod.CUSTOM }
        val fajrAngle = effectiveCustom?.fajrAngle ?: method.fajrAngle
        val ishaAngle = effectiveCustom?.ishaAngle ?: method.ishaAngle
        val maghribAngle = effectiveCustom?.maghribAngle ?: method.maghribAngle
        val ishaInterval = when {
            effectiveCustom != null -> effectiveCustom.ishaIntervalMinutes
            useRamadanIshaInterval && method.ramadanIshaIntervalMinutes > 0 ->
                method.ramadanIshaIntervalMinutes
            else -> method.ishaIntervalMinutes
        }

        // ---- Solar geometry, iterated so the declination is evaluated at the
        // ---- actual moment of local solar noon rather than at 00:00 UT.
        val jd0 = julianDay(date)
        var noonUt = 12.0 - longitude / 15.0
        repeat(2) {
            noonUt = 12.0 - longitude / 15.0 - solarCoordinates(jd0 + noonUt / 24.0).equationOfTime
        }
        val solar = solarCoordinates(jd0 + noonUt / 24.0)
        val declination = solar.declination

        // ---- Sunrise / sunset bracket the night, and every high-latitude rule
        // ---- is expressed relative to them.
        val sunriseHourAngle = hourAngle(-SUNRISE_ANGLE, latitude, declination)
        val hasTrueSunriseAndSunset = isAttainable(-SUNRISE_ANGLE, latitude, declination)
        val sunriseUt = noonUt - sunriseHourAngle
        val sunsetUt = noonUt + sunriseHourAngle

        val maghribUt = if (maghribAngle > 0.0) {
            noonUt + hourAngle(-maghribAngle, latitude, declination)
        } else {
            sunsetUt
        }

        // ---- Asr: the moment an object's shadow reaches its own length (plus the
        // ---- noon shadow) times the school's ratio.
        //
        // The shadow-ratio model assumes the sun is above the horizon. Inside the
        // polar circles `|latitude - declination|` exceeds 90 degrees, the tangent
        // turns negative, and the formula returns a *negative* altitude -- a
        // "shadow" cast by a sun that never rose. Taken literally that puts Asr
        // after Maghrib. Asr necessarily falls between Dhuhr and Maghrib, so
        // enforce that directly; the clamp only ever binds in the degenerate case.
        val asrAltitude = Math.toDegrees(
            atan(1.0 / (school.shadowRatio + tan(Math.toRadians(abs(latitude - declination)))))
        )
        val asrUt = (noonUt + hourAngle(asrAltitude, latitude, declination))
            .coerceIn(noonUt, maghribUt)

        // ---- Twilight prayers, which are the ones that can fail to exist.
        val fajrAttainable = isAttainable(-fajrAngle, latitude, declination)
        val ishaAttainable = isAttainable(-ishaAngle, latitude, declination)
        val rawFajrUt = noonUt - hourAngle(-fajrAngle, latitude, declination)
        val rawIshaUt = noonUt + hourAngle(-ishaAngle, latitude, declination)

        val effectiveRule = resolveRule(highLatitudeRule, latitude)
        val night = nightLength(sunriseUt, sunsetUt)

        val fajrUt = clampIntoNight(
            time = rawFajrUt,
            limit = sunriseUt - nightPortion(effectiveRule, fajrAngle, night),
            isBefore = true
        )

        val ishaUt = if (ishaInterval > 0) {
            maghribUt + ishaInterval / 60.0
        } else {
            clampIntoNight(
                time = rawIshaUt,
                limit = sunsetUt + nightPortion(effectiveRule, ishaAngle, night),
                isBefore = false
            )
        }

        // The clock shows whole minutes, so the seconds have to go somewhere. Every prayer goes to the
        // NEAREST minute (half a minute rounds up), the way mosque timetables and other prayer apps
        // read: Isha of 19:16:15 is 19:16, Fajr of 05:08:46 is 05:09. The adhan can therefore fall up to
        // 30 s before the exact astronomical moment, never a whole minute after it.
        // (The first version rounded UP so the adhan could never run ahead of the sun; that put it up to
        // 59 s after the mosque's call, which a user in Aleppo heard as "Emran is a minute late".)
        // Sunrise is not a prayer: it marks the END of Fajr, so its partial minute is dropped.
        return PrayerTimes(
            fajr = toLocalDateTime(date, fajrUt, zone).roundToMinute(),
            sunrise = toLocalDateTime(date, sunriseUt, zone).truncatedTo(java.time.temporal.ChronoUnit.MINUTES),
            dhuhr = toLocalDateTime(date, noonUt, zone).roundToMinute(),
            asr = toLocalDateTime(date, asrUt, zone).roundToMinute(),
            maghrib = toLocalDateTime(date, maghribUt, zone).roundToMinute(),
            isha = toLocalDateTime(date, ishaUt, zone).roundToMinute(),
            date = date,
            zone = zone,
            hasTrueSunriseAndSunset = hasTrueSunriseAndSunset,
            twilightIsAstronomical = fajrAttainable && ishaAttainable
        ).withAdjustments(adjustments)
    }

    /** Convenience overload for callers that hold an IANA id string. */
    fun calculateTimes(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: String,
        method: CalculationMethod = CalculationMethod.MWL,
        school: AsrSchool = AsrSchool.SHAFII,
        highLatitudeRule: HighLatitudeRule = HighLatitudeRule.AUTOMATIC,
        adjustments: Adjustments = Adjustments.NONE,
        custom: CustomParameters? = null,
        useRamadanIshaInterval: Boolean = false
    ): PrayerTimes = calculateTimes(
        date = date,
        latitude = latitude,
        longitude = longitude,
        zone = safeZone(zoneId),
        method = method,
        school = school,
        highLatitudeRule = highLatitudeRule,
        adjustments = adjustments,
        custom = custom,
        useRamadanIshaInterval = useRamadanIshaInterval
    )

    /**
     * Legacy overload retained so existing callers that still pass a raw UTC
     * offset keep compiling.
     *
     * This reintroduces the DST bug for whoever calls it -- a fixed offset cannot
     * describe a real place -- so it exists only as a migration shim. Prefer the
     * [ZoneId] overload above.
     */
    @Deprecated(
        message = "A fixed UTC offset cannot represent a real location across DST. " +
            "Pass a ZoneId (e.g. ZoneId.of(\"America/Chicago\")) instead.",
        replaceWith = ReplaceWith("calculateTimes(date, latitude, longitude, zone, method, school)")
    )
    fun calculateTimes(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        timezone: Double,
        method: CalculationMethod = CalculationMethod.MWL,
        school: AsrSchool = AsrSchool.SHAFII
    ): PrayerTimes = calculateTimes(
        date = date,
        latitude = latitude,
        longitude = longitude,
        zone = ZoneOffset.ofTotalSeconds((timezone * 3600.0).roundToLong().toInt()),
        method = method,
        school = school
    )

    /** Parses an IANA id, falling back to the device zone rather than throwing. */
    fun safeZone(zoneId: String?): ZoneId = try {
        if (zoneId.isNullOrBlank()) ZoneId.systemDefault() else ZoneId.of(zoneId)
    } catch (e: Exception) {
        ZoneId.systemDefault()
    }

    // -----------------------------------------------------------------------
    // High-latitude handling
    // -----------------------------------------------------------------------

    private fun resolveRule(rule: HighLatitudeRule, latitude: Double): HighLatitudeRule =
        if (rule == HighLatitudeRule.AUTOMATIC) {
            if (abs(latitude) > 48.0) HighLatitudeRule.ANGLE_BASED else HighLatitudeRule.NONE
        } else {
            rule
        }

    /**
     * Length of the night in hours: the complement of the day.
     *
     * The caller substitutes a nominal half-day inside the polar circles, so both
     * arguments are always real numbers by the time this runs.
     */
    private fun nightLength(sunriseUt: Double, sunsetUt: Double): Double =
        24.0 - (sunsetUt - sunriseUt)

    /**
     * How far from sunrise/sunset the rule permits Fajr/Isha to sit.
     * NaN means "no limit", which is how [HighLatitudeRule.NONE] opts out.
     */
    private fun nightPortion(rule: HighLatitudeRule, angle: Double, night: Double): Double =
        when (rule) {
            HighLatitudeRule.MIDDLE_OF_NIGHT -> night / 2.0
            HighLatitudeRule.ONE_SEVENTH_OF_NIGHT -> night / 7.0
            HighLatitudeRule.ANGLE_BASED -> night * (angle / 60.0)
            HighLatitudeRule.NONE, HighLatitudeRule.AUTOMATIC -> Double.NaN
        }

    /**
     * Pulls a twilight prayer back inside the portion of the night the chosen
     * rule allows.
     *
     * [time] is always a real number now that [hourAngle] clamps, so this only
     * has to handle the "reached, but unreasonably deep into the night" case --
     * Isha at 01:40 with Fajr forty minutes later. A NaN [limit] means the user
     * selected no rule, in which case the astronomical value stands.
     */
    private fun clampIntoNight(time: Double, limit: Double, isBefore: Boolean): Double {
        if (limit.isNaN()) return time
        return if (isBefore) maxOf(time, limit) else minOf(time, limit)
    }

    // -----------------------------------------------------------------------
    // Astronomy
    // -----------------------------------------------------------------------

    private data class SolarCoordinates(
        /** Degrees. */
        val declination: Double,
        /** Hours, normalised to (-12, 12]. */
        val equationOfTime: Double
    )

    /**
     * Low-precision solar position (USNO). Accurate to well under a minute of
     * time for the date range this app cares about.
     */
    private fun solarCoordinates(julianDay: Double): SolarCoordinates {
        val d = julianDay - 2451545.0

        // Mean anomaly, mean longitude, ecliptic longitude.
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2.0 * g))

        val obliquity = 23.439 - 0.00000036 * d

        val declination = Math.toDegrees(asin(sinDeg(obliquity) * sinDeg(l)))
        val rightAscension = fixHour(
            Math.toDegrees(atan2(cosDeg(obliquity) * sinDeg(l), cosDeg(l))) / 15.0
        )

        // q/15 and RA are each in [0, 24), so their difference lands anywhere in
        // (-24, 24). The true equation of time never exceeds ~17 minutes, so wrap
        // into (-12, 12] rather than trusting the raw subtraction.
        val equationOfTime = wrapHalfDay(q / 15.0 - rightAscension)

        return SolarCoordinates(declination, equationOfTime)
    }

    /**
     * Hour angle, in hours, between solar noon and the moment the sun sits at
     * [altitude] degrees above the horizon.
     *
     * When that altitude is never reached, `cos H` falls outside [-1, 1] and there
     * is no solution. Rather than return NaN -- which propagated through every
     * downstream fallback and collapsed four prayers onto a single sentinel -- the
     * cosine is clamped, yielding the physical limiting case:
     *
     *  * clamped to `+1` (altitude never reached going down): hour angle 0, so the
     *    event degenerates onto solar noon. This is the polar-night sunrise.
     *  * clamped to `-1` (altitude always exceeded): hour angle 12h, so the event
     *    degenerates onto solar midnight. This is the midnight-sun sunset.
     *
     * Both are the correct limits of the continuous function, and both keep the
     * resulting times ordered (possibly with ties) instead of undefined.
     * [isAttainable] reports whether clamping actually occurred so callers can
     * tell a real horizon crossing from a degenerate one.
     */
    private fun hourAngle(altitude: Double, latitude: Double, declination: Double): Double {
        val cosHourAngle = cosHourAngle(altitude, latitude, declination)
        if (cosHourAngle.isNaN()) return 0.0
        return Math.toDegrees(acos(cosHourAngle.coerceIn(-1.0, 1.0))) / 15.0
    }

    /** True when the sun genuinely reaches [altitude] at this latitude on this date. */
    private fun isAttainable(altitude: Double, latitude: Double, declination: Double): Boolean {
        val c = cosHourAngle(altitude, latitude, declination)
        return !c.isNaN() && c >= -1.0 && c <= 1.0
    }

    private fun cosHourAngle(altitude: Double, latitude: Double, declination: Double): Double =
        (sinDeg(altitude) - sinDeg(latitude) * sinDeg(declination)) /
            (cosDeg(latitude) * cosDeg(declination))

    /** Julian Day Number at 00:00 UT on [date] (Gregorian). */
    private fun julianDay(date: LocalDate): Double {
        var year = date.year
        var month = date.monthValue
        val day = date.dayOfMonth

        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = floor(year / 100.0)
        val b = 2.0 - a + floor(a / 4.0)

        return floor(365.25 * (year + 4716)) +
            floor(30.6001 * (month + 1)) +
            day + b - 1524.5
    }

    // -----------------------------------------------------------------------
    // Conversion
    // -----------------------------------------------------------------------

    /**
     * Converts "UT hours relative to 00:00 UT on [date]" into wall-clock time in
     * [zone].
     *
     * [utHours] is intentionally unbounded. Negative values and values past 24
     * are normal and correct near the antimeridian, and adding them as a duration
     * to a UTC instant lands on the right calendar day automatically.
     */
    private fun toLocalDateTime(date: LocalDate, utHours: Double, zone: ZoneId): LocalDateTime {
        if (utHours.isNaN() || utHours.isInfinite()) {
            // Should be unreachable: every path either yields a real time or a
            // high-latitude fallback. Degrade to local noon instead of crashing.
            return date.atTime(12, 0)
        }

        val baseInstant = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val instant = baseInstant.plus(Duration.ofSeconds((utHours * 3600.0).roundToLong()))

        return instant.atZone(zone).toLocalDateTime()
    }

    // -----------------------------------------------------------------------
    // Small helpers
    // -----------------------------------------------------------------------

    /** The nearest whole minute: 30 seconds or more rounds up. */
    private fun LocalDateTime.roundToMinute(): LocalDateTime {
        val floor = truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        return if (second >= 30) floor.plusMinutes(1) else floor
    }

    private fun sinDeg(degrees: Double) = sin(Math.toRadians(degrees))

    private fun cosDeg(degrees: Double) = cos(Math.toRadians(degrees))

    private fun fixAngle(angle: Double): Double {
        val a = angle % 360.0
        return if (a < 0) a + 360.0 else a
    }

    private fun fixHour(hour: Double): Double {
        val h = hour % 24.0
        return if (h < 0) h + 24.0 else h
    }

    /** Wraps an hour value into (-12, 12]. */
    private fun wrapHalfDay(hour: Double): Double = fixHour(hour + 12.0) - 12.0
}
