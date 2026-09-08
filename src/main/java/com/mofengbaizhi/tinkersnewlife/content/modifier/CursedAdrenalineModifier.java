package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;

/**
 * 词条·咒上·腺速（诅咒金属材料护甲自带：armor 部件）
 * <p>
 * 穿戴者受到敌对伤害或摔落伤害时，消耗 10 点灵魂能量，获得 5 秒迅捷 II
 * （等效移动速度加成，无原版 Speed 的视角变化）；冷却 1 分钟。
 * 实际逻辑由 {@code content/modifier/events/CursedAdrenalineHandler} 处理。
 */
public class CursedAdrenalineModifier extends BaseCombatModifier {
}
