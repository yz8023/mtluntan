package io.mtluntan.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.mtluntan.app.data.db.dao.AccountDao
import io.mtluntan.app.data.db.dao.DraftDao
import io.mtluntan.app.data.db.dao.FavoriteDao
import io.mtluntan.app.data.db.dao.HistoryDao
import io.mtluntan.app.data.db.dao.ReadMarkDao
import io.mtluntan.app.data.db.dao.ThreadCacheDao
import io.mtluntan.app.data.db.entity.AccountEntity
import io.mtluntan.app.data.db.entity.DraftEntity
import io.mtluntan.app.data.db.entity.FavoriteEntity
import io.mtluntan.app.data.db.entity.HistoryEntity
import io.mtluntan.app.data.db.entity.ReadMarkEntity
import io.mtluntan.app.data.db.entity.ThreadCacheEntity

@Database(
    entities = [
        AccountEntity::class,
        HistoryEntity::class,
        DraftEntity::class,
        FavoriteEntity::class,
        ThreadCacheEntity::class,
        ReadMarkEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun historyDao(): HistoryDao
    abstract fun draftDao(): DraftDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun threadCacheDao(): ThreadCacheDao
    abstract fun readMarkDao(): ReadMarkDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mtluntan.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}