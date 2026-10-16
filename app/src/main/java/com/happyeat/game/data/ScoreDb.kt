package com.happyeat.game.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "scores")
data class ScoreRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userName: String,
    val score: Int,
    val createdAt: Long,
)

@Dao
interface ScoreDao {
    @Insert
    suspend fun insert(record: ScoreRecord)

    @Query("SELECT * FROM scores ORDER BY score DESC, createdAt ASC LIMIT :limit")
    suspend fun topScores(limit: Int): List<ScoreRecord>
}

@Database(entities = [ScoreRecord::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scoreDao(): ScoreDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "happyeat.db",
                ).build().also { instance = it }
            }
    }
}
