package io.github.sammynashed.emran.data.local.db

import androidx.room.*
import io.github.sammynashed.emran.data.local.model.PrayerOffset
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerDao {

    // Prayer Offsets
    @Query("SELECT * FROM prayer_offsets")
    fun getAllOffsets(): Flow<List<PrayerOffset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffset(offset: PrayerOffset)
}
