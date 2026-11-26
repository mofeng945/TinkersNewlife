package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;

/**
 * 词条·堕落（诅咒金属材料自带）
 * <p>
 * 对目标造成伤害时，消耗 0.5% 灵魂能量上限（单次上限 100 点）的灵魂能量增幅此次攻击：
 * 每 5 点灵魂 +0.2 攻击伤害、+0.05 格击退（击退累计上限 3 格）。灵魂不足时不增幅。
 * 实际逻辑由 {@code content/modifier/events/CorruptionHandler} 处理。
 */
public class CorruptionTrait extends BaseCombatModifier {
}
