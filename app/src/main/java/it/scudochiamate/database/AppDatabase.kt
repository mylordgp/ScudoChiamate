package it.scudochiamate.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.Date

@Database(entities = [BlockedCall::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedCallDao(): BlockedCallDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scudo_chiamate.db"
                ).build().also { INSTANCE = it }
            }
    }
}

/** Converte Date ↔ Long per Room. */
class Converters {
    @TypeConverter fun fromDate(date: Date?): Long? = date?.time
    @TypeConverter fun toDate(ms: Long?): Date?    = ms?.let { Date(it) }
}
