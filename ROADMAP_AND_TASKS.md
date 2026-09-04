# 实施路线图与任务检查单 (ROADMAP_AND_TASKS.md)

后续接力实现该项目的 AI 模型或开发者，请依照以下阶段和 Checklist 顺序进行原子化开发与验证。

---

## 阶段 0：工程骨架与基础依赖配置 (P0 - Foundation) ✅ 已完成

- [x] **Task 0.1**: 初始化 Kotlin Multiplatform 根工程 (`build.gradle.kts`, `settings.gradle.kts`)，配置 Compose Multiplatform 插件。
- [x] **Task 0.2**: 创建子模块架构 (`core:model`, `core:network`, `core:database`, `core:designsystem`, `core:common`, `composeApp`)。
- [x] **Task 0.3**: 配置 Koin 依赖注入图谱，初始化 DataStore 密钥安全存储 (Token/Preferences)。
- [x] **Task 0.4**: 实现 `core:designsystem` 中的 MD3E 调色板、Typography、Shape 与 `MD3EMotion` 弹簧物理预设。

> 实施备注：Kotlin 2.3.21 + CMP 1.12.0 + AGP 9.3 组合下，KMP 模块 Android 目标采用官方
> `com.android.kotlin.multiplatform.library` 插件；Android APK 入口拆分为独立薄壳模块
> `androidApp`（AGP 9 移除了 KMP 场景下的 application 插件）。

---

## 阶段 1：网络适配与 NovelAI V5 领域模型 (P1 - Core Data & Network) ✅ 已完成

- [x] **Task 1.1**: 基于 Ktor Client 实现 `NovelAIApiService`：
  - `POST /ai/generate-image` (处理 Zip/PNG 二进制流响应)
  - `GET /user/subscription` (从图像服务拉取订阅档位与官方 `usage` 状态，读取失败严格闭锁)
  - `POST /ai/encode-vibe` (单次付费动作编码 Vibe，不自动重试)
- [x] **Task 1.2**: 编写 `OpusFreeCalculator` (免费像素面积钳位与步数自适应校验算法)。
- [x] **Task 1.3**: 基于 Okio 编写 `PngChunkParser`，实现零拷贝提取 PNG 内 `tEXt:Comment` 生成元数据。
- [x] **Task 1.4**: 构建 `core:database` Room KMP 数据库，创建 `ImageEntity` 与 `ImageDao`。

> 附带产出：`SmartDispatcher` 智能双轨路由状态机、`TagTrie` Danbooru 联想树、
> `WildcardEngine` 通配符引擎、`PromptDraftStore` 图库回填契约；全部附 jvmTest 单元测试。

---

## 阶段 2：生成工作台与 V5 角色画板 (P2 - Feature Workbench)

- [x] **Task 2.1**: 开发 `PromptEditor` (支持 Danbooru Tag Trie 树联想输入、`{}`/`[]` 权重语法高亮)。
- [x] **Task 2.2**: 开发 `V5CharacterCanvas` (支持在 Compose Canvas 上添加、选择和拖拽 V5 最多 22 个角色中心点，绑定独立 Prompt/UC；V4.5 按官方上限限制为 6 个)。
- [x] **Task 2.3**: 开发参数面板 (模型/比例胶囊、高级采样参数、真实 Opus usage 状态环、V5 透明背景、跨端选图及付费确认式 Vibe Transfer 托盘)。
- [x] **Task 2.4**: 实现应用级持久化串行生成队列与下载存储管道：每次明确的用户操作只入队一个不可变参数快照，并按 FIFO 单消费者提交；`n_samples` 的多图响应支持 PNG/ZIP，ZIP 按 16 张 PNG、64 个条目、单条目 32 MiB、总解压 128 MiB 上限解析；单图文件先临时落盘、校验后发布至应用隔离目录，再批量写入数据库，失败时执行补偿清理并报告未完整回滚；未提交任务可取消，运行中只能停止本地等待，传输错误和进程中断均不自动重试或重提交。

