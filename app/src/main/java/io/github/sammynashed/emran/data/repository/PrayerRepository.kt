package io.github.sammynashed.emran.data.repository

import io.github.sammynashed.emran.data.local.db.PrayerDao
import io.github.sammynashed.emran.data.local.model.PrayerOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PrayerRepository(private val prayerDao: PrayerDao) {

    // Retrieve offsets as a map of key -> minutes
    val offsetsMap: Flow<Map<String, Int>> = prayerDao.getAllOffsets().map { list ->
        list.associate { it.prayerKey to it.offsetMinutes }
    }

    suspend fun saveOffset(prayerKey: String, offsetMinutes: Int) = withContext(Dispatchers.IO) {
        prayerDao.insertOffset(PrayerOffset(prayerKey, offsetMinutes))
    }
}
