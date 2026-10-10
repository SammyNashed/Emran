package io.github.sammynashed.emran.data.local.db

import androidx.room.*
import io.github.sammynashed.emran.data.local.model.CustomTasbeeh
import kotlinx.coroutines.flow.Flow

@Dao
interface TasbeehDao {
    @Query("SELECT * FROM custom_tasbeehs ORDER BY id ASC")
    fun getAllTasbeehs(): Flow<List<CustomTasbeeh>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasbeeh(tasbeeh: CustomTasbeeh): Long

    @Update
    suspend fun updateTasbeeh(tasbeeh: CustomTasbeeh)

    @Query("UPDATE custom_tasbeehs SET count = :count WHERE id = :id")
    suspend fun updateCount(id: Int, count: Int)

    @Delete
    suspend fun deleteTasbeeh(tasbeeh: CustomTasbeeh)

    @Query("UPDATE custom_tasbeehs SET count = 0")
    suspend fun resetAllCounts()

    @Query("DELETE FROM custom_tasbeehs")
    suspend fun deleteAllTasbeehs()
}
