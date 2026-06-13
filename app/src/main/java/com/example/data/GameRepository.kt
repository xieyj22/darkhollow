package com.example.data

import android.content.Context
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GameRepository(private val context: Context) {
    private val db: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "darkhollow_game.db"
        ).fallbackToDestructiveMigration().build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val gameSaveAdapter by lazy {
        moshi.adapter(FullGameState::class.java)
    }

    private val metaProgressionAdapter by lazy {
        moshi.adapter(MetaProgression::class.java)
    }

    private val gameSaveDao = db.gameSaveDao()
    private val metaSaveDao = db.metaSaveDao()

    val activeGameFlow: Flow<FullGameState?> = gameSaveDao.getActiveSaveFlow().map { gameSave ->
        gameSave?.let {
            try {
                gameSaveAdapter.fromJson(it.stateJson)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    val metaProgressionFlow: Flow<MetaProgression?> = metaSaveDao.getMetaSaveFlow().map { metaSave ->
        metaSave?.let {
            try {
                metaProgressionAdapter.fromJson(it.metaJson)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun getActiveGame(): FullGameState? = withContext(Dispatchers.IO) {
        val gameSave = gameSaveDao.getActiveSave()
        gameSave?.let {
            try {
                gameSaveAdapter.fromJson(it.stateJson)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun getMetaProgression(): MetaProgression = withContext(Dispatchers.IO) {
        val metaSave = metaSaveDao.getMetaSave()
        if (metaSave != null) {
            try {
                metaProgressionAdapter.fromJson(metaSave.metaJson) ?: MetaProgression()
            } catch (e: Exception) {
                e.printStackTrace()
                MetaProgression()
            }
        } else {
            MetaProgression()
        }
    }

    suspend fun saveGame(state: FullGameState) = withContext(Dispatchers.IO) {
        val json = gameSaveAdapter.toJson(state)
        gameSaveDao.insertSave(GameSave(id = 1, stateJson = json))
    }

    suspend fun saveMetaProgression(meta: MetaProgression) = withContext(Dispatchers.IO) {
        val json = metaProgressionAdapter.toJson(meta)
        metaSaveDao.insertMetaSave(MetaSave(id = 1, metaJson = json))
    }

    suspend fun deleteGame() = withContext(Dispatchers.IO) {
        gameSaveDao.deleteActiveSave()
    }
}
