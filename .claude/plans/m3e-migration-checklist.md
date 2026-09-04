# Material 3 Expressive (M3E) 完整迁移检查清单

## 当前状态诊断

### ✅ 已完成
1. **主题层**：已使用 `MaterialExpressiveTheme`
2. **颜色方案**：已定义 `studioDarkColorScheme()` / `studioLightColorScheme()`
3. **Typography**：已定义 `MD3ETypography`
4. **Shapes**：已定义 `MD3EShapes`（但使用硬编码圆角，未使用 M3E 默认）
5. **Motion**：已导出 `expressiveSlowSpatialSpec()` / `expressiveFastSpatialSpec()`

### ❌ 缺失项
1. **依赖管理**：
   - 缺少 `androidx.graphics:graphics-shapes`（MaterialShapes 需要）
   - 缺少 `material3-adaptive-*` 系列（自适应布局）
   - Compose Multiplatform 1.12.0 → Material3 版本未知（需确认 ≥ 1.4.0）

2. **主题配置**：
   - 缺少 `MotionScheme.expressive()` 显式传入
   - 自定义 Shapes 覆盖了 M3E 默认（应使用 MaterialTheme.shapes.* 直接引用）
   - 缺少 Dynamic Color 支持（Android 12+）

3. **组件替换**：
   - 仍在使用传统组件（Button、FilterChip、CircularProgressIndicator）
   - 未启用 M3E 新组件（SegmentedButton、FloatingToolbar、WavyProgressIndicator）

4. **动效规范**：
   - `MD3EMotion.kt` 定义了自定义 Spring，但未使用 `motionScheme.*Spec()`
   - 组件动画仍在手写 `tween` / `spring` 参数

5. **形状变形**：
   - 未使用 MaterialShapes（多边形预设）
   - 未使用 MorphShape（形状变形动画）

6. **Lint 检查**：
   - 无依赖冲突检查（可能混入 M2）
   - 无硬编码颜色/形状检查

---

## 实施计划

### Phase 1: 依赖与配置（30 分钟）

#### 1.1 添加 M3E 完整依赖

**文件：** `gradle/libs.versions.toml`

```toml
[versions]
# 新增
composeBom = "2024.12.00"        # 或更新的 BOM
graphicsShapes = "1.0.1"
material3Adaptive = "1.0.2"      # 或最新 alpha

[libraries]
# 新增
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
graphics-shapes = { module = "androidx.graphics:graphics-shapes", version.ref = "graphicsShapes" }
material3-adaptive = { module = "androidx.compose.material3.adaptive:adaptive", version.ref = "material3Adaptive" }
material3-adaptive-layout = { module = "androidx.compose.material3.adaptive:adaptive-layout", version.ref = "material3Adaptive" }
material3-adaptive-navigation = { module = "androidx.compose.material3.adaptive:adaptive-navigation", version.ref = "material3Adaptive" }
material3-adaptive-navigation-suite = { module = "androidx.compose.material3:material3-adaptive-navigation-suite", version.ref = "material3Adaptive" }
```

**注意：** Compose Multiplatform 项目不能直接用 AndroidX BOM，需要：
1. 检查 CMP 1.12.0 捆绑的 material3 版本
2. 如果 < 1.4.0，需要等待 CMP 1.13+ 或手动覆盖（可能破坏兼容性）

#### 1.2 更新 core/designsystem 依赖