---

## 阶段 3：万级虚拟瀑布流图库与灯箱 (P3 - Feature Gallery)

- [x] **Task 3.1**: 开发自适应多列虚拟瀑布流 `LazyVerticalStaggeredGrid`：Room/Paging 3 以 60 条分页、300 条最大加载窗口稳定读取万级记录；Coil Compose 默认共享加载器仅按布局约束解码平台缩略图并复用内存缓存，不在网格回退加载原图。
- [x] **Task 3.2**: 实现图片卡片悬浮/选中描边与保序多选操作：全选仅读取 Paging 已加载快照；批量评分以单条 Room SQL 同步 `starRating/isFavorite`；桌面目录选择器与 Android SAF 均按不覆盖策略复制原图并报告部分失败；最多两个稳定图片 ID 可直接带入对比实验室。
- [x] **Task 3.3**: 开发自适应全屏大图灯箱 `ImageViewer`：支持双击 1x/2.5x 弹簧切换、1x–5x 捏合缩放、放大后拖拽与视口边界钳位；窄屏改用纵向布局，PNG Info 可滚动展示并支持「一键回填到工作台」。

---

## 阶段 4：对比实验室与滑动筛选流 (P4 - Compare & Swipe)

- [x] **Task 4.1**: `SplitSliderViewer` 使用 Skia `clipRect` 卷帘分屏；分割位置按实际容器宽度归一化并有路径切换复位与边界测试。120Hz 实机帧率尚未测量。
- [x] **Task 4.2**: `SideBySideViewer` 实现双图共享缩放/位移、双击 1x/2.5x、1x–5x 钳位及视口边界限制。
- [x] **Task 4.3**: `SwipeCardDeck` 采用稳定的 `detectDragGestures` 单飞行动画门闩实现左/右滑筛选、严格 35% 宽度阈值、键盘方向键与 Room 偏好打标；并非字面使用实验性的 `AnchoredDraggableState`。
- [x] **Task 4.4**: 偏好雷达按喜欢图片分页读取提示词，使用有界标签解析与去重统计 Top Tags，并反哺工作台建议词；包含过期请求保护与失败保留。

---

## 阶段 5：跨端适配与原生打包 (P5 - Polish & Distribution)

- [x] **Task 5.1**: 完成自适应多端断点联动：手机使用四主入口 NavigationBar +“更多”菜单与参数 ModalBottomSheet，PC 使用品牌化 NavigationRail、自定义标题栏和 Ctrl+1…6 导航快捷键；JVM 编译与桌面启动冒烟通过。
- [x] **Task 5.2**: R8 FullMode release 构建成功，APK 已包含 `baseline.prof`/`baseline.profm`；已提供手动 profile 入口与可构建的 baseline profile generator。官方 Baseline Profile Gradle Plugin 1.4.1 与当前 AGP/KMP 组合不兼容，真机采集仍待办。
- [x] **Task 5.3**: Windows 原生分发实际验收通过：portable Zip 为 74,436,985 bytes、281 个条目（含 launcher/runtime），MSI 为 75,529,404 bytes；同时保留 Exe 任务。
- [x] **Task 5.4**: 完成前端视觉系统重构：静态灰蓝语义调色板、紧凑中文 Typography、分层 Shape、共享页面头/区块/状态/空态组件；工作台宽屏双列独立滚动并前置预览与生成动作，图库改为图片优先卡片，六个页面统一 Material 图标与信息层级。桌面扩展图标依赖已缩减为核心图标集，避免约 37 MB 的无用分发开销；`allTests`、Android debug/release、lintRelease、Windows portable/MSI 与桌面启动冒烟均通过。GUI 自动截图通道不可用，因此最终主观视觉仍需人工验收。
- [ ] **Task 5.5**: 端到端全链路冒烟测试与 120Hz 掉帧检测尚未完成；真实付费生成、设备采集及 120Hz 实机验证明确不纳入自动化冒险执行。
