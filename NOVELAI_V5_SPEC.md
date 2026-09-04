# NovelAI V5 技术规范与用量安全调度 (NOVELAI_V5_SPEC.md)

本文档定义与 NovelAI 官方 API 的通信协议、NovelAI Diffusion V5 模型新特性的适配，以及在避免意外计费前提下利用 Opus 订阅额度的调度规则。

---

## 1. 官方 API 核心端点与鉴权

* **图像服务 Base URL**: `https://image.novelai.net`
* **鉴权方式**: HTTP Header `Authorization: Bearer <API_TOKEN>`
* **核心接口**:
  1. `POST /ai/generate-image`：图像生成主要接口（支持流式与二进制 Zip/PNG 返回）
  2. `POST /ai/encode-vibe`：把用户主动选择的参考图编码为 Vibe（二进制响应）
  3. `GET /user/subscription`：获取订阅档位及 `usage` 状态

`usage` 包含 `isNegative`、`percent` 与 `timeUntilNextPercent`。其中 `percent` 的官方范围为
`[0, 100+]`，不得截断或换算为“剩余可生成张数”；`isNegative=true` 才是当前额度不可用的明确标志。
当前公开响应未提供可依赖的 Anlas 余额字段，客户端不得猜测余额。

`/ai/generate-image` 的二进制响应可能是单个 PNG 或 ZIP。客户端不得假设 ZIP 只包含一张图，必须逐条解析并执行固定安全上限：最多 16 张 PNG、64 个条目、单条目 32 MiB、解压后总量 128 MiB；超限、PNG 签名无效或没有有效 PNG 时拒绝整个响应。每张图先写入临时文件，单图原图与缩略图均成功后才返回正式路径，再批量写入图库数据库；文件与数据库之间采用补偿清理，不宣称跨系统原子事务。

---

## 2. NovelAI Diffusion V5 关键特性与 Payload 规范

### (1) 模型标识枚举
* `nai-diffusion-5-curated`：精选二次元模型
* `nai-diffusion-5-full`：全能模型（支持更复杂的概念组合与文字渲染）
* `nai-diffusion-4-5-full`：V4.5 全功能模型（用于 Opus 无限池保底抽卡）

### (2) V5 专属参数定义
```json
{
  "input": "masterpiece, best quality, 2girls, silver hair, blue eyes, highly detailed",
  "model": "nai-diffusion-5-full",
  "parameters": {
    "width": 1024,
    "height": 1024,
    "scale": 6.0,
    "sampler": "k_euler",
    "steps": 28,
    "n_samples": 1,
    "ucPreset": 0,
    "qualityToggle": true,
    "dynamic_thresholding": false,
    "controlnet_strength": 1.0,
    "legacy": false,
    "add_original_image": true,
    "cfg_rescale": 0.0,
    "noise_schedule": "native",
    "negative_prompt": "lowres, bad anatomy, bad hands, text, error, missing fingers",
    "image_format": "png",
    "straight_alpha": false,
    "tag_hint_transparent_background": false,
    "v4_prompt": {
      "caption": {
        "base_caption": "masterpiece, best quality, 2girls, silver hair, blue eyes, highly detailed",
        "char_captions": [
          {
            "char_caption": "girl, gothic lolita, standing",
            "centers": [{ "x": 0.35, "y": 0.5 }]
          },
          {
            "char_caption": "girl, maid dress, smiling",
            "centers": [{ "x": 0.75, "y": 0.5 }]
          }
        ]
      },
      "use_coords": true,
      "use_order": true,
      "legacy_uc": false
    },
    "v4_negative_prompt": {
      "caption": {
        "base_caption": "lowres, bad anatomy, bad hands",
        "char_captions": [
          { "char_caption": "", "centers": [{ "x": 0.35, "y": 0.5 }] },
          { "char_caption": "", "centers": [{ "x": 0.75, "y": 0.5 }] }
        ]
      },
      "use_coords": true,
      "use_order": true,
      "legacy_uc": false
    }
  }
}
```