**文件：** `core/designsystem/build.gradle.kts`

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.material3)
            api(compose.materialIconsExtended)
            
            // M3E 增强
            api(libs.graphics.shapes)              // MaterialShapes
            
            // Adaptive 布局（桌面端可选，但统一 API）
            api(libs.material3.adaptive)
            api(libs.material3.adaptive.layout)
            api(libs.material3.adaptive.navigation)
        }
    }
}
```

#### 1.3 启用全局 OptIn

**文件：** `core/designsystem/build.gradle.kts`

```kotlin
kotlin {
    sourceSets.all {
        languageSettings {
            optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            optIn("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
        }
    }
}
```

---

### Phase 2: 主题规范化（1 小时）

#### 2.1 修正 MD3ETheme 配置

**文件：** `core/designsystem/.../theme/MD3ETheme.kt`

**改动：**

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MD3ETheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,  // 新增：动态色支持
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        // Android 12+ 动态色（需要 platform-specific 实现）
        dynamicColor && Platform.isAndroid && Build.VERSION.SDK_INT >= 31 ->
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        darkTheme -> studioDarkColorScheme()
        else -> studioLightColorScheme()
    }
    
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),  // 关键：显式传入
        shapes = Shapes(),                         // 使用 M3E 默认，删除自定义
        typography = MD3ETypography,               // 保留中文优化
        content = content,
    )
}
```

**删除硬编码 Shapes：**

```kotlin
// ❌ 删除这些
val MD3EShapes = Shapes(...)
val MD3EPillShape = RoundedCornerShape(percent = 50)

// ✅ 直接使用 MaterialTheme.shapes.*
// MaterialTheme.shapes.small / medium / large / extraLarge
// MaterialTheme.shapes.largeIncreased / extraLargeIncreased (M3E 新增)
```

#### 2.2 扩展 Motion Spec 导出

**文件：** `core/designsystem/.../motion/MD3EMotion.kt`

**重构：** 移除自定义 Spring 常量，统一使用 `motionScheme`

```kotlin
package com.novelstudio.core.designsystem.motion

import androidx.compose.animation.core.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/** M3E 动效规范：所有动画统一取自 MaterialTheme.motionScheme */
object MD3EMotion {
    /** 快速空间变换：位置、尺寸（300ms） */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> fastSpatial(): AnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()
    
    /** 慢速空间变换：大范围位移、复杂布局（500ms） */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> slowSpatial(): AnimationSpec<T> = MaterialTheme.motionScheme.slowSpatialSpec()
    
    /** 快速效果：颜色、透明度（150ms） */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> fastEffects(): AnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()
    
    /** 慢速效果：渐变、阴影（300ms） */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> slowEffects(): AnimationSpec<T> = MaterialTheme.motionScheme.slowEffectsSpec()
    
    /** 默认效果（250ms，大多数场景） */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> defaultEffects(): AnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()
    
    // ❌ 删除所有硬编码 spring/tween：
    // val StandardEasing = tween<Float>(...)
    // val GentleSpring = spring<Float>(...)
    // 改为在使用处调用 MD3EMotion.fastSpatial<Float>() 等
}

/** 形状变形动画辅助（需要 graphics-shapes） */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberShapeMorph(from: Shape, to: Shape, progress: Float): Shape {
    // 使用 Morph + MorphShape（需要 MaterialShapes）
    // 这里简化，后续再实现
    return if (progress < 0.5f) from else to
}
```

**迁移说明：**

所有业务代码中的动画规格：

```kotlin
// ❌ 旧写法
val scale by animateFloatAsState(target, spring(dampingRatio = 0.8f, stiffness = 380f))

// ✅ 新写法
val scale by animateFloatAsState(target, MD3EMotion.fastSpatial())
```

#### 2.3 Typography 补充 Emphasized 变体

**文件：** `core/designsystem/.../theme/MD3ETypography.kt`

```kotlin
val MD3ETypography = Typography(
    // 已有的基础样式...
    
    // M3E 新增：Emphasized 强调样式（用于标题、CTA）
    displayLargeEmphasized = TextStyle(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold),
    displayMediumEmphasized = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
    titleLargeEmphasized = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMediumEmphasized = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.15.sp),
    labelLargeEmphasized = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp),
)
```

---

### Phase 3: 组件系统重构（3 小时）

#### 3.1 新建 M3E 组件封装

**文件：** `core/designsystem/.../components/StudioExpressiveComponents.kt`

```kotlin
package com.novelstudio.core.designsystem.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** 分段控制：单选模式（替代 FilterChip 单选） */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StudioSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selectedIndex == index,
                onClick = { onSelectionChange(index) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                enabled = enabled,
            ) {
                Text(label)
            }
        }
    }
}

/** 分段控制：多选模式 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StudioMultiSegmentedControl(
    options: List<String>,
    selectedIndices: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    MultiChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                checked = index in selectedIndices,
                onCheckedChange = { checked ->
                    onSelectionChange(
                        if (checked) selectedIndices + index
                        else selectedIndices - index
                    )
                },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label)
            }
        }
    }
}

/** 波浪进度指示器：确定进度 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StudioWavyProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    // LinearWavyProgressIndicator 在 1.5.0-alpha27 可用
    // 如果当前版本不支持，降级到 LinearProgressIndicator
    try {
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = modifier,
        )
    } catch (e: NoSuchMethodError) {
        // 降级
        LinearProgressIndicator(progress = { progress }, modifier = modifier)
    }
}

/** 加载指示器：不确定进度 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StudioLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    // LoadingIndicator 在 1.5.0-alpha27 可用
    try {
        LoadingIndicator(modifier)
    } catch (e: NoSuchMethodError) {
        CircularProgressIndicator(modifier)
    }
}

/** 浮动工具栏：上下文操作 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StudioFloatingToolbar(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable FloatingToolbarScope.() -> Unit,
) {
    // HorizontalFloatingToolbar 在 1.5.0-alpha27 可用
    // 降级方案：Surface + Row
    try {
        HorizontalFloatingToolbar(
            expanded = expanded,
            onDismiss = onDismiss,
            modifier = modifier,
            content = content,
        )
    } catch (e: NoSuchMethodError) {
        // 降级到自定义 Surface
        if (expanded) {
            Surface(
                modifier = modifier,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // content() 需要转换为普通 Composable
                }
            }
        }
    }
}

/** 按钮：M3E 形状变形 + 5 级尺寸 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StudioButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),  // 启用形状变形
        contentPadding = ButtonDefaults.contentPaddingFor(size.containerHeight),
    ) {
        content()
    }
}

enum class ButtonSize(val containerHeight: Dp) {
    XSmall(ButtonDefaults.XSmallContainerHeight),
    Small(ButtonDefaults.SmallContainerHeight),
    Medium(ButtonDefaults.MediumContainerHeight),
    Large(ButtonDefaults.LargeContainerHeight),
    XLarge(ButtonDefaults.XLargeContainerHeight),
}
```

#### 3.2 组件替换映射表

**创建迁移指南：** `.claude/plans/component-migration-map.md`

| 旧组件 | 新组件 | 文件位置 | 优先级 |
|--------|--------|----------|--------|
| `FilterChip`（单选） | `StudioSegmentedControl` | GenerationParameterPanel.kt | P0 |
| `CircularProgressIndicator` | `StudioLoadingIndicator` | PreviewStage.kt, GenerationQueuePanel.kt | P0 |
| `LinearProgressIndicator` | `StudioWavyProgress` | GalleryScreen.kt | P1 |
| `Button` | `StudioButton` + shapes | 全局 | P1 |
| `Row` + 工具按钮 | `StudioFloatingToolbar` | ImageToolsScreen.kt | P2 |
| 手写参数滑块 | 保留 `StudioParameterSlider` | - | 已完成 |

---

### Phase 4: 动效与形状迁移（2 小时）

#### 4.1 全局搜索替换动画规格

**脚本：** `.temp-migrate-motion-specs.sh`

```bash
#!/bin/bash
# 替换所有硬编码动画规格为 MD3EMotion 调用

# 1. expandVertically + spring → MD3EMotion.slowSpatial()
rg -l "expandVertically.*spring" --type kotlin | while read file; do
    # 需要手动审查，因为涉及泛型参数
    echo "TODO: $file - expandVertically with custom spring"
done

# 2. animateFloatAsState with custom spring
rg -l "animateFloatAsState.*spring\\(" --type kotlin | while read file; do
    echo "TODO: $file - animateFloatAsState with custom spring"
done

# 3. AnimatedContent transitionSpec
rg -l "slideInVertically.*fadeIn" --type kotlin | while read file; do
    echo "TODO: $file - AnimatedContent transition"
done
```

**手动迁移示例：**

```kotlin
// ❌ 旧代码
val scale by animateFloatAsState(
    targetValue = if (pressed) 0.95f else 1f,
    animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f)
)

// ✅ 新代码
val scale by animateFloatAsState(
    targetValue = if (pressed) 0.95f else 1f,
    animationSpec = MD3EMotion.fastSpatial()
)
```

#### 4.2 形状迁移：RoundedCornerShape → MaterialTheme.shapes

**全局替换：**

```kotlin
// ❌ 旧代码
Box(Modifier.clip(RoundedCornerShape(12.dp)))

// ✅ 新代码
Box(Modifier.clip(MaterialTheme.shapes.medium))
```

**映射表：**

| 旧圆角值 | 新 Shape 令牌 |
|---------|--------------|
| 4.dp | MaterialTheme.shapes.extraSmall |
| 8.dp | MaterialTheme.shapes.small |
| 12.dp | MaterialTheme.shapes.medium |
| 16.dp | MaterialTheme.shapes.large |
| 24.dp | MaterialTheme.shapes.extraLarge |
| 28.dp | MaterialTheme.shapes.largeIncreased (M3E) |
| 32.dp | MaterialTheme.shapes.extraLargeIncreased (M3E) |
| 50% | MaterialTheme.shapes.full (圆形/药丸) |

---

### Phase 5: 检查与验证（1 小时）

#### 5.1 依赖冲突检查

```bash
# 检查是否混入 Material 2
./gradlew :composeApp:dependencies --configuration jvmRuntimeClasspath | grep "androidx.compose.material:" | grep -v material3

# 预期输出：只有 material-icons（图标库），无 material:material

# 检查 Material3 版本
./gradlew :composeApp:dependencies --configuration jvmRuntimeClasspath | grep "material3:"
```

#### 5.2 硬编码检查脚本

**文件：** `.temp-lint-hardcoded-tokens.sh`

```bash
#!/bin/bash
echo "=== 检查硬编码颜色 ==="
rg "Color\(0x" --type kotlin src/ | grep -v "// TODO: migrate" | head -20

echo ""
echo "=== 检查硬编码圆角 ==="
rg "RoundedCornerShape\([0-9]" --type kotlin src/ | grep -v ".dp\)" | head -20

echo ""
echo "=== 检查硬编码动画 ==="
rg "tween\(|spring\(" --type kotlin src/ | grep -v "MD3EMotion" | head -20

echo ""
echo "=== 检查 Material 2 导入 ==="
rg "androidx\.compose\.material\." --type kotlin src/ | grep -v "material3" | grep -v "icons" | head -20
```

#### 5.3 Preview 覆盖

**每个 Screen 添加：**

```kotlin
@PreviewLightDark
@PreviewDynamicColors
@Composable
private fun WorkbenchScreenPreview() {
    MD3ETheme {
        WorkbenchScreen(/* ... preview state ... */)
    }
}
```

#### 5.4 运行时验证

```kotlin
// 在 App() 启动时打印 Motion Scheme 验证
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App() {
    MD3ETheme {
        val motionScheme = MaterialTheme.motionScheme
        LaunchedEffect(Unit) {
            println("Motion Scheme: ${motionScheme.javaClass.simpleName}")
            println("Fast Spatial: ${motionScheme.fastSpatialSpec<Float>()}")
        }
        // ...
    }
}
```

---

## 实施优先级

### P0（本周内完成）
1. ✅ 依赖添加（graphics-shapes, adaptive-*）
2. ✅ MD3ETheme 修正（MotionScheme.expressive(), 删除自定义 Shapes）
3. ✅ MD3EMotion 重构（移除硬编码 spring/tween）
4. ✅ StudioExpressiveComponents.kt 创建
5. ✅ GenerationParameterPanel SegmentedButton 替换
6. ✅ PreviewStage LoadingIndicator 替换

### P1（下周）
1. Gallery WavyProgressIndicator
2. 全局 Button shapes 启用
3. 全局动画规格迁移
4. 全局形状令牌迁移
5. 依赖冲突检查 + 修复

### P2（按需）
1. FloatingToolbar 应用
2. MaterialShapes 多边形
3. MorphShape 形状变形动画
4. Adaptive Layout（大屏优化）

---

## 风险与降级方案

### 风险 1：Compose Multiplatform 1.12.0 捆绑的 Material3 版本 < 1.4.0
**影响：** M3E 组件不可用
**降级：**
- 等待 CMP 1.13+ 更新
- 或手动覆盖 material3 版本（可能破坏稳定性）
- 使用 try-catch 降级到传统组件

### 风险 2：SegmentedButton 等 Alpha API 不稳定
**降级：**
- 保留 FilterChip 作为 fallback
- 使用 `@Suppress("DEPRECATION")` 避免编译警告

### 风险 3：Desktop 平台部分 M3E 组件渲染异常
**降级：**
- 平台检测 + 条件渲染
- Desktop 优先使用稳定组件

---

## 验收标准

- [ ] `./gradlew :composeApp:dependencies` 无 Material 2 依赖
- [ ] 所有动画使用 `MD3EMotion.*()` 或 `MaterialTheme.motionScheme.*Spec()`
- [ ] 所有形状使用 `MaterialTheme.shapes.*`
- [ ] 无硬编码 `Color(0x...)` / `RoundedCornerShape(数字.dp)`
- [ ] GenerationParameterPanel 模型选择使用 SegmentedButton
- [ ] PreviewStage 使用 LoadingIndicator
- [ ] 所有 Screen 有 `@PreviewLightDark` + `@PreviewDynamicColors`
- [ ] 编译通过，无 `@OptIn` 警告外泄

---

**签名：** 幽浮喵 φ(≧ω≦*)♪  
**日期：** 2026-09-04  
**预计完成：** 2026-09-11（7 天）