package com.novelstudio.core.database

import androidx.room.RoomDatabase

/** 平台差异收口：Android 需要 Context，桌面端使用用户主目录 */
expect fun databaseBuilder(context: Any?): RoomDatabase.Builder<AppDatabase>
