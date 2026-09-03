# 架构规约与技术底座 (ARCHITECTURE.md)

本应用采用 **Kotlin + Compose Multiplatform (CMP)** 构建，采用原生 Skia 渲染管线，杜绝任何基于 WebView 的内存冗余与 DOM 重排损耗。

---

## 1. 系统模块拓扑 (Multi-Module Topology)

```
                       ┌──────────────────────┐
                       │     :composeApp      │ (入口与平台层)
                       └──────────┬───────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         ▼                        ▼                        ▼
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│:feature:workbenc│      │:feature:gallery │      │:feature:compare │ ... (:feature:swipe, :inpaint)
└────────┬────────┘      └────────┬────────┘      └────────┬────────┘
         │                        │                        │
         └────────────────────────┼────────────────────────┘
                                  ▼
         ┌─────────────────────────────────────────────────┐
         │              :core:designsystem                 │ (MD3E 组件库与动态色彩)
         └────────────────────────┬────────────────────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         ▼                        ▼                        ▼
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│  :core:network  │      │ :core:database  │      │  :core:common   │
└────────┬────────┘      └────────┬────────┘      └────────┬────────┘
         │                        │                        │
         └────────────────────────┼────────────────────────┘
                                  ▼
                       ┌──────────────────────┐
                       │     :core:model      │ (领域实体与协议类型)
                       └──────────────────────┘
```

---

## 2. 核心技术栈清单 (Tech Stack)

| 层次 | 框架 / 库 | 版本基线 | 作用与职责 |
| :--- | :--- | :--- | :--- |
| **语言与运行时** | Kotlin Multiplatform | 2.1.0+ | 跨平台语言支持 (Android JVM + Windows Native/Desktop JVM) |
| **UI 与渲染** | Compose Multiplatform | 1.7.1+ / 1.9+ | 纯原生 Skia 渲染，支持 Android 与 Windows 桌面 |
| **设计系统** | Material 3 + Adaptive | 1.4.0-alpha+ | MD3 Expressive 表现力组件集与自适应多屏支持 |
| **依赖注入** | Koin | 4.0.0+ | KMP 官方无反射轻量依赖注入 |
| **网络层** | Ktor Client + OkHttp Engine | 3.0.0+ | 高性能 HTTP/2 与 SSE 通信，全平台直连 NAI API (免 CORS) |
| **图片加载与位图池**| Coil 3 | 3.0.0+ | CMP 跨端支持，集成内存位图复用池与异步磁盘缓存 |
| **本地数据库** | Room KMP + SQLite | 2.7.0-alpha+ | 官方多平台持久化，存储元数据、收藏、历史与词频 |
| **配置存储** | Multiplatform DataStore | 1.1.0+ | 加密存储 API Token、模型首选项与工作台草稿 |
| **二进制与 IO** | Okio | 3.9.0+ | 极速解析 PNG tEXt Chunk 元数据、读写本地原图文件 |

---

## 3. 核心数据流与线程模型 (Unidirectional Data Flow)

```
[ 用户交互 / 触发操作 ]
        │
        ▼
   [ ViewModel ] ──── (StateFlow / SharedFlow) ────▶ [ Compose UI (Skia 绘制) ]
        │                                                     ▲
        ▼ (调度至 Dispatchers.IO)                              │
   [ Repository ]                                             │
        │                                                     │
   ┌────┴───────────────────────────┐                         │
   ▼                                ▼                         │
[ Ktor Client (NAI API) ]    [ Room KMP / Okio ] ─────────────┘
 (下载二进制 Blob/流)          (写入本地文件 + 提取元数据)
```

1. **主线程极简**：主线程仅负责 Compose 状态重组（Recomposition）与 Skia 绘制命令下发。
2. **重度 IO 隔离**：所有 PNG 解码、tEXt Chunk 元数据解析、Blurhash 生成、本地文件写入均通过 `withContext(Dispatchers.IO)` 在后台协程池执行。
3. **响应式图库**：`Room DAO` 查询返回 `PagingData` 或 `Flow<List<ImageEntity>>`，数据变更时秒级响应，无需轮询。

---

## 4. 本地持久化实体设计 (Database Schema)

```kotlin
// ImageRecordEntity.kt (Room Entity)
@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val id: String,              // UUID / SHA256
    val filePath: String,                   // 磁盘绝对路径 (应用隔离目录)
    val thumbnailPath: String,              // WebP 缩略图路径 (256x256)
    val blurHash: String,                   // 占位模糊字符串
    val prompt: String,                     // 提示词
    val uc: String,                         // 负面提示词
    val model: String,                      // 如 nai-diffusion-4-5-full, nai-diffusion-5
    val seed: Long,                         // 随机种子
    val steps: Int,                         // 采样步数
    val scale: Float,                       // CFG Scale
    val sampler: String,                    // 采样器算法
    val width: Int,                         // 原始宽度
    val height: Int,                        // 原始高度
    val starRating: Int,                    // 1=不喜欢/垃圾箱, 3=普通, 5=收藏/喜欢
    val isFavorite: Boolean,                // 是否加入喜欢卡片库
    val hasTransparency: Boolean,           // 是否包含 V5 透明背景
    val rawMetadataJson: String,            // 完整的 NAI 官方元数据 JSON 字符串
    val createdAt: Long                     // 毫秒时间戳
)
```

---

## 5. 位图池与海量图库内存优化策略

* **Coil 3 深度定制**：
  * **采样率控制**：瀑布流卡片列表严禁加载 2K/4K 原图，强制使用由后台解析生成的 WebP 缩略图；
  * **Bitmap Pool**：重用回收的 `android.graphics.Bitmap` 或 Skia `Image` 内存块，避免高频滑动触发 JVM/ART 垃圾回收（GC 抖动）；
  * **视口感知**：离开屏幕超过 2 屏外的图片资源自动释放强引用。
