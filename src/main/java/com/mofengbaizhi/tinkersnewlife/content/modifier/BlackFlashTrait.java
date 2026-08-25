package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 黑闪强化（Black Flash）
 * <p>
 * 效果说明：
 * - 每次攻击有 0.01% 基础概率触发黑闪
 * - 黑闪伤害 = 原伤害的 2.5 次方
 * - 触发后 60 秒内，黑闪概率提升 10%（可叠加）
 * - 触发后 60 秒内，玩家速度、攻击伤害、跳跃高度变为 120%
 * </p>
 * <p>
 * 实际逻辑由 BlackFlashHandler 处理
 * </p>
 */
public class BlackFlashTrait extends Modifier {
    // 标记类，所有逻辑在 BlackFlashHandler 中处理
}