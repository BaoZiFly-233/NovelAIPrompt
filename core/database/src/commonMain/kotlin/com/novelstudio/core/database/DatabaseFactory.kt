package com.novelstudio.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** 平台差异收口：Android 需要 Context，桌面端使用用户主目录 */
expect fun databaseBuilder(context: Any?): RoomDatabase.Builder<AppDatabase>

/**
 * 所有平台共用同一 SQLite 驱动与查询调度策略，避免 Android、桌面行为漂移。
 */
fun buildDatabase(context: Any?): AppDatabase = databaseBuilder(context)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    .build()
