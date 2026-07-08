package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val isButtonEnabled: Boolean = false,
    val iconType: String = "omni", // "omni", "star", "bolt", "assistant", "circle", "rocket"
    val iconColorHex: String = "#FF6200EE", // Background overlay color
    val iconTintHex: String = "#FFFFFFFF",  // Foreground icon color
    val bgOpacity: Float = 1.0f,
    val symbolOpacity: Float = 1.0f,
    val buttonSizeDp: Int = 60,
    val buttonOpacity: Float = 0.8f,
    val entryPoint: Int = 7,
    val isButtonFixed: Boolean = false,
    val lastX: Int = 100,
    val lastY: Int = 300
)

@Entity(tableName = "invocation_logs")
data class InvocationLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val entryPoint: Int,
    val isSuccessful: Boolean,
    val errorMessage: String? = null
)

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettings)
}

@Dao
interface InvocationLogDao {
    @Query("SELECT * FROM invocation_logs ORDER BY timestamp DESC LIMIT 100")
    fun getLogsFlow(): Flow<List<InvocationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: InvocationLog)

    @Query("DELETE FROM invocation_logs")
    suspend fun clearLogs()
}

@Database(entities = [AppSettings::class, InvocationLog::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun invocationLogDao(): InvocationLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omni_button_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
