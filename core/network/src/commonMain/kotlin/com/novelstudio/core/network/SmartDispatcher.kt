package com.novelstudio.core.network

import com.novelstudio.core.model.DispatchDecision
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.OpusBatteryState
import com.novelstudio.core.model.SubscriptionTier

/**
 * 智能双轨路由状态机（NOVELAI_V5_SPEC.md §3）。
 *
 * - 显式 V5 模式：电量 > 10% 走 V5 电池（0 Anlas），否则弹窗确认扣 Anlas；
 * - 探索抽卡模式：电量 > 30% 走 V5 标准池，≤ 30% 自动切 V4.5 无限池；
 * - V4.5 模型本身不消耗 V5 电池；非免费额度（像素/步数超标）必须走 Anlas 确认；
 * - 非 Opus 订阅没有电池权益，统一走 Anlas 确认。
 */
object SmartDispatcher {

    fun decide(
        parameters: GenerationParameters,
        battery: OpusBatteryState,
        explorationMode: Boolean = false,
    ): DispatchDecision {
        if (parameters.model == NaiModel.V4_5_FULL || parameters.model == NaiModel.V4_5_CURATED) {
            return DispatchDecision.USE_V5_BATTERY // V4.5 不动 V5 电池，0 Anlas
        }
        if (!OpusFreeCalculator.isFreeGeneration(parameters.width, parameters.height, parameters.steps)) {
            return DispatchDecision.CONFIRM_ANLAS // 超出免费额度，必须扣 Anlas
        }
        if (!battery.isOpus) {
            return DispatchDecision.CONFIRM_ANLAS // 非 Opus 无电池权益
        }
        return if (explorationMode) {
            if (battery.batteryPercent > OpusBatteryState.BATTERY_EXPLORATION_LOW) {
                DispatchDecision.USE_V5_BATTERY
            } else {
                DispatchDecision.FALLBACK_V4_5
            }
        } else {
            if (battery.batteryPercent > OpusBatteryState.BATTERY_LOW) {
                DispatchDecision.USE_V5_BATTERY
            } else {
                DispatchDecision.CONFIRM_ANLAS
            }
        }
    }

    /** 探索模式下电量不足时的 V4.5 降级参数（保底抽卡，零损耗） */
    fun degradeToV4_5(parameters: GenerationParameters): GenerationParameters =
        parameters.copy(model = NaiModel.V4_5_FULL)
}
