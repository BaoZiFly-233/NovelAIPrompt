package com.novelstudio.app.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 不触发网络与付费生成的端到端界面冒烟和帧时序基准。
 * 120Hz 验收必须在 120Hz 实体设备上运行本测试并检查 Macrobenchmark 报告。
 */
@RunWith(AndroidJUnit4::class)
class CoreJourneyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupAndCoreNavigationFrameTiming() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.text("智能工作台")), TIMEOUT_MS))

        openPage("图库", "万级图库")
        openPage("对比", "对比实验室")
        openPage("筛选", "滑动筛选")
        openPage("重绘", "局部重绘")
        openPage("设置", "NovelAI API Token")
        openPage("工作台", "智能工作台")
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openPage(
        navigationLabel: String,
        screenTitle: String,
    ) {
        val navigationItem = checkNotNull(device.findObject(By.text(navigationLabel))) {
            "找不到导航项：$navigationLabel"
        }
        navigationItem.click()
        check(device.wait(Until.hasObject(By.text(screenTitle)), TIMEOUT_MS)) {
            "页面未在时限内显示：$screenTitle"
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.novelstudio.app"
        const val TIMEOUT_MS = 5_000L
    }
}
