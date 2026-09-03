package com.novelstudio.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual fun databaseBuilder(context: Any?): RoomDatabase.Builder<AppDatabase> {
    val appContext = context as? Context
        ?: error("Android 平台必须传入 ApplicationContext 构建 Room 数据库")
    return Room.databaseBuilder(
        context = appContext,
        klass = AppDatabase::class.java,
        name = AppDatabase.DATABASE_NAME,
    )
}
