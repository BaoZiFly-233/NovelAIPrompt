# MD3E (Material 3 Expressive) 设计与动效规约 (MD3E_DESIGN_SPEC.md)

本项目全量遵循 Google 推出的 **Material 3 Expressive (MD3E)** 设计语言，融合 HCT 动态色彩、弹簧物理动效（Spring Physics）与 PC/Android 自适应布局。

---

## 1. 响应式布局断点系统 (Adaptive Breakpoints)

依据屏幕可用宽度（`WindowWidthSizeClass`）自动平滑切换布局形态：

| 设备类型 | 宽度断点 (`widthDp`) | 导航容器 | 布局形态 |
| :--- | :--- | :--- | :--- |
| **手机竖屏 (Compact)** | `< 600 dp` | **NavigationBar** (底部导航栏) | 单列堆叠 + 弹簧 Modal BottomSheet |
| **折叠屏/平板 (Medium)**| `600 .. 839 dp` | **NavigationRail** (左侧窄轨栏) | 双列分栏 (Master-Detail) |
| **PC 桌面/宽屏 (Expanded)**| `≥ 840 dp` | **NavigationRail / PermanentDrawer** | 三栏布局 (导航轨 + 主画布 + 属性抽屉) |

---

## 2. MD3E 核心组件形态

1. **Expressive Button & Segmented Controls**：
   * 药丸型（Pill-shaped）全圆角按钮（`shape = RoundedCornerShape(percent = 50)`）；
   * 采用高亮色块（Filled Tonal）突出当前选中的模型、比例与采样器。
2. **Dynamic Color (HCT) 提取**：
   * 应用主题基色支持随当前选中的生成图片自动提取 Dominant Color（基于 `material-color-utilities`）；
   * 保证在深色/浅色模式下的对比度与易读性。
3. **Modal BottomSheet with Spring Dynamics**：
   * 移动端工作台参数设置采用向上拉取的 BottomSheet，支持半屏预览与全屏拖拽；
   * 带有手势物理阻尼感。

---

## 3. 弹簧物理与手势动效参数 (Spring Dynamics)

禁止使用生硬的线性（Linear）或普通贝塞尔动画，所有位移与缩放全部绑定 **Spring Physics**。

### Compose 弹簧预设参数：
```kotlin
object MD3EMotion {
    // 弹簧跟手动效 (用于卡片拖拽、卷帘比对)
    val SnappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy, // 0.75f
        stiffness = Spring.StiffnessMediumLow           // 400f
    )
    
    // 柔和过渡动效 (用于页面切换、抽屉展开)
    val GentleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,     // 1.0f
        stiffness = Spring.StiffnessLow                 // 200f
    )
    
    // 卡片飞出抛掷动效 (Swipe Deck)
    val ThrowSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,    // 0.6f
        stiffness = Spring.StiffnessMedium              // 800f
    )
}
```

---

## 4. 四大核心模块的 MD3E 交互标准

### (1) 生成工作台 (Workbench)
* **Prompt 编辑区**：自定义 `VisualTransformation` 渲染 `{}` 括号高亮（黄色/橙色渐变加权）与 `[]` 括号（弱化灰色）；
* **22 角色定位画板**：半透明覆层 Canvas，支持多点触控与鼠标框选角色，拖动锚点实时调整 `center_x/y` 与 `width/height`；
* **Opus 电池仪表环**：右上角动态环形指示器，以绿色（充足）、黄色（中等）、红橙色（枯竭）直观反映 V5 充能状态。

### (2) 虚拟瀑布流图库 (Gallery)
* **自适应网格**：`LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(180.dp))`；
* **卡片视觉**：统一圆角 `16.dp`，顶部带有透明渐变遮罩显示 Seed/Model 徽标，右下角带有收藏 Star 胶囊；
* **灯箱过渡**：点击图片使用 `SharedTransitionLayout` 实现无缝放大的共享元素动画。

### (3) 对比实验室 (Compare Matrix)
* **Split Slider (卷帘线)**：中央手柄采用悬浮药丸胶囊，左右拖拽通过 Skia `clipRect` 实现硬件级 120fps 像素切割；
* **Side-by-Side (双图同动)**：单指/鼠标拖动任意一幅图，另一幅图以完全相同的缩放比例与中心偏移量镜像平移。

### (4) 滑动喜欢/不喜欢卡片流 (Swipe Deck)
* **交互规则**：
  * **左滑 (Swipe Left)**：旋转 $-15^\circ$ 并伴随红色/灰暗色遮罩 ➔ 判定为「不喜欢/归档」；
  * **右滑 (Swipe Right)**：旋转 $+15^\circ$ 并伴随青绿色/金色粒子动效 ➔ 判定为「喜欢/收藏 (Star 5)」；
  * **上滑 (Swipe Up)**：呼出完整 PNG Info 详情抽屉；
* **键盘快捷键 (PC 端)**：`←` 不喜欢，`→` 喜欢，`Space` 重新生成，`↑` 展开详情。