* **透明背景**：V5 开关会给基础 Prompt 加入 `transparent background`，并发送公开契约中的
  `image_format=png`、`straight_alpha` 与 `tag_hint_transparent_background`；V4.5 不发送启用态。
* **`v4_prompt` / `v4_negative_prompt`**：尽管字段名保留 `v4_` 前缀，它们是 V4 及以上模型的多角色条件结构。
* **`char_captions[].centers[]`**：V5 支持最多 22 个角色，V4/V4.5 支持最多 6 个角色。
  * 每个中心点只包含 `[0.0, 1.0]` 范围内的 `x` 与 `y`；公开 API 不接收角色框 `width` / `height`。
  * 角色框尺寸只能作为纯 UI 辅助，不能序列化进生成请求；当前客户端直接用固定尺寸圆点避免误导。
* **Vibe Transfer**：先经 `POST /ai/encode-vibe` 编码，再通过三个等长数组发送：
  `reference_image_multiple`、`reference_strength_multiple`、
  `reference_information_extracted_multiple`。最多 16 个；编码以及超过 4 个 Vibe 的生成可能产生
  ImageAnlas 成本，均必须由用户明确动作触发。

---

## 3. Opus 用量安全调度算法

### (1) 免费像素钳位算法 (Opus Max-Free Clamper)
* **规则**：总像素 $W \times H \le 1,048,576$ (即 $1024 \times 1024$)，且必须为 64 的整数倍；步数 $\le 28$ 时享受免费/电池额度。
* **Kotlin 算法实现**：
```kotlin
object OpusFreeCalculator {
    const val MAX_FREE_PIXELS = 1048576 // 1024 * 1024
    const val MAX_FREE_STEPS = 28
    const val GRID_STEP = 64

    fun clampResolution(aspectRatioWidth: Int, aspectRatioHeight: Int): Pair<Int, Int> {
        val ratio = aspectRatioWidth.toDouble() / aspectRatioHeight.toDouble()
        var w = kotlin.math.sqrt(MAX_FREE_PIXELS * ratio).toInt()
        var h = (w / ratio).toInt()
        
        // 对齐 64 倍数
        w = (w / GRID_STEP) * GRID_STEP
        h = (h / GRID_STEP) * GRID_STEP
        
        while (w * h > MAX_FREE_PIXELS) {
            if (w > h) w -= GRID_STEP else h -= GRID_STEP
        }
        return Pair(w.coerceAtLeast(64), h.coerceAtLeast(64))
    }
}
```

### (2) 智能双轨路由状态机 (Smart Dispatcher)

1. 订阅/usage 查询失败时直接停止，生成请求不得提交。
2. 超过常规像素或 28 步、一次多图、超过 4 个 Vibe、非有效 Opus 订阅时，先要求用户确认
   可能消耗 ImageAnlas。
3. V5 以官方 `usage.isNegative` 判断额度是否可用，不再使用未经官方定义的 10% 截止线。
4. 探索模式在 usage 百分比不高于 30% 时可降级到 V4.5；若角色数超过 6、存在模型专属
   Vibe 等无法无损降级的参数，则不自动改模型，改为显示 ImageAnlas 确认。
5. 图像生成和 Vibe 编码均可能计费或已在服务端完成，传输错误后禁止自动重试。应用进程中断后也禁止自动恢复或重提交；遗留运行中任务只能标记为结果未知并等待用户自行判断。

---

## 4. PNG tEXt Chunk 元数据提取规约

NovelAI 官方将生成参数序列化后写入 PNG 格式的 `tEXt` 块，关键字为 `Comment` 或 `Description`（包含 JSON 格式的主提示词、负面提示词、Seed、Sampler 等）。

### Okio 零拷贝解析契约：
1. 读取 PNG 文件头 `89 50 4E 47 0D 0A 1A 0A`；
2. 循环遍历 Chunk 块（Length 4B + Type 4B + Data NB + CRC 4B）；
3. 命中 `tEXt` 且关键字为 `Comment` 时，读取 UTF-8 字符串；
4. 解析 JSON 并映射至 `ImageEntity` 实体，供图库一键回填（Fork）到工作台。
