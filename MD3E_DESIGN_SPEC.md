# MD3E (Material 3 Expressive) 设计与动效规约 (MD3E_DESIGN_SPEC.md)

本项目以 **Material 3** 为基础构建跨端创作工作台，并借鉴 Expressive 的层级、色彩和物理反馈。当前实现采用经过对比度校准的静态灰蓝调色板、重点交互弹簧动效与 PC/Android 自适应布局；不把规划中的动态取色写成既有能力。

---

## 1. 响应式布局断点系统 (Adaptive Breakpoints)

依据屏幕可用宽度（`WindowWidthSizeClass`）自动平滑切换布局形态：

| 设备类型 | 宽度断点 (`widthDp`) | 导航容器 | 布局形态 |
| :--- | :--- | :--- | :--- |
| **手机竖屏 (Compact)** | `< 600 dp` | **NavigationBar**（4 个主入口 + 更多） | 单列堆叠 + 参数 Modal BottomSheet |
| **折叠屏/平板 (Medium)**| `600 .. 999 dp` | **NavigationRail**（设置置底） | 单列内容 + 常驻参数区 |
| **PC 桌面/宽屏 (Expanded)**| `≥ 1000 dp` | **NavigationRail**（设置置底） | 工作台双列独立滚动，其余页面宽屏自适应 |

---

## 2. MD3E 核心组件形态

1. **容器、按钮与选择控件**：
   * 页面区块使用低层级 Surface、细描边与 `16.dp` 圆角，按钮使用 `12.dp` 中圆角；
   * Pill 只用于 Chip、状态和分段选择，避免所有控件都呈现为大胶囊；
   * 采用高亮色块突出当前选中的模型、比例与采样器。
2. **静态语义调色板**：
   * 以中性灰蓝作为工作台背景，电光靛蓝为主强调色，青色和暖金仅承担次级语义；
   * Light/Dark 均提供完整 Surface Container 层级，并保证正文、弱化文字与描边可读；
   * 从图片提取 Dominant Color 属于后续增强方向，当前未实现。
3. **Modal BottomSheet with Spring Dynamics**：
   * 移动端工作台参数设置采用向上拉取的 BottomSheet，支持半屏预览与全屏拖拽；
   * 带有手势物理阻尼感。

---

## 3. 弹簧物理与手势动效参数 (Spring Dynamics)

拖拽回弹、缩放复位、卡片抛出等空间运动使用 **Spring Physics**。颜色、透明度和短暂状态切换可使用平台默认时序；动效服务于空间关系，不为装饰而堆叠。

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
* **Prompt 编辑区**：Prompt、偏好建议和负向提示词组合为一个职责明确的区块；自定义 `VisualTransformation` 渲染 `{}` 括号高亮与 `[]` 括号弱化；
* **预览与参数**：宽屏时左侧组织创作输入，右侧固定预览、生成动作、参数和队列，两列独立滚动；窄屏把预览与生成动作前置，详细参数进入 BottomSheet；
* **22 角色定位画板**：Canvas 支持触控与鼠标选择角色，拖动锚点实时调整公开 API 接受的归一化中心点 `x/y`；空列表不渲染无意义的网格。
* **Opus 用量仪表环**：右上角动态环形指示器，以绿色（充足）、黄色（中等）、红橙色（不可用）直观反映 V5 usage 状态；未知状态单独呈现且禁止自动生成。

### (2) 虚拟瀑布流图库 (Gallery)
* **自适应网格**：`LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(200.dp))`；
* **卡片视觉**：图片是第一视觉层，元数据置于稳定的下方信息区；收藏、选择和模型状态使用 Material Icon 与紧凑状态标签，不叠加大面积渐变装饰；
* **悬浮与多选**：桌面悬浮及选中态以描边区分；多选操作栏横向滚动，避免手机窄屏按钮溢出；
* **大图手势**：灯箱支持双击弹簧缩放、1x–5x 捏合与边界内平移；窄屏使用图片在上、可滚动 PNG Info 在下的纵向布局；
* **灯箱过渡**：当前使用独立灯箱 Dialog 与弹簧缩放/平移；`SharedTransitionLayout` 仅为后续视觉增强方向，尚未宣称已实现。

### (3) 对比实验室 (Compare Matrix)
* **Split Slider (卷帘线)**：中央手柄采用悬浮药丸胶囊，左右拖拽通过 Skia `clipRect` 实现低分配像素切割；120fps 目标需在相应设备上实测，不在此处预先承诺。
* **Side-by-Side (双图同动)**：单指/鼠标拖动任意一幅图，另一幅图以完全相同的缩放比例与中心偏移量镜像平移。

### (4) 滑动喜欢/不喜欢卡片流 (Swipe Deck)
* **交互规则**：
  * **左滑 (Swipe Left)**：旋转 $-15^\circ$ 并伴随红色/灰暗色遮罩 ➔ 判定为「不喜欢/归档」；
  * **右滑 (Swipe Right)**：旋转 $+15^\circ$ 并伴随青绿色/金色粒子动效 ➔ 判定为「喜欢/收藏 (Star 5)」；
  * **详情查看**：Swipe 卡片流当前不绑定上滑详情抽屉；完整 PNG Info 由图库灯箱查看；
* **键盘快捷键 (PC 端)**：`←` 不喜欢、`→` 喜欢；不绑定 `Space` 重新生成或付费提交，避免误触产生计费请求。
