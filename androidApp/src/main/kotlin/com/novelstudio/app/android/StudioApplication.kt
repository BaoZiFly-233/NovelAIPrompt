package com.novelstudio.app.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.novelstudio.CrashReporter
import com.novelstudio.di.appModule
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import java.io.File

class StudioApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // 崩溃日志必须最先安装，之后任何阶段的异常都能落盘回溯
        CrashReporter.install(applicationContext)

        startKoin {
            modules(appModule(applicationContext))
        }
    }

    /**
     * Coil 3 全局单例：内存缓存取堆的 25%，磁盘缓存 256MB 上限并落在应用缓存目录，
     * 图库瀑布流只加载 WebP 缩略图，避免万级图片 OOM。
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes((Runtime.getRuntime().maxMemory() * 0.25).toLong())
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache").absolutePath.toPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
}
