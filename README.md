# NovelAI Diffusion Studio (MD3E Native Client)

> 基于 **Kotlin + Compose Multiplatform (CMP)** 原生渲染管线打造的高性能、多端自适应 NovelAI 图像生成应用。  
> 专为 **PC (Windows) + Android** 双端设计，拒绝 WebView 框架内存膨胀，追求纯原生 120Hz 极致性能。

---

## 核心设计理念

1. **绝对原生渲染**：采用 Google 与 JetBrains 维护的 Compose Multiplatform + Skia GPU 原生绘制管线，彻底消除 WebView2/Chromium 的冗余多进程开销；图库通过 Room/Paging 3 分页、Coil 3 约束尺寸解码与内存缓存控制资源占用。
2. **Material 3 创作工作台**：使用中性灰蓝语义色板、克制的 Surface 层级与重点交互弹簧动效；Navigation Rail、精简 Bottom Bar 和工作台双列布局会按宽度自适应切换。
3. **NovelAI V5 用量安全调度**：结合 NovelAI Diffusion V5 的 Opus usage 规则与 V4.5 无限池，在避免意外计费的前提下实现双轨路由、22 角色独立坐标定位画板与原生透明通道支持。
4. **全功能闭环流**：涵盖「智能工作台」➔「应用级持久化串行生成队列与下载存储」➔「万级虚拟瀑布流图库」➔「卷帘/同动对比实验室」➔「手势卡片偏好筛选」➔「Skia 原生 Inpainting 精修」。

---

## 构建指南 (Build Guide)

本项目所有工具链与依赖缓存**强制落盘 D 盘**（C 盘空间受限），约定如下：

| 组件 | 位置 | 说明 |
| :--- | :--- | :--- |
| JDK 17 (Temurin) | `D:\DevTools\jdk-17` | 构建 JDK，运行前设置 `JAVA_HOME` |
| Gradle 9.7.1 发行版 | `D:\DevTools\gradle-9.7.1` | Wrapper 引导用；项目日常使用 `gradlew` |
| Gradle 依赖缓存 | `D:\DevCaches\gradle` | `gradlew` 已内置 `GRADLE_USER_HOME` 指向此处 |
| Android SDK | `D:\DevTools\android-sdk` | `local.properties` 已指向此处（不入库） |

常用命令（Git Bash / PowerShell 均可）：

```bash
# 桌面端（Windows）运行
./gradlew :composeApp:run

# 桌面端打包（nativeDistributions）
./gradlew :composeApp:packageDistributionForCurrentOS

# Windows 绿色免安装 Zip（含 launcher/runtime）
./gradlew :composeApp:packagePortableZip

# Windows MSI 安装包
./gradlew :composeApp:packageMsi

# Android APK（输出 androidApp/build/outputs/apk）
./gradlew :androidApp:assembleDebug

# 单元测试（Opus 钳位算法、双轨调度、PNG 解析等）
./gradlew jvmTest

# Android Macrobenchmark（需要已连接的实体或模拟器设备）
./gradlew :baselineprofile:assembleBenchmark
./gradlew :baselineprofile:connectedBenchmarkAndroidTest
```

> 依赖存档说明：`GRADLE_USER_HOME` 已写死进 `gradlew`/`gradlew.bat`，任何人在任何目录执行
> wrapper 都会把依赖下载到 `D:\DevCaches\gradle`，不会污染 C 盘用户目录。

> 当前状态：前端视觉系统、六页信息层级与跨端导航已完成一轮重构；Windows portable Zip/MSI 和 Android
> debug/release 均已重新构建验收。Android release 的 R8 与 profile 产物已验收；官方 Baseline Profile Gradle
> Plugin 1.4.1 因与当前 AGP/KMP 组合不兼容，真机 profile 采集仍待办。Macrobenchmark harness 已可构建，
> 但尚未在设备上运行；真实 NovelAI 付费生成和 120Hz 实机掉帧采集不会由自动化测试触发。

---

## 文档索引

后续参与实现的 AI 模型或开发者，请按顺序查阅以下技术规约：

| 规约文档 | 内容与用途 | 重点实现模块 |
| :--- | :--- | :--- |
| [**ARCHITECTURE.md**](./ARCHITECTURE.md) | KMP 多模块架构、状态流设计、数据库与缓存方案 | `core:*`, `composeApp` |
| [**NOVELAI_V5_SPEC.md**](./NOVELAI_V5_SPEC.md) | NAI V5 API 协议、Opus 充能电池算法、角色坐标与元数据提取 | `core:network`, `core:model` |
| [**MD3E_DESIGN_SPEC.md**](./MD3E_DESIGN_SPEC.md) | Material 3 Expressive 规范、PC/Android 断点与弹簧物理体系 | `core:designsystem`, UI 层 |
| [**ROADMAP_AND_TASKS.md**](./ROADMAP_AND_TASKS.md) | 分阶段任务拆解、原子化 Checklist 与验收标准 | 全局开发执行 |

---

## 模块结构图

```
NovelAIPrompt/
├── composeApp/                     # 跨端入口与平台胶水层
│   ├── commonMain/                 # 跨平台顶层导航与依赖装配 (Koin)
│   ├── androidMain/                # Android 专有配置、原生手势、深链
│   └── desktopMain/                # Windows 桌面入口、窗口控制、Tray
├── core/
│   ├── network/                    # Ktor 客户端、NAI API 适配、Opus 电池监控
│   ├── database/                   # Room KMP、元数据、Star 评分、Tag 词频库
│   ├── model/                      # 领域实体 (Prompt, GenerationTask, V5Character)
│   ├── designsystem/               # Material 3 Tokens、共享工作台组件与弹簧动效规范
│   └── common/                     # PNG tEXt Chunk 解析器、Wildcard 引擎、Trie 树
└── feature/
    ├── workbench/                  # 提示词编辑器、22 角色定位画板、参数面板与生成队列入口
    ├── gallery/                    # Room 分页、瀑布流、多选导出、可缩放灯箱与 PNG Info
    ├── compare/                    # GPU 卷帘分屏对比、两图镜像同步平移缩放
    ├── swipe/                      # MD3E 卡片堆叠手势流、偏好统计雷达
    └── inpaint/                    # Skia 原生局部重绘涂抹画板
```
