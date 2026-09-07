package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 强化·噬魂（占用升级槽，1 级占 1 槽，2/3 级无槽）：
 * 每级 +25% 灵魂能量获取，持有一级后额外 +1 灵魂能量获取，最高 3 级。
 * <p>实际增幅逻辑在 {@code content/modifier/events/SoulEaterHandler}（服务端 PlayerTick 检测
 * 玩家灵魂增量，持有本强化的装备时按等级补发增幅部分）。
 */
public class SoulEaterModifier extends Modifier {
}
