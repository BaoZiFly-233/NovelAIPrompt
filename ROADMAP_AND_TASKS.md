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
  - `GET /user/subscription` (拉取订阅档位与 Anlas)
  - `GET /user/data` (解析 V5 Opus 充能电池百分比)
- [x] **Task 1.2**: 编写 `OpusFreeCalculator` (免费像素面积钳位与步数自适应校验算法)。
- [x] **Task 1.3**: 基于 Okio 编写 `PngChunkParser`，实现零拷贝提取 PNG 内 `tEXt:Comment` 生成元数据。
- [x] **Task 1.4**: 构建 `core:database` Room KMP 数据库，创建 `ImageEntity` 与 `ImageDao`。

> 附带产出：`SmartDispatcher` 智能双轨路由状态机、`TagTrie` Danbooru 联想树、
> `WildcardEngine` 通配符引擎、`PromptDraftStore` 图库回填契约；全部附 jvmTest 单元测试。

---

## 阶段 2：生成工作台与 V5 角色画板 (P2 - Feature Workbench)

- [ ] **Task 2.1**: 开发 `PromptEditor` (支持 Danbooru Tag Trie 树联想输入、`{}`/`[]` 权重语法高亮)。
- [ ] **Task 2.2**: 开发 `V5CharacterCanvas` (支持在 Compose Canvas 上拖拽添加、缩放最多 22 个角色坐标框，绑定独立 Prompt)。
- [ ] **Task 2.3**: 开发参数面板 (模型选择、比例胶囊选择器、Opus 电池状态环、透明背景开关、Vibe Transfer 托盘)。
- [ ] **Task 2.4**: 实现批量生成队列控制器与后台下载存储管道。

---

## 阶段 3：万级虚拟瀑布流图库与灯箱 (P3 - Feature Gallery)

- [ ] **Task 3.1**: 开发自适应多列虚拟瀑布流 `LazyVerticalStaggeredGrid`，集成 Coil 3 位图池与 WebP 缩略图加载。
- [ ] **Task 3.2**: 实现图片卡片悬浮状态与多选操作 (批量打标、批量导出、加入对比)。
- [ ] **Task 3.3**: 开发大图灯箱 `ImageViewer` (支持手势双击缩放、拖拽平移、PNG Info 侧边栏展示与「一键回填到工作台」)。

---

## 阶段 4：对比实验室与滑动筛选流 (P4 - Compare & Swipe)

- [ ] **Task 4.1**: 开发 `SplitSliderViewer` (基于 Skia `clipRect` 实现 120fps 高性能卷帘分屏比对)。
- [ ] **Task 4.2**: 开发 `SideBySideViewer` (双图镜像缩放与位移联动)。
- [ ] **Task 4.3**: 基于 `AnchoredDraggableState` 开发 `SwipeCardDeck` (左滑不喜欢、右滑喜欢、粒子动效与 Room 偏好打标)。
- [ ] **Task 4.4**: 开发用户偏好雷达分析器 (统计喜欢图片中的 Top Tags 并反哺给工作台推荐词)。

---

## 阶段 5：跨端适配与原生打包 (P5 - Polish & Distribution)

- [ ] **Task 5.1**: 自适应多端断点联动 (手机 NavigationBar + BottomSheet，PC NavigationRail + 自定义标题栏/快捷键)。
- [ ] **Task 5.2**: Android 端配置 Baseline Profile 与 R8 FullMode 混淆优化。
- [ ] **Task 5.3**: Windows 桌面端配置 Compose Gradle `nativeDistributions` (打包绿色免安装 Zip / MSI 安装包)。
- [ ] **Task 5.4**: 端到端全链路冒烟测试与 120Hz 掉帧检测。
