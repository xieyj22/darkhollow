package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "game_save")
data class GameSave(
    @PrimaryKey val id: Int = 1,
    val stateJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "meta_save")
data class MetaSave(
    @PrimaryKey val id: Int = 1,
    val metaJson: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface GameSaveDao {
    @Query("SELECT * FROM game_save WHERE id = 1 LIMIT 1")
    fun getActiveSaveFlow(): Flow<GameSave?>

    @Query("SELECT * FROM game_save WHERE id = 1 LIMIT 1")
    suspend fun getActiveSave(): GameSave?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSave(save: GameSave)

    @Query("DELETE FROM game_save WHERE id = 1")
    suspend fun deleteActiveSave()
}

@Dao
interface MetaSaveDao {
    @Query("SELECT * FROM meta_save WHERE id = 1 LIMIT 1")
    fun getMetaSaveFlow(): Flow<MetaSave?>

    @Query("SELECT * FROM meta_save WHERE id = 1 LIMIT 1")
    suspend fun getMetaSave(): MetaSave?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetaSave(save: MetaSave)
}

@Database(entities = [GameSave::class, MetaSave::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameSaveDao(): GameSaveDao
    abstract fun metaSaveDao(): MetaSaveDao
}
