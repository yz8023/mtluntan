package io.mtluntan.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.mtluntan.app.data.db.dao.AccountDao
import io.mtluntan.app.data.db.dao.AiDao
import io.mtluntan.app.data.db.dao.BlacklistDao
import io.mtluntan.app.data.db.dao.DraftDao
import io.mtluntan.app.data.db.dao.FavoriteDao
import io.mtluntan.app.data.db.dao.FollowDao
import io.mtluntan.app.data.db.dao.HistoryDao
import io.mtluntan.app.data.db.dao.OfflinePostDao
import io.mtluntan.app.data.db.dao.ReadMarkDao
import io.mtluntan.app.data.db.dao.SignRecordDao
import io.mtluntan.app.data.db.dao.ThreadCacheDao
import io.mtluntan.app.data.db.dao.UnlockClaimDao
import io.mtluntan.app.data.db.entity.AccountEntity
import io.mtluntan.app.data.db.entity.AiMessageEntity
import io.mtluntan.app.data.db.entity.AiSessionEntity
import io.mtluntan.app.data.db.entity.BlacklistEntity
import io.mtluntan.app.data.db.entity.DraftEntity
import io.mtluntan.app.data.db.entity.FavoriteEntity
import io.mtluntan.app.data.db.entity.FollowEntity
import io.mtluntan.app.data.db.entity.HistoryEntity
import io.mtluntan.app.data.db.entity.OfflinePostEntity
import io.mtluntan.app.data.db.entity.ReadMarkEntity
import io.mtluntan.app.data.db.entity.SignRecordEntity
import io.mtluntan.app.data.db.entity.ThreadCacheEntity
import io.mtluntan.app.data.db.entity.UnlockClaimEntity

@Database(
    entities = [
        AccountEntity::class,
        HistoryEntity::class,
        DraftEntity::class,
        FavoriteEntity::class,
        ThreadCacheEntity::class,
        ReadMarkEntity::class,
        SignRecordEntity::class,
        UnlockClaimEntity::class,
        BlacklistEntity::class,
        FollowEntity::class,
        AiSessionEntity::class,
        AiMessageEntity::class,
        OfflinePostEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun historyDao(): HistoryDao
    abstract fun draftDao(): DraftDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun threadCacheDao(): ThreadCacheDao
    abstract fun readMarkDao(): ReadMarkDao
    abstract fun signRecordDao(): SignRecordDao
    abstract fun unlockClaimDao(): UnlockClaimDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun followDao(): FollowDao
    abstract fun aiDao(): AiDao
    abstract fun offlinePostDao(): OfflinePostDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mtluntan.db",
                )
                    // v1 → v2 → v3 都有显式迁移：
                    // v2 那次把 7 张新表漏在 schema 外（只有 6 张表被创建），
                    // 直接 destructive 会把账号 / 历史 / 草稿全部清空，
                    // 所以这里必须把表补齐而不是删库。
                    .addMigrations(*AppMigrations.ALL)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
    }
}
