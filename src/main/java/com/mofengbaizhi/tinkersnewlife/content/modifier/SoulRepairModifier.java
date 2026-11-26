package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 强化·灵魂修复（占用升级槽）：每 20 tick 消耗 {@code max(1, 6-等级)} 点灵魂能量
 * 恢复 1 点工具耐久；每次恢复有 {@code 5%×等级} 概率额外恢复 1 点耐久。上限 3 级。
 * <p>等级上限由安装配方控制（单配方 {@code level:3}，1 黑暗金属锭 + 2 活力核心）。
 * 具体逻辑在 {@code content/modifier/events/SoulRepairHandler}（服务端 PlayerTickEvent 驱动，
 * 扫描主手/副手持匠魂工具）。本类仅作为可安装的升级槽强化空壳。
 */
public class SoulRepairModifier extends Modifier {
}
