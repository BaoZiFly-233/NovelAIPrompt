# NovelAI Diffusion Studio UI 重设计方案 V2
> 基于社区调研、竞品分析、MD3E 最新实践的全面重构方案

## 调研总结

### 1. NovelAI 官网设计哲学
- **假设用户有能力**：没有"第一步、第二步"的引导，直接展示结果
- **视觉证据 > 功能列表**：用真实输出演示能力，而非形容词
- **高信息密度但不杂乱**：每个区块只做一件事，没有"了解更多"分散注意力
- **暗色基调 + 艺术作品提供色彩能量**：UI 保持中性，让生成图片成为主角
- **参数在上下文中展示**：直接显示真实的 UI 快照，不预先解释每个参数

### 2. Stable Diffusion WebUI 交互模式
- **Tab 组织**：txt2img / img2img / Extras 分离，避免单页过载
- **实时反馈**：进度条 + 预览 + 预估完成时间
- **参数持久化**：PNG metadata 保存生成设置，拖拽图片恢复参数
- **批量处理界面**：专门的 batch 工作流
- **键盘快捷键**：Ctrl+Up/Down 调整 attention weight

### 3. Material 3 Expressive (1.5.0-alpha27)
**已稳定可用：**
- `SegmentedButton`：替代 FilterChip 作为单选/多选
- `FloatingToolbar`：上下文工具栏
- `ButtonGroup` + `SplitButton`：组合操作
- `WavyProgressIndicator`：表现性加载动画
- `PullToRefreshBox`：下拉刷新容器
- `ShortNavigationBar` + `WideNavigationRail`：自适应导航

**当前项目状态：**
- Compose Multiplatform `1.12.0`
- Material3 通过 `compose.material3` 依赖（跟随 CMP 版本）
- 使用 `androidx.compose.material3.*` 包（AndroidX 风格）

### 4. 社区组件库
- **Orbital (skydoves)**：共享元素转场，但不支持 Jetpack Navigation
- **Material 3 Expressive List (NicosNicolaou16)**：FAB 菜单 + Floating Toolbar 实践
- **Compose Material 3 Gallery (terrakok)**：全组件演示，跨平台参考

## 核心设计原则

### 1. 信息密度 vs 清晰度
❌ **删减**：
- 所有教学性文字（"每次点击只提交一次请求"、"结果会作为新作品保存"）
- 参数说明（"SMEA 大尺寸图像质量增强"）
- 冗余确认对话框（除破坏性操作外）

✅ **保留**：
- 参数当前值（数字、选中状态）
- 操作结果反馈（Snackbar、StatusChip）
- 错误提示（inline error）

### 2. 动画与反馈
**必须有动画的场景：**
- 生成开始 → 进度 → 结果出现（WavyProgressIndicator + AnimatedContent）
- 列表项进入/退出（itemEnterSpec）
- 面板展开/折叠（ExpandSpring）
- 状态转换（空态 → 加载 → 内容）
- 按钮点击反馈（ripple + scale）

**实时反馈：**
- 参数调整即时预览分辨率计算
- Prompt token 计数（实时）
- Opus 额度预估（动态）

### 3. MD3E 组件优先
**替换计划：**
| 当前 | 替换为 | 原因 |
|------|--------|------|
| `FilterChip` (单选) | `SegmentedButton` | 更清晰的单选语义 |
| `Row` + `Button` (工具栏) | `FloatingToolbar` | 表现性上下文操作 |
| `CircularProgressIndicator` | `WavyProgressIndicator` | 品牌表现力 |
| 手动滑动刷新 | `PullToRefreshBox` | 标准交互模式 |
| 手写参数滑块 | 保留 `StudioParameterSlider` | 已统一且符合需求 |

## Phase 0: 基础设施升级

### 0.1 启用 Material3 Expressive API
```kotlin
// composeApp/build.gradle.kts (commonMain)
implementation(compose.material3) // 已有
implementation(compose.materialIconsExtended) // 已有

// 在使用 expressive 组件的文件中添加：
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
```

