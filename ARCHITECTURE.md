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
| **语言与运行时** | Kotlin Multiplatform | 2.3.21 | 跨平台语言支持 (Android JVM + Windows Desktop JVM) |
| **UI 与渲染** | Compose Multiplatform | 1.12.0 | 原生 Skia 渲染，支持 Android 与 Windows 桌面 |
| **设计系统** | Compose Material 3 + Adaptive | 1.12.0 基线 | MD3 Expressive 组件与自适应多屏支持 |
| **依赖注入** | Koin | 4.2.2 | KMP 无反射轻量依赖注入 |
| **网络层** | Ktor Client + OkHttp Engine | 3.5.2 | HTTP 二进制请求与全平台 NAI API 适配 |
| **图片加载与缓存**| Coil 3 | 3.6.1 | 约束尺寸解码、默认共享内存缓存与异步图像加载；不依赖已移除的 Bitmap Pool |
| **本地数据库** | Room KMP + Bundled SQLite | 2.8.4 / 2.6.2 | 多平台持久化、稳定分页、任务队列与图库元数据 |
| **配置存储** | Multiplatform DataStore | 1.1.7 | 存储 API Token、模型首选项与工作台草稿 |
| **二进制与 IO** | Okio | 3.18.1 | 解析 PNG tEXt Chunk 元数据与处理二进制响应 |

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
3. **响应式图库**：图库主网格使用 Room `PagingSource` 与 Paging 3，每页 60 条、最多保留 300 条加载窗口；其他轻量消费者仍可使用 `Flow<List<ImageEntity>>`，数据变更无需轮询。

### 3.1 生成队列与下载存储

生成工作流为应用级持久化串行队列，不依赖 Android WorkManager、前台服务或其他系统后台任务：

`ViewModel → GenerationQueueController → 单消费者 Repository/API → PNG/ZIP 解析 → 临时文件发布 → ImageEntity`

* 每个任务入队时保存不可变参数快照，按 FIFO 顺序执行；状态包括 `QUEUED`、`RUNNING`、`WAITING_ANLAS_CONFIRMATION`、`SUCCEEDED`、`FAILED`、`FAILED_UNKNOWN` 与 `CANCELLED`。
* 一次最多一个网络生成提交。生成响应可以是单 PNG 或 ZIP；ZIP 解包最多接收 16 张 PNG、64 个条目，单条目最多 32 MiB，解压后总量最多 128 MiB，并校验 PNG 签名。
* BlurHash 在内存中计算；原图和缩略图先写入临时文件，单图调用只有两者均发布完成才返回成功，随后再批量写入数据库。文件与数据库并非跨系统原子事务；失败时会补偿清理本次文件和记录，清理不完整会明确报错。
* 排队或待确认任务可取消；运行中取消只停止本地等待并标记结果未知。传输错误、运行中取消和进程中断都不会自动重试或重提交，避免服务端已完成时重复计费。

---

## 4. 本地持久化实体设计 (Database Schema)

```kotlin
// ImageRecordEntity.kt (Room Entity)
@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val id: String,              // UUID / SHA256
    val filePath: String,                   // 磁盘绝对路径 (应用隔离目录)
    val thumbnailPath: String,              // 平台缩略图路径（最长边 256px）
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

## 5. 图像缓存与海量图库内存优化策略

* **Coil 3 图像管线**：
  * **采样率控制**：瀑布流卡片严禁回退加载 2K/4K 原图，只读取最长边 256px 的平台缩略图，并由 `AsyncImage` 按布局约束选择解码尺寸；
  * **共享缓存**：使用 Coil Compose 默认共享 `ImageLoader` 的内存缓存复用近期解码结果。Coil 3 不提供旧版 Bitmap Pool，架构不依赖已移除的接口；
  * **分页窗口**：Room/Paging 3 按 `createdAt DESC, id DESC` 稳定分页，`createdAt + id` 复合索引避免万级记录反复全表排序；每页 60 条，已加载窗口上限 300 条。
* **图库交互与跨端导出**：多选状态只保存有序图片 ID；批量评分由单条 SQL 原子更新星级与收藏标记。原图导出由桌面目录选择器或 Android SAF 获取用户目标位置，采用安全文件名、冲突递增后缀和仅复制策略，单文件失败不会删除源文件或回滚已成功导出的其他文件。
* **灯箱与对比传递**：灯箱仅按需加载一张原图，缩放范围为 1x–5x，双击与复位使用 MD3E 弹簧；图库到对比实验室最多传递两个稳定 ID，对比页按 ID 查询，避免持有过期分页对象或重新全量加载图库。

## 6. 对比与偏好筛选边界

* **对比实验室**：卷帘视图以 `clipRect` 进行分割；并排视图共享缩放与位移状态，并分别按容器尺寸钳制偏移。两种视图均在图片路径切换时复位，几何计算有纯函数测试。
* **Swipe 卡片流**：使用稳定的拖拽手势识别与单飞行动画门闩，横向位移达到卡片宽度 35% 才提交喜欢/不喜欢；列表只查询首张中性图片与计数，不把全库 `observeAll()` 物化到内存。该实现不依赖实验性的 `AnchoredDraggableState`。
* **偏好雷达**：Room 按喜欢评分分页读取提示词，每页有界解析、单图标签去重后增量计数，再将 Top Tags 合并到工作台建议词；取消或刷新时丢弃过期响应。

## 7. 跨端入口与分发验证

* **响应式入口**：窄屏使用 NavigationBar，并将工作台参数收纳进 ModalBottomSheet；宽屏使用 NavigationRail、桌面自定义标题栏及 Ctrl+1…6 导航快捷键。JVM 编译和桌面启动冒烟已通过，GUI 截图验收因通道不可用未执行。
* **Android 优化**：R8 FullMode release 已构建成功，APK 含 `baseline.prof`/`baseline.profm`；profile generator 与手动入口可构建。官方 Baseline Profile Gradle Plugin 1.4.1 与当前 AGP/KMP 组合不兼容，真机采集仍待办。
* **Windows 分发**：portable Zip 和 MSI 均已实际构建并检查产物；Zip 包含 launcher/runtime，可独立分发。Macrobenchmark harness 已添加且 `assembleBenchmark` 成功，但设备运行、真实付费 E2E 与 120Hz 实机测量尚未执行。

## 8. 验证边界

当前单元测试与 JVM 集成测试覆盖核心几何、队列、Room 查询、标签统计和失败保护；Android release 混淆/profile 产物与 Windows 安装包产物已完成构建验收。真机 profile 采集、Macrobenchmark 设备运行、120Hz 帧率采集及真实付费生成链路仍须独立验收，不能由文档推定已完成。
