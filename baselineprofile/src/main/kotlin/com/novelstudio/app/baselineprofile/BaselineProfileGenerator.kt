package com.novelstudio.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndCoreNavigation() = rule.collect(
        packageName = "com.novelstudio.app",
        maxIterations = 1,
        stableIterations = 1,
    ) {
        pressHome()
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.text("智能工作台")), 5_000))
        checkNotNull(device.findObject(By.text("图库"))) { "找不到图库导航项" }.click()
        check(device.wait(Until.hasObject(By.text("万级图库")), 5_000))
        checkNotNull(device.findObject(By.text("工作台"))) { "找不到工作台导航项" }.click()
        check(device.wait(Until.hasObject(By.text("智能工作台")), 5_000))
    }
}
