package io.github.sammynashed.emran.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.sammynashed.emran.data.local.model.PrayerOffset
import io.github.sammynashed.emran.data.local.model.CustomTasbeeh

@Database(
    entities = [PrayerOffset::class, CustomTasbeeh::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun prayerDao(): PrayerDao
    abstract fun tasbeehDao(): TasbeehDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v9 drops the prayer log and the Qada tallies: Emran no longer records
         * which prayers were prayed or missed. An explicit migration rather than
         * the destructive fallback, because that fallback would also wipe the
         * Fine-Tune offsets and the user's own tasbeehs, which are kept.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS prayer_records")
                db.execSQL("DROP TABLE IF EXISTS qada_tally")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "emran_salah_db"
                )
                .addMigrations(MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
