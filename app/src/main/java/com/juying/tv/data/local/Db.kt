package com.juying.tv.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history", primaryKeys = ["siteKey", "vodId"])
data class HistoryEntity(
    val siteKey: String,
    val vodId: String,
    val vodName: String,
    val vodPic: String,
    val sourceName: String = "",
    val sourceIndex: Int = 0,
    val episodeIndex: Int = 0,
    val episodeName: String = "",
    val position: Long = 0L,
    val duration: Long = 0L,
    val updateTime: Long = System.currentTimeMillis(),
)

@Entity(tableName = "favorite", primaryKeys = ["siteKey", "vodId"])
data class FavoriteEntity(
    val siteKey: String,
    val vodId: String,
    val vodName: String,
    val vodPic: String,
    val vodRemarks: String = "",
    val addTime: Long = System.currentTimeMillis(),
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY updateTime DESC LIMIT 200")
    fun all(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE siteKey = :siteKey AND vodId = :vodId")
    suspend fun get(siteKey: String, vodId: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: HistoryEntity)

    @Query("DELETE FROM history WHERE siteKey = :siteKey AND vodId = :vodId")
    suspend fun delete(siteKey: String, vodId: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite ORDER BY addTime DESC")
    fun all(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE siteKey = :siteKey AND vodId = :vodId)")
    fun isFavorite(siteKey: String, vodId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE siteKey = :siteKey AND vodId = :vodId")
    suspend fun delete(siteKey: String, vodId: String)
}

@Database(entities = [HistoryEntity::class, FavoriteEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    abstract fun favorite(): FavoriteDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "juying.db")
                .build().also { instance = it }
        }
    }
}
