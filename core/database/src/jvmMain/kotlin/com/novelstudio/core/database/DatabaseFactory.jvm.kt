package com.novelstudio.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

actual fun databaseBuilder(context: Any?): RoomDatabase.Builder<AppDatabase> {
    val dbDir = Path.of(System.getProperty("user.home"), ".novelai-studio")
    Files.createDirectories(dbDir)
    return Room.databaseBuilder<AppDatabase>(
        name = dbDir.resolve(AppDatabase.DATABASE_NAME).absolutePathString(),
    )
}
