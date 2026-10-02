package io.mtluntan.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 迁移。
 *
 * 用户反馈「账号 / 历史全没了」的根因：
 * v2 的 `@Database(entities = ...)` 只写了 6 张表，其余 7 张（签到记录 / 解锁认领 /
 * 黑名单 / 关注 / AI 会话 / 离线帖）在库里**根本没建**，运营商又用了
 * `fallbackToDestructiveMigration()` —— 一旦版本号变化就直接删库重建。
 *
 * 现在改成「收敛式迁移」：
 *  - 所有 DDL 都从 Room 导出的 schema（app/schemas，版本 3）里**原样**取出，
 *    与实体定义一字不差，避免迁移后 Room 校验失败；
 *  - `CREATE TABLE IF NOT EXISTS` + `PRAGMA table_info` 判重，重复执行无副作用；
 *  - v1 / v2 → v3 都收敛到同一份 schema，数据（账号、历史、草稿、收藏）全部保留；
 *  - 只有「降级」这种无解情况才允许清库。
 */
object AppMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) = converge(db)
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) = converge(db)
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

    /** 把任意旧版本的库补齐成 v3 的结构（幂等）。 */
    private fun converge(db: SupportSQLiteDatabase) {
        NEW_TABLES.forEach { t ->
            create(db, t.name, t.sql)
            t.indices.forEach { create(db, it.name, it.sql) }
        }
        LEGACY_TABLES.forEach { (table, columns) ->
            columns.forEach { (column, clause) ->
                addColumn(db, table, column, clause)
            }
        }
    }

    private fun addColumn(db: SupportSQLiteDatabase, table: String, column: String, clause: String) {
        if (!tableExists(db, table)) return
        if (hasColumn(db, table, column)) return
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $clause")
    }

    private fun create(db: SupportSQLiteDatabase, name: String, sql: String) {
        db.execSQL(sql)
    }

    private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
        runCatching {
            db.query("PRAGMA table_info(`$table`)").use { c ->
                val i = c.getColumnIndex("name")
                while (c.moveToNext()) if (i >= 0 && c.getString(i) == column) return true
            }
            false
        }.getOrDefault(false)

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        runCatching {
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
                .use { it.moveToNext() }
        }.getOrDefault(false)

    private data class Idx(val name: String, val sql: String)
    private data class Tbl(val name: String, val sql: String, val indices: List<Idx> = emptyList())

    private val NEW_TABLES = listOf(
        Tbl("sign_records", "CREATE TABLE IF NOT EXISTS `sign_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `account` TEXT NOT NULL, `date` TEXT NOT NULL, `at` INTEGER NOT NULL, `ok` INTEGER NOT NULL, `alreadySigned` INTEGER NOT NULL, `rank` INTEGER NOT NULL, `reward` TEXT NOT NULL, `message` TEXT NOT NULL)", listOf()),
        Tbl("unlock_claims", "CREATE TABLE IF NOT EXISTS `unlock_claims` (`tid` INTEGER NOT NULL, `at` INTEGER NOT NULL, `ok` INTEGER NOT NULL, `replyText` TEXT NOT NULL, PRIMARY KEY(`tid`))", listOf()),
        Tbl("blacklist", "CREATE TABLE IF NOT EXISTS `blacklist` (`uid` INTEGER NOT NULL, `username` TEXT NOT NULL, `reason` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`uid`))", listOf()),
        Tbl("follow_state", "CREATE TABLE IF NOT EXISTS `follow_state` (`uid` INTEGER NOT NULL, `username` TEXT NOT NULL, `following` INTEGER NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`uid`))", listOf()),
        Tbl("ai_sessions", "CREATE TABLE IF NOT EXISTS `ai_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `account` TEXT NOT NULL, `tid` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)", listOf()),
        Tbl("ai_messages", "CREATE TABLE IF NOT EXISTS `ai_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `at` INTEGER NOT NULL)", listOf()),
        Tbl("offline_posts", "CREATE TABLE IF NOT EXISTS `offline_posts` (`tid` INTEGER NOT NULL, `title` TEXT NOT NULL, `boardName` TEXT NOT NULL, `authorName` TEXT NOT NULL, `html` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`tid`))", listOf()),
    )

    private val LEGACY_TABLES = listOf(
        "accounts" to listOf(
            "uid" to "INTEGER NOT NULL DEFAULT 0",
            "nickname" to "TEXT NOT NULL DEFAULT ''",
            "avatarUrl" to "TEXT NOT NULL DEFAULT ''",
            "cookieString" to "TEXT NOT NULL DEFAULT ''",
            "creditsText" to "TEXT NOT NULL DEFAULT ''",
            "expired" to "INTEGER NOT NULL DEFAULT 0",
            "signDays" to "INTEGER NOT NULL DEFAULT 0",
            "lastSignedAt" to "INTEGER NOT NULL DEFAULT 0",
            "passwordCipher" to "TEXT NOT NULL DEFAULT ''",
            "enabled" to "INTEGER NOT NULL DEFAULT 1",
            "sortOrder" to "INTEGER NOT NULL DEFAULT 0",
            "lastSignOk" to "INTEGER NOT NULL DEFAULT 0",
            "lastSignRank" to "INTEGER NOT NULL DEFAULT 0",
            "lastSignReward" to "TEXT NOT NULL DEFAULT ''",
            "createdAt" to "INTEGER NOT NULL DEFAULT 0",
        ),
        "history" to listOf(
            "tid" to "INTEGER NOT NULL DEFAULT 0",
            "title" to "TEXT NOT NULL DEFAULT ''",
            "boardName" to "TEXT NOT NULL DEFAULT ''",
            "authorName" to "TEXT NOT NULL DEFAULT ''",
            "readAt" to "INTEGER NOT NULL DEFAULT 0",
            "lastFloor" to "INTEGER NOT NULL DEFAULT 0",
            "totalPages" to "INTEGER NOT NULL DEFAULT 0",
            "isRead" to "INTEGER NOT NULL DEFAULT 0",
        ),
    )
}
