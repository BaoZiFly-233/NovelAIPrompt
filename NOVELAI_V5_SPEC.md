# NovelAI V5 技术规范与算力套利算法 (NOVELAI_V5_SPEC.md)

本文档定义与 NovelAI 官方 API 的通信协议、NovelAI Diffusion V5 模型新特性的适配，以及最大化利用 Opus 订阅算力的核心调度算法。

---

## 1. 官方 API 核心端点与鉴权

* **Base URL**: `https://api.novelai.net` / `https://image.novelai.net`
* **鉴权方式**: HTTP Header `Authorization: Bearer <API_TOKEN>`
* **核心接口**:
  1. `POST /ai/generate-image`：图像生成主要接口（支持流式与二进制 Zip/PNG 返回）
  2. `GET /user/subscription`：获取当前订阅档位 (Opus/Scroll/Tablet) 与 Anlas 余额
  3. `GET /user/data`：获取账户配置与 V5 Opus 充能电池状态

---

## 2. NovelAI Diffusion V5 关键特性与 Payload 规范

### (1) 模型标识枚举
* `nai-diffusion-5-curated`：精选二次元模型
* `nai-diffusion-5-full`：全能模型（支持更复杂的概念组合与文字渲染）
* `nai-diffusion-4-5-full`：V4.5 全功能模型（用于 Opus 无限池保底抽卡）

### (2) V5 专属参数定义
```json
{
  "input": "masterpiece, best quality, 1girl, solo, silver hair, blue eyes, highly detailed",
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
    "uncond_scale": 1.0,
    "cfg_rescale": 0.0,
    "noise_schedule": "native",
    "negative_prompt": "lowres, bad anatomy, bad hands, text, error, missing fingers",
    "transparent_background": false,
    "character_prompts": [
      {
        "prompt": "1girl, solo, gothic lolita, standing",
        "uc": "",
        "center_x": 0.35,
        "center_y": 0.5,
        "width": 0.4,
        "height": 0.8
      },
      {
        "prompt": "1girl, maid dress, smiling",
        "uc": "",
        "center_x": 0.75,
        "center_y": 0.5,
        "width": 0.35,
        "height": 0.7
      }
    ]
  }
}
```

* **`transparent_background` (Boolean)**: 是否原生输出带 Alpha 透明通道的 PNG。
* **`character_prompts` (Array)**: 支持最多 22 个角色独立坐标定位。
  * `center_x`, `center_y`, `width`, `height` 均为 `[0.0, 1.0]` 归一化浮点数。

---

## 3. Opus 算力套利与动态调度算法

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
```
                  ┌───────── 生成任务触发 ─────────┐
                  │                              │
                  ▼                              ▼
      [ 用户明确指定 V5 模式 ]          [ 探索抽卡模式 (Exploration) ]
                  │                              │
                  ▼                              ▼
          [ 检查 V5 电池电量 ]            [ 检查 V5 电池电量 ]
         /                  \            /                  \
        /                    \          /                    \
   [ > 10% 电量 ]       [ <= 10% 电量 ]  [ > 30% 电量 ]   [ <= 30% 电量 ]
        │                     │              │                 │
        ▼                     ▼              ▼                 ▼
  扣除 V5 电池       弹窗预警/确认扣Anlas  走 V5 标准池      自动切 V4.5 无限池
  (0 Anlas)           (防误扣保护)       (0 Anlas)         (100% 免费/零损耗)
```

---

## 4. PNG tEXt Chunk 元数据提取规约

NovelAI 官方将生成参数序列化后写入 PNG 格式的 `tEXt` 块，关键字为 `Comment` 或 `Description`（包含 JSON 格式的主提示词、负面提示词、Seed、Sampler 等）。

### Okio 零拷贝解析契约：
1. 读取 PNG 文件头 `89 50 4E 47 0D 0A 1A 0A`；
2. 循环遍历 Chunk 块（Length 4B + Type 4B + Data NB + CRC 4B）；
3. 命中 `tEXt` 且关键字为 `Comment` 时，读取 UTF-8 字符串；
4. 解析 JSON 并映射至 `ImageEntity` 实体，供图库一键回填（Fork）到工作台。
