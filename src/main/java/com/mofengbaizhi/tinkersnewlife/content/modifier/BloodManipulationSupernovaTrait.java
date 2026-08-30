package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 赤血操术·超新星（术式特性，占用术式槽）
 * <p>
 * 指向目标，在目标位置生成微小血球，0.8 秒后爆炸（不破坏方块）：
 * 中心伤害 = (1 + 咒力亲和/100) × 咒力输出 × 消耗血量 × 10。
 * 每次消耗 10% 最大生命值，咒力消耗为「解」的 5/2 倍。
 * 实际逻辑由 {@code TechniqueHandler} 处理，本类仅作注册标记。
 */
public class BloodManipulationSupernovaTrait extends Modifier {
}
