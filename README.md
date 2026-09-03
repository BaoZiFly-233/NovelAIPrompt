# NovelAI Diffusion Studio (MD3E Native Client)

> 基于 **Kotlin + Compose Multiplatform (CMP)** 原生渲染管线打造的高性能、多端自适应 NovelAI 图像生成应用。  
> 专为 **PC (Windows) + Android** 双端设计，拒绝 WebView 框架内存膨胀，追求纯原生 120Hz 极致性能。

---

## 核心设计理念

1. **绝对原生渲染**：采用 Google 与 JetBrains 维护的 Compose Multiplatform + Skia GPU 原生绘制管线，彻底消除 WebView2/Chromium 的冗余多进程开销，位图池由 Coil 3 统一接管，实现万级图库无卡顿浏览。
2. **MD3E (Material 3 Expressive) 一等公民**：全面融入 Material 3 表现力设计规范，包括 HCT 动态取色提取、弹簧物理动效（Spring Dynamics）、Navigation Rail 与 Bottom Bar 的自适应断点切换。
3. **NovelAI V5 算力最大化套利**：深度结合 NovelAI Diffusion V5 模型的 Opus 充能电池规则与 V4.5 无限池，实现智能双轨算力路由、22 角色独立坐标定位画板与原生透明通道支持。
4. **全功能闭环流**：涵盖「智能工作台」➔「万级虚拟瀑布流图库」➔「卷帘/同动对比实验室」➔「手势卡片偏好筛选」➔「Skia 原生 Inpainting 精修」。

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

# 桌面端打包（Zip/MSI，输出 composeApp/build/compose/binaries）
./gradlew :composeApp:packageDistributionForCurrentOS

# Android APK（输出 androidApp/build/outputs/apk）
./gradlew :androidApp:assembleDebug

# 单元测试（Opus 钳位算法、双轨调度、PNG 解析等）
./gradlew jvmTest
```

> 依赖存档说明：`GRADLE_USER_HOME` 已写死进 `gradlew`/`gradlew.bat`，任何人在任何目录执行
> wrapper 都会把依赖下载到 `D:\DevCaches\gradle`，不会污染 C 盘用户目录。

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
│   ├── designsystem/               # MD3E Tokens、HCT 动态调色盘、弹簧动效规范
│   └── common/                     # PNG tEXt Chunk 解析器、Wildcard 引擎、Trie 树
└── feature/
    ├── workbench/                  # 提示词编辑器、22 角色定位画板、参数面板
    ├── gallery/                    # 动态多列虚拟瀑布流、PNG Info 读取、灯箱
    ├── compare/                    # GPU 卷帘分屏对比、两图镜像同步平移缩放
    ├── swipe/                      # MD3E 卡片堆叠手势流、偏好统计雷达
    └── inpaint/                    # Skia 原生局部重绘涂抹画板
```
