package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;

/**
 * 词条·抗魔（黑暗金属材料护甲自带：armor 部件）
 * <p>
 * 受到魔法伤害时，把 {@code 5%×等级 + 1.5} 点魔法伤害转化为物理伤害（等效：直接减免等量魔法伤害）。
 * 实际逻辑由 {@code content/modifier/events/DarkMetalMagicResistHandler} 处理。
 */
public class DarkMetalMagicResistModifier extends BaseCombatModifier {
}