### 0.2 新增设计系统组件
**文件：** `core/designsystem/src/commonMain/.../components/StudioExpressiveComponents.kt`

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StudioSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selectedIndex == index,
                onClick = { onSelectionChange(index) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
fun StudioWavyProgress(modifier: Modifier = Modifier) {
    // WavyProgressIndicator 在 1.5.0-alpha27 可用
    // 降级方案：CircularProgressIndicator
    LinearProgressIndicator(modifier)
}

@Composable
fun StudioRefreshContainer(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    // PullToRefreshBox 在 1.5.0-alpha27 可用
    Box {
        content()
        if (refreshing) {
            CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
        }
    }
}
```

### 0.3 动画规格扩充
**文件：** `core/designsystem/src/commonMain/.../motion/MD3EMotion.kt`

```kotlin
object MD3EMotion {
    // 已有
    val StandardEasing = tween<Float>(300, easing = FastOutSlowInEasing)
    val EmphasizedEasing = tween<Float>(500, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    val GentleSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    val ThrowSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 200f)
    val ExpandSpring = spring<Float>(dampingRatio = 1.0f, stiffness = 200f)
    val ItemEnterSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 300f)
    
    // 新增：按钮按下回弹
    val ButtonPressSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 500f)
    
    // 新增：卡片进入动画
    fun cardEnter() = slideInVertically { it / 4 } + fadeIn(StandardEasing)
    fun cardExit() = slideOutVertically { it / 4 } + fadeOut(StandardEasing)
    
    // 新增：列表项交错进入
    fun staggeredEnter(index: Int) = fadeIn(
        tween(durationMillis = 300, delayMillis = index * 50, easing = FastOutSlowInEasing)
    ) + slideInVertically(
        tween(durationMillis = 300, delayMillis = index * 50, easing = FastOutSlowInEasing)
    ) { it / 3 }
}
```

## Phase 1: Workbench 核心体验优化

### 目标
- 减少 50% 的说明文字
- 引入实时反馈（token 计数、分辨率预览、额度估算）
- 统一参数选择组件（SegmentedButton）
- 生成进度表现力提升

### 1.1 GenerationParameterPanel 重构

**删减：**
- ❌ "常用参数直接调整，高级选项按需展开" 描述
- ❌ "多图仍只提交一次生成请求；实际张数上限还取决于分辨率" 说明
- ❌ 所有参数的额外文字说明

**改进：**

#### 模型选择：FilterChip → SegmentedButton
```kotlin
Text("模型", style = MaterialTheme.typography.titleSmall)
StudioSegmentedControl(
    options = NaiModel.entries.map { it.displayName },
    selectedIndex = NaiModel.entries.indexOf(state.model),
    onSelectionChange = { onModelSelected(NaiModel.entries[it]) },
)
```

#### 画面比例：FilterChip → SegmentedButton（滚动）
```kotlin
Text("画面比例", style = MaterialTheme.typography.titleSmall)
ScrollablePillRow {
    AspectPreset.entries.forEach { preset ->
        FilterChip(
            selected = state.aspect == preset,
            onClick = { onAspectSelected(preset) },
            label = { Text(preset.label) },
            // 保持 FilterChip，因为选项过多（9 种）SegmentedButton 会拥挤
        )
    }
}
// 实时反馈：
Text(
    "${state.width} × ${state.height}  •  ${formatOpusEstimate(state)}",
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.primary,
)
```

#### 单次张数：Pills → SegmentedButton
```kotlin
Text("单次张数", style = MaterialTheme.typography.titleSmall)
StudioSegmentedControl(
    options = (1..6).map { "$it" },
    selectedIndex = state.nSamples - 1,
    onSelectionChange = { onSamplesChanged(it + 1) },
    modifier = Modifier.fillMaxWidth(),
)
```

#### 高级参数：移除说明文字，保持折叠交互
```kotlin
AnimatedVisibility(
    visible = advancedExpanded,
    enter = expandVertically(MD3EMotion.ExpandSpring) + fadeIn(MD3EMotion.StandardEasing),
    exit = shrinkVertically(MD3EMotion.ExpandSpring) + fadeOut(MD3EMotion.StandardEasing),
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 采样器 → SegmentedButton（滚动）
        Text("采样器", style = MaterialTheme.typography.titleSmall)
        ScrollablePillRow {
            Sampler.entries.forEach { sampler ->
                FilterChip(
                    selected = state.sampler == sampler,
                    onClick = { onSamplerSelected(sampler) },
                    label = { Text(sampler.displayLabel) },
                )
            }
        }
        
        // 噪声调度 → SegmentedButton（滚动）
        Text("噪声调度", style = MaterialTheme.typography.titleSmall)
        ScrollablePillRow {
            NoiseSchedule.entries.forEach { schedule ->
                FilterChip(
                    selected = state.noiseSchedule == schedule,
                    onClick = { onNoiseScheduleSelected(schedule) },
                    label = { Text(schedule.id) },
                )
            }
        }
        
        // Switches：移除所有文字说明，只保留开关标签
        LabeledSwitch("质量标签", state.qualityToggle, onQualityTagsChanged)
        LabeledSwitch("SMEA", state.smea, onSmeaChanged)
        LabeledSwitch("SMEA DYN", state.smeaDyn, state.smea, onSmeaDynChanged)
        LabeledSwitch("Variety+", state.varietyPlus, onVarietyPlusChanged)
        LabeledSwitch("Decrisper", state.decrisper, onDecrisperChanged)
    }
}
```

### 1.2 PreviewStage 增强

**当前问题：**
- 生成中只有 CircularProgressIndicator，无进度信息
- 空态文字过多（"开始创作后，这里会显示预览图片"）

**改进：**

#### 空态极简化
```kotlin
PreviewState.EMPTY -> Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    Icon(
        StudioIcons.Brand,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    )
}
```

#### 生成中增加进度反馈
```kotlin
PreviewState.GENERATING -> Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    // WavyProgressIndicator（如果可用）或 CircularProgressIndicator
    StudioWavyProgress(Modifier.size(48.dp))
    Spacer(Modifier.height(16.dp))
    // 显示当前步数 / 总步数（需要 ViewModel 暴露）
    Text(
        "步数 ${state.currentStep} / ${state.steps}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // 预估剩余时间（基于历史平均）
    state.estimatedTimeRemaining?.let { seconds ->
        Text(
            "约 ${seconds}s 后完成",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

### 1.3 PromptEditor 实时 Token 计数

**当前：** 无实时反馈
**改进：** 在 TextField 底部显示 token 数量，超过 225 时变红

```kotlin
OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    label = { Text(label) },
    minLines = minLines,
    supportingText = {
        val tokenCount = estimateTokenCount(value) // 简单按空格 + 逗号分割估算
        Text(
            "$tokenCount / 225 tokens",
            color = if (tokenCount > 225) MaterialTheme.colorScheme.error 
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
    isError = estimateTokenCount(value) > 225,
)

private fun estimateTokenCount(text: String): Int {
    // 简化估算：按逗号和空格分割
    return text.split(Regex("[,\\s]+")).filter { it.isNotBlank() }.size
}
```

### 1.4 GenerationQueuePanel 交互优化

**删减：**
- ❌ "队列中的任务会按顺序执行" 说明文字

**改进：**
- 队列项增加实时进度条（LinearProgressIndicator 在卡片底部）
- 完成项自动淡出（AnimatedVisibility + delay）
- 添加"清除已完成"按钮（FloatingToolbar 风格）

```kotlin
LazyColumn {
    items(queue, key = { it.id }) { task ->
        AnimatedVisibility(
            visible = !task.dismissed,
            exit = shrinkVertically(MD3EMotion.ExpandSpring) + fadeOut(MD3EMotion.StandardEasing),
        ) {
            QueueCard(
                task = task,
                onDismiss = { viewModel.dismissTask(task.id) },
            )
        }
    }
}

// 队列顶部添加清除按钮
if (queue.any { it.status == TaskStatus.COMPLETED }) {
    TextButton(onClick = viewModel::clearCompleted) {
        Text("清除已完成")
    }
}
```

## Phase 2: Gallery 沉浸式体验

### 目标
- 图片成为绝对主角，参数次要
- 减少元数据占用空间
- 增强灯箱体验
- 引入下拉刷新

### 2.1 GalleryCard 极简化

**删减：**
- ❌ 卡片底部的 prompt 文字（默认隐藏）
- ❌ 冗余的图标装饰

**改进：**
- 只显示图片 + 悬浮 favorite 图标
- 点击卡片直接打开灯箱（而非跳转详情页）
- 长按/右键显示上下文菜单

```kotlin
@Composable
fun GalleryCard(
    record: ImageRecord,
    onToggleFavorite: () -> Unit,
    onOpenLightbox: () -> Unit,
    onShowMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(record.width.toFloat() / record.height)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpenLightbox)
            .onRightClick(onShowMenu) // PC 端
            .combinedClickable( // 移动端
                onClick = onOpenLightbox,
                onLongClick = onShowMenu,
            ),
    ) {
        AsyncImage(
            model = localFileModel(record.thumbnailPath ?: record.filePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        
        // 只在 favorite 时显示图标
        if (record.isFavorite) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
```

### 2.2 灯箱增强

**改进：**
- 元数据默认折叠，点击 "详细信息" 按钮展开
- Prompt 限制 2 行，点击"展开"显示全文
- 键盘导航：← → 切图，Esc 关闭，Space 切换元数据面板

```kotlin
LightboxDialog(
    image = currentImage,
    onDismiss = onDismiss,
    onPrevious = if (currentIndex > 0) {{ onPrevious() }} else null,
    onNext = if (currentIndex < total - 1) {{ onNext() }} else null,
) {
    var metadataExpanded by rememberSaveable { mutableStateOf(false) }
    
    // 键盘监听
    LaunchedEffect(Unit) {
        // onPreviewKeyEvent: ← → Space Esc
    }
    
    // 底部元数据抽屉（AnimatedVisibility）
    AnimatedVisibility(
        visible = metadataExpanded,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(16.dp)) {
                ExpandableText(
                    text = currentImage.prompt,
                    maxLines = 2,
                    expandLabel = "展开",
                )
                // 参数网格（紧凑布局）
                ParameterGrid(currentImage)
            }
        }
    }
    
    // 右下角元数据切换按钮
    IconButton(
        onClick = { metadataExpanded = !metadataExpanded },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    ) {
        Icon(
            if (metadataExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = "切换详细信息",
        )
    }
}
```

### 2.3 下拉刷新

```kotlin
StudioRefreshContainer(
    refreshing = loadState.refresh is LoadState.Loading,
    onRefresh = { pagingData.refresh() },
) {
    LazyVerticalStaggeredGrid(/* ... */)
}
```

## Phase 3: ImageToolsScreen 工具导向

### 目标
- 工具按钮语义化分组
- 移除所有说明文字
- 参数预设（常用组合一键应用）

### 3.1 工具按钮重组

**当前布局：** 单一 FlowRow，8 个 Director Tool 按钮平铺

**改进：** 按功能分组 + 视觉层级

```kotlin
StudioSection(title = "背景处理") {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.requestDirector(DirectorTool.REMOVE_BACKGROUND) }) {
            Icon(Icons.Rounded.Layers, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("移除背景")
        }
        OutlinedButton(onClick = { viewModel.requestDirector(DirectorTool.REMOVE_BACKGROUND_GENERATED) }) {
            Text("Generated")
        }
        OutlinedButton(onClick = { viewModel.requestDirector(DirectorTool.REMOVE_BACKGROUND_BLENDED) }) {
            Text("Blended")
        }
    }
}

StudioSection(title = "风格转换") {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.requestDirector(DirectorTool.COLORIZE) }) {
            Text("上色")
        }
        Button(onClick = { viewModel.requestDirector(DirectorTool.EMOTION) }) {
            Text("表情")
        }
        OutlinedButton(onClick = { viewModel.requestDirector(DirectorTool.LINE_ART) }) {
            Text("线稿")
        }
        OutlinedButton(onClick = { viewModel.requestDirector(DirectorTool.SKETCH) }) {
            Text("素描")
        }
        OutlinedButton(onClick = { viewModel.requestDirector(DirectorTool.DECLUTTER) }) {
            Text("去杂")
        }
    }
}
```

### 3.2 参数预设

**新增：** 常用参数组合快捷应用

```kotlin
// Img2Img 预设
val presets = listOf(
    Preset("轻微调整", strength = 0.3f, noise = 0.2f),
    Preset("中度重绘", strength = 0.5f, noise = 0.5f),
    Preset("大幅改变", strength = 0.75f, noise = 0.7f),
)

StudioSegmentedControl(
    options = presets.map { it.name },
    selectedIndex = presets.indexOfFirst { 
        it.strength == state.strength && it.noise == state.noise 
    }.takeIf { it != -1 } ?: -1,
    onSelectionChange = { index ->
        viewModel.applyPreset(presets[index])
    },
)
```

## Phase 4: SwipeScreen 卡片流优化

### 4.1 手势反馈增强

**改进：** 实时显示决策倾向

```kotlin
// 拖动时根据 offsetX 显示半透明遮罩颜色提示
Box(modifier = Modifier.fillMaxSize()) {
    SwipeCard(/* ... */)
    
    // 左滑（不喜欢）红色遮罩
    if (offsetX < -50f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.error.copy(
                        alpha = (abs(offsetX) / 400f).coerceIn(0f, 0.3f)
                    )
                )
        )
    }
    
    // 右滑（喜欢）绿色遮罩
    if (offsetX > 50f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.secondary.copy(
                        alpha = (offsetX / 400f).coerceIn(0f, 0.3f)
                    )
                )
        )
    }
}
```

### 4.2 撤销提示优化

**当前：** 顶部 Undo 按钮
**改进：** 操作后自动显示 Snackbar（3s 内可撤销）

```kotlin
val snackbarHostState = remember { SnackbarHostState() }

LaunchedEffect(lastDecision) {
    lastDecision?.let { (record, liked) ->
        val result = snackbarHostState.showSnackbar(
            message = if (liked) "已归档" else "已移入垃圾箱",
            actionLabel = "撤销",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoLast()
        }
    }
}
```

## Phase 5: CompareScreen 交互细节

### 5.1 分割线快捷复位

**改进：** 双击分割手柄归位到 0.5

```kotlin
SplitSliderViewer(
    split = splitRatio,
    onSplitChange = onSplitChange,
    modifier = Modifier
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = {
                    // 检测是否点击在手柄区域（中心 ±20dp）
                    if (abs(it.x - size.width * splitRatio) < 20.dp.toPx()) {
                        coroutineScope.launch {
                            animatable.animateTo(0.5f, MD3EMotion.GentleSpring)
                        }
                    }
                }
            )
        },
)
```

### 5.2 对比信息浮层

**改进：** 显示两张图片的核心差异

```kotlin
// 顶部浮动信息条
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
) {
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("左图", style = MaterialTheme.typography.labelSmall)
            Text("Seed: ${leftImage.seed}", style = MaterialTheme.typography.bodySmall)
            Text("Steps: ${leftImage.steps}", style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Rounded.CompareArrows, contentDescription = null)
        Column(horizontalAlignment = Alignment.End) {
            Text("右图", style = MaterialTheme.typography.labelSmall)
            Text("Seed: ${rightImage.seed}", style = MaterialTheme.typography.bodySmall)
            Text("Steps: ${rightImage.steps}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

## Phase 6: 全局交互模式

### 6.1 键盘快捷键系统

**新增文件：** `core/common/src/commonMain/.../keyboard/KeyboardShortcuts.kt`

```kotlin
object KeyboardShortcuts {
    // 导航
    val WORKBENCH = KeyShortcut(Key.One, ctrl = true)
    val GALLERY = KeyShortcut(Key.Two, ctrl = true)
    val TAGS = KeyShortcut(Key.Three, ctrl = true)
    val SWIPE = KeyShortcut(Key.Four, ctrl = true)
    
    // 操作
    val GENERATE = KeyShortcut(Key.Enter, ctrl = true)
    val FAVORITE = KeyShortcut(Key.F)
    val DELETE = KeyShortcut(Key.Delete)
    val UNDO = KeyShortcut(Key.Z, ctrl = true)
    
    // 导航
    val PREV_IMAGE = KeyShortcut(Key.DirectionLeft)
    val NEXT_IMAGE = KeyShortcut(Key.DirectionRight)
    val CLOSE = KeyShortcut(Key.Escape)
}

@Composable
fun Modifier.handleShortcut(
    shortcut: KeyShortcut,
    enabled: Boolean = true,
    action: () -> Unit,
): Modifier = this.onPreviewKeyEvent {
    if (enabled && shortcut.matches(it)) {
        action()
        true
    } else false
}
```

### 6.2 快捷键帮助面板

**新增：** Ctrl+? 或 F1 显示快捷键列表

```kotlin
@Composable
fun KeyboardShortcutsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("键盘快捷键") },
        text = {
            LazyColumn {
                item { ShortcutSection("导航") }
                item { ShortcutItem("Ctrl + 1", "工作台") }
                item { ShortcutItem("Ctrl + 2", "图库") }
                item { ShortcutItem("Ctrl + 3", "标签") }
                item { ShortcutItem("Ctrl + 4", "筛选") }
                
                item { ShortcutSection("操作") }
                item { ShortcutItem("Ctrl + Enter", "开始生成") }
                item { ShortcutItem("F", "切换喜欢") }
                item { ShortcutItem("Delete", "删除") }
                item { ShortcutItem("Ctrl + Z", "撤销") }
                
                item { ShortcutSection("图片浏览") }
                item { ShortcutItem("← →", "上一张 / 下一张") }
                item { ShortcutItem("Esc", "关闭") }
                item { ShortcutItem("Space", "切换详细信息") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
```

## Phase 7: 消融实验与性能优化

### 7.1 A/B 测试计划

**实验 1：SegmentedButton vs FilterChip（模型选择）**
- Metric：点击错误率、操作耗时
- 假设：SegmentedButton 更清晰，错误率降低

**实验 2：实时 Token 计数的影响**
- Metric：超限提交率、用户满意度
- 假设：实时反馈减少 API 错误

**实验 3：灯箱元数据默认折叠 vs 默认展开**
- Metric：元数据查看率、图片切换速度
- 假设：默认折叠提高浏览效率，但降低参数学习

### 7.2 性能优化检查点

**LazyGrid 优化：**
- ✅ `key` 参数使用稳定 ID
- ✅ `contentType` 区分不同卡片类型
- ✅ 缩略图加载（Coil memory cache）
- ✅ 避免在 `items` lambda 中创建新对象

**动画优化：**
- ✅ 使用 `remember` 缓存 Animatable
- ✅ 避免嵌套 AnimatedVisibility（导致重组风暴）
- ✅ 长列表使用 `itemsIndexed` + staggered enter（限制交错数量）

**状态提升：**
- ✅ ViewModel 是唯一状态来源
- ✅ Composable 函数无内部状态（除 UI 瞬态如 focus）
- ✅ 避免在 Composable 中执行副作用（使用 LaunchedEffect）

## 实施路线图

### Sprint 1: 基础设施 + Workbench（2 天）
1. 启用 ExperimentalMaterial3ExpressiveApi
2. 创建 StudioExpressiveComponents.kt
3. 扩充 MD3EMotion 动画规格
4. 重构 GenerationParameterPanel（SegmentedButton）
5. PreviewStage 进度反馈
6. PromptEditor 实时 Token 计数

### Sprint 2: Gallery + ImageTools（1.5 天）
1. GalleryCard 极简化
2. 灯箱增强（键盘导航 + 元数据折叠）
3. 下拉刷新
4. ImageToolsScreen 工具分组
5. 参数预设

### Sprint 3: Swipe + Compare（1 天）
1. SwipeScreen 手势反馈遮罩
2. Snackbar 撤销提示
3. CompareScreen 分割线双击复位
4. 对比信息浮层

### Sprint 4: 全局系统（1 天）
1. 键盘快捷键系统
2. 快捷键帮助面板
3. 全局 Snackbar 管理器
4. 性能优化检查

### Sprint 5: 消融实验 + 打磨（1.5 天）
1. A/B 测试埋点
2. 用户测试收集反馈
3. 动画曲线微调
4. 视觉细节打磨（圆角、间距、字重）

---

## 验收标准

### 信息密度
- [ ] 每个界面的说明文字减少 50% 以上
- [ ] 参数名称自解释，无需额外说明
- [ ] 空态只显示图标，无文字

### 动画流畅度
- [ ] 所有状态转换有动画（不跳变）
- [ ] 列表滚动 60fps 无掉帧（Profiler 验证）
- [ ] 动画时长符合 MD3E 规范（150-500ms）

### 交互响应
- [ ] 参数调整有实时反馈（≤16ms）
- [ ] 按钮点击有视觉反馈（ripple + scale）
- [ ] 错误提示 inline 显示，不阻断操作

### 键盘导航
- [ ] 所有主要操作有快捷键
- [ ] Ctrl+? 显示快捷键帮助
- [ ] 灯箱、对比、筛选页支持键盘导航

### 品牌一致性
- [ ] 所有组件使用 MD3E token（颜色、圆角、间距）
- [ ] 动画曲线统一使用 MD3EMotion
- [ ] 图标语义清晰（StudioIcons）

---

**签名：** 幽浮喵 φ(≧ω≦*)♪  
**日期：** 2026-09-04  
**状态：** 待主人审批喵～