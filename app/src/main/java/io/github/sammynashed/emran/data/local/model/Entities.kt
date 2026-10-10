package io.github.sammynashed.emran.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prayer_offsets")
data class PrayerOffset(
    @PrimaryKey val prayerKey: String, // e.g. "FAJR", "SUNRISE", "DHUHR", "ASR", "MAGHRIB", "ISHA"
    val offsetMinutes: Int
)

@Entity(tableName = "custom_tasbeehs")
data class CustomTasbeeh(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val textArabic: String,
    val textTransliteration: String,
    val count: Int,
    val target: Int = 33,
    val colorHex: String = "#FF64B5F6"
)
